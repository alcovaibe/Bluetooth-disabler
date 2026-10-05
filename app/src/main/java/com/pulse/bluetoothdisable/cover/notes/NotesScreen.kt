package com.pulse.bluetoothdisable.cover.notes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.pulse.bluetoothdisable.R
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val NOTES_RECOVERY_HOLD_MILLIS = 3_000L

private enum class SecretAction { EDIT, DELETE }

@Composable
fun NotesScreen(
    viewModel: NotesViewModel,
    recoveryEnabled: Boolean = false,
    onRecoveryHold: () -> Unit = {},
    onUnlock: () -> Unit,
) {
    val state = viewModel.uiState
    val selected = state.selectedNoteId?.let { id -> state.notes.firstOrNull { it.id == id } }
    var deleting by remember { mutableStateOf<LocalNote?>(null) }
    var secretAction by remember { mutableStateOf<Pair<SecretAction, LocalNote>?>(null) }

    BackHandler(enabled = state.editorOpen || selected != null) {
        if (state.editorOpen) viewModel.closeEditor() else viewModel.closeNote()
    }

    when {
        state.editorOpen && state.draft != null -> NoteEditorScreen(
            draft = state.draft,
            busy = state.busy,
            storageError = state.storageError,
            viewModel = viewModel,
            onBack = viewModel::closeEditor,
        )

        selected != null -> NoteDetailScreen(
            note = selected,
            viewModel = viewModel,
            onBack = viewModel::closeNote,
            onUnlock = onUnlock,
            onFavorite = { viewModel.setFavorite(selected, !selected.favorite) },
            onEdit = {
                if (viewModel.isSecretNote(selected)) secretAction = SecretAction.EDIT to selected
                else viewModel.edit(selected)
            },
            onDelete = {
                if (viewModel.isSecretNote(selected)) secretAction = SecretAction.DELETE to selected
                else deleting = selected
            },
        )

        else -> NotesListScreen(
            state = state,
            onOpen = viewModel::open,
            onAdd = viewModel::add,
            onAddChecklist = viewModel::addChecklist,
            onRetry = viewModel::refresh,
            recoveryEnabled = recoveryEnabled,
            onRecoveryHold = onRecoveryHold,
        )
    }

    secretAction?.let { (action, note) ->
        AlertDialog(
            onDismissRequest = { secretAction = null },
            title = { Text(stringResource(R.string.notes_hidden_access_warning_title)) },
            text = {
                Text(
                    stringResource(
                        if (action == SecretAction.EDIT) R.string.notes_hidden_access_edit_warning
                        else R.string.notes_hidden_access_delete_warning,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        secretAction = null
                        if (action == SecretAction.EDIT) viewModel.edit(note) else deleting = note
                    },
                ) { Text(stringResource(R.string.continue_action)) }
            },
            dismissButton = {
                TextButton(onClick = { secretAction = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    deleting?.let { note ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.notes_delete_note)) },
            text = { Text(stringResource(R.string.notes_delete_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        viewModel.delete(note)
                    },
                ) {
                    Text(stringResource(R.string.notes_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesListScreen(
    state: NotesUiState,
    onOpen: (LocalNote) -> Unit,
    onAdd: () -> Unit,
    onAddChecklist: () -> Unit,
    onRetry: () -> Unit,
    recoveryEnabled: Boolean,
    onRecoveryHold: () -> Unit,
) {
    var suppressNextClick by remember { mutableStateOf(false) }

    // If authentication/confirmation temporarily disables the hold listener and later returns to
    // idle, make sure a cancelled long hold cannot eat the user's next ordinary tap.
    LaunchedEffect(recoveryEnabled) {
        if (recoveryEnabled) suppressNextClick = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.launcher_name_notes))
                        if (state.notes.isNotEmpty()) {
                            Text(
                                stringResource(R.string.notes_count, state.notes.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onAddChecklist, enabled = !state.busy) {
                        Icon(
                            Icons.Rounded.Checklist,
                            contentDescription = stringResource(R.string.notes_new_checklist),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (suppressNextClick) suppressNextClick = false else onAdd()
                },
                modifier = Modifier.notesRecoveryHold(
                    enabled = recoveryEnabled && !state.busy,
                    onHold = {
                        suppressNextClick = true
                        onRecoveryHold()
                    },
                ),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.notes_add))
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                state.storageError -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.notes_storage_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.notes_retry)) }
                }
                state.notes.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.notes_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.notes_empty_hint), style = MaterialTheme.typography.bodySmall)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(
                        count = state.notes.size,
                        key = { index -> state.notes[index].id },
                    ) { index ->
                        val note = state.notes[index]
                        NoteListItem(note = note, onClick = { onOpen(note) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteListItem(note: LocalNote, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) { DateTimeFormatter.ofPattern("d MMMM, HH:mm", locale) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            dateFormatter.format(Instant.ofEpochMilli(note.createdAt).atZone(ZoneId.systemDefault())),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        note.title.ifBlank { stringResource(R.string.notes_note) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (note.favorite) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.Favorite,
                            contentDescription = stringResource(R.string.notes_favorite),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                // Reserve the same line slots in every card; they also scale with the system font.
                Text(
                    notePreview(note),
                    minLines = 4,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (note.images.isNotEmpty()) {
                        stringResource(R.string.notes_images_count, note.images.size)
                    } else {
                        ""
                    },
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun notePreview(note: LocalNote): String = when (note.type) {
    NoteType.TEXT -> note.body.trim()
    NoteType.CHECKLIST -> note.checklist.filter { it.text.isNotBlank() }
        .joinToString("\n") { "• ${it.text}" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteDetailScreen(
    note: LocalNote,
    viewModel: NotesViewModel,
    onBack: () -> Unit,
    onUnlock: () -> Unit,
    onFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.notes_back),
                        )
                    }
                },
                title = {
                    Text(
                        note.title.ifBlank { stringResource(R.string.notes_note) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        },
        bottomBar = {
            NotesActionDock(
                favorite = note.favorite,
                onFavorite = onFavorite,
                onEdit = onEdit,
                onDelete = onDelete,
                modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (note.title.isNotBlank()) {
                item { Text(note.title, style = MaterialTheme.typography.headlineSmall) }
            }
            when (note.type) {
                NoteType.TEXT -> addRichTextItems(note, viewModel, onUnlock)
                NoteType.CHECKLIST -> {
                    items(
                        count = note.checklist.size,
                        key = { index -> note.checklist[index].id },
                    ) { index ->
                        val row = note.checklist[index]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = row.checked,
                                onCheckedChange = { viewModel.toggleChecklistItem(note, row.id) },
                            )
                            Text(
                                row.text,
                                modifier = Modifier.weight(1f),
                                textDecoration = if (row.checked) TextDecoration.LineThrough else null,
                                color = if (row.checked) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(
                        R.string.notes_last_edited,
                        DateUtils.getRelativeTimeSpanString(note.updatedAt),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.addRichTextItems(
    note: LocalNote,
    viewModel: NotesViewModel,
    onUnlock: () -> Unit,
) {
    val sortedImages = note.images.sortedBy { it.offset }
    var cursor = 0
    sortedImages.forEach { image ->
        val end = image.offset.coerceIn(cursor, note.body.length)
        if (end > cursor) {
            val from = cursor
            val to = end
            item(key = "text-$from-$to") {
                RichTextSegment(
                    note = note,
                    start = from,
                    end = to,
                    onTap = { offset -> viewModel.tap(note, offset, onUnlock) },
                )
            }
        }
        item(key = "image-${image.id}") {
            NoteImagePreview(file = viewModel.imageFile(image), modifier = Modifier.fillMaxWidth())
        }
        cursor = end
    }
    if (cursor < note.body.length || (note.body.isEmpty() && note.images.isEmpty())) {
        val from = cursor
        val to = note.body.length
        item(key = "text-tail-$from-$to") {
            RichTextSegment(
                note = note,
                start = from,
                end = to,
                onTap = { offset -> viewModel.tap(note, offset, onUnlock) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditorScreen(
    draft: LocalNote,
    busy: Boolean,
    storageError: Boolean,
    viewModel: NotesViewModel,
    onBack: () -> Unit,
) {
    var textValue by remember(draft.id) {
        mutableStateOf(TextFieldValue(text = draft.body, selection = TextRange(draft.body.length)))
    }
    LaunchedEffect(draft.body) {
        if (textValue.text != draft.body) {
            val cursor = textValue.selection.start.coerceAtMost(draft.body.length)
            textValue = TextFieldValue(text = draft.body, selection = TextRange(cursor))
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importImage(uri, textValue.selection.start)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !busy) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.notes_back),
                        )
                    }
                },
                title = {
                    Text(
                        draft.title.ifBlank { stringResource(R.string.notes_note) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp).size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                TextField(
                    value = draft.title,
                    onValueChange = viewModel::updateTitle,
                    placeholder = { Text(stringResource(R.string.notes_title_label)) },
                    singleLine = true,
                    enabled = !busy,
                    colors = transparentTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.titleLarge,
                )
            }

            if (storageError) {
                item { Text(stringResource(R.string.notes_storage_error), color = MaterialTheme.colorScheme.error) }
            }

            when (draft.type) {
                NoteType.TEXT -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FormatButton(Icons.Rounded.FormatBold, R.string.notes_format_bold) {
                                viewModel.toggleStyle(
                                    textValue.selection.start,
                                    textValue.selection.end,
                                    TextStyleKind.BOLD,
                                )
                            }
                            FormatButton(Icons.Rounded.FormatItalic, R.string.notes_format_italic) {
                                viewModel.toggleStyle(
                                    textValue.selection.start,
                                    textValue.selection.end,
                                    TextStyleKind.ITALIC,
                                )
                            }
                            FormatButton(Icons.Rounded.FormatUnderlined, R.string.notes_format_underline) {
                                viewModel.toggleStyle(
                                    textValue.selection.start,
                                    textValue.selection.end,
                                    TextStyleKind.UNDERLINE,
                                )
                            }
                            FormatButton(Icons.Rounded.FormatStrikethrough, R.string.notes_format_strike) {
                                viewModel.toggleStyle(
                                    textValue.selection.start,
                                    textValue.selection.end,
                                    TextStyleKind.STRIKE,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            FilledTonalIconButton(
                                onClick = {
                                    picker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                                enabled = !busy,
                            ) {
                                Icon(
                                    Icons.Rounded.AddPhotoAlternate,
                                    contentDescription = stringResource(R.string.notes_add_image),
                                )
                            }
                        }
                    }
                    item {
                        TextField(
                            value = textValue,
                            onValueChange = { changed ->
                                if (changed.text.length <= NotesPolicy.MAX_BODY_LENGTH) {
                                    textValue = changed
                                    viewModel.updateBody(changed.text, draft.styles)
                                }
                            },
                            enabled = !busy,
                            placeholder = { Text(stringResource(R.string.notes_body_label)) },
                            minLines = 10,
                            colors = transparentTextFieldColors(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp),
                        )
                    }
                    if (draft.images.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.notes_images_in_note),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        items(
                            count = draft.images.size,
                            key = { index -> draft.images[index].id },
                        ) { index ->
                            val image = draft.images.sortedBy { it.offset }[index]
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                NoteImagePreview(
                                    file = viewModel.imageFile(image),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        stringResource(R.string.notes_image_position, image.offset),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(
                                        onClick = { viewModel.removeImage(image) },
                                        enabled = !busy,
                                    ) { Text(stringResource(R.string.notes_delete)) }
                                }
                            }
                        }
                    }
                }

                NoteType.CHECKLIST -> {
                    items(
                        count = draft.checklist.size,
                        key = { index -> draft.checklist[index].id },
                    ) { index ->
                        val row = draft.checklist[index]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = row.checked,
                                onCheckedChange = { viewModel.toggleChecklistItem(row.id) },
                                enabled = !busy,
                            )
                            OutlinedTextField(
                                value = row.text,
                                onValueChange = { viewModel.updateChecklistItem(row.id, it) },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(stringResource(R.string.notes_checklist_item)) },
                            )
                            IconButton(
                                onClick = { viewModel.removeChecklistItem(row.id) },
                                enabled = !busy,
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.notes_delete),
                                )
                            }
                        }
                    }
                    item {
                        TextButton(onClick = viewModel::addChecklistItem, enabled = !busy) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.notes_add_checklist_item))
                        }
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.notes_autosave_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FormatButton(icon: ImageVector, labelRes: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = stringResource(labelRes))
    }
}

@Composable
private fun NotesActionDock(
    favorite: Boolean,
    onFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NoteActionItem(
                icon = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = stringResource(if (favorite) R.string.notes_unpin else R.string.notes_pin),
                tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                onClick = onFavorite,
                modifier = Modifier.weight(1f),
            )
            NoteActionItem(
                icon = Icons.Rounded.Edit,
                label = stringResource(R.string.notes_edit),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onEdit,
                modifier = Modifier.weight(1f),
            )
            NoteActionItem(
                icon = Icons.Rounded.Delete,
                label = stringResource(R.string.notes_delete),
                tint = MaterialTheme.colorScheme.error,
                onClick = onDelete,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NoteActionItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .semantics { role = Role.Button }
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RichTextSegment(note: LocalNote, start: Int, end: Int, onTap: (Int) -> Unit) {
    val safeStart = start.coerceIn(0, note.body.length)
    val safeEnd = end.coerceIn(safeStart, note.body.length)
    val text = note.body.substring(safeStart, safeEnd)
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = styledSlice(note, safeStart, safeEnd),
        style = MaterialTheme.typography.bodyLarge,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(text) {
                detectTapGestures { position ->
                    val result = layout ?: return@detectTapGestures
                    if (text.isNotEmpty()) {
                        val local = result.getOffsetForPosition(position).coerceIn(0, text.lastIndex)
                        onTap(safeStart + local)
                    }
                }
            },
    )
}

private fun styledSlice(note: LocalNote, start: Int, end: Int): AnnotatedString {
    val builder = AnnotatedString.Builder(note.body.substring(start, end))
    note.styles.forEach { style ->
        val overlapStart = maxOf(start, style.start)
        val overlapEnd = minOf(end, style.end)
        if (overlapStart < overlapEnd) {
            builder.addStyle(style.toSpanStyle(), overlapStart - start, overlapEnd - start)
        }
    }
    return builder.toAnnotatedString()
}

private fun NoteTextStyle.toSpanStyle(): SpanStyle {
    val decoration = when {
        underline && strikeThrough -> TextDecoration.combine(
            listOf(TextDecoration.Underline, TextDecoration.LineThrough),
        )
        underline -> TextDecoration.Underline
        strikeThrough -> TextDecoration.LineThrough
        else -> null
    }
    return SpanStyle(
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        textDecoration = decoration,
    )
}

@Composable
private fun NoteImagePreview(file: File?, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = null, file?.absolutePath, file?.lastModified()) {
        value = withContext(Dispatchers.IO) {
            file?.takeIf { it.exists() }?.let(::loadSampledBitmap)
        }
    }
    if (bitmap == null) {
        Surface(
            modifier = modifier.heightIn(min = 100.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.notes_image_unavailable),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        val image = bitmap ?: return
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = stringResource(R.string.notes_image),
            contentScale = ContentScale.Crop,
            modifier = modifier
                .aspectRatio((image.width.toFloat() / image.height.coerceAtLeast(1)).coerceIn(0.7f, 1.8f))
                .clip(RoundedCornerShape(14.dp)),
        )
    }
}

private fun loadSampledBitmap(file: File): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
    val bitmap = BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return@runCatching null
    val orientation = runCatching {
        ExifInterface(file).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (degrees == 0f) bitmap else Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        Matrix().apply { postRotate(degrees) },
        true,
    )
}.getOrNull()

@Composable
private fun transparentTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

/** Passive hold observer: it never consumes the FAB's short-click gesture. */
private fun Modifier.notesRecoveryHold(
    enabled: Boolean,
    onHold: () -> Unit,
): Modifier = composed {
    val currentOnHold = rememberUpdatedState(onHold)
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var released = false
                val timer = launch {
                    delay(NOTES_RECOVERY_HOLD_MILLIS)
                    if (!released) currentOnHold.value()
                }
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) break
                    }
                } finally {
                    released = true
                    timer.cancel()
                }
            }
        }
    }
}

/** Compatibility editor retained for Notes setup before the cover is activated. */
@Composable
internal fun NoteEditorDialog(
    note: LocalNote?,
    busy: Boolean,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(note?.id) { mutableStateOf(note?.title.orEmpty()) }
    var body by remember(note?.id) { mutableStateOf(note?.body.orEmpty()) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(if (note == null) R.string.notes_add else R.string.notes_edit_note)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= NotesPolicy.MAX_TITLE_LENGTH) title = it },
                    enabled = !busy,
                    label = { Text(stringResource(R.string.notes_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { if (it.length <= NotesPolicy.MAX_BODY_LENGTH) body = it },
                    enabled = !busy,
                    label = { Text(stringResource(R.string.notes_body_label)) },
                    minLines = 5,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = NotesPolicy.isValid(title, body) && !busy,
                onClick = { onSave(title, body) },
            ) { Text(stringResource(R.string.notes_save)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
internal fun GenerateNotesDialog(onGenerate: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notes_generate_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.notes_generate_description))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 5, 7, 10).forEach { count ->
                        TextButton(onClick = { onGenerate(count) }) { Text(count.toString()) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
