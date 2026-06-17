package com.example.douyin.ui

import android.net.Uri
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.douyin.network.ApiClient
import kotlinx.coroutines.delay

data class VideoProgressState(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L
) {
    val isSeekable: Boolean
        get() = durationMs > 0L

    val progressFraction: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val bufferedFraction: Float
        get() = if (durationMs > 0L) {
            (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

data class VideoSeekRequest(
    val id: Long,
    val positionMs: Long
)

@Composable
fun LocalVideoPlayer(
    @RawRes videoRes: Int? = null,
    videoUrl: String? = null,
    isActive: Boolean,
    isPaused: Boolean = false,
    seekRequest: VideoSeekRequest? = null,
    onProgress: (VideoProgressState) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val progressCallback = rememberUpdatedState(onProgress)
    val mediaUri = remember(videoRes, videoUrl) {
        ApiClient.resolveUrl(videoUrl)?.let(Uri::parse)
            ?: videoRes?.let { Uri.parse("android.resource://${context.packageName}/$it") }
    }
    val player = remember(mediaUri) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            mediaUri?.let {
                setMediaItem(MediaItem.fromUri(it))
                prepare()
            }
        }
    }

    LaunchedEffect(isActive, isPaused, player) {
        if (isActive && !isPaused) {
            player.playWhenReady = true
            player.play()
        } else {
            player.playWhenReady = false
            player.pause()
        }
    }

    LaunchedEffect(seekRequest, player) {
        val request = seekRequest ?: return@LaunchedEffect
        val durationMs = player.seekableDurationMs()
        val targetMs = if (durationMs > 0L) {
            request.positionMs.coerceIn(0L, durationMs)
        } else {
            request.positionMs.coerceAtLeast(0L)
        }
        player.seekTo(targetMs)
        progressCallback.value(player.progressState())
    }

    LaunchedEffect(isActive, player) {
        while (isActive) {
            progressCallback.value(player.progressState())
            delay(250)
        }
        progressCallback.value(player.progressState())
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = player
            }
        },
        update = { it.player = player }
    )
}

private fun Player.progressState(): VideoProgressState {
    return VideoProgressState(
        positionMs = currentPosition.coerceAtLeast(0L),
        durationMs = seekableDurationMs(),
        bufferedPositionMs = bufferedPosition.coerceAtLeast(0L)
    )
}

private fun Player.seekableDurationMs(): Long {
    val value = duration
    return if (value == C.TIME_UNSET || value <= 0L) 0L else value
}
