package com.pulse.bluetoothdisable.ui

import com.pulse.bluetoothdisable.domain.ProtectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtectionUiStateTest {

    @Test
    fun `ownership defaults to absent until checked`() {
        assertFalse(ProtectionUiState().isDeviceOwner)
    }

    @Test
    fun `Bluetooth states do not determine ownership`() {
        for (state in ProtectionState.entries) {
            assertTrue(ProtectionUiState(state = state, isDeviceOwner = true).isDeviceOwner)
            assertFalse(ProtectionUiState(state = state, isDeviceOwner = false).isDeviceOwner)
        }
    }

    @Test
    fun `operation transitions preserve the last checked ownership`() {
        for (isOwner in listOf(false, true)) {
            val initial = ProtectionUiState(state = ProtectionState.READY, isDeviceOwner = isOwner)
            for (state in listOf(ProtectionState.ENABLING, ProtectionState.DISABLING)) {
                assertEquals(isOwner, initial.copy(state = state).isDeviceOwner)
            }
        }
    }
}
