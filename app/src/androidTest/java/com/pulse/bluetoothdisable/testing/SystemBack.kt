package com.pulse.bluetoothdisable.testing

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue

/** Sends Back to the foreground window after navigation replaces an ActivityScenario's activity. */
internal fun pressSystemBack() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val automation = instrumentation.uiAutomation
    val downTime = SystemClock.uptimeMillis()
    fun event(action: Int) = KeyEvent(
        downTime, SystemClock.uptimeMillis(), action, KeyEvent.KEYCODE_BACK,
        0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
        KeyEvent.FLAG_FROM_SYSTEM or KeyEvent.FLAG_VIRTUAL_HARD_KEY,
        InputDevice.SOURCE_KEYBOARD,
    )
    assertTrue("Back key down must be delivered", automation.injectInputEvent(
        event(KeyEvent.ACTION_DOWN), true,
    ))
    assertTrue("Back key up must be delivered", automation.injectInputEvent(
        event(KeyEvent.ACTION_UP), true,
    ))
    instrumentation.waitForIdleSync()
}
