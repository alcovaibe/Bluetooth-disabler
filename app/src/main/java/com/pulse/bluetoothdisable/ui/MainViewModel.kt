package com.pulse.bluetoothdisable.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.bluetoothdisable.admin.DeviceOwnerManager
import com.pulse.bluetoothdisable.diagnostics.CapabilityDetector
import com.pulse.bluetoothdisable.domain.ProtectionEngine
import com.pulse.bluetoothdisable.domain.ProtectionError
import com.pulse.bluetoothdisable.domain.ProtectionResult
import com.pulse.bluetoothdisable.domain.ProtectionState
import com.pulse.bluetoothdisable.policy.AndroidBluetoothPolicyController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ProtectionUiState(
    val state: ProtectionState = ProtectionState.NOT_PROVISIONED,
    val error: ProtectionError? = null,
    // Ownership is independent of Bluetooth capability and policy-read errors.
    val isDeviceOwner: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val deviceOwnerStatus = DeviceOwnerManager(application)
    private val engine = ProtectionEngine(
        deviceOwnerStatus = deviceOwnerStatus,
        bluetoothCapability = CapabilityDetector(application),
        bluetoothPolicy = AndroidBluetoothPolicyController(application),
    )

    private val _uiState = MutableStateFlow(ProtectionUiState())
    val uiState: StateFlow<ProtectionUiState> = _uiState.asStateFlow()

    private val operationMutex = Mutex()
    private var operationJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        launchEngineOperation(engine::currentState)
    }

    fun enableProtection() {
        _uiState.value = _uiState.value.copy(state = ProtectionState.ENABLING, error = null)
        launchEngineOperation(engine::enableProtection)
    }

    fun disableProtection() {
        _uiState.value = _uiState.value.copy(state = ProtectionState.DISABLING, error = null)
        launchEngineOperation(engine::disableProtection)
    }

    private fun launchEngineOperation(operation: () -> ProtectionResult) {
        // DevicePolicyManager and Bluetooth adapter calls are system-service work and must not
        // block Compose's main/UI thread. The mutex also prevents a cancelled but already-running
        // platform call from racing a newer request. Only the newest coroutine may publish UI state.
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            val (result, isDeviceOwner) = withContext(Dispatchers.IO) {
                operationMutex.withLock {
                    operation() to deviceOwnerStatus.isDeviceOwner()
                }
            }
            applyResult(result, isDeviceOwner)
        }
    }

    private fun applyResult(result: ProtectionResult, isDeviceOwner: Boolean) {
        _uiState.value = when (result) {
            is ProtectionResult.Success -> ProtectionUiState(
                state = result.state,
                isDeviceOwner = isDeviceOwner,
            )
            is ProtectionResult.Failure -> ProtectionUiState(
                state = ProtectionState.ERROR,
                error = result.error,
                isDeviceOwner = isDeviceOwner,
            )
        }
    }
}
