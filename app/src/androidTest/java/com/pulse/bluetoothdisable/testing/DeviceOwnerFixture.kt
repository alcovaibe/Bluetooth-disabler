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

    @Suppress("DEPRECATION") // Android exposes this API specifically for testing owner removal.
    fun clear() {
        if (!provisioningAttempted) return
        if (policy.isDeviceOwnerApp(context.packageName)) {
            policy.clearDeviceOwnerApp(context.packageName)
        } else if (policy.isAdminActive(admin)) {
            policy.removeActiveAdmin(admin)
        }
        // Clearing the owner also removes its admin asynchronously. A second
        // removeActiveAdmin call races with that removal and can throw SecurityException.
        val deadline = SystemClock.uptimeMillis() + 10_000
        while ((policy.isDeviceOwnerApp(context.packageName) || policy.isAdminActive(admin)) &&
            SystemClock.uptimeMillis() < deadline
        ) {
            SystemClock.sleep(50)
        }
        assertFalse("Temporary Device Owner must be removed", policy.isDeviceOwnerApp(context.packageName))
        assertFalse("Temporary administrator must be removed", policy.isAdminActive(admin))
        provisioningAttempted = false
    }
}
