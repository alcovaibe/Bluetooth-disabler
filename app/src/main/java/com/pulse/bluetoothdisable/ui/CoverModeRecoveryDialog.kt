package com.pulse.bluetoothdisable.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.ui.theme.AppColors

@Composable
internal fun CoverModeRecoveryDialog(
    @StringRes descriptionRes: Int,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    CoverModeBottomSheet(onDismiss = onDismiss, testTag = "cover_recovery_dialog") {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            Text(
                text = stringResource(R.string.cover_recovery_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(descriptionRes),
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
                onClick = onReset,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.CoverRecoveryReset,
                    contentColor = Color.White,
                ),
                modifier = Modifier.weight(1f, fill = false).testTag("cover_recovery_reset"),
            ) {
                Text(stringResource(R.string.cover_recovery_reset), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
