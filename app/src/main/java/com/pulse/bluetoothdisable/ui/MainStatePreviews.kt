package com.pulse.bluetoothdisable.ui

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.domain.ProtectionError
import com.pulse.bluetoothdisable.domain.ProtectionState
import com.pulse.bluetoothdisable.launcher.LauncherStyle
import com.pulse.bluetoothdisable.localization.LanguageManager
import com.pulse.bluetoothdisable.theme.ThemeManager
import com.pulse.bluetoothdisable.ui.theme.BluetoothDisableTheme
import com.pulse.bluetoothdisable.updates.UpdateIcon

// 6 policy states x 2 languages x 2 themes = 24 Android Studio previews.
// Preview-only callbacks. No runtime changes, Cover Mode edits, or shared theme edits.
@Composable
private fun MainPolicyStatePreview(state: ProtectionState) {
    val dark = isSystemInDarkTheme()
    val locale = LocalConfiguration.current.locales[0].language
    BluetoothDisableTheme(darkTheme = dark) {
        MainScreen(
            uiState = ProtectionUiState(
                state = state,
                error = if (state == ProtectionState.ERROR) ProtectionError.READ_POLICY else null,
                isDeviceOwner = true,
            ),
            appVersion = "1.4.1",
            launcherIconHidden = false,
            selectedLauncherStyle = LauncherStyle.DEFAULT,
            tileAdded = true,
            canRequestTile = true,
            selectedLanguage = if (locale == LanguageManager.RUSSIAN)
                LanguageManager.RUSSIAN else LanguageManager.ENGLISH,
            selectedTheme = if (dark) ThemeManager.DARK else ThemeManager.LIGHT,
            showHideAction = false,
            onHideToCover = {},
            onLanguageSelected = {},
            onThemeSelected = {},
            onEnableProtection = {},
            onDisableProtection = {},
            onRefresh = {},
            onLauncherStyleSelected = {},
            onRequestAddTile = {},
            updateControls = {
                IconButton(onClick = {}, modifier = Modifier.size(56.dp)) {
                    Icon(UpdateIcon, contentDescription = null)
                }
            },
        )
    }
}

@Preview(name = "READY / RU / Light", locale = "ru", showBackground = true)
@Preview(name = "READY / RU / Dark", locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "READY / EN / Light", locale = "en", showBackground = true)
@Preview(name = "READY / EN / Dark", locale = "en", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun PreviewReady() = MainPolicyStatePreview(ProtectionState.READY)

@Preview(name = "PROTECTED / RU / Light", locale = "ru", showBackground = true)
@Preview(name = "PROTECTED / RU / Dark", locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "PROTECTED / EN / Light", locale = "en", showBackground = true)
@Preview(name = "PROTECTED / EN / Dark", locale = "en", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun PreviewProtected() = MainPolicyStatePreview(ProtectionState.PROTECTED)

@Preview(name = "ENABLING / RU / Light", locale = "ru", showBackground = true)
@Preview(name = "ENABLING / RU / Dark", locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "ENABLING / EN / Light", locale = "en", showBackground = true)
@Preview(name = "ENABLING / EN / Dark", locale = "en", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun PreviewEnabling() = MainPolicyStatePreview(ProtectionState.ENABLING)

@Preview(name = "DISABLING / RU / Light", locale = "ru", showBackground = true)
@Preview(name = "DISABLING / RU / Dark", locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "DISABLING / EN / Light", locale = "en", showBackground = true)
@Preview(name = "DISABLING / EN / Dark", locale = "en", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun PreviewDisabling() = MainPolicyStatePreview(ProtectionState.DISABLING)

@Preview(name = "ERROR / RU / Light", locale = "ru", showBackground = true)
@Preview(name = "ERROR / RU / Dark", locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "ERROR / EN / Light", locale = "en", showBackground = true)
@Preview(name = "ERROR / EN / Dark", locale = "en", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun PreviewError() = MainPolicyStatePreview(ProtectionState.ERROR)

@Preview(name = "UNSUPPORTED / RU / Light", locale = "ru", showBackground = true)
@Preview(name = "UNSUPPORTED / RU / Dark", locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "UNSUPPORTED / EN / Light", locale = "en", showBackground = true)
@Preview(name = "UNSUPPORTED / EN / Dark", locale = "en", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun PreviewUnsupported() = MainPolicyStatePreview(ProtectionState.UNSUPPORTED)
