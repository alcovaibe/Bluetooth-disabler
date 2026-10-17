package com.pulse.bluetoothdisable.cover.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.cover.CoverIcons

@Composable
internal fun CalendarNoteCard(
    note: CalendarNote,
    busy: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember(note.id) { mutableStateOf(false) }
    OutlinedCard(onClick = onClick, enabled = !busy) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            // Identical UI for ordinary notes and notes matching the access rule.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    note.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(top = 12.dp),
                )
                IconButton(onClick = { expanded = !expanded }, enabled = !busy) {
                    Icon(
                        painterResource(R.drawable.ic_expand_more),
                        contentDescription = stringResource(
                            if (expanded) R.string.calendar_hide_note_actions
                            else R.string.calendar_show_note_actions,
                        ),
                        modifier = Modifier.rotate(if (expanded) 180f else 0f),
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    TextButton(onClick = onEdit, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.calendar_edit_note), modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = onDelete, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(CoverIcons.Trash, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.calendar_delete),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
