package com.pulse.bluetoothdisable.cover.notes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.pulse.bluetoothdisable.MainActivity
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.cover.CoverDeviceAuthenticator
import com.pulse.bluetoothdisable.cover.CoverMode
import com.pulse.bluetoothdisable.cover.CoverModeManager
import com.pulse.bluetoothdisable.cover.CoverModeNavigator
import com.pulse.bluetoothdisable.cover.CoverRecoveryManager
import com.pulse.bluetoothdisable.cover.CoverTheme
import com.pulse.bluetoothdisable.cover.enableCoverEdgeToEdge
import com.pulse.bluetoothdisable.localization.LanguageManager

class NotesCoverActivity : FragmentActivity() {
    private lateinit var viewModel: NotesViewModel
    private lateinit var recovery: CoverRecoveryManager
    private lateinit var authenticator: CoverDeviceAuthenticator
    private var recoveryState by mutableStateOf(CoverRecoveryManager.State.IDLE)
    private var resumed by mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val modes = CoverModeManager(this)
        if (!modes.isNotesReady()) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            finish()
            return
        }

        enableCoverEdgeToEdge()
        recovery = CoverRecoveryManager(
            activeMode = modes::activeMode,
            resetCover = modes::resetNotesCover,
            onStateChanged = { recoveryState = it },
            modeToRecover = CoverMode.NOTES,
        )
        authenticator = CoverDeviceAuthenticator(this)
        viewModel = ViewModelProvider(this)[NotesViewModel::class.java]

        setContent {
            // Keep the Notes disguise visually stable instead of inheriting wallpaper-dependent
            // Material You colours that make the cover look unrelated between devices.
            CoverTheme(this) {
                NotesScreen(
                    viewModel = viewModel,
                    recoveryEnabled = resumed && recoveryState == CoverRecoveryManager.State.IDLE,
                    onRecoveryHold = ::beginRecovery,
                    onUnlock = { CoverModeNavigator.openMainFromCover(this, CoverMode.NOTES) },
                )
                if (recoveryState == CoverRecoveryManager.State.CONFIRMING) {
                    AlertDialog(
                        onDismissRequest = recovery::cancel,
                        title = { Text(stringResource(R.string.cover_recovery_title)) },
                        text = { Text(stringResource(R.string.notes_recovery_description)) },
                        dismissButton = {
                            TextButton(onClick = recovery::cancel) { Text(stringResource(R.string.cancel)) }
                        },
                        confirmButton = {
                            TextButton(onClick = ::resetCover) { Text(stringResource(R.string.cover_recovery_reset)) }
                        },
                    )
                }
            }
        }
    }

    private fun beginRecovery() {
        if (!resumed) return
        val attempt = recovery.begin() ?: return
        window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val started = authenticator.authenticate { success ->
            if (success) recovery.authenticationSucceeded(attempt)
            else recovery.authenticationRejected(attempt)
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
        if (::viewModel.isInitialized) viewModel.flushEditor()
        if (::recovery.isInitialized && recovery.state == CoverRecoveryManager.State.CONFIRMING) recovery.cancel()
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
            viewModel.flushEditor()
            viewModel.resetTransientUi()
            viewModel.refresh()
        }
    }
}
