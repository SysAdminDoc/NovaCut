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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.ui.theme.ClearCutChromeIconButton
import com.novacut.editor.ui.theme.LocalClearCutColors
import com.novacut.editor.ui.theme.Mocha
import com.novacut.editor.ui.theme.Radius
import com.novacut.editor.ui.theme.TouchTarget

@Composable
fun MiniPlayerBar(
    isPlaying: Boolean,
    playheadMs: Long,
    totalDurationMs: Long,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalClearCutColors.current
    val progress = if (totalDurationMs > 0L) {
        (playheadMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.panel,
        shape = RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (colors.highContrast) colors.cardStrokeStrong else colors.cardStroke.copy(alpha = 0.9f)
        )
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            colors.panelHighest.copy(alpha = 0.94f),
                            colors.panel.copy(alpha = 0.98f)
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ClearCutChromeIconButton(
                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(
                    if (isPlaying) R.string.preview_pause else R.string.preview_play
                ),
                onClick = onTogglePlayback,
                tint = Mocha.Rosewater,
                containerColor = Mocha.Rosewater.copy(alpha = 0.14f),
                borderColor = Mocha.Rosewater.copy(alpha = 0.22f),
                shape = RoundedCornerShape(Radius.md)
            )

            Text(
                text = formatTimecode(playheadMs),
                color = colors.text,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(46.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
                    .height(TouchTarget.minimum),
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
                color = colors.subtext,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(46.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
