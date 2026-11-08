package com.pulse.bluetoothdisable.ui

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.io.File
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.pulse.bluetoothdisable.BuildConfig
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.domain.ProtectionState
import com.pulse.bluetoothdisable.launcher.LauncherStyle
import com.pulse.bluetoothdisable.localization.LanguageManager
import com.pulse.bluetoothdisable.theme.ThemeManager
import com.pulse.bluetoothdisable.ui.theme.BluetoothDisableTheme
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Captures screenshots from the real running Compose UI on an Android emulator.
 * These files are gathered by scripts/run_android_tests.sh and uploaded as CI artifacts.
 */
class AboutScreenScreenshotsInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun captureFiveRealAppScreens() {
        val application = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(application.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag("ru")))
        }
        val localized = application.createConfigurationContext(configuration)
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides configuration,
                LocalResources provides localized.resources,
            ) {
                BluetoothDisableTheme(darkTheme = true) {
                    MainScreen(
                        uiState = ProtectionUiState(ProtectionState.READY, isDeviceOwner = true),
                        appVersion = BuildConfig.VERSION_NAME,
                        launcherIconHidden = false,
                        selectedLauncherStyle = LauncherStyle.DEFAULT,
                        tileAdded = true,
                        canRequestTile = true,
                        selectedLanguage = LanguageManager.RUSSIAN,
                        selectedTheme = ThemeManager.DARK,
                        showHideAction = false,
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
        }
        compose.onNodeWithTag("main_screen").assertIsDisplayed()
        capture("01-main-screen.png")

        // The former drawer screenshot is now a view of the selected navigation tab.
        compose.onNodeWithTag("check_updates").performClick()
        capture("02-floating-bottom-navigation.png")

        compose.onNodeWithTag("bottom_nav_language").performClick()
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithTag("language_selection_sheet").assertIsDisplayed() }.isSuccess
        }
        capture("03-language-bottom-sheet.png")
        compose.onNodeWithTag("language_selection_sheet_option_ru").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("bottom_nav_theme").performClick()
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithTag("theme_selection_sheet").assertIsDisplayed() }.isSuccess
        }
        capture("04-theme-bottom-sheet.png")
        compose.onNodeWithTag("theme_selection_sheet_option_dark").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("bottom_nav_about").performClick()
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithTag("about_app_screen").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithTag("about_version").assertIsDisplayed()
        compose.onNodeWithTag("about_app_logo").assertIsDisplayed()
        capture("05-about-app.png")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // Compose being idle does not guarantee SurfaceFlinger has latched the
        // last frame of the animated navigation transition.
        android.os.SystemClock.sleep(1100)
        instrumentation.waitForIdleSync()
        // UiAutomation captures the emulator display directly. Unlike an external
        // screencap shell command, this does not depend on shell path permissions.
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
            ?: throw AssertionError("Android could not capture screen: $name")
        val outputDir = File(instrumentation.targetContext.filesDir, "about-previews")
        assertTrue("Cannot create screenshot directory", outputDir.isDirectory || outputDir.mkdirs())
        val file = File(outputDir, name)
        try {
            file.outputStream().use { stream ->
                assertTrue("PNG encoding failed: $name",
                    screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally {
            screenshot.recycle()
        }
        assertTrue("Missing or empty emulator screenshot: $name", file.isFile && file.length() > 1_000)
    }

}
