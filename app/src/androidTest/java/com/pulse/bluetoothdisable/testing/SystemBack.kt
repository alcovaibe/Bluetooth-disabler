package com.pulse.bluetoothdisable.testing

import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue

/** Sends Back to the foreground window after navigation replaces an ActivityScenario's activity. */
internal fun pressSystemBack() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val automation = instrumentation.uiAutomation
    assertTrue("Back key down must be delivered", automation.injectInputEvent(
        KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK), true,
    ))
    assertTrue("Back key up must be delivered", automation.injectInputEvent(
        KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK), true,
    ))
    instrumentation.waitForIdleSync()
}
