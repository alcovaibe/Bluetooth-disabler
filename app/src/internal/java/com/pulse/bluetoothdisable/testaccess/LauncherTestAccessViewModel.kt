package com.pulse.bluetoothdisable.testaccess

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LauncherTestAccessViewModel(application: Application) : AndroidViewModel(application) {
    private val store = InternalTestTokenStore(application)
    private val storageMutex = Mutex()
    private val client = TestConfigClient()
    private val lease = TestAccessLease()
    private val mutableState = MutableStateFlow(TestAccessUiState(supported = true))
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    private var expiry: Job? = null

    fun allowsLauncherChange(): Boolean = lease.isValid(SystemClock.elapsedRealtime())

    fun refresh() {
        if (operation?.isActive == true) return
        checkConfig()
    }

    fun saveToken(token: String) {
        val normalized = token.trim()
        invalidate()
        operation?.cancel()
        if (!TestConfigClient.TOKEN_PATTERN.matches(normalized)) {
            mutableState.value = mutableState.value.copy(busy = false, allowed = false, status = TestAccessStatus.INVALID_TOKEN)
            return
        }
        checkConfig { store.write(normalized) }
    }

    fun clearToken() {
        invalidate()
        operation?.cancel()
        mutableState.value = mutableState.value.copy(busy = true, allowed = false)
        operation = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { storageMutex.withLock { store.clear() } }
                mutableState.value = TestAccessUiState(supported = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(busy = false, allowed = false, status = TestAccessStatus.STORAGE_ERROR)
            }
        }
    }

    private fun checkConfig(beforeRead: (() -> Unit)? = null) {
        mutableState.value = mutableState.value.copy(
            busy = true,
            allowed = allowsLauncherChange(),
            status = TestAccessStatus.CHECKING,
        )
        operation = viewModelScope.launch {
            val token = try {
                withContext(Dispatchers.IO) {
                    storageMutex.withLock {
                        beforeRead?.invoke()
                        store.read()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                invalidate()
                mutableState.value = mutableState.value.copy(busy = false, allowed = false, status = TestAccessStatus.STORAGE_ERROR)
                return@launch
            }
            if (token == null) {
                invalidate()
                mutableState.value = TestAccessUiState(supported = true)
                return@launch
            }
            mutableState.value = mutableState.value.copy(hasToken = true)
            val started = SystemClock.elapsedRealtime()
            try {
                val config = withContext(Dispatchers.IO) { client.fetch(token) }
                expiry?.cancel()
                val allowed = lease.apply(config, started, SystemClock.elapsedRealtime())
                mutableState.value = mutableState.value.copy(
                    busy = false,
                    allowed = allowed,
                    status = if (allowed) TestAccessStatus.ALLOWED else TestAccessStatus.DENIED,
                )
                if (allowed) scheduleExpiry()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                // A transport outage cannot extend the previously issued lease.
                mutableState.value = mutableState.value.copy(
                    busy = false,
                    allowed = allowsLauncherChange(),
                    status = TestAccessStatus.UNAVAILABLE,
                )
            } catch (_: Exception) {
                invalidate()
                mutableState.value = mutableState.value.copy(busy = false, allowed = false, status = TestAccessStatus.UNAVAILABLE)
            }
        }
    }

    private fun scheduleExpiry() {
        expiry = viewModelScope.launch {
            delay(lease.remainingMillis(SystemClock.elapsedRealtime()))
            lease.clear()
            mutableState.value = mutableState.value.copy(allowed = false, status = TestAccessStatus.EXPIRED)
        }
    }

    private fun invalidate() {
        expiry?.cancel()
        lease.clear()
    }
}
