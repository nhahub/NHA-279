package com.trailerly.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * A Jetpack Compose wrapper for the android-youtube-player library.
 * Handles lifecycle management automatically and provides ToS-compliant YouTube playback.
 *
 * The library automatically handles YouTube's Terms of Service compliance.
 * The player automatically pauses when the app goes to background and resumes when returning.
 *
 * @param videoId The YouTube video ID to play
 * @param modifier Modifier for styling the player view
 * @param onReady Optional callback when the player is ready
 */
@Composable
fun YouTubePlayerComposable(
    videoId: String,
    modifier: Modifier = Modifier,
    onReady: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var youTubePlayerView by remember { mutableStateOf<YouTubePlayerView?>(null) }
    
    DisposableEffect(videoId) {
        val newYoutubePlayerView = YouTubePlayerView(context).apply {
            lifecycleOwner.lifecycle.addObserver(this)

            addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                override fun onReady(youTubePlayer: YouTubePlayer) {
                    youTubePlayer.loadVideo(videoId, 0f)
                    onReady?.invoke()
                }
            })
        }
        
        youTubePlayerView = newYoutubePlayerView

        onDispose {
            newYoutubePlayerView.release()
            youTubePlayerView = null
        }
    }

    youTubePlayerView?.let { playerView ->
        AndroidView(
            modifier = modifier.clip(RoundedCornerShape(12.dp)),
            factory = { playerView }
        )
    }
}