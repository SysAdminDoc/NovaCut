package com.novacut.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.novacut.editor.ui.theme.Mocha

@Composable
fun MiniPlayerBar(
    isPlaying: Boolean,
    playheadMs: Long,
    totalDurationMs: Long,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (totalDurationMs > 0L) {
        (playheadMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Mocha.Panel,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.CardStroke.copy(alpha = 0.9f))
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Mocha.PanelHighest.copy(alpha = 0.94f),
                            Mocha.Panel.copy(alpha = 0.98f)
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = Mocha.Rosewater.copy(alpha = 0.14f),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Mocha.Rosewater.copy(alpha = 0.22f))
            ) {
                IconButton(
                    onClick = onTogglePlayback,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Mocha.Rosewater,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = formatTimecode(playheadMs),
                color = Mocha.Text,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(46.dp)
            )

            Slider(
                value = progress,
                onValueChange = { fraction ->
                    if (totalDurationMs > 0L) {
                        onSeek((fraction * totalDurationMs).toLong())
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Mocha.Sky,
                    activeTrackColor = Mocha.Sky,
                    inactiveTrackColor = Mocha.Surface1,
                    activeTickColor = Mocha.Sky,
                    inactiveTickColor = Mocha.Surface1
                )
            )

            Text(
                text = formatTimecode(totalDurationMs),
                color = Mocha.Subtext0,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(46.dp)
            )
        }
    }
}
