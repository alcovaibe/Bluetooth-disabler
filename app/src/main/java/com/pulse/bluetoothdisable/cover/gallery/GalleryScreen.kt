package com.pulse.bluetoothdisable.cover.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PhotoAlbum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.cover.coverRecoveryHold
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

private enum class GallerySection {
    PHOTOS,
    ALBUMS,
}

private const val FAVORITES_ALBUM_ID = "__favorites__"

@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    recoveryEnabled: Boolean,
    onRecoveryHold: () -> Unit,
    onAddPhotos: () -> Unit,
) {
    val state = viewModel.uiState
    var section by remember { mutableStateOf(GallerySection.PHOTOS) }
    var selectedAlbumId by remember { mutableStateOf<String?>(null) }
    var showCreateAlbum by remember { mutableStateOf(false) }

    state.photoError?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPhotoError,
            title = { Text(stringResource(R.string.gallery_add_photos)) },
            text = { Text(stringResource(if (error == GalleryPhotoError.PICKER_UNAVAILABLE) R.string.gallery_picker_unavailable else R.string.gallery_import_error)) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissPhotoError(); onAddPhotos() }) {
                    Text(stringResource(R.string.notes_retry))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPhotoError) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    val selected = state.selectedImageId?.let { id -> state.images.firstOrNull { it.id == id } }
    val albumImages = selectedAlbumId?.let { id ->
        when (id) {
            FAVORITES_ALBUM_ID -> state.images.filter { it.favorite }
            else -> state.images.filter { id in it.albumIds }
        }
    }
    val viewerImages = when {
        selected == null -> emptyList()
        albumImages?.any { it.id == selected.id } == true -> albumImages
        selectedAlbumId != null -> listOf(selected)
        else -> state.images
    }

    if (selected != null) {
        // A new photo/revision gets its own painter, bitmap state, transforms and gestures.
        key(selected.id, selected.encryptedFileName) {
            GalleryViewer(
                image = selected,
                images = viewerImages,
                albums = state.albums,
                viewModel = viewModel,
                recoveryEnabled = recoveryEnabled,
                onRecoveryHold = onRecoveryHold,
                onBack = viewModel::closeViewer,
            )
        }
        return
    }

    if (selectedAlbumId != null) {
        val title = if (selectedAlbumId == FAVORITES_ALBUM_ID) {
            stringResource(R.string.gallery_favorites)
        } else {
            state.albums.firstOrNull { it.id == selectedAlbumId }?.name
                ?: stringResource(R.string.gallery_albums)
        }
        GalleryAlbumDetail(
            title = title,
            images = albumImages.orEmpty(),
            viewModel = viewModel,
            onBack = { selectedAlbumId = null },
        )
        return
    }

    if (showCreateAlbum) {
        GalleryCreateAlbumDialog(
            onCreate = { name ->
                showCreateAlbum = false
                viewModel.createAlbum(name)
            },
            onDismiss = { showCreateAlbum = false },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.launcher_name_gallery),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .weight(1f)
                        .coverRecoveryHold(recoveryEnabled, onRecoveryHold),
                )
                when (section) {
                    GallerySection.PHOTOS -> {
                        TextButton(onClick = onAddPhotos, enabled = !state.busy) {
                            Text(stringResource(R.string.gallery_add_photos))
                        }
                    }
                    GallerySection.ALBUMS -> {
                        IconButton(
                            onClick = { showCreateAlbum = true },
                            enabled = !state.busy,
                            modifier = Modifier.testTag("gallery_create_album"),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.gallery_create_album))
                        }
                    }
                }
            }
        },
        bottomBar = {
            GallerySectionDock(
                section = section,
                onSection = { section = it },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.storageError -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.gallery_storage_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                section == GallerySection.PHOTOS && state.images.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.gallery_empty))
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onAddPhotos, enabled = !state.busy) {
                                Text(stringResource(R.string.gallery_add_photos))
                            }
                        }
                    }
                }

                section == GallerySection.PHOTOS -> {
                    GalleryTimeline(
                        images = state.images,
                        viewModel = viewModel,
                    )
                }

                else -> {
                    GalleryAlbumsOverview(
                        images = state.images,
                        albums = state.albums,
                        viewModel = viewModel,
                        onOpenAlbum = { selectedAlbumId = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun GallerySectionDock(
    section: GallerySection,
    onSection: (GallerySection) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier, containerColor = Color.Transparent, tonalElevation = 0.dp) {
        GallerySection.entries.forEach { item ->
            NavigationBarItem(
                selected = section == item,
                onClick = { onSection(item) },
                modifier = Modifier.testTag("gallery_section_$item"),
                icon = {
                    Icon(
                        if (item == GallerySection.PHOTOS) Icons.Rounded.Photo else Icons.Rounded.PhotoAlbum,
                        contentDescription = null,
                    )
                },
                label = {
                    Text(stringResource(if (item == GallerySection.PHOTOS) R.string.gallery_photos else R.string.gallery_albums))
                },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent),
            )
        }
    }
}

@Composable
private fun GalleryTimeline(
    images: List<GalleryImage>,
    viewModel: GalleryViewModel,
) {
    val locale = LocalConfiguration.current.locales[0]
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val groups = images.groupBy { image ->
        Instant.ofEpochMilli(image.capturedAt ?: image.importedAt).atZone(zone).toLocalDate()
    }.toSortedMap(compareByDescending { it })

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        groups.forEach { (date, datedImages) ->
            item(key = "date-$date") {
                val label = when (date) {
                    today -> stringResource(R.string.gallery_today)
                    today.minusDays(1) -> stringResource(R.string.gallery_yesterday)
                    else -> date.format(
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale),
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(
                items = datedImages.chunked(3),
                key = { row -> "date-$date-${row.joinToString("|") { it.id }}" },
            ) { row ->
                GalleryImageRow(row = row, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun GalleryGrid(images: List<GalleryImage>, viewModel: GalleryViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(images.chunked(3), key = { row -> row.joinToString("|") { it.id } }) { row ->
            GalleryImageRow(row = row, viewModel = viewModel)
        }
    }
}

@Composable
private fun GalleryImageRow(
    row: List<GalleryImage>,
    viewModel: GalleryViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(3) { column ->
            val image = row.getOrNull(column)
            if (image == null) {
                Spacer(Modifier.weight(1f))
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(132.dp)
                        .clickable { viewModel.select(image) },
                ) {
                    GalleryStoredImage(
                        id = image.id,
                        thumbnail = true,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (image.favorite) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = stringResource(R.string.gallery_favorite),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                    MaterialTheme.shapes.small,
                                )
                                .padding(5.dp)
                                .size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryAlbumsOverview(
    images: List<GalleryImage>,
    albums: List<GalleryAlbum>,
    viewModel: GalleryViewModel,
    onOpenAlbum: (String) -> Unit,
) {
    val favoriteImages = images.filter { it.favorite }
    val entries = buildList {
        if (favoriteImages.isNotEmpty()) {
            add(
                AlbumEntry(
                    id = FAVORITES_ALBUM_ID,
                    name = null,
                    images = favoriteImages,
                    favorite = true,
                ),
            )
        }
        albums.forEach { album ->
            add(
                AlbumEntry(
                    id = album.id,
                    name = album.name,
                    images = images.filter { album.id in it.albumIds },
                    favorite = false,
                ),
            )
        }
    }

    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.gallery_no_albums),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(entries.chunked(2), key = { row -> row.joinToString("|") { it.id } }) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(2) { index ->
                    val entry = row.getOrNull(index)
                    if (entry == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        GalleryAlbumCard(
                            entry = entry,
                            viewModel = viewModel,
                            onClick = { onOpenAlbum(entry.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

private data class AlbumEntry(
    val id: String,
    val name: String?,
    val images: List<GalleryImage>,
    val favorite: Boolean,
)

@Composable
private fun GalleryAlbumCard(
    entry: AlbumEntry,
    viewModel: GalleryViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        onClick = onClick,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val cover = entry.images.firstOrNull()
                if (cover != null) {
                    GalleryStoredImage(
                        id = cover.id,
                        thumbnail = true,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.PhotoAlbum,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (entry.favorite) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    )
                }
            }
            Text(
                text = entry.name ?: stringResource(R.string.gallery_favorites),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp),
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.gallery_photo_count, entry.images.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun GalleryAlbumDetail(
    title: String,
    images: List<GalleryImage>,
    viewModel: GalleryViewModel,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.gallery_back),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(
                    text = images.size.toString(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        },
    ) { padding ->
        if (images.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.gallery_album_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Box(Modifier.fillMaxSize().padding(padding)) {
                GalleryGrid(images = images, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun GalleryViewer(
    image: GalleryImage,
    images: List<GalleryImage>,
    albums: List<GalleryAlbum>,
    viewModel: GalleryViewModel,
    recoveryEnabled: Boolean,
    onRecoveryHold: () -> Unit,
    onBack: () -> Unit,
) {
    var showInfo by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showAlbumPicker by remember { mutableStateOf(false) }
    var showAlbumCreator by remember { mutableStateOf(false) }
    var scale by remember(image.id) { mutableFloatStateOf(1f) }
    var translation by remember(image.id) { mutableStateOf(Offset.Zero) }
    val bitmap by galleryBitmap(viewModel, image.id, thumbnail = false)
    LaunchedEffect(image.id, images) {
        viewModel.prefetchNeighbors(images, image.id)
    }

    BackHandler {
        onBack()
    }

    fun move(delta: Int) {
        val index = images.indexOfFirst { it.id == image.id }
        if (index < 0) return
        val target = (index + delta).coerceIn(0, images.lastIndex)
        if (target != index) viewModel.select(images[target])
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.gallery_back),
                    )
                }
                Text(
                    stringResource(R.string.launcher_name_gallery),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f)
                        .coverRecoveryHold(recoveryEnabled, onRecoveryHold),
                )
                IconButton(
                    onClick = { showInfo = true },
                    modifier = Modifier.testTag("gallery_info"),
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = stringResource(R.string.gallery_info))
                }
            }

            Box(
                modifier = Modifier
                    .testTag("gallery_viewer")
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .galleryViewerGestures(
                        imageId = image.id,
                        scale = scale,
                        translation = translation,
                        onTransform = { nextScale, nextTranslation ->
                            scale = nextScale
                            translation = nextTranslation
                        },
                        onSwipe = { direction -> move(direction) },
                    )
                    .pointerInput(image.id) {
                        detectTapGestures(onDoubleTap = {
                            scale = if (scale > 1.01f) 1f else 2f
                            translation = Offset.Zero
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    bitmap != null -> {
                        Image(
                            bitmap = bitmap!!,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .testTag("gallery_viewer_image_${image.id}")
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = translation.x
                                    translationY = translation.y
                                },
                        )
                    }
                    viewModel.uiState.storageError -> {
                        Text(
                            stringResource(R.string.gallery_storage_error),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    else -> CircularProgressIndicator()
                }
            }

            Surface(tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GalleryToolbarAction(
                        icon = if (image.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        labelRes = R.string.gallery_favorite,
                        tint = if (image.favorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    ) {
                        viewModel.toggleFavorite(image)
                    }
                    GalleryToolbarAction(
                        icon = Icons.Rounded.PhotoAlbum,
                        labelRes = R.string.gallery_album,
                    ) {
                        showAlbumPicker = true
                    }
                    GalleryToolbarAction(
                        icon = Icons.Rounded.Edit,
                        labelRes = R.string.gallery_edit,
                    ) {
                        showEdit = true
                    }
                    GalleryToolbarAction(
                        icon = Icons.Rounded.Delete,
                        labelRes = R.string.gallery_delete,
                        tint = MaterialTheme.colorScheme.error,
                    ) {
                        showDelete = true
                    }
                }
            }
        }
    }

    if (showInfo) {
        GalleryInfoDialog(image = image, onDismiss = { showInfo = false })
    }
    if (showEdit) {
        GalleryEditDialog(
            onRotateLeft = {
                showEdit = false
                viewModel.rotate(image, -90)
            },
            onRotateRight = {
                showEdit = false
                viewModel.rotate(image, 90)
            },
            onCropSquare = {
                showEdit = false
                viewModel.cropSquare(image)
            },
            onDismiss = { showEdit = false },
        )
    }
    if (showAlbumPicker) {
        GalleryAlbumPickerDialog(
            image = image,
            albums = albums,
            onSelect = { album ->
                showAlbumPicker = false
                viewModel.addToAlbum(image, album)
            },
            onCreate = {
                showAlbumPicker = false
                showAlbumCreator = true
            },
            onDismiss = { showAlbumPicker = false },
        )
    }
    if (showAlbumCreator) {
        GalleryCreateAlbumDialog(
            onCreate = { name ->
                showAlbumCreator = false
                viewModel.createAlbum(name, image)
            },
            onDismiss = { showAlbumCreator = false },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.gallery_delete_title)) },
            text = { Text(stringResource(R.string.gallery_delete_message)) },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.delete(image)
                }) { Text(stringResource(R.string.gallery_delete)) }
            },
        )
    }
}

@Composable
private fun GalleryToolbarAction(
    icon: ImageVector,
    labelRes: Int,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(labelRes),
                tint = tint,
                modifier = Modifier.size(26.dp),
            )
            Text(
                stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = tint,
            )
        }
    }
}

@Composable
private fun GalleryAlbumPickerDialog(
    image: GalleryImage,
    albums: List<GalleryAlbum>,
    onSelect: (GalleryAlbum) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gallery_add_to_album)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (albums.isEmpty()) {
                    Text(
                        stringResource(R.string.gallery_no_albums),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(albums, key = { it.id }) { album ->
                            TextButton(
                                onClick = { onSelect(album) },
                                enabled = album.id !in image.albumIds,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = album.name,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(
                        stringResource(R.string.gallery_create_album),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun GalleryCreateAlbumDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gallery_create_album)) },
        text = {
            TextField(
                value = name,
                onValueChange = { if (it.length <= 80) name = it },
                label = { Text(stringResource(R.string.gallery_album_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.gallery_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun GalleryInfoDialog(image: GalleryImage, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gallery_info)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { Text("${image.width} × ${image.height}") }
                image.colorSpace?.let { color ->
                    item { Text(stringResource(R.string.gallery_color_space, color)) }
                }
                image.shooting.forEach { (key, value) -> item { Text("$key: $value") } }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.gallery_close)) }
        },
    )
}

@Composable
private fun GalleryEditDialog(
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onCropSquare: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gallery_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRotateLeft, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.gallery_rotate_left))
                }
                Button(onClick = onRotateRight, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.gallery_rotate_right))
                }
                Button(onClick = onCropSquare, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.gallery_crop_square))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun GalleryStoredImage(
    id: String,
    thumbnail: Boolean,
    viewModel: GalleryViewModel,
    modifier: Modifier,
) {
    val bitmap by galleryBitmap(viewModel, id, thumbnail)
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

@Composable
private fun galleryBitmap(
    viewModel: GalleryViewModel,
    id: String,
    thumbnail: Boolean,
): State<ImageBitmap?> {
    val image = viewModel.uiState.images.firstOrNull { it.id == id }
    val revision = if (thumbnail) image?.thumbnailFileName else image?.encryptedFileName
    // produceState retains its previous value when only effect keys change. Scope the state
    // to the asset identity so a new ID cannot display the old bitmap while decoding.
    return key(id, thumbnail, revision) {
        produceState(initialValue = viewModel.cachedPreview(id, thumbnail), viewModel) {
            value = viewModel.preview(id, thumbnail)
        }
    }
}

private fun Modifier.galleryViewerGestures(
    imageId: String,
    scale: Float,
    translation: Offset,
    onTransform: (Float, Offset) -> Unit,
    onSwipe: (Int) -> Unit,
): Modifier = pointerInput(imageId, scale, translation) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)

        var localScale = scale
        var localTranslation = translation
        var previousCentroid: Offset? = null
        var previousSpan: Float? = null
        var horizontalDrag = 0f
        var verticalDrag = 0f
        var multiTouch = false

        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break

            if (pressed.size >= 2) {
                multiTouch = true
                val first = pressed[0]
                val second = pressed[1]
                val centroid = Offset(
                    (first.position.x + second.position.x) / 2f,
                    (first.position.y + second.position.y) / 2f,
                )
                val span = (first.position - second.position).getDistance()
                val oldCentroid = previousCentroid
                val oldSpan = previousSpan

                if (oldCentroid != null && oldSpan != null && oldSpan > 0f) {
                    val zoom = span / oldSpan
                    val nextScale = (localScale * zoom).coerceIn(1f, 5f)
                    val pan = centroid - oldCentroid
                    localTranslation = if (nextScale <= 1.01f) {
                        Offset.Zero
                    } else {
                        localTranslation + pan
                    }
                    localScale = nextScale
                    onTransform(localScale, localTranslation)
                }

                previousCentroid = centroid
                previousSpan = span
                pressed.forEach { it.consume() }
            } else {
                previousCentroid = null
                previousSpan = null
                val change = pressed.first()
                val delta = change.positionChange()

                if (localScale > 1.01f) {
                    localTranslation += delta
                    onTransform(localScale, localTranslation)
                    change.consume()
                } else if (!multiTouch) {
                    horizontalDrag += delta.x
                    verticalDrag += delta.y
                    if (abs(delta.x) > abs(delta.y)) change.consume()
                }
            }
        }

        if (
            !multiTouch &&
            localScale <= 1.01f &&
            abs(horizontalDrag) > 120f &&
            abs(horizontalDrag) > abs(verticalDrag) * 1.2f
        ) {
            onSwipe(if (horizontalDrag < 0f) 1 else -1)
        }
    }
}
