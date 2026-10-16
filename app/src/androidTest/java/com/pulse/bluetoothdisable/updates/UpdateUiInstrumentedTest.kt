package com.pulse.bluetoothdisable.updates

import androidx.activity.compose.LocalActivityResultRegistryOwner
import android.content.res.Configuration
import android.content.Context
import android.os.LocaleList
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performClick
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.ui.theme.BluetoothDisableTheme
import androidx.test.core.app.ApplicationProvider
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
        var downloads = 0
        val pending = kotlinx.coroutines.CompletableDeferred<java.io.File>()
        lateinit var controller: UpdateController
        compose.runOnUiThread {
            controller = UpdateController(scope, UpdateSource { snapshot() }, prefs, true, 99, 37, { time }, { time }, object : UpdateDownloader {
                override suspend fun download(release: UpdateManifest.Release, progress: (Long, Long?) -> Unit): java.io.File {
                    downloads++
                    progress(50, 100)
                    return pending.await()
                }
                override suspend fun verify(file: java.io.File, release: UpdateManifest.Release) = Unit
            })
            controller.enterMain()
        }
        val app = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(app.resources.configuration).apply { setLocales(LocaleList(Locale.forLanguageTag(language))) }
        val localized = app.createConfigurationContext(configuration)
        val localizedResources = localized.resources
        compose.setContent {
            val registryOwner = LocalActivityResultRegistryOwner.current
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner,
                LocalContext provides localized, LocalConfiguration provides configuration, LocalResources provides localizedResources) {
                BluetoothDisableTheme(darkTheme = dark) {
                    Surface { Column { UpdatePanel(controller, language) } }
                }
            }
        }
        compose.onNodeWithTag("check_updates").performClick()
        compose.onNodeWithTag("update_available").assertIsDisplayed()
        compose.onNodeWithText(if (language == "ru") "Исправления фотографий" else "Photo fixes").assertIsDisplayed()
        assertEquals(0, downloads)
        compose.onNodeWithText(if (language == "ru") "Позже" else "Later").performClick()
        compose.onNodeWithTag("update_available").assertDoesNotExist()
        assertEquals(100, prefs.deferredCode)
        compose.runOnUiThread { time += 5001 }
        compose.onNodeWithTag("check_updates").performClick()
        compose.onNodeWithTag("update_available").assertIsDisplayed()
        compose.onNodeWithText(if (language == "ru") "Скачать" else "Download").performClick()
        compose.waitForIdle()
        assertEquals(1, downloads)
        compose.onNodeWithTag("download_progress", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(if (language == "ru") "Скачивание… 50%" else "Downloading… 50%").assertIsDisplayed()
        compose.runOnUiThread { pending.complete(java.io.File("ui-fixture.apk")) }
        compose.onNodeWithText(if (language == "ru") "Установить" else "Install").assertIsDisplayed()
        compose.onNodeWithTag("download_progress", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText(if (language == "ru") "Позже" else "Later").performClick()
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
            retryText = stringResource(R.string.updates_retry)
            BluetoothDisableTheme { Surface { Column { UpdatePanel(controller, "en") } } }
        }
        compose.onNodeWithTag("check_updates").performClick()
        compose.onNodeWithTag("updates_result").assertIsDisplayed()
        compose.runOnUiThread { fail = false; time += 6000 }
        compose.mainClock.advanceTimeBy(6_000)
        compose.waitForIdle()
        compose.waitUntil(7_000) { compose.onAllNodes(hasText(retryText) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(retryText).performClick()
        compose.onNodeWithTag("update_available").assertIsDisplayed()
        compose.runOnUiThread { controller.leaveMain() }
        compose.onNodeWithTag("update_available").assertDoesNotExist()
        compose.runOnUiThread { controller.check() }
        compose.onNodeWithTag("update_available").assertDoesNotExist()
    }
}
