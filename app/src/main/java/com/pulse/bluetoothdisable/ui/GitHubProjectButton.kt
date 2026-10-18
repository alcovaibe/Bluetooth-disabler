package com.pulse.bluetoothdisable.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.ui.theme.AppColors

@Composable
internal fun GitHubProjectButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.testTag("project_github"),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.ProvisioningLink),
    ) {
        Icon(painterResource(R.drawable.ic_github_project), contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.action_project_github),
            modifier = Modifier.weight(1f, fill = false),
            textAlign = TextAlign.Center,
        )
    }
}
