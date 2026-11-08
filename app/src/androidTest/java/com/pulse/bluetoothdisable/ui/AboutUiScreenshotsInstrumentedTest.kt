package com.pulse.bluetoothdisable.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.pulse.bluetoothdisable.BuildConfig
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.domain.ProtectionState
import com.pulse.bluetoothdisable.launcher.LauncherStyle
import com.pulse.bluetoothdisable.localization.LanguageManager
import com.pulse.bluetoothdisable.theme.ThemeManager
import com.pulse.bluetoothdisable.ui.theme.BluetoothDisableTheme
import com.pulse.bluetoothdisable.updates.UpdateIcon
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/** Capture real emulator frames for design review, not AI-generated mockups. */
class AboutUiScreenshotsInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun captureFiveActualAppStates() {
        // The PR CI matrix uploads these captures for API 35; other APIs still
        // exercise navigation in MainNavigationDrawerInstrumentedTest.
        if (Build.VERSION.SDK_INT != 35) return
        val app = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(app.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag("ru")))
        }
        val localized = app.createConfigurationContext(config)
        val theme = mutableStateOf(ThemeManager.DARK)
        val language = mutableStateOf(LanguageManager.RUSSIAN)
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides config,
                LocalResources provides localized.resources,
            ) {
                BluetoothDisableTheme(darkTheme = theme.value == ThemeManager.DARK) {
                    MainScreen(
                        uiState = ProtectionUiState(ProtectionState.READY, isDeviceOwner = true),
                        appVersion = BuildConfig.VERSION_NAME,
                        launcherIconHidden = false,
                        selectedLauncherStyle = LauncherStyle.DEFAULT,
                        tileAdded = false,
                        canRequestTile = true,
                        selectedLanguage = language.value,
                        selectedTheme = theme.value,
                        showHideAction = false,
                        onHideToCover = {},
                        onLanguageSelected = { language.value = it },
                        onThemeSelected = { theme.value = it },
                        onEnableProtection = {},
                        onDisableProtection = {},
                        onRefresh = {},
                        onLauncherStyleSelected = {},
                        onRequestAddTile = {},
                        updateControls = {
                            IconButton(onClick = {}, modifier = Modifier.size(56.dp)) {
                                Icon(UpdateIcon, contentDescription = localized.getString(R.string.updates_check))
                            }
                        },
                    )
                }
            }
        }

        fun menu() = compose.onNodeWithContentDescription(localized.getString(R.string.navigation_menu_open))
        compose.onNodeWithTag("main_screen").assertIsDisplayed()
        capture("01-main.png")
        menu().performClick()
        awaitTag("drawer_about")
        capture("02-drawer.png")

        compose.onNodeWithTag("drawer_language").performClick()
        awaitTag("language_selection_sheet")
        capture("03-language.png")
        compose.onNodeWithTag("language_selection_sheet_option_ru").performClick()

        menu().performClick()
        awaitTag("drawer_theme")
        compose.onNodeWithTag("drawer_theme").performClick()
        awaitTag("theme_selection_sheet")
        capture("04-theme.png")
        compose.onNodeWithTag("theme_selection_sheet_option_dark").performClick()

        menu().performClick()
        awaitTag("drawer_about")
        compose.onNodeWithTag("drawer_about").performClick()
        awaitTag("about_app_screen")
        compose.onNodeWithTag("about_licenses").assertIsDisplayed()
        compose.onNodeWithTag("about_privacy").assertIsDisplayed()
        compose.onNodeWithTag("about_github").assertIsDisplayed()
        capture("05-about.png")
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithTag(tag).assertIsDisplayed() }.isSuccess
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (cmd in listOf(
            "mkdir -p /data/local/tmp/calendar-previews/about",
            "screencap -p /data/local/tmp/calendar-previews/about/$name",
        )) {
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(cmd)).use { it.readBytes() }
        }
    }
}
