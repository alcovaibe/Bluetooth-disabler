package com.pulse.bluetoothdisable.cover.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pulse.bluetoothdisable.R
import java.time.LocalDate
import java.time.format.TextStyle

/** Deliberately no rememberSaveable, ViewModel, draft store, history, logs or navigation writes. */
@Composable
internal fun CalendarDateSearch(
    notes: List<CalendarNote>,
    loading: Boolean,
    failed: Boolean,
    onDismiss: () -> Unit,
) {
    var day by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<LocalDate?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(day, month, year) {
        runCatching {
            val y = year.toInt()
            require(y in CalendarDates.YEAR_RANGE)
            LocalDate.of(y, month.toInt(), day.toInt())
        }.getOrNull()
    }
    AlertDialog(
        modifier = Modifier.testTag("calendar_date_search"),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendar_search_date)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.calendar_search_private_hint), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchDateField(day, { day = it; result = null }, R.string.calendar_search_day, 2, Modifier.weight(1f), "calendar_search_day")
                    SearchDateField(month, { month = it; result = null }, R.string.calendar_search_month, 2, Modifier.weight(1f), "calendar_search_month")
                    SearchDateField(year, { year = it; result = null }, R.string.calendar_search_year, 4, Modifier.weight(1.4f), "calendar_search_year")
                }
                result?.let { found ->
                    Text(CalendarDates.formatDate(found, locale), modifier = Modifier.testTag("calendar_search_result"))
                    Text(found.dayOfWeek.getDisplayName(TextStyle.FULL, locale))
                    when {
                        loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        failed -> Text(stringResource(R.string.calendar_storage_error), color = MaterialTheme.colorScheme.error)
                        else -> {
                            val matches = notes.filter { it.date == found }
                            if (matches.isEmpty()) Text(stringResource(R.string.calendar_no_notes))
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // Read-only text: no secret-entry gestures, editing or automatic draft creation.
                                items(matches, key = { it.id }) { Text(it.text) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { result = date }, enabled = date != null, modifier = Modifier.testTag("calendar_search_submit")) {
                Text(stringResource(R.string.calendar_search_find))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.calendar_close)) }
        },
    )
}

@Composable
private fun SearchDateField(
    value: String,
    onChange: (String) -> Unit,
    label: Int,
    length: Int,
    modifier: Modifier,
    tag: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            if (input.length <= length && input.all { it in '0'..'9' }) onChange(input)
        },
        modifier = modifier.testTag(tag),
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
