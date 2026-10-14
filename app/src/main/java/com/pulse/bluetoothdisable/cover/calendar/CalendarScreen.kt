package com.pulse.bluetoothdisable.cover.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.cover.coverRecoveryHold
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    recoveryEnabled: Boolean = false,
    onRecoveryHold: () -> Unit = {},
    onUnlock: () -> Unit,
) {
    val state = viewModel.uiState
    val locale = LocalConfiguration.current.locales[0]
    var choosingDate by remember { mutableStateOf(false) }
    var searchingDate by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) searchingDate = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var deleting by remember { mutableStateOf<CalendarNote?>(null) }
    val previousDescription = stringResource(R.string.calendar_previous_month)
    val nextDescription = stringResource(R.string.calendar_next_month)
    val addDescription = stringResource(R.string.calendar_add_note)
    val chooseDescription = stringResource(R.string.calendar_choose_date)
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.testTag("calendar_toolbar"),
                title = {
                    Text(
                        stringResource(R.string.launcher_name_calendar),
                        modifier = Modifier.coverRecoveryHold(recoveryEnabled, onRecoveryHold),
                    )
                },
                actions = {
                    IconButton(onClick = { searchingDate = true }) {
                        Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.calendar_search_date))
                    }
                    TextButton(onClick = viewModel::today) {
                        Text(
                            stringResource(R.string.calendar_today),
                            modifier = Modifier.coverRecoveryHold(recoveryEnabled, onRecoveryHold),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::add,
                modifier = Modifier.semantics { contentDescription = addDescription },
            ) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.moveMonth(-1) },
                        enabled = !(state.month.year == CalendarDates.YEAR_RANGE.first && state.month.monthValue == 1),
                        modifier = Modifier.semantics { contentDescription = previousDescription },
                    ) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                    TextButton(
                        onClick = { choosingDate = true },
                        modifier = Modifier.weight(1f).semantics { contentDescription = chooseDescription },
                    ) {
                        Text(CalendarDates.formatMonth(state.month, locale), style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(
                        onClick = { viewModel.moveMonth(1) },
                        enabled = !(state.month.year == CalendarDates.YEAR_RANGE.last && state.month.monthValue == 12),
                        modifier = Modifier.semantics { contentDescription = nextDescription },
                    ) {
                        Text("›", style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    DayOfWeek.entries.forEach { day ->
                        Box(Modifier.weight(1f).height(40.dp), contentAlignment = Alignment.Center) {
                            Text(day.getDisplayName(TextStyle.SHORT, locale), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            items(CalendarDates.monthCells(state.month).chunked(7)) { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
                            if (date != null) {
                                val selected = date == state.selectedDate
                                Surface(
                                    onClick = { viewModel.select(date) },
                                    shape = CircleShape,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    border = if (date == today) {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                    } else {
                                        null
                                    },
                                    modifier = Modifier.size(44.dp)
                                        .testTag("calendar_day_$date")
                                        .semantics {
                                            contentDescription = CalendarDates.formatDate(date, locale)
                                        },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(date.dayOfMonth.toString())
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(
                    CalendarDates.formatDate(state.selectedDate, locale),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            if (state.loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            if (state.storageError) {
                item {
                    Text(
                        stringResource(R.string.calendar_storage_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = viewModel::refresh) {
                        Text(stringResource(R.string.calendar_retry))
                    }
                }
            }
            if (!state.editorOpen && state.noteError != null) {
                item {
                    Text(
                        calendarNoteErrorText(state.noteError),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            val notes = state.notes.filter { it.date == state.selectedDate }
            if (!state.loading && !state.storageError && notes.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.calendar_no_notes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(notes, key = { it.id }) { note ->
                CalendarNoteCard(
                    note = note,
                    busy = state.busy,
                    onClick = { viewModel.click(note, onUnlock) },
                    onEdit = { viewModel.edit(note) },
                    onDelete = { deleting = note },
                )
            }
        }
    }

    if (searchingDate) {
        CalendarDateSearch(
            notes = state.notes,
            loading = state.loading,
            failed = state.storageError,
            onDismiss = { searchingDate = false },
        )
    }

    if (choosingDate) {
        CalendarDateDialog(
            state.selectedDate,
            onSelect = {
                viewModel.select(it)
                choosingDate = false
            },
            onDismiss = { choosingDate = false },
        )
    }

    if (state.editorOpen) {
        CalendarNoteDialog(
            editing = state.editingNote != null,
            text = state.editorText,
            busy = state.busy,
            failed = state.storageError,
            noteError = state.noteError,
            onTextChange = viewModel::updateEditorText,
            onSave = viewModel::save,
            onDismiss = viewModel::closeEditor,
        )
    }

    state.viewingNote?.let { note ->
        AlertDialog(
            onDismissRequest = viewModel::closeViewer,
            title = { Text(stringResource(R.string.calendar_note)) },
            text = {
                Text(
                    note.text,
                    modifier = Modifier.fillMaxWidth().testTag("calendar_note_view"),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::closeViewer) {
                    Text(stringResource(R.string.calendar_close))
                }
            },
        )
    }

    deleting?.let { note ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.calendar_delete_note)) },
            text = { Text(stringResource(R.string.calendar_delete_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(note)
                        deleting = null
                    },
                ) {
                    Text(
                        stringResource(R.string.calendar_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun CalendarNoteDialog(
    editing: Boolean,
    text: String,
    busy: Boolean,
    failed: Boolean,
    noteError: CalendarNotePolicy.Violation?,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                stringResource(
                    if (editing) R.string.calendar_edit_note else R.string.calendar_add_note,
                ),
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    enabled = !busy,
                    label = { Text(stringResource(R.string.calendar_note)) },
                    modifier = Modifier.fillMaxWidth().testTag("calendar_note_text"),
                    minLines = 3,
                    maxLines = 6,
                    isError = noteError != null || failed,
                    supportingText = {
                        Text("${text.length}/${CalendarNotePolicy.MAX_TEXT_LENGTH}")
                    },
                )
                noteError?.let {
                    Text(calendarNoteErrorText(it), color = MaterialTheme.colorScheme.error)
                }
                if (failed) {
                    Text(
                        stringResource(R.string.calendar_storage_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = CalendarNotePolicy.normalize(text).isNotEmpty() && !busy,
            ) {
                Text(stringResource(R.string.calendar_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun calendarNoteErrorText(error: CalendarNotePolicy.Violation): String =
    stringResource(
        when (error) {
            CalendarNotePolicy.Violation.EMPTY -> R.string.calendar_note_empty
            CalendarNotePolicy.Violation.TOO_LONG -> R.string.calendar_note_too_long
            CalendarNotePolicy.Violation.DUPLICATE -> R.string.calendar_note_duplicate
            CalendarNotePolicy.Violation.DATE_LIMIT -> R.string.calendar_note_limit
        },
    )
