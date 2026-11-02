package com.pulse.bluetoothdisable.cover.notes

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.cover.CoverMode
import com.pulse.bluetoothdisable.cover.CoverModeManager
import com.pulse.bluetoothdisable.cover.CoverModeNavigator
import com.pulse.bluetoothdisable.localization.LanguageManager
import com.pulse.bluetoothdisable.ui.CoverModeConfirmationDialog
import com.pulse.bluetoothdisable.ui.theme.BluetoothDisableTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotesCoverSetupActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = LocalNotesRepository(this)
        setContent {
            BluetoothDisableTheme(darkTheme = isSystemInDarkTheme()) {
                NotesSetupScreen(
                    repository = repository,
                    onCancel = ::finish,
                    onComplete = { note, start, end, onResult ->
                        lifecycleScope.launch {
                            val success = withContext(Dispatchers.IO) {
                                runCatching {
                                    CoverModeManager(this@NotesCoverSetupActivity)
                                        .activateNotes(note.id, start, end, note.body)
                                }.isSuccess
                            }
                            if (success) {
                                Toast.makeText(
                                    this@NotesCoverSetupActivity,
                                    R.string.notes_setup_completed,
                                    Toast.LENGTH_SHORT,
                                ).show()
                                CoverModeNavigator.openCover(this@NotesCoverSetupActivity, CoverMode.NOTES)
                            } else {
                                onResult(false)
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun NotesCoverConfirmationDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    CoverModeConfirmationDialog(
        titleRes = R.string.notes_cover_confirm_title,
        messageRes = R.string.notes_cover_confirm_message,
        onContinue = onContinue,
        onDismiss = onDismiss,
    )
}

private enum class NotesSetupStep { NOTES, SECRET }

@Composable
private fun NotesSetupScreen(
    repository: LocalNotesRepository,
    onCancel: () -> Unit,
    onComplete: (LocalNote, Int, Int, (Boolean) -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val language = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
    var notes by remember { mutableStateOf<List<LocalNote>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var storageError by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableStateOf(NotesSetupStep.NOTES) }
    var editing by remember { mutableStateOf<LocalNote?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showGenerate by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var setupFailed by remember { mutableStateOf(false) }

    suspend fun reload() {
        val result = withContext(Dispatchers.IO) { runCatching { repository.notes() } }
        notes = result.getOrDefault(emptyList())
        storageError = result.isFailure
        loading = false
        if (selectedId != null && notes.none { it.id == selectedId }) selectedId = null
    }

    LaunchedEffect(Unit) { reload() }
    BackHandler(enabled = step == NotesSetupStep.SECRET || saving) {
        if (!saving) step = NotesSetupStep.NOTES
    }

    val selected = selectedId?.let { id -> notes.firstOrNull { it.id == id } }

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.safeDrawingPadding().imePadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.notes_setup_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(
                    if (step == NotesSetupStep.NOTES) R.string.notes_setup_description
                    else R.string.notes_secret_instruction,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.notes_setup_delete_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            if (storageError) {
                Text(stringResource(R.string.notes_storage_error), color = MaterialTheme.colorScheme.error)
            }
            if (setupFailed) {
                Text(stringResource(R.string.notes_setup_failed), color = MaterialTheme.colorScheme.error)
            }

            if (step == NotesSetupStep.NOTES) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { creating = true; editing = null }, enabled = !saving) {
                        Text(stringResource(R.string.notes_add))
                    }
                    TextButton(onClick = { showGenerate = true }, enabled = !saving) {
                        Text(stringResource(R.string.notes_generate))
                    }
                }
                Text(stringResource(R.string.notes_select_access_note), style = MaterialTheme.typography.titleMedium)
                if (loading) {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                } else if (notes.isEmpty()) {
                    Text(stringResource(R.string.notes_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(notes, key = { it.id }) { note ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { selectedId = note.id },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = selectedId == note.id, onClick = { selectedId = note.id })
                                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                        Text(
                                            note.title.ifBlank { note.body.lineSequence().firstOrNull().orEmpty() },
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(note.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    TextButton(onClick = { editing = note; creating = false }) {
                                        Text(stringResource(R.string.notes_edit))
                                    }
                                }
                            }
                        }
                    }
                }
                Button(
                    enabled = selected != null && !saving,
                    onClick = { step = NotesSetupStep.SECRET; setupFailed = false },
                ) {
                    Text(stringResource(R.string.continue_action))
                }
                TextButton(enabled = !saving, onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            } else if (selected != null) {
                SecretSelectionStep(
                    note = selected,
                    saving = saving,
                    onBack = { step = NotesSetupStep.NOTES },
                    onFinish = { start, end ->
                        saving = true
                        setupFailed = false
                        onComplete(selected, start, end) { success ->
                            saving = false
                            setupFailed = !success
                        }
                    },
                )
            }
        }
    }

    if (creating || editing != null) {
        NoteEditorDialog(
            note = editing,
            busy = saving,
            onSave = { title, body ->
                saving = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { repository.save(title, body, editing?.id, editing?.pinned) }
                    }
                    saving = false
                    if (result.isSuccess) {
                        creating = false
                        editing = null
                        selectedId = result.getOrThrow().id
                        reload()
                    } else {
                        storageError = true
                    }
                }
            },
            onDismiss = { if (!saving) { creating = false; editing = null } },
        )
    }

    if (showGenerate) {
        GenerateNotesDialog(
            onGenerate = { count ->
                showGenerate = false
                saving = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            NotesGenerator.generate(count, language).forEach { generated ->
                                repository.save(generated.title, generated.body)
                            }
                        }
                    }
                    saving = false
                    storageError = result.isFailure
                    reload()
                }
            },
            onDismiss = { showGenerate = false },
        )
    }
}

@Composable
private fun ColumnScope.SecretSelectionStep(
    note: LocalNote,
    saving: Boolean,
    onBack: () -> Unit,
    onFinish: (Int, Int) -> Unit,
) {
    var field by remember(note.id, note.body) { mutableStateOf(TextFieldValue(note.body)) }
    val start = minOf(field.selection.start, field.selection.end)
    val end = maxOf(field.selection.start, field.selection.end)
    val valid = NotesPolicy.isValidSecretRange(note.body, start, end)

    Text(note.title.ifBlank { stringResource(R.string.notes_note) }, style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(
        value = field,
        onValueChange = { changed ->
            field = TextFieldValue(
                text = note.body,
                selection = TextRange(
                    changed.selection.start.coerceIn(0, note.body.length),
                    changed.selection.end.coerceIn(0, note.body.length),
                ),
            )
        },
        readOnly = true,
        label = { Text(stringResource(R.string.notes_body_label)) },
        supportingText = { Text(stringResource(R.string.notes_secret_hint)) },
        modifier = Modifier.fillMaxWidth().weight(1f),
    )
    if (valid) {
        Text(
            stringResource(R.string.notes_selected_fragment, note.body.substring(start, end)),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Button(enabled = valid && !saving, onClick = { onFinish(start, end) }) {
        Text(stringResource(R.string.notes_finish_setup))
    }
    TextButton(enabled = !saving, onClick = onBack) { Text(stringResource(R.string.notes_back)) }
}
