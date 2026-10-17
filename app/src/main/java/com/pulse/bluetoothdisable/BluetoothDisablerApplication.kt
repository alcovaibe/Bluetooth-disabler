package com.pulse.bluetoothdisable

import android.app.Application
import com.pulse.bluetoothdisable.cover.CoverModeManager
import com.pulse.bluetoothdisable.cover.gallery.GalleryTrashCleanupService
import com.pulse.bluetoothdisable.transfer.DeviceTransferGuard

class BluetoothDisablerApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Run before activities/services read local preferences. If an OEM migration
        // tool copied app-private data from another device, reset it to clean-install state.
        DeviceTransferGuard.enforce(this)

        // Roll back an interrupted cover-mode activation and repair the legacy 1.0.5
        // calculator launcher style, which did not yet have an access code.
        CoverModeManager(this).recoverInterruptedSetup()
        GalleryTrashCleanupService.initialize(this)
    }
}
