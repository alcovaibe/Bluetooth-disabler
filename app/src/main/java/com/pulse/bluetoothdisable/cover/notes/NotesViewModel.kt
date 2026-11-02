package com.pulse.bluetoothdisable.cover.notes

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class NotesUiState(
    val notes: List<LocalNote> = emptyList(),
    val loading: Boolean = true,
    val storageError: Boolean = false,
    val selectedNoteId: String? = null,
    val selectedNoteIds: Set<String> = emptySet(),
    val editorOpen: Boolean = false,
    val draft: LocalNote? = null,
    val draftIsNew: Boolean = false,
    val busy: Boolean = false,
)

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocalNotesRepository(application)
    private val access = NotesAccessManager(application)
    private val language = application.resources.configuration.locales[0].language
    private val persistMutex = Mutex()
    private var persistJob: Job? = null

    var uiState by mutableStateOf(NotesUiState())
        private set

    init {
        refresh()
    }

    fun open(note: LocalNote) {
        if (uiState.busy) return
        uiState = uiState.copy(selectedNoteId = note.id, selectedNoteIds = emptySet())
    }

    fun toggleSelection(note: LocalNote) {
        if (uiState.busy || uiState.notes.none { it.id == note.id }) return
        val ids = uiState.selectedNoteIds
        uiState = uiState.copy(selectedNoteIds = if (note.id in ids) ids - note.id else ids + note.id)
    }

    fun clearSelection() {
        uiState = uiState.copy(selectedNoteIds = emptySet())
    }

    fun closeNote() {
        uiState = uiState.copy(selectedNoteId = null)
    }

    fun add() = startNew(NoteType.TEXT)

    fun addChecklist() = startNew(NoteType.CHECKLIST)

    private fun startNew(type: NoteType) {
        if (uiState.busy) return
        val now = System.currentTimeMillis()
        uiState = uiState.copy(
            editorOpen = true,
            draftIsNew = true,
            draft = LocalNote(
                id = UUID.randomUUID().toString(),
                title = "",
                body = "",
                createdAt = now,
                updatedAt = now,
                favorite = false,
                type = type,
                checklist = if (type == NoteType.CHECKLIST) {
                    listOf(ChecklistItem(UUID.randomUUID().toString(), "", false))
                } else {
                    emptyList()
                },
            ),
        )
    }

    fun edit(note: LocalNote) {
        if (uiState.busy) return
        uiState = uiState.copy(editorOpen = true, draft = note, draftIsNew = false)
    }

    fun isSecretNote(note: LocalNote): Boolean = access.isSecretNote(note.id)

    fun closeEditor() {
        val state = uiState
        val draft = state.draft
        persistJob?.cancel()
        if (draft != null && NotesPolicy.isPersistable(draft)) {
            persistSnapshot(draft, closeAfter = true)
        } else {
            uiState = state.copy(editorOpen = false, draft = null, draftIsNew = false)
            viewModelScope.launch(Dispatchers.IO) {
                persistMutex.withLock { repository.cleanupOrphans() }
            }
        }
    }

    /** Flush the current editor state when the Activity is backgrounded. */
    fun flushEditor() {
        persistJob?.cancel()
        val draft = uiState.draft ?: return
        if (NotesPolicy.isPersistable(draft)) persistSnapshot(draft)
    }

    fun updateTitle(value: String) {
        if (value.length > NotesPolicy.MAX_TITLE_LENGTH) return
        updateDraft { it.copy(title = value, updatedAt = System.currentTimeMillis()) }
    }

    fun updateBody(value: String, styles: List<NoteTextStyle>) {
        if (value.length > NotesPolicy.MAX_BODY_LENGTH) return
        val old = uiState.draft ?: return
        val adjusted = if (styles === old.styles) adjustStyles(old.body, value, old.styles) else styles
        updateDraft {
            it.copy(
                body = value,
                styles = NotesPolicy.normalizeStyles(value, adjusted),
                images = it.images.map { image -> image.copy(offset = image.offset.coerceIn(0, value.length)) },
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    fun toggleStyle(start: Int, end: Int, style: TextStyleKind) {
        val draft = uiState.draft ?: return
        val from = minOf(start, end).coerceIn(0, draft.body.length)
        val to = maxOf(start, end).coerceIn(0, draft.body.length)
        if (from == to) return
        val exact = draft.styles.indexOfFirst { span ->
            span.start == from && span.end == to && when (style) {
                TextStyleKind.BOLD -> span.bold
                TextStyleKind.ITALIC -> span.italic
                TextStyleKind.UNDERLINE -> span.underline
                TextStyleKind.STRIKE -> span.strikeThrough
            }
        }
        updateDraft { current ->
            val styles = current.styles.toMutableList()
            if (exact >= 0) {
                styles.removeAt(exact)
            } else {
                styles += NoteTextStyle(
                    start = from,
                    end = to,
                    bold = style == TextStyleKind.BOLD,
                    italic = style == TextStyleKind.ITALIC,
                    underline = style == TextStyleKind.UNDERLINE,
                    strikeThrough = style == TextStyleKind.STRIKE,
                )
            }
            current.copy(styles = styles, updatedAt = System.currentTimeMillis())
        }
    }

    fun importImage(uri: Uri, offset: Int) {
        val draft = uiState.draft ?: return
        if (draft.type != NoteType.TEXT || uiState.busy) return
        uiState = uiState.copy(busy = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.importImage(draft.id, uri, offset.coerceIn(0, draft.body.length)) }
            }
            if (result.isFailure) {
                uiState = uiState.copy(busy = false, storageError = true)
                return@launch
            }
            val image = result.getOrThrow()
            val current = uiState.draft
            if (current == null || current.id != draft.id) {
                withContext(Dispatchers.IO) {
                    persistMutex.withLock { repository.cleanupOrphans() }
                }
                uiState = uiState.copy(busy = false)
                return@launch
            }
            uiState = uiState.copy(
                busy = false,
                draft = current.copy(
                    images = (current.images + image).sortedBy { it.offset },
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            schedulePersist()
        }
    }

    fun removeImage(image: NoteImage) {
        val draft = uiState.draft ?: return
        if (image !in draft.images) return
        uiState = uiState.copy(
            draft = draft.copy(
                images = draft.images.filterNot { it.id == image.id },
                updatedAt = System.currentTimeMillis(),
            ),
        )
        // Do not delete the file before the metadata update is durable. Repository cleanup runs
        // after successful AtomicFile commit and removes the now-unreferenced attachment.
        schedulePersist()
    }

    fun addChecklistItem() {
        updateDraft { draft ->
            if (draft.type != NoteType.CHECKLIST) return@updateDraft draft
            draft.copy(
                checklist = draft.checklist + ChecklistItem(UUID.randomUUID().toString(), "", false),
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    fun updateChecklistItem(id: String, text: String) {
        if (text.length > NotesPolicy.MAX_CHECKLIST_ITEM_LENGTH) return
        updateDraft { draft ->
            draft.copy(
                checklist = draft.checklist.map { if (it.id == id) it.copy(text = text) else it },
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    fun toggleChecklistItem(id: String) {
        updateDraft { draft ->
            draft.copy(
                checklist = draft.checklist.map { if (it.id == id) it.copy(checked = !it.checked) else it },
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    fun toggleChecklistItem(note: LocalNote, id: String) {
        if (uiState.busy || note.type != NoteType.CHECKLIST) return
        mutate {
            repository.upsert(
                note.copy(
                    checklist = note.checklist.map { if (it.id == id) it.copy(checked = !it.checked) else it },
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun removeChecklistItem(id: String) {
        updateDraft { draft ->
            draft.copy(
                checklist = draft.checklist.filterNot { it.id == id },
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    fun delete(note: LocalNote) = deleteNotes(setOf(note.id))

    fun deleteNotes(ids: Set<String>) {
        if (ids.isEmpty()) return
        mutate {
            val deletesSecret = ids.any(access::isSecretNote)
            repository.deleteAll(ids)
            if (deletesSecret) access.clear()
        }
    }

    fun setFavorite(note: LocalNote, favorite: Boolean) = mutate {
        repository.setFavorite(note.id, favorite)
    }

    /** Compatibility with existing callers/tests. */
    fun setPinned(note: LocalNote, pinned: Boolean) = setFavorite(note, pinned)

    fun generate(count: Int) {
        if (uiState.busy || count !in setOf(2, 5, 7, 10)) return
        mutate {
            NotesGenerator.generate(count, language).forEach { generated ->
                repository.save(generated.title, generated.body)
            }
        }
    }

    fun tap(note: LocalNote, offset: Int, onUnlock: () -> Unit) {
        if (uiState.busy) return
        viewModelScope.launch {
            val matches = withContext(Dispatchers.IO) { access.matchesTap(note.id, note.body, offset) }
            if (matches) onUnlock()
        }
    }

    fun imageFile(image: NoteImage) = repository.imageFile(image)

    private fun updateDraft(transform: (LocalNote) -> LocalNote) {
        val draft = uiState.draft ?: return
        if (uiState.busy) return
        uiState = uiState.copy(draft = transform(draft))
        schedulePersist()
    }

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(150)
            val draft = uiState.draft ?: return@launch
            if (NotesPolicy.isPersistable(draft)) persistSnapshot(draft)
            else withContext(Dispatchers.IO) {
                persistMutex.withLock { repository.cleanupOrphans() }
            }
        }
    }

    private fun persistSnapshot(snapshot: LocalNote, closeAfter: Boolean = false) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                persistMutex.withLock {
                    runCatching {
                        val previous = repository.notes().firstOrNull { it.id == snapshot.id }
                        val saved = repository.upsert(snapshot)
                        if (previous != null && access.isSecretNote(previous.id) &&
                            !access.noteStillMatches(saved.id, saved.body)
                        ) {
                            // The UI warns before editing the hidden-access note. If the signed
                            // fragment no longer matches, remove only the obsolete verifier.
                            access.clear()
                        }
                        saved to repository.notes()
                    }
                }
            }
            if (result.isSuccess) {
                val (saved, notes) = result.getOrThrow()
                val currentDraft = uiState.draft
                val sameDraft = currentDraft?.id == saved.id
                uiState = uiState.copy(
                    notes = notes,
                    storageError = false,
                    draftIsNew = if (sameDraft) false else uiState.draftIsNew,
                    editorOpen = if (closeAfter && sameDraft) false else uiState.editorOpen,
                    draft = if (closeAfter && sameDraft) null else currentDraft,
                )
            } else {
                uiState = uiState.copy(storageError = true)
            }
        }
    }

    private fun mutate(action: () -> Unit) {
        if (uiState.busy) return
        uiState = uiState.copy(busy = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                persistMutex.withLock {
                    runCatching {
                        action()
                        repository.notes()
                    }
                }
            }
            uiState = if (result.isSuccess) {
                val notes = result.getOrThrow()
                val selected = uiState.selectedNoteId?.takeIf { id -> notes.any { it.id == id } }
                uiState.copy(
                    notes = notes,
                    selectedNoteId = selected,
                    selectedNoteIds = uiState.selectedNoteIds.intersect(notes.map { it.id }.toSet()),
                    busy = false,
                    storageError = false,
                )
            } else {
                uiState.copy(busy = false, storageError = true)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repository.notes() } }
            uiState = uiState.copy(
                notes = result.getOrDefault(emptyList()),
                selectedNoteIds = if (result.isSuccess) {
                    uiState.selectedNoteIds.intersect(result.getOrThrow().map { it.id }.toSet())
                } else uiState.selectedNoteIds,
                loading = false,
                storageError = result.isFailure,
            )
        }
    }

    fun resetTransientUi() {
        persistJob?.cancel()
        uiState = uiState.copy(selectedNoteId = null, selectedNoteIds = emptySet(), editorOpen = false, draft = null, draftIsNew = false)
    }

    private fun adjustStyles(oldText: String, newText: String, styles: List<NoteTextStyle>): List<NoteTextStyle> {
        if (oldText == newText || styles.isEmpty()) return styles
        val prefix = oldText.zip(newText).takeWhile { (a, b) -> a == b }.size
        var suffix = 0
        val maxSuffix = minOf(oldText.length - prefix, newText.length - prefix)
        while (suffix < maxSuffix && oldText[oldText.lastIndex - suffix] == newText[newText.lastIndex - suffix]) suffix++
        val oldChangeEnd = oldText.length - suffix
        val delta = newText.length - oldText.length
        return styles.mapNotNull { span ->
            val shifted = when {
                span.end <= prefix -> span
                span.start >= oldChangeEnd -> span.copy(start = span.start + delta, end = span.end + delta)
                else -> span.copy(end = span.end + delta)
            }
            val start = shifted.start.coerceIn(0, newText.length)
            val end = shifted.end.coerceIn(start, newText.length)
            if (start == end) null else shifted.copy(start = start, end = end)
        }
    }
}

enum class TextStyleKind { BOLD, ITALIC, UNDERLINE, STRIKE }
