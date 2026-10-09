package com.pulse.bluetoothdisable.testing

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.pulse.bluetoothdisable.admin.AppDeviceAdminReceiver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** Owns only the test's temporary provisioning on an unmanaged emulator. */
internal class DeviceOwnerFixture(private val context: Context) {
    private val policy = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(context, AppDeviceAdminReceiver::class.java)
    private var provisioningAttempted = false

    fun requireUnmanaged() {
        assertFalse("Tests require an unmanaged device", policy.isDeviceOwnerApp(context.packageName))
        assertFalse("Tests must not replace an existing administrator", policy.isAdminActive(admin))
    }

    fun provision() {
        requireUnmanaged()
        provisioningAttempted = true
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("dpm set-device-owner ${admin.flattenToString()}")
        val output = ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        assertTrue("Device Owner provisioning failed: $output", policy.isDeviceOwnerApp(context.packageName))
    }

    fun clear() {
        if (!provisioningAttempted) return

        // The debug APK is testOnly. Removing it via dpm clears both the owner and
        // its active admin; calling the two app APIs back-to-back races on Android 13+.
        val output = if (policy.isDeviceOwnerApp(context.packageName) || policy.isAdminActive(admin)) {
            val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("dpm remove-active-admin ${admin.flattenToString()}")
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        } else {
            "Owner and administrator are already removed."
        }

        // Wait for asynchronous framework state updates before the next test starts.
        val deadline = SystemClock.uptimeMillis() + 15_000
        while ((policy.isDeviceOwnerApp(context.packageName) || policy.isAdminActive(admin)) &&
            SystemClock.uptimeMillis() < deadline
        ) {
            SystemClock.sleep(100)
        }
        assertFalse("Temporary Device Owner must be removed. dpm: $output", policy.isDeviceOwnerApp(context.packageName))
        assertFalse("Temporary administrator must be removed. dpm: $output", policy.isAdminActive(admin))
        provisioningAttempted = false
    }
}
