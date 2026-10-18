package com.pulse.bluetoothdisable.cover.calendar

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.pulse.bluetoothdisable.MainActivity
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.cover.CoverDeviceAuthenticator
import com.pulse.bluetoothdisable.cover.CoverMode
import com.pulse.bluetoothdisable.cover.CoverModeManager
import com.pulse.bluetoothdisable.cover.CoverModeNavigator
import com.pulse.bluetoothdisable.cover.CoverRecoveryManager
import com.pulse.bluetoothdisable.cover.enableCoverEdgeToEdge
import com.pulse.bluetoothdisable.localization.LanguageManager
import com.pulse.bluetoothdisable.ui.CoverModeRecoveryDialog

class CalendarCoverActivity : FragmentActivity() {
    private lateinit var viewModel: CalendarViewModel
    private lateinit var recovery: CoverRecoveryManager
    private lateinit var authenticator: CoverDeviceAuthenticator
    private var recoveryState by mutableStateOf(CoverRecoveryManager.State.IDLE)
    private var resumed by mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!CoverModeManager(this).isCalendarReady()) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            finish()
            return
        }

        enableCoverEdgeToEdge()
        val modes = CoverModeManager(this)
        recovery = CoverRecoveryManager(
            activeMode = modes::activeMode,
            resetCover = modes::resetCalendarCover,
            onStateChanged = { recoveryState = it },
            modeToRecover = CoverMode.CALENDAR,
        )
        authenticator = CoverDeviceAuthenticator(this)
        viewModel = ViewModelProvider(this)[CalendarViewModel::class.java]

        setContent {
            CalendarCoverTheme(this) {
                CalendarScreen(
                    viewModel,
                    recoveryEnabled = resumed && recoveryState == CoverRecoveryManager.State.IDLE,
                    onRecoveryHold = ::beginRecovery,
                ) {
                    CoverModeNavigator.openMainFromCover(this, CoverMode.CALENDAR)
                }
                if (recoveryState == CoverRecoveryManager.State.CONFIRMING) {
                    CoverModeRecoveryDialog(
                        descriptionRes = R.string.calendar_recovery_description,
                        onReset = ::resetCover,
                        onDismiss = recovery::cancel,
                    )
                }
            }
        }
    }

    private fun beginRecovery() {
        if (!resumed) return
        val attempt = recovery.begin() ?: return
        val started = authenticator.authenticate { success ->
            if (success) {
                recovery.authenticationSucceeded(attempt)
            } else {
                recovery.authenticationRejected(attempt)
            }
        }
        if (!started) {
            recovery.authenticationRejected(attempt)
            Toast.makeText(this, R.string.cover_recovery_auth_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun resetCover() {
        try {
            if (recovery.confirmReset()) CoverModeNavigator.openDefaultMain(this)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.cover_recovery_error, Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
    }

    override fun onPause() {
        resumed = false
        if (::viewModel.isInitialized) viewModel.persistOpenDraft()
        if (::recovery.isInitialized && recovery.state == CoverRecoveryManager.State.CONFIRMING) {
            recovery.cancel()
        }
        super.onPause()
    }

    override fun onDestroy() {
        if (::recovery.isInitialized) recovery.cancel()
        if (::authenticator.isInitialized) authenticator.close()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::recovery.isInitialized) recovery.cancel()
        if (::viewModel.isInitialized) {
            viewModel.closeEditor()
            viewModel.closeViewer()
            viewModel.today()
            viewModel.refresh()
        }
    }
}
