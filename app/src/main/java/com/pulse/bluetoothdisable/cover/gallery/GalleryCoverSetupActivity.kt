package com.pulse.bluetoothdisable.cover.gallery

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.cover.CoverMode
import com.pulse.bluetoothdisable.cover.CoverModeManager
import com.pulse.bluetoothdisable.cover.CoverModeNavigator
import com.pulse.bluetoothdisable.localization.LanguageManager
import com.pulse.bluetoothdisable.ui.CoverModeConfirmationDialog
import com.pulse.bluetoothdisable.ui.theme.BluetoothDisableTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryCoverSetupActivity : ComponentActivity() {
    private lateinit var repository: GalleryRepository
    private var pickerResult by mutableStateOf<List<android.net.Uri>>(emptyList())

    private val picker = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        pickerResult = uris
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = GalleryRepository(this)

        setContent {
            BluetoothDisableTheme(darkTheme = isSystemInDarkTheme()) {
                GallerySetupScreen(
                    repository = repository,
                    pickedUris = pickerResult,
                    onPickedUrisConsumed = { pickerResult = emptyList() },
                    onPickPhotos = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onCancel = ::finish,
                    onComplete = { imageId, sequence, onResult ->
                        lifecycleScope.launch {
                            val activation = withContext(Dispatchers.IO) {
                                runCatching {
                                    val stillExists = repository.image(imageId) != null
                                    require(stillExists) { "Secret image was removed before activation" }
                                    CoverModeManager(this@GalleryCoverSetupActivity)
                                        .activateGallery(imageId, sequence)
                                }
                            }
                            if (activation.isSuccess) {
                                Toast.makeText(
                                    this@GalleryCoverSetupActivity,
                                    R.string.gallery_setup_completed,
                                    Toast.LENGTH_SHORT,
                                ).show()
                                CoverModeNavigator.openCover(this@GalleryCoverSetupActivity, CoverMode.GALLERY)
                            } else {
                                Log.e(TAG, "Unable to activate Gallery Cover Mode", activation.exceptionOrNull())
                                onResult(false)
                            }
                        }
                    },
                )
            }
        }
    }

    companion object {
        private const val TAG = "GalleryCoverSetup"
    }
}

@Composable
fun GalleryCoverConfirmationDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    CoverModeConfirmationDialog(
        titleRes = R.string.gallery_cover_confirm_title,
        messageRes = R.string.gallery_cover_confirm_message,
        onContinue = onContinue,
        onDismiss = onDismiss,
    )
}

private enum class GallerySetupStep { IMAGE, FIRST_SEQUENCE, CONFIRM_SEQUENCE }

@Composable
private fun GallerySetupScreen(
    repository: GalleryRepository,
    pickedUris: List<android.net.Uri>,
    onPickedUrisConsumed: () -> Unit,
    onPickPhotos: () -> Unit,
    onCancel: () -> Unit,
    onComplete: (String, List<GalleryTapZone>, (Boolean) -> Unit) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var images by remember { mutableStateOf<List<GalleryImage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var storageError by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableStateOf(GallerySetupStep.IMAGE) }
    var firstSequence by remember { mutableStateOf<List<GalleryTapZone>>(emptyList()) }
    var currentSequence by remember { mutableStateOf<List<GalleryTapZone>>(emptyList()) }
    var mismatch by remember { mutableStateOf(false) }
    var setupFailed by remember { mutableStateOf(false) }

    suspend fun reload() {
        val result = withContext(Dispatchers.IO) { runCatching { repository.images() } }
        images = result.getOrDefault(emptyList())
        storageError = result.isFailure
        loading = false
        if (selectedId != null && images.none { it.id == selectedId }) selectedId = null
    }

    LaunchedEffect(pickedUris) {
        if (pickedUris.isEmpty()) {
            reload()
            return@LaunchedEffect
        }
        busy = true
        val result = withContext(Dispatchers.IO) {
            runCatching { repository.importUris(context.contentResolver, pickedUris) }
        }
        val importFailed = result.isFailure
        reload()
        if (importFailed) storageError = true
        busy = false
        // Clearing this effect's key earlier cancels reload at its IO suspension point.
        // Publish the refreshed UI first, then acknowledge the picker result as the last step.
        onPickedUrisConsumed()
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.safeDrawingPadding().imePadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.gallery_setup_title), style = MaterialTheme.typography.headlineSmall)
            if (storageError) {
                Text(
                    stringResource(R.string.gallery_storage_error),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (setupFailed) {
                Text(
                    stringResource(R.string.gallery_setup_failed),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            when (step) {
                GallerySetupStep.IMAGE -> {
                    Text(
                        stringResource(R.string.gallery_setup_pick_secret),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onPickPhotos, enabled = !busy) {
                        Text(stringResource(R.string.gallery_add_photos))
                    }
                    if (loading || busy) {
                        CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    }
                    if (!loading && images.isEmpty()) {
                        Text(stringResource(R.string.gallery_empty))
                    } else {
                        GallerySetupGrid(
                            images = images,
                            selectedId = selectedId,
                            repository = repository,
                            onSelect = { selectedId = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Button(
                        enabled = selectedId != null && !busy,
                        onClick = {
                            currentSequence = emptyList()
                            firstSequence = emptyList()
                            mismatch = false
                            step = GallerySetupStep.FIRST_SEQUENCE
                        },
                    ) { Text(stringResource(R.string.continue_action)) }
                    TextButton(onClick = onCancel, enabled = !busy) {
                        Text(stringResource(R.string.cancel))
                    }
                }

                GallerySetupStep.FIRST_SEQUENCE,
                GallerySetupStep.CONFIRM_SEQUENCE -> {
                    Text(
                        stringResource(
                            if (step == GallerySetupStep.FIRST_SEQUENCE) {
                                R.string.gallery_sequence_first_instruction
                            } else {
                                R.string.gallery_sequence_confirm_instruction
                            },
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(
                            R.string.gallery_sequence_progress,
                            currentSequence.size,
                            GalleryAccessPolicy.SEQUENCE_LENGTH,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (mismatch) {
                        Text(
                            stringResource(R.string.gallery_sequence_mismatch),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    GallerySequencePad(
                        selected = currentSequence,
                        enabled = !busy,
                        onZone = { zone ->
                            if (
                                currentSequence.size < GalleryAccessPolicy.SEQUENCE_LENGTH &&
                                zone !in currentSequence
                            ) {
                                mismatch = false
                                currentSequence = currentSequence + zone
                            }
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                currentSequence = emptyList()
                                mismatch = false
                            },
                        ) {
                            Text(stringResource(R.string.gallery_sequence_clear))
                        }
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                currentSequence = emptyList()
                                mismatch = false
                                step = GallerySetupStep.IMAGE
                            },
                        ) {
                            Text(stringResource(R.string.gallery_back))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        enabled = currentSequence.size == GalleryAccessPolicy.SEQUENCE_LENGTH && !busy,
                        onClick = {
                            if (step == GallerySetupStep.FIRST_SEQUENCE) {
                                firstSequence = currentSequence
                                currentSequence = emptyList()
                                mismatch = false
                                step = GallerySetupStep.CONFIRM_SEQUENCE
                            } else if (currentSequence != firstSequence) {
                                mismatch = true
                                currentSequence = emptyList()
                            } else {
                                val imageId = selectedId ?: return@Button
                                busy = true
                                setupFailed = false
                                onComplete(imageId, firstSequence) { success ->
                                    busy = false
                                    setupFailed = !success
                                }
                            }
                        },
                    ) {
                        Text(
                            stringResource(
                                if (step == GallerySetupStep.FIRST_SEQUENCE) {
                                    R.string.continue_action
                                } else {
                                    R.string.gallery_finish_setup
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GallerySetupGrid(
    images: List<GalleryImage>,
    selectedId: String?,
    repository: GalleryRepository,
    onSelect: (String) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(images.chunked(3), key = { row -> row.joinToString("|") { it.id } }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { index ->
                    val image = row.getOrNull(index)
                    if (image == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(128.dp)
                                .testTag("gallery_setup_image_${image.id}")
                                .clickable { onSelect(image.id) },
                            border = BorderStroke(
                                if (selectedId == image.id) 3.dp else 1.dp,
                                if (selectedId == image.id) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                        ) {
                            SetupThumbnail(repository, image.id)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupThumbnail(repository: GalleryRepository, id: String) {
    val bitmap by produceState<ImageBitmap?>(null, id) {
        val bytes = withContext(Dispatchers.IO) {
            runCatching { repository.thumbnailBytes(id) }.getOrNull()
        }
        value = bytes?.let { data ->
            withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
            }
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun GallerySequencePad(
    selected: List<GalleryTapZone>,
    enabled: Boolean,
    onZone: (GalleryTapZone) -> Unit,
) {
    val rows = listOf(
        listOf(GalleryTapZone.TOP_LEFT, null, GalleryTapZone.TOP_RIGHT),
        listOf(null, GalleryTapZone.CENTER, null),
        listOf(GalleryTapZone.BOTTOM_LEFT, null, GalleryTapZone.BOTTOM_RIGHT),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { zone ->
                    if (zone == null) {
                        Spacer(Modifier.weight(1f).height(70.dp))
                    } else {
                        val order = selected.indexOf(zone).takeIf { it >= 0 }?.plus(1)
                        Button(
                            onClick = { onZone(zone) },
                            enabled = enabled &&
                                zone !in selected &&
                                selected.size < GalleryAccessPolicy.SEQUENCE_LENGTH,
                            modifier = Modifier.weight(1f).height(70.dp),
                        ) {
                            Text(if (order == null) zoneSymbol(zone) else "$order ${zoneSymbol(zone)}")
                        }
                    }
                }
            }
        }
    }
}

private fun zoneSymbol(zone: GalleryTapZone): String = when (zone) {
    GalleryTapZone.TOP_LEFT -> "↖"
    GalleryTapZone.TOP_RIGHT -> "↗"
    GalleryTapZone.CENTER -> "●"
    GalleryTapZone.BOTTOM_LEFT -> "↙"
    GalleryTapZone.BOTTOM_RIGHT -> "↘"
}
