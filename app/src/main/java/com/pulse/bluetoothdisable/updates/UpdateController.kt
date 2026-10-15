package com.pulse.bluetoothdisable.updates

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.bluetoothdisable.BuildConfig
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

internal enum class UpdateStatus { IDLE, CHECKING, AVAILABLE, CURRENT, NETWORK_ERROR, INVALID_RESPONSE, INTERNAL_DISABLED, BROWSER_ERROR }
internal data class UpdateUiState(val status: UpdateStatus = UpdateStatus.IDLE, val release: UpdateManifest.Release? = null)

internal interface UpdatePreferences {
    var lastAutomaticAttempt: Long
    var deferredCode: Int
}

internal class UpdateController(
    private val scope: CoroutineScope,
    private val source: UpdateSource,
    private val preferences: UpdatePreferences,
    private val enabled: Boolean,
    private val installedCode: Long,
    private val sdk: Int,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val monotonicClock: () -> Long = SystemClock::elapsedRealtime,
) {
    private val mutableState = MutableStateFlow(UpdateUiState())
    val state = mutableState.asStateFlow()
    private var active = false
    private var generation = 0L
    private var job: Job? = null
    private var lastManualAttempt: Long? = null
    private var cached: UpdateSnapshot? = null

    fun enterMain() {
        active = true
        if (!enabled) return
        val now = wallClock()
        val last = preferences.lastAutomaticAttempt
        // Clock rollback must not create a request loop or suppress checks forever.
        if (last != 0L && now < last) { preferences.lastAutomaticAttempt = now; return }
        if (last == 0L || now - last >= AUTOMATIC_INTERVAL) check(manual = false)
    }

    fun leaveMain() {
        active = false
        generation++
        job?.cancel()
        job = null
        mutableState.value = UpdateUiState()
    }

    fun check(manual: Boolean = true) {
        if (!active || job?.isActive == true) return
        if (!enabled) {
            if (manual) mutableState.value = UpdateUiState(UpdateStatus.INTERNAL_DISABLED)
            return
        }
        val elapsed = monotonicClock()
        if (manual && lastManualAttempt?.let { elapsed - it < MANUAL_INTERVAL } == true) return
        if (!requests.tryLock()) return
        if (manual) lastManualAttempt = elapsed else preferences.lastAutomaticAttempt = wallClock()
        val requestGeneration = generation
        mutableState.value = UpdateUiState(UpdateStatus.CHECKING)
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val result = source.load(cached)
                if (!active || requestGeneration != generation) return@launch
                cached = result
                val update = result.manifest.release?.takeIf { it.isUpdateFor(installedCode, sdk) }
                mutableState.value = when {
                    update != null && (manual || update.versionCode > preferences.deferredCode) -> UpdateUiState(UpdateStatus.AVAILABLE, update)
                    manual -> UpdateUiState(UpdateStatus.CURRENT)
                    else -> UpdateUiState()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: InvalidUpdateResponse) {
                if (active && requestGeneration == generation) mutableState.value = UpdateUiState(if (manual) UpdateStatus.INVALID_RESPONSE else UpdateStatus.IDLE)
            } catch (_: IOException) {
                if (active && requestGeneration == generation) mutableState.value = UpdateUiState(if (manual) UpdateStatus.NETWORK_ERROR else UpdateStatus.IDLE)
            } finally {
                requests.unlock()
            }
        }
    }

    fun defer() {
        mutableState.value.release?.let { preferences.deferredCode = maxOf(preferences.deferredCode, it.versionCode) }
        dismiss()
    }
    fun dismiss() { mutableState.value = UpdateUiState() }
    fun browserFailed() { if (active) mutableState.value = UpdateUiState(UpdateStatus.BROWSER_ERROR) }

    private companion object {
        const val AUTOMATIC_INTERVAL = 24 * 60 * 60 * 1000L
        const val MANUAL_INTERVAL = 5_000L
        val requests = Mutex()
    }
}

internal class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val source = SiteUpdateSource()
    private val preferences = application.getSharedPreferences("app_update_check", Context.MODE_PRIVATE)
    val controller = UpdateController(
        viewModelScope, source, object : UpdatePreferences {
            override var lastAutomaticAttempt: Long
                get() = preferences.getLong("last_automatic_attempt", 0L)
                set(value) { preferences.edit { putLong("last_automatic_attempt", value) } }
            override var deferredCode: Int
                get() = preferences.getInt("deferred_code", 0)
                set(value) { preferences.edit { putInt("deferred_code", value) } }
        },
        enabled = BuildConfig.PUBLIC_UPDATE_CHECKS && BuildConfig.APPLICATION_ID == UpdateUrls.APPLICATION_ID,
        installedCode = BuildConfig.VERSION_CODE.toLong(), sdk = Build.VERSION.SDK_INT,
    )
    override fun onCleared() {
        controller.leaveMain()
        source.close()
    }
}
