package com.pulse.bluetoothdisable.cover.gallery

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

enum class GalleryPhotoError { PICKER_UNAVAILABLE, IMPORT_FAILED }

data class GalleryUiState(
    val images: List<GalleryImage> = emptyList(),
    val albums: List<GalleryAlbum> = emptyList(),
    val loading: Boolean = true,
    val storageError: Boolean = false,
    val selectedImageId: String? = null,
    val busy: Boolean = false,
    val photoError: GalleryPhotoError? = null,
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GalleryRepository(application)
    private val access = GalleryAccessManager(application)
    private val detector = GallerySequenceDetector()
    private var verificationInProgress = false
    private var verificationGeneration = 0L
    private var refreshJob: Job? = null
    private data class PreviewKey(val id: String, val revision: String, val thumbnail: Boolean)
    private val previews = GalleryMemoryCache<PreviewKey, ImageBitmap>(
        maxBytes = minOf(64L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 4),
        sizeOf = { it.asAndroidBitmap().allocationByteCount.toLong() },
    )
    private val previewLoads = mutableMapOf<PreviewKey, Deferred<ImageBitmap?>>()
    private val previewSlots = Semaphore(2)
    private var prefetchJob: Job? = null

    var uiState by mutableStateOf(GalleryUiState())
        private set

    init {
        refresh()
    }

    fun select(image: GalleryImage) {
        resetSequence()
        uiState = uiState.copy(selectedImageId = image.id)
    }

    fun closeViewer() {
        resetSequence()
        prefetchJob?.cancel()
        uiState = uiState.copy(selectedImageId = null)
    }

    private fun previewKey(id: String, thumbnail: Boolean): PreviewKey? {
        val image = uiState.images.firstOrNull { it.id == id } ?: return null
        return PreviewKey(id, if (thumbnail) image.thumbnailFileName else image.encryptedFileName, thumbnail)
    }

    internal fun cachedPreview(id: String, thumbnail: Boolean): ImageBitmap? =
        previewKey(id, thumbnail)?.let { previews[it] }

    /** Main-thread coordinator: concurrent requests for the same asset share one decode. */
    internal suspend fun preview(id: String, thumbnail: Boolean, reportFailure: Boolean = true): ImageBitmap? {
        val key = previewKey(id, thumbnail) ?: return null
        previews[key]?.let { return it }
        val load = previewLoads[key] ?: viewModelScope.async(start = CoroutineStart.LAZY) {
            previewSlots.withPermit {
                val decoded = withContext(Dispatchers.IO) {
                    try {
                        val bytes = if (thumbnail) repository.thumbnailBytes(id) else repository.imageBytes(id)
                        checkNotNull(decodeGalleryPreview(bytes, if (thumbnail) 512 else 2048))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                }
                // Editing/deleting a photo while decoding must never cache its previous revision.
                if (key == previewKey(id, thumbnail) && decoded != null) previews.put(key, decoded)
                decoded.takeIf { key == previewKey(id, thumbnail) }
            }
        }.also { deferred ->
            previewLoads[key] = deferred
            deferred.invokeOnCompletion { if (previewLoads[key] === deferred) previewLoads.remove(key) }
        }
        val bitmap = load.await()
        if (bitmap == null && reportFailure && key == previewKey(id, thumbnail)) imageDecodeFailed(id)
        return bitmap
    }

    internal fun prefetchNeighbors(images: List<GalleryImage>, selectedId: String) {
        prefetchJob?.cancel()
        val index = images.indexOfFirst { it.id == selectedId }
        if (index < 0) return
        val neighbors = listOfNotNull(images.getOrNull(index + 1), images.getOrNull(index - 1))
        val relevantIds = neighbors.mapTo(mutableSetOf(selectedId)) { it.id }
        previewLoads.toMap().forEach { (key, job) ->
            if (!key.thumbnail && key.id !in relevantIds) job.cancel()
        }
        prefetchJob = viewModelScope.launch {
            // Finish the visible photo first; speculative failures must not replace it with an error.
            preview(selectedId, thumbnail = false, reportFailure = false)
            for (neighbor in neighbors) {
                preview(neighbor.id, thumbnail = false, reportFailure = false)
            }
        }
    }

    private fun prunePreviews() {
        val current = uiState.images.associateBy { it.id }
        fun valid(key: PreviewKey): Boolean {
            val image = current[key.id] ?: return false
            return key.revision == if (key.thumbnail) image.thumbnailFileName else image.encryptedFileName
        }
        previews.retainKeys(::valid)
        previewLoads.toMap().forEach { (key, job) -> if (!valid(key)) job.cancel() }
    }

    override fun onCleared() {
        prefetchJob?.cancel()
        previews.clear()
        super.onCleared()
    }

    fun resetSequence() {
        detector.reset()
        verificationGeneration++
        verificationInProgress = false
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty() || uiState.busy) return
        mutate(importing = true) {
            repository.importUris(getApplication<Application>().contentResolver, uris)
        }
    }

    fun pickerUnavailable() {
        uiState = uiState.copy(photoError = GalleryPhotoError.PICKER_UNAVAILABLE)
    }

    fun dismissPhotoError() {
        uiState = uiState.copy(photoError = null)
    }

    fun toggleFavorite(image: GalleryImage) = mutate {
        repository.toggleFavorite(image.id)
    }

    fun createAlbum(name: String, image: GalleryImage? = null) = mutate {
        val album = repository.createAlbum(name)
        if (image != null) repository.addToAlbum(image.id, album.id)
    }

    fun addToAlbum(image: GalleryImage, album: GalleryAlbum) = mutate {
        repository.addToAlbum(image.id, album.id)
    }

    fun delete(image: GalleryImage) = mutate {
        repository.delete(image.id)
        if (access.isSecretImage(image.id)) access.clear()
    }

    fun rotate(image: GalleryImage, degrees: Int) = mutate {
        repository.rotate(image.id, degrees)
    }

    fun cropSquare(image: GalleryImage) = mutate {
        repository.cropCenterSquare(image.id)
    }

    fun tapZone(zone: GalleryTapZone, onUnlock: () -> Unit) {
        val imageId = uiState.selectedImageId ?: return
        if (uiState.busy || verificationInProgress) return
        val complete = detector.tap(zone, SystemClock.elapsedRealtime()) ?: return
        val generation = ++verificationGeneration
        verificationInProgress = true
        viewModelScope.launch {
            val matches = withContext(Dispatchers.IO) { access.matches(imageId, complete) }
            if (verificationGeneration != generation) return@launch
            verificationInProgress = false
            if (matches && uiState.selectedImageId == imageId) onUnlock()
        }
    }

    fun imageDecodeFailed(id: String) {
        if (uiState.images.none { it.id == id }) return
        if (uiState.selectedImageId != null && uiState.selectedImageId != id) return
        resetSequence()
        uiState = uiState.copy(storageError = true)
    }

    fun refresh() {
        resetSequence()
        // onResume runs when Photo Picker returns. It must not replace an import with an old snapshot.
        if (uiState.busy) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.images() to repository.albums() }
            }
            val currentImages = result.getOrNull()?.first ?: emptyList()
            val currentAlbums = result.getOrNull()?.second ?: emptyList()
            uiState = uiState.copy(
                images = currentImages,
                albums = currentAlbums,
                loading = false,
                storageError = result.isFailure,
                selectedImageId = uiState.selectedImageId?.takeIf { id ->
                    currentImages.any { it.id == id }
                },
            )
            prunePreviews()
        }
    }

    fun resetTransientUi() {
        resetSequence()
        prefetchJob?.cancel()
        previewLoads.values.toList().forEach { it.cancel() }
        previews.clear()
        uiState = uiState.copy(selectedImageId = null)
    }

    private fun mutate(importing: Boolean = false, action: () -> Unit) {
        if (uiState.busy) return
        refreshJob?.cancel()
        refreshJob = null
        resetSequence()
        uiState = uiState.copy(busy = true, photoError = null)
        viewModelScope.launch {
            val (mutation, snapshot) = withContext(Dispatchers.IO) {
                val mutation = runCatching { action() }
                // Even a failed import must finish the initial load and retain stored photos.
                val snapshot = runCatching { repository.images() to repository.albums() }
                mutation to snapshot
            }
            val images = snapshot.getOrNull()?.first ?: uiState.images
            val albums = snapshot.getOrNull()?.second ?: uiState.albums
            uiState = uiState.copy(
                images = images,
                albums = albums,
                selectedImageId = uiState.selectedImageId?.takeIf { id -> images.any { it.id == id } },
                busy = false,
                loading = false,
                storageError = snapshot.isFailure || (!importing && mutation.isFailure),
                photoError = if (importing && mutation.isFailure && snapshot.isSuccess) GalleryPhotoError.IMPORT_FAILED else null,
            )
            prunePreviews()
        }
    }
}
