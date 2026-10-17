package com.pulse.bluetoothdisable.cover.gallery

import android.media.MediaPlayer
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pulse.bluetoothdisable.R
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun GalleryVideoPlayer(image: GalleryImage, poster: ImageBitmap?, viewModel: GalleryViewModel) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var source by remember(image.id) { mutableStateOf<GalleryVideoDataSource?>(null) }
    var holder by remember(image.id) { mutableStateOf<SurfaceHolder?>(null) }
    var player by remember(image.id) { mutableStateOf<MediaPlayer?>(null) }
    var ready by remember(image.id) { mutableStateOf(false) }
    var playing by remember(image.id) { mutableStateOf(false) }
    var rendered by remember(image.id) { mutableStateOf(false) }
    var failed by remember(image.id) { mutableStateOf(false) }
    var position by remember(image.id) { mutableFloatStateOf(0f) }
    var duration by remember(image.id) { mutableFloatStateOf((image.durationMillis ?: 0).toFloat()) }
    var seeking by remember(image.id) { mutableStateOf(false) }
    val surface = remember(image.id) { SurfaceView(context) }

    LaunchedEffect(image.id, image.encryptedFileName) {
        var opened: GalleryVideoDataSource? = null
        try {
            try {
                withContext(Dispatchers.IO) { opened = viewModel.openVideoSource(image.id) }
                source = opened
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (_: Exception) { failed = true }
            awaitCancellation()
        } finally { opened?.close() }
    }

    DisposableEffect(surface) {
        val callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(value: SurfaceHolder) { holder = value }
            override fun surfaceChanged(value: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
            override fun surfaceDestroyed(value: SurfaceHolder) { holder = null }
        }
        surface.holder.addCallback(callback)
        if (surface.holder.surface.isValid) holder = surface.holder
        onDispose { surface.holder.removeCallback(callback) }
    }

    DisposableEffect(source, holder) {
        val data = source
        val display = holder
        var active = true
        val media = if (data != null && display != null) MediaPlayer() else null
        if (media != null) {
            player = media
            try {
                media.setDisplay(display)
                media.setDataSource(checkNotNull(data))
                media.setOnPreparedListener {
                    if (active) {
                        duration = it.duration.coerceAtLeast(1).toFloat()
                        ready = true
                    }
                }
                media.setOnInfoListener { _, what, _ ->
                    if (active && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) rendered = true
                    false
                }
                media.setOnCompletionListener { if (active) { playing = false; position = duration } }
                media.setOnErrorListener { _, _, _ ->
                    if (active) { playing = false; failed = true; ready = false }
                    true
                }
                media.prepareAsync()
            } catch (_: Exception) { failed = true; playing = false }
        }
        onDispose {
            active = false
            media?.release()
            player = null
            ready = false
        }
    }

    DisposableEffect(owner, image.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                if (ready) runCatching { player?.takeIf { it.isPlaying }?.pause() }
                playing = false
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(player, ready, playing) {
        val media = player
        if (ready && media != null) {
            try {
                if (playing) {
                    if (position >= duration) media.seekTo(0)
                    media.start()
                } else if (media.isPlaying) media.pause()
            } catch (_: Exception) { failed = true; playing = false }
        }
    }

    LaunchedEffect(player, ready) {
        while (ready) {
            if (!seeking) runCatching { player?.currentPosition?.let { position = it.toFloat() } }
            delay(250)
        }
    }

    val playbackState = stringResource(when {
        failed -> R.string.gallery_video_error
        !ready -> R.string.gallery_video_loading
        playing && rendered -> R.string.gallery_video_playing
        else -> R.string.gallery_video_paused
    })
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("gallery_video_player")
        .semantics { stateDescription = playbackState }, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { surface },
            modifier = Modifier.fillMaxWidth().aspectRatio(image.width.toFloat() / image.height.coerceAtLeast(1)),
        )
        if (!rendered && poster != null) {
            Image(poster, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        if (failed) {
            Text(stringResource(R.string.gallery_video_error), color = Color.White, modifier = Modifier.padding(24.dp))
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.72f)).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(
                enabled = !failed && source != null,
                onClick = {
                    playing = !playing
                },
                modifier = Modifier.testTag("gallery_video_play"),
            ) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.gallery_pause else R.string.gallery_play), tint = Color.White)
            }
            Slider(
                value = position.coerceIn(0f, duration.coerceAtLeast(1f)),
                valueRange = 0f..duration.coerceAtLeast(1f),
                onValueChange = { seeking = true; position = it },
                onValueChangeFinished = {
                    if (ready) runCatching { player?.seekTo(position.toInt()) }
                    seeking = false
                },
                enabled = ready && !failed,
                modifier = Modifier.weight(1f).testTag("gallery_video_seek"),
            )
            Text("${galleryDuration(position.toLong())} / ${galleryDuration(duration.toLong())}", color = Color.White)
        }
    }
}

internal fun galleryDuration(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
    else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}
