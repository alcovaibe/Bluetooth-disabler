package com.pulse.bluetoothdisable.cover.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.ui.CoverModeBottomSheet

@Composable
internal fun GalleryDeleteDialog(
    video: Boolean,
    permanently: Boolean,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    CoverModeBottomSheet(onDismiss = onDismiss, testTag = "gallery_delete_dialog") {
        Text(
            stringResource(when {
                permanently && video -> R.string.gallery_delete_video_forever_question
                permanently -> R.string.gallery_delete_forever_question
                video -> R.string.gallery_delete_video_question
                else -> R.string.gallery_delete_question
            }),
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
            fontWeight = FontWeight.Bold,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.testTag("gallery_delete_confirm"),
            ) {
                Text(stringResource(R.string.gallery_delete), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
