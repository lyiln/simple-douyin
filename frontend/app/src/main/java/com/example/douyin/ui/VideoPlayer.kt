package com.example.douyin.ui

import android.net.Uri
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.douyin.network.ApiClient

@Composable
fun LocalVideoPlayer(
    @RawRes videoRes: Int? = null,
    videoUrl: String? = null,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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

    LaunchedEffect(isActive) {
        if (isActive) {
            player.playWhenReady = true
            player.play()
        } else {
            player.playWhenReady = false
            player.pause()
        }
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
