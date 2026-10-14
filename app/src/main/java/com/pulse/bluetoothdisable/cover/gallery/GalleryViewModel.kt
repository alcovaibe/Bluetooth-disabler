package com.pulse.bluetoothdisable.cover.gallery

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
        uiState = uiState.copy(selectedImageId = null)
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

    suspend fun imageBytes(id: String): ByteArray? {
        val result = withContext(Dispatchers.IO) {
            runCatching { repository.imageBytes(id) }
        }
        if (result.isFailure) uiState = uiState.copy(storageError = true)
        return result.getOrNull()
    }

    suspend fun thumbnailBytes(id: String): ByteArray? {
        val result = withContext(Dispatchers.IO) {
            runCatching { repository.thumbnailBytes(id) }
        }
        if (result.isFailure) uiState = uiState.copy(storageError = true)
        return result.getOrNull()
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
        }
    }

    fun resetTransientUi() {
        resetSequence()
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
        }
    }
}
