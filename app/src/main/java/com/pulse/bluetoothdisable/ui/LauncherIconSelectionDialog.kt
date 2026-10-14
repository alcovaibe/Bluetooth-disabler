package com.pulse.bluetoothdisable.ui

import android.widget.ImageView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.launcher.LauncherStyle

@Composable
fun LauncherIconSelectionDialog(
    selectedStyle: LauncherStyle,
    onStyleSelected: (LauncherStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(24.dp)
                        .fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.change_launcher_icon_title),
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 20.dp),
                    )

                    LauncherOptionCard(
                        style = LauncherStyle.DEFAULT,
                        selected = selectedStyle == LauncherStyle.DEFAULT,
                        label = stringResource(R.string.launcher_option_default),
                        onClick = { onStyleSelected(LauncherStyle.DEFAULT) },
                    )
                    LauncherOptionCard(
                        style = LauncherStyle.CALCULATOR,
                        selected = selectedStyle == LauncherStyle.CALCULATOR,
                        label = stringResource(R.string.launcher_name_calculator),
                        onClick = { onStyleSelected(LauncherStyle.CALCULATOR) },
                    )
                    LauncherOptionCard(
                        style = LauncherStyle.NOTES,
                        selected = selectedStyle == LauncherStyle.NOTES,
                        label = stringResource(R.string.launcher_name_notes),
                        onClick = { onStyleSelected(LauncherStyle.NOTES) },
                    )
                    LauncherOptionCard(
                        style = LauncherStyle.CALENDAR,
                        selected = selectedStyle == LauncherStyle.CALENDAR,
                        label = stringResource(R.string.launcher_name_calendar),
                        onClick = { onStyleSelected(LauncherStyle.CALENDAR) },
                    )
                    LauncherOptionCard(
                        style = LauncherStyle.GALLERY,
                        selected = selectedStyle == LauncherStyle.GALLERY,
                        label = stringResource(R.string.launcher_name_gallery),
                        onClick = { onStyleSelected(LauncherStyle.GALLERY) },
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(
                                text = stringResource(R.string.cancel),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LauncherOptionCard(
    style: LauncherStyle,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LauncherOptionIcon(style)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            RadioButton(
                selected = selected,
                onClick = onClick,
            )
        }
    }
}

@Composable
private fun LauncherOptionIcon(style: LauncherStyle) {
    if (style == LauncherStyle.DEFAULT) {
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setImageResource(R.mipmap.ic_launcher)
                }
            },
            modifier = Modifier.size(56.dp),
        )
        return
    }

    val iconResource = when (style) {
        LauncherStyle.CALCULATOR -> R.drawable.calculator_icon
        LauncherStyle.NOTES -> R.drawable.notes_icon
        LauncherStyle.CALENDAR -> R.drawable.calendar_icon
        LauncherStyle.GALLERY -> R.drawable.gallery_icon
        LauncherStyle.DEFAULT -> error("Default icon is rendered separately")
    }

    Image(
        painter = painterResource(iconResource),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(56.dp),
    )
}
