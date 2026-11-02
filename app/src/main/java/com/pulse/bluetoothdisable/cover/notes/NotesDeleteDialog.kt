package com.pulse.bluetoothdisable.cover.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.ui.CoverModeBottomSheet

@Composable
internal fun NotesDeleteDialog(
    count: Int,
    showCount: Boolean,
    busy: Boolean,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    CoverModeBottomSheet(onDismiss = onDismiss, testTag = "notes_delete_dialog") {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            Text(
                text = if (showCount) pluralStringResource(R.plurals.notes_delete_selected_title, count, count)
                    else stringResource(R.string.notes_delete_note),
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (showCount) pluralStringResource(R.plurals.notes_delete_selected_message, count, count)
                    else stringResource(R.string.notes_delete_confirmation),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Button(
                onClick = onDelete,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.weight(1f, fill = false).testTag("notes_delete_confirm"),
            ) {
                Text(stringResource(R.string.notes_delete), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
