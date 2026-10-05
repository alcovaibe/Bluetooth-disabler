package com.pulse.bluetoothdisable.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pulse.bluetoothdisable.MainActivity
import com.pulse.bluetoothdisable.cover.CoverModeManager
import com.pulse.bluetoothdisable.domain.ProtectionState
import com.pulse.bluetoothdisable.localization.LanguageManager
import com.pulse.bluetoothdisable.testing.DeviceOwnerFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceOwnerUiInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val deviceOwner = DeviceOwnerFixture(context)

    @Before fun before() {
        deviceOwner.requireUnmanaged()
        CoverModeManager(context).resetToDefault()
        LanguageManager.setSelectedLanguage(context, LanguageManager.ENGLISH)
    }

    @After fun after() { deviceOwner.clear() }

    @Test fun changeIconIsAbsentWithoutDeviceOwner() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                val state = ViewModelProvider(it)[MainViewModel::class.java].uiState.value
                assertEquals(false, state.isDeviceOwner)
                assertEquals(ProtectionState.NOT_PROVISIONED, state.state)
            }
            compose.onNodeWithText("CHANGE ICON").assertDoesNotExist()
        }
    }

    @Test fun changeIconFollowsDeviceOwnerProvisioningAndRemoval() {
        deviceOwner.provision()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: MainViewModel
            scenario.onActivity { model = ViewModelProvider(it)[MainViewModel::class.java] }
            compose.waitUntil(10_000) { model.uiState.value.isDeviceOwner }
            compose.onNodeWithText("CHANGE ICON").performScrollTo().assertIsDisplayed()

            deviceOwner.clear()
            scenario.onActivity { model.refresh() }
            compose.waitUntil(10_000) { !model.uiState.value.isDeviceOwner }
            assertEquals(ProtectionState.NOT_PROVISIONED, model.uiState.value.state)
            compose.onNodeWithText("CHANGE ICON").assertDoesNotExist()
        }
    }
}
