package com.pulse.bluetoothdisable.ui

import com.pulse.bluetoothdisable.domain.ProtectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionUiStateTest {

    @Test
    fun `isDeviceOwner is false when state is NOT_PROVISIONED`() {
        val state = ProtectionUiState(state = ProtectionState.NOT_PROVISIONED)
        assertFalse(state.isDeviceOwner)
    }

    @Test
    fun `isDeviceOwner is false when state is UNSUPPORTED`() {
        val state = ProtectionUiState(state = ProtectionState.UNSUPPORTED)
        assertFalse(state.isDeviceOwner)
    }

    @Test
    fun `isDeviceOwner is true when state is READY`() {
        val state = ProtectionUiState(state = ProtectionState.READY)
        assertTrue(state.isDeviceOwner)
    }

    @Test
    fun `isDeviceOwner is true when state is PROTECTED`() {
        val state = ProtectionUiState(state = ProtectionState.PROTECTED)
        assertTrue(state.isDeviceOwner)
    }

    @Test
    fun `isDeviceOwner is true when state is ENABLING`() {
        val state = ProtectionUiState(state = ProtectionState.ENABLING)
        assertTrue(state.isDeviceOwner)
    }

    @Test
    fun `isDeviceOwner is true when state is DISABLING`() {
        val state = ProtectionUiState(state = ProtectionState.DISABLING)
        assertTrue(state.isDeviceOwner)
    }
}
