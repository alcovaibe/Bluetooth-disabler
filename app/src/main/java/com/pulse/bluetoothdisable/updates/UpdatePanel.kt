package com.pulse.bluetoothdisable.updates

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pulse.bluetoothdisable.R
import kotlinx.coroutines.delay

@Composable
internal fun UpdatePanel(controller: UpdateController, language: String, openRelease: ((String) -> Unit)? = null) {
    val state by controller.state.collectAsState()
    val context = LocalContext.current
    Spacer(Modifier.height(16.dp))
    TextButton(
        onClick = { controller.check() }, enabled = state.status != UpdateStatus.CHECKING,
        modifier = Modifier.fillMaxWidth().testTag("check_updates"),
    ) { Text(stringResource(R.string.updates_check)) }
    if (state.status == UpdateStatus.CHECKING) CircularProgressIndicator(Modifier.testTag("updates_progress"))
    val release = state.release
    if (state.status == UpdateStatus.AVAILABLE && release != null) {
        AlertDialog(
            onDismissRequest = controller::defer,
            modifier = Modifier.testTag("update_available"),
            title = { Text(stringResource(R.string.updates_available, release.versionName)) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(if (language == "ru") release.ru else release.en)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val uri = UpdateUrls.release(release.releaseUrl)
                        controller.defer()
                        if (openRelease != null) openRelease(uri.toASCIIString()) else {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri.toASCIIString())).apply {
                                addCategory(Intent.CATEGORY_BROWSABLE)
                            })
                        }
                    } catch (_: ActivityNotFoundException) { controller.browserFailed()
                    } catch (_: SecurityException) { controller.browserFailed()
                    } catch (_: IllegalArgumentException) { controller.browserFailed() }
                }) { Text(stringResource(R.string.updates_details)) }
            },
            dismissButton = { TextButton(onClick = controller::defer) { Text(stringResource(R.string.updates_later)) } },
        )
    }
    val message = when (state.status) {
        UpdateStatus.CURRENT -> R.string.updates_current
        UpdateStatus.NETWORK_ERROR -> R.string.updates_network_error
        UpdateStatus.INVALID_RESPONSE -> R.string.updates_invalid_response
        UpdateStatus.INTERNAL_DISABLED -> R.string.updates_internal_disabled
        UpdateStatus.BROWSER_ERROR -> R.string.updates_browser_error
        else -> null
    }
    if (message != null) {
        var retryEnabled by remember(state) { mutableStateOf(false) }
        LaunchedEffect(state) { delay(5_000); retryEnabled = true }
        AlertDialog(
            onDismissRequest = controller::dismiss,
            modifier = Modifier.testTag("updates_result"),
            title = { Text(stringResource(R.string.updates_check)) },
            text = { Text(stringResource(message)) },
            confirmButton = { TextButton(onClick = controller::dismiss) { Text(stringResource(R.string.updates_close)) } },
            dismissButton = {
                if (state.status == UpdateStatus.NETWORK_ERROR || state.status == UpdateStatus.INVALID_RESPONSE) {
                    TextButton(enabled = retryEnabled, onClick = { controller.dismiss(); controller.check() }) { Text(stringResource(R.string.updates_retry)) }
                }
            },
        )
    }
}
