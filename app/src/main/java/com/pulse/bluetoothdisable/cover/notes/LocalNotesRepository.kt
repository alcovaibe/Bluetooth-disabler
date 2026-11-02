package com.pulse.bluetoothdisable.cover.notes

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent, app-private Notes storage.
 *
 * The encrypted metadata file is written through [AtomicFile]. Attachments are copied into an
 * app-private directory and never depend on a temporary picker URI after import. The repository
 * also understands the original v1 array schema so existing Notes Cover data is migrated without
 * user intervention.
 */
class LocalNotesRepository(context: Context) {
    private val appContext = context.applicationContext
    private val file = AtomicFile(File(appContext.filesDir, FILE_NAME))
    private val imageDir = File(appContext.filesDir, IMAGE_DIR_NAME).apply { mkdirs() }

    fun notes(): List<LocalNote> = synchronized(lock) {
        cleanupTemporaryImages()
        readNotes()
    }

    /** Backward-compatible save API used by Notes Cover setup and generated notes. */
    fun save(
        title: String,
        body: String,
        id: String? = null,
        pinned: Boolean? = null,
    ): LocalNote = synchronized(lock) {
        val previous = id?.let { noteId -> readNotes().firstOrNull { it.id == noteId } }
        val now = System.currentTimeMillis()
        val note = LocalNote(
            id = previous?.id ?: id ?: UUID.randomUUID().toString(),
            title = NotesPolicy.normalizeTitle(title),
            body = NotesPolicy.normalizeBody(body),
            createdAt = previous?.createdAt ?: now,
            updatedAt = now,
            favorite = pinned ?: previous?.favorite ?: false,
            type = previous?.type ?: NoteType.TEXT,
            styles = previous?.styles.orEmpty(),
            images = previous?.images.orEmpty(),
            checklist = previous?.checklist.orEmpty(),
        )
        require(NotesPolicy.isPersistable(note))
        upsertLocked(note)
    }

    fun upsert(note: LocalNote): LocalNote = synchronized(lock) {
        require(NotesPolicy.isPersistable(note))
        upsertLocked(
            note.copy(
                title = NotesPolicy.normalizeTitle(note.title),
                body = NotesPolicy.normalizeBody(note.body),
                styles = NotesPolicy.normalizeStyles(note.body, note.styles),
                images = note.images.map { it.copy(offset = it.offset.coerceIn(0, note.body.length)) },
                checklist = note.checklist.filter { it.text.length <= NotesPolicy.MAX_CHECKLIST_ITEM_LENGTH },
            ),
        )
    }

    fun delete(id: String) = deleteAll(setOf(id))

    /** Commit a selection in one metadata write before removing orphaned attachments. */
    fun deleteAll(ids: Set<String>) = synchronized(lock) {
        if (ids.isEmpty()) return@synchronized
        val notes = readNotes()
        writeNotes(notes.filterNot { it.id in ids })
    }

    fun setFavorite(id: String, favorite: Boolean): LocalNote = synchronized(lock) {
        val notes = readNotes()
        val previous = notes.firstOrNull { it.id == id } ?: error("Unknown note")
        // Favourite is metadata, not content editing: keep updatedAt so the fixed last-edited
        // ordering does not jump merely because the star was toggled.
        val updated = previous.copy(favorite = favorite)
        writeNotes(notes.map { if (it.id == id) updated else it })
        updated
    }

    /** Compatibility wrapper retained for older setup call sites. */
    fun setPinned(id: String, pinned: Boolean): LocalNote = setFavorite(id, pinned)

    fun importImage(noteId: String, uri: Uri, offset: Int): NoteImage = synchronized(lock) {
        require(noteId.isNotBlank())
        val fileName = "${noteId}_${UUID.randomUUID()}.img"
        val destination = File(imageDir, fileName)
        val temporary = File(imageDir, "$fileName.tmp")
        try {
            appContext.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open selected image" }
                temporary.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            require(temporary.length() in 1..NotesPolicy.MAX_IMAGE_BYTES) { "Image is empty or too large" }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            NoteImage(UUID.randomUUID().toString(), fileName, offset.coerceAtLeast(0))
        } catch (error: Exception) {
            temporary.delete()
            destination.delete()
            throw error
        }
    }

    fun imageFile(image: NoteImage): File? =
        File(imageDir, image.fileName).takeIf { it.exists() && it.isFile }

    /** Remove files that are not referenced by the durable encrypted metadata. */
    fun cleanupOrphans() = synchronized(lock) {
        cleanupOrphanImages(readNotes())
    }

    /** Test/reset helper only. Normal Cover Mode transitions must never call this. */
    internal fun clear() = synchronized(lock) {
        file.delete()
        imageDir.listFiles()?.forEach(File::delete)
        NotesCipher.clearKey()
    }

    private fun upsertLocked(note: LocalNote): LocalNote {
        val notes = readNotes().toMutableList()
        val previous = notes.firstOrNull { it.id == note.id }
        val normalized = note.copy(createdAt = previous?.createdAt ?: note.createdAt)
        notes.removeAll { it.id == normalized.id }
        notes.add(normalized)
        writeNotes(notes)
        return normalized
    }

    private fun readNotes(): List<LocalNote> {
        val content = try {
            file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: FileNotFoundException) {
            return emptyList()
        }
        val plain = NotesCipher.decrypt(content)
        val array = if (plain.trimStart().startsWith("[")) JSONArray(plain)
        else JSONObject(plain).getJSONArray("notes")
        return (0 until array.length()).map { index -> decodeNote(array.getJSONObject(index)) }
            .sortedByDescending { it.updatedAt }
    }

    private fun decodeNote(item: JSONObject): LocalNote {
        val body = item.optString("body", "")
        val styles = item.optJSONArray("styles")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                runCatching {
                    val style = array.getJSONObject(index)
                    NoteTextStyle(
                        start = style.getInt("start"),
                        end = style.getInt("end"),
                        bold = style.optBoolean("bold", false),
                        italic = style.optBoolean("italic", false),
                        underline = style.optBoolean("underline", false),
                        strikeThrough = style.optBoolean("strikeThrough", false),
                    )
                }.getOrNull()
            }
        }.orEmpty()
        val images = item.optJSONArray("images")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                runCatching {
                    val image = array.getJSONObject(index)
                    NoteImage(
                        id = image.getString("id"),
                        fileName = image.getString("fileName"),
                        offset = image.optInt("offset", body.length).coerceIn(0, body.length),
                    )
                }.getOrNull()
            }
        }.orEmpty()
        val checklist = item.optJSONArray("checklist")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                runCatching {
                    val row = array.getJSONObject(index)
                    ChecklistItem(
                        id = row.getString("id"),
                        text = row.optString("text", ""),
                        checked = row.optBoolean("checked", false),
                    )
                }.getOrNull()
            }
        }.orEmpty()
        return LocalNote(
            id = item.getString("id"),
            title = item.optString("title", ""),
            body = body,
            createdAt = item.getLong("createdAt"),
            updatedAt = item.getLong("updatedAt"),
            favorite = item.optBoolean("favorite", item.optBoolean("pinned", false)),
            type = runCatching { NoteType.valueOf(item.optString("type", NoteType.TEXT.name)) }
                .getOrDefault(NoteType.TEXT),
            styles = NotesPolicy.normalizeStyles(body, styles),
            images = images,
            checklist = checklist,
        )
    }

    private fun writeNotes(notes: List<LocalNote>) {
        val array = JSONArray().apply { notes.forEach { put(encodeNote(it)) } }
        val json = JSONObject().apply { put("version", 2); put("notes", array) }.toString()
        val encrypted = NotesCipher.encrypt(json)
        val stream = file.startWrite()
        try {
            stream.write(encrypted)
            file.finishWrite(stream)
            // Cleanup happens only after the new metadata is durable. Doing it from notes() could
            // race a newly imported image before its metadata update reached disk.
            cleanupOrphanImages(notes)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    private fun encodeNote(note: LocalNote): JSONObject = JSONObject().apply {
        put("id", note.id); put("title", note.title); put("body", note.body)
        put("createdAt", note.createdAt); put("updatedAt", note.updatedAt)
        put("favorite", note.favorite); put("type", note.type.name)
        put("styles", JSONArray().apply {
            note.styles.forEach { style -> put(JSONObject().apply {
                put("start", style.start); put("end", style.end); put("bold", style.bold)
                put("italic", style.italic); put("underline", style.underline)
                put("strikeThrough", style.strikeThrough)
            }) }
        })
        put("images", JSONArray().apply {
            note.images.forEach { image -> put(JSONObject().apply {
                put("id", image.id); put("fileName", image.fileName); put("offset", image.offset)
            }) }
        })
        put("checklist", JSONArray().apply {
            note.checklist.forEach { row -> put(JSONObject().apply {
                put("id", row.id); put("text", row.text); put("checked", row.checked)
            }) }
        })
    }

    private fun cleanupTemporaryImages() {
        imageDir.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach(File::delete)
    }

    private fun cleanupOrphanImages(notes: List<LocalNote>) {
        val referenced = notes.flatMapTo(mutableSetOf()) { note -> note.images.map { it.fileName } }
        imageDir.listFiles()?.forEach { candidate ->
            if (candidate.name.endsWith(".tmp") || (candidate.isFile && candidate.name !in referenced)) {
                candidate.delete()
            }
        }
    }

    companion object {
        const val FILE_NAME = "local_notes_v1.json"
        const val IMAGE_DIR_NAME = "notes_images"
        private val lock = Any()
    }
}
