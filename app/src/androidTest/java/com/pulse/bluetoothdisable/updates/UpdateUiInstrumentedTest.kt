package com.pulse.bluetoothdisable.updates

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.ui.theme.BluetoothDisableTheme
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** UI uses an injected source; no real release, credentials, browser or network is needed. */
class UpdateUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private class Preferences : UpdatePreferences {
        override var lastAutomaticAttempt = 100_000_000L
        override var deferredCode = 0
    }
    @After fun after() { scope.cancel() }

    private fun snapshot(): UpdateSnapshot {
        val text = """{"schemaVersion":1,"platform":"android","channel":"stable","applicationId":"com.pulse.bluetoothdisable","release":{"versionCode":100,"versionName":"1.2.0","minSdk":26,"releaseUrl":"https://github.com/alcovaibe/Bluetooth-disabler/releases/tag/v1.2.0","notes":{"ru":"Исправления фотографий","en":"Photo fixes"}}}"""
        return UpdateSnapshot(UpdateManifest.parse(text.toByteArray()), "\"fixture\"")
    }

    private fun verifyUi(language: String, dark: Boolean) {
        val prefs = Preferences()
        var time = 100_000_000L
        var opened: String? = null
        lateinit var controller: UpdateController
        compose.runOnUiThread {
            controller = UpdateController(scope, UpdateSource { snapshot() }, prefs, true, 99, 37, { time }, { time })
            controller.enterMain()
        }
        compose.setContent {
            val original = LocalContext.current
            val configuration = Configuration(original.resources.configuration).apply { setLocales(LocaleList(Locale.forLanguageTag(language))) }
            val localized = original.createConfigurationContext(configuration)
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides configuration) {
                BluetoothDisableTheme(darkTheme = dark) {
                    Surface { Column { UpdatePanel(controller, language) { opened = it } } }
                }
            }
        }
        compose.onNodeWithTag("check_updates").performClick()
        compose.onNodeWithTag("update_available").assertIsDisplayed()
        compose.onNodeWithText(if (language == "ru") "Исправления фотографий" else "Photo fixes").assertIsDisplayed()
        assertEquals(null, opened)
        compose.onNodeWithText(if (language == "ru") "Позже" else "Later").performClick()
        compose.onNodeWithTag("update_available").assertDoesNotExist()
        assertEquals(100, prefs.deferredCode)
        compose.runOnUiThread { time += 5001 }
        compose.onNodeWithTag("check_updates").performClick()
        compose.onNodeWithTag("update_available").assertIsDisplayed()
        compose.onNodeWithText(if (language == "ru") "Подробнее" else "Details").performClick()
        compose.waitForIdle()
        assertEquals("https://github.com/alcovaibe/Bluetooth-disabler/releases/tag/v1.2.0", opened)
        compose.runOnUiThread { controller.leaveMain() }
        compose.onNodeWithTag("update_available").assertDoesNotExist()
    }

    @Test fun russianLight() = verifyUi("ru", false)
    @Test fun russianDark() = verifyUi("ru", true)
    @Test fun englishLight() = verifyUi("en", false)
    @Test fun englishDark() = verifyUi("en", true)

    @Test fun manualErrorCanBeRetriedAndLeavingMainRemovesDialogs() {
        var fail = true
        var time = 100_000_000L
        lateinit var controller: UpdateController
        compose.runOnUiThread {
            controller = UpdateController(scope, UpdateSource { if (fail) throw IOException() else snapshot() }, Preferences(), true, 99, 37, { time }, { time })
            controller.enterMain()
        }
        var retryText = ""
        compose.setContent {
            retryText = LocalContext.current.getString(R.string.updates_retry)
            BluetoothDisableTheme { Surface { Column { UpdatePanel(controller, "en") } } }
        }
        compose.onNodeWithTag("check_updates").performClick()
        compose.onNodeWithTag("updates_result").assertIsDisplayed()
        compose.runOnUiThread { fail = false; time += 6000 }
        compose.mainClock.advanceTimeBy(6_000)
        compose.waitForIdle()
        compose.onNodeWithText(retryText).performClick()
        compose.onNodeWithTag("update_available").assertIsDisplayed()
        compose.runOnUiThread { controller.leaveMain() }
        compose.onNodeWithTag("update_available").assertDoesNotExist()
        compose.runOnUiThread { controller.check() }
        compose.onNodeWithTag("update_available").assertDoesNotExist()
    }
}
