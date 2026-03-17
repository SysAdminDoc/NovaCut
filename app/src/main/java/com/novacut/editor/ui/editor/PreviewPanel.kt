package com.novacut.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.novacut.editor.engine.VideoEngine
import com.novacut.editor.ui.theme.Mocha

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PreviewPanel(
    engine: VideoEngine,
    playheadMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    isLooping: Boolean = false,
    onTogglePlayback: () -> Unit,
    onToggleLoop: () -> Unit = {},
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Crust),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Video Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Mocha.Mantle),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    }
                },
                update = { playerView ->
                    playerView.player = engine.getPlayer()
                },
                modifier = Modifier.fillMaxSize()
            )

            // No-op: PlayerView lifecycle is managed by AndroidView factory/update

            // Overlay play button when paused and no content
            if (!isPlaying && totalDurationMs == 0L) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = Mocha.Overlay0,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Add media to get started",
                        color = Mocha.Overlay0,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Playback Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Timestamp
            Text(
                text = formatTimestamp(playheadMs),
                color = Mocha.Subtext0,
                fontSize = 12.sp,
                modifier = Modifier.width(70.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Skip backward
            IconButton(
                onClick = { onSeek((playheadMs - 5000).coerceAtLeast(0)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Replay5,
                    contentDescription = "Back 5s",
                    tint = Mocha.Text,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Previous frame
            IconButton(
                onClick = { onSeek((playheadMs - 33).coerceAtLeast(0)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "Previous frame",
                    tint = Mocha.Text,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Play/Pause
            FilledIconButton(
                onClick = onTogglePlayback,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Mocha.Mauve,
                    contentColor = Mocha.Crust
                )
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(28.dp)
                )
            }

            // Next frame
            IconButton(
                onClick = { onSeek((playheadMs + 33).coerceAtMost(totalDurationMs)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Next frame",
                    tint = Mocha.Text,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Skip forward
            IconButton(
                onClick = { onSeek((playheadMs + 5000).coerceAtMost(totalDurationMs)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Forward5,
                    contentDescription = "Forward 5s",
                    tint = Mocha.Text,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Loop toggle
            IconButton(
                onClick = onToggleLoop,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Loop,
                    contentDescription = if (isLooping) "Disable loop" else "Enable loop",
                    tint = if (isLooping) Mocha.Mauve else Mocha.Overlay0,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Duration
            Text(
                text = formatTimestamp(totalDurationMs),
                color = Mocha.Subtext0,
                fontSize = 12.sp,
                modifier = Modifier.width(70.dp)
            )
        }
    }
}

fun formatTimestamp(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val millis = (ms % 1000) / 10 // Show centiseconds (00-99)

    return if (hours > 0) {
        "%d:%02d:%02d.%02d".format(hours, minutes, seconds, millis)
    } else {
        "%02d:%02d.%02d".format(minutes, seconds, millis)
    }
}
