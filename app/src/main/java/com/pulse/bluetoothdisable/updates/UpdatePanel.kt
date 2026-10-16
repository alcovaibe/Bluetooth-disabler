package com.pulse.bluetoothdisable.updates

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.ui.CoverModeBottomSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun UpdatePanel(controller: UpdateController, language: String) {
    val state by controller.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installing by remember { mutableStateOf(false) }
    // Returning from settings retains the downloaded APK. Installation remains an explicit tap.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { installing = false }
    val installerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { installing = false }
    fun install() {
        if (installing) return
        installing = true
        scope.launch {
            var launched = false
            try {
                val file = controller.verifiedApk() ?: return@launch
                if (!context.packageManager.canRequestPackageInstalls()) {
                    permissionLauncher.launch(UpdateInstaller.permissionIntent(context))
                } else {
                    installerLauncher.launch(UpdateInstaller.installIntent(context, file))
                }
                launched = true
            } catch (error: kotlinx.coroutines.CancellationException) { throw error
            } catch (_: Exception) { controller.installFailed()
            } finally { if (!launched) installing = false }
        }
    }
    Spacer(Modifier.height(16.dp))
    TextButton(
        onClick = { controller.check() }, enabled = state.status !in setOf(UpdateStatus.CHECKING, UpdateStatus.DOWNLOADING),
        modifier = Modifier.fillMaxWidth().testTag("check_updates"),
    ) { Text(stringResource(R.string.updates_check)) }
    if (state.status == UpdateStatus.CHECKING) CircularProgressIndicator(Modifier.testTag("updates_progress"))
    val release = state.release
    if (release != null) {
        CoverModeBottomSheet(onDismiss = controller::defer, testTag = "update_available") {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                SheetTitle(stringResource(R.string.updates_available, release.versionName))
                SheetBody(if (language == "ru") release.ru else release.en)
                when (state.status) {
                    UpdateStatus.DOWNLOADING -> {
                        val total = state.total
                        if (total != null && total > 0) {
                            val progress = (state.received.toFloat() / total).coerceIn(0f, 1f)
                            SheetBody(stringResource(R.string.updates_downloading_percent, (progress * 100).toInt()))
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("download_progress"))
                        } else {
                            SheetBody(stringResource(R.string.updates_downloading))
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp).testTag("download_progress"))
                        }
                    }
                    UpdateStatus.READY -> SheetBody(stringResource(R.string.updates_ready))
                    UpdateStatus.DOWNLOAD_ERROR -> SheetBody(stringResource(R.string.updates_download_error))
                    UpdateStatus.INVALID_APK -> SheetBody(stringResource(R.string.updates_invalid_apk))
                    UpdateStatus.INSTALL_ERROR -> SheetBody(stringResource(R.string.updates_install_error))
                    else -> Unit
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = controller::defer) {
                    ActionText(stringResource(if (state.status == UpdateStatus.DOWNLOADING) R.string.cancel else R.string.updates_later))
                }
                when (state.status) {
                    UpdateStatus.READY, UpdateStatus.INSTALL_ERROR -> TextButton(onClick = { install() }, enabled = !installing) {
                        ActionText(stringResource(R.string.updates_install))
                    }
                    UpdateStatus.DOWNLOADING -> Unit
                    else -> TextButton(onClick = controller::download) {
                        ActionText(stringResource(if (state.status == UpdateStatus.AVAILABLE) R.string.updates_download else R.string.updates_retry))
                    }
                }
            }
        }
    }
    val message = when (state.status) {
        UpdateStatus.CURRENT -> R.string.updates_current
        UpdateStatus.NETWORK_ERROR -> R.string.updates_network_error
        UpdateStatus.INVALID_RESPONSE -> R.string.updates_invalid_response
        UpdateStatus.INTERNAL_DISABLED -> R.string.updates_internal_disabled
        else -> null
    }
    if (message != null) {
        var retryEnabled by remember(state) { mutableStateOf(false) }
        LaunchedEffect(state) { delay(5_000); retryEnabled = true }
        CoverModeBottomSheet(onDismiss = controller::dismiss, testTag = "updates_result") {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                SheetTitle(stringResource(R.string.updates_check))
                SheetBody(stringResource(message))
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                if (state.status == UpdateStatus.NETWORK_ERROR || state.status == UpdateStatus.INVALID_RESPONSE) {
                    TextButton(enabled = retryEnabled, onClick = { controller.dismiss(); controller.check() }) { ActionText(stringResource(R.string.updates_retry)) }
                }
                TextButton(onClick = controller::dismiss) { ActionText(stringResource(R.string.updates_close)) }
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String) = Text(text, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

@Composable
private fun SheetBody(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))

@Composable
private fun ActionText(text: String) = Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
