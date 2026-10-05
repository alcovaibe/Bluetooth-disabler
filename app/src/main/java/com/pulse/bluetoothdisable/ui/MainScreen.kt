package com.pulse.bluetoothdisable.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.domain.ProtectionError
import com.pulse.bluetoothdisable.domain.ProtectionState
import com.pulse.bluetoothdisable.launcher.LauncherStyle
import com.pulse.bluetoothdisable.localization.LanguageManager
import com.pulse.bluetoothdisable.theme.ThemeManager
import com.pulse.bluetoothdisable.ui.theme.BluetoothDisableTheme

@Composable
fun MainScreen(
    uiState: ProtectionUiState,
    appVersion: String,
    launcherIconHidden: Boolean,
    selectedLauncherStyle: LauncherStyle,
    tileAdded: Boolean,
    canRequestTile: Boolean,
    selectedLanguage: String,
    selectedTheme: String,
    showHideAction: Boolean,
    onHideToCover: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onThemeSelected: (String) -> Unit,
    onEnableProtection: () -> Unit,
    onDisableProtection: () -> Unit,
    onRefresh: () -> Unit,
    onLauncherStyleSelected: (LauncherStyle) -> Unit,
    onRequestAddTile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showIconDialog by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Surface(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            ThemeSwitcher(
                selectedTheme = selectedTheme,
                onThemeSelected = onThemeSelected,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 12.dp)
                    .zIndex(1f),
            )

            LanguageSwitcher(
                selectedLanguage = selectedLanguage,
                onLanguageSelected = onLanguageSelected,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 8.dp, end = 12.dp)
                    .zIndex(1f),
            )

            Text(
                text = "v $appVersion",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, bottom = 8.dp)
                    .zIndex(1f),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, top = 72.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.protection_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(32.dp))

                val statusText = when (uiState.state) {
                    ProtectionState.NOT_PROVISIONED -> null
                    ProtectionState.READY -> R.string.status_ready
                    ProtectionState.ENABLING -> R.string.status_enabling
                    ProtectionState.PROTECTED -> R.string.status_protected
                    ProtectionState.DISABLING -> R.string.status_disabling
                    ProtectionState.ERROR -> R.string.status_error
                    ProtectionState.UNSUPPORTED -> R.string.status_unsupported
                }
                if (statusText != null) {
                    Text(
                        text = stringResource(statusText),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                val messageText = when (uiState.state) {
                    ProtectionState.NOT_PROVISIONED -> stringResource(R.string.message_not_provisioned)
                    ProtectionState.READY -> stringResource(R.string.message_ready)
                    ProtectionState.ENABLING -> stringResource(R.string.message_enabling)
                    ProtectionState.PROTECTED -> stringResource(R.string.message_protected)
                    ProtectionState.DISABLING -> stringResource(R.string.message_disabling)
                    ProtectionState.ERROR -> stringResource(errorMessageResource(uiState.error))
                    ProtectionState.UNSUPPORTED -> stringResource(R.string.message_unsupported)
                }
                Text(
                    text = messageText,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (uiState.state == ProtectionState.NOT_PROVISIONED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            val url = if (selectedLanguage == LanguageManager.RUSSIAN) {
                                "https://github.com/alcovaibe/Bluetooth-disabler#установка"
                            } else {
                                "https://github.com/alcovaibe/Bluetooth-disabler/blob/main/README_EN.md#installation"
                            }
                            uriHandler.openUri(url)
                        },
                    ) {
                        Text(stringResource(R.string.provisioning_help))
                    }
                }

                if (uiState.state == ProtectionState.ENABLING ||
                    uiState.state == ProtectionState.DISABLING
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    CircularProgressIndicator()
                }

                Spacer(modifier = Modifier.height(32.dp))

                when (uiState.state) {
                    ProtectionState.READY -> ActionButton(
                        text = stringResource(R.string.action_enable),
                        onClick = onEnableProtection,
                    )

                    ProtectionState.PROTECTED -> ActionButton(
                        text = stringResource(R.string.action_disable),
                        onClick = onDisableProtection,
                    )

                    ProtectionState.NOT_PROVISIONED,
                    ProtectionState.ERROR -> ActionButton(
                        text = stringResource(R.string.action_refresh),
                        onClick = onRefresh,
                    )

                    ProtectionState.ENABLING,
                    ProtectionState.DISABLING,
                    ProtectionState.UNSUPPORTED -> Unit
                }

                if (showHideAction) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onHideToCover,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.action_hide_to_cover),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                if (launcherIconHidden) {
                    Text(
                        text = stringResource(R.string.launcher_hidden),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (!tileAdded) {
                    if (canRequestTile) {
                        OutlinedButton(
                            onClick = onRequestAddTile,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.action_add_tile),
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.tile_manual_hint),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (uiState.isDeviceOwner) {
                    OutlinedButton(
                        onClick = { showIconDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.action_hide_launcher),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    if (showIconDialog) {
        LauncherIconSelectionDialog(
            selectedStyle = selectedLauncherStyle,
            onStyleSelected = { style ->
                showIconDialog = false
                onLauncherStyleSelected(style)
            },
            onDismiss = { showIconDialog = false },
        )
    }
}

@Composable
private fun LanguageSwitcher(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectorButton(
            label = "RU",
            selected = selectedLanguage == LanguageManager.RUSSIAN,
            onClick = { onLanguageSelected(LanguageManager.RUSSIAN) },
        )
        SelectorDivider()
        SelectorButton(
            label = "EN",
            selected = selectedLanguage == LanguageManager.ENGLISH,
            onClick = { onLanguageSelected(LanguageManager.ENGLISH) },
        )
    }
}

@Composable
private fun ThemeSwitcher(
    selectedTheme: String,
    onThemeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectorButton(
            label = stringResource(R.string.theme_light),
            selected = selectedTheme == ThemeManager.LIGHT,
            onClick = { onThemeSelected(ThemeManager.LIGHT) },
            compact = true,
        )
        SelectorDivider()
        SelectorButton(
            label = stringResource(R.string.theme_dark),
            selected = selectedTheme == ThemeManager.DARK,
            onClick = { onThemeSelected(ThemeManager.DARK) },
            compact = true,
        )
    }
}

@Composable
private fun SelectorDivider() {
    Text(
        text = "|",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
private fun SelectorButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
        )
    }
}

private fun errorMessageResource(error: ProtectionError?): Int = when (error) {
    ProtectionError.READ_POLICY -> R.string.error_read_policy
    ProtectionError.ENABLE_NOT_CONFIRMED -> R.string.error_enable_not_confirmed
    ProtectionError.ENABLE_FAILED -> R.string.error_enable_failed
    ProtectionError.DISABLE_NOT_CONFIRMED -> R.string.error_disable_not_confirmed
    ProtectionError.DISABLE_FAILED -> R.string.error_disable_failed
    null -> R.string.message_error
}

@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    BluetoothDisableTheme {
        MainScreen(
            uiState = ProtectionUiState(state = ProtectionState.READY),
            appVersion = "1.0.6",
            launcherIconHidden = false,
            selectedLauncherStyle = LauncherStyle.DEFAULT,
            tileAdded = false,
            canRequestTile = true,
            selectedLanguage = LanguageManager.ENGLISH,
            selectedTheme = ThemeManager.LIGHT,
            showHideAction = true,
            onHideToCover = {},
            onLanguageSelected = {},
            onThemeSelected = {},
            onEnableProtection = {},
            onDisableProtection = {},
            onRefresh = {},
            onLauncherStyleSelected = {},
            onRequestAddTile = {},
        )
    }
}
