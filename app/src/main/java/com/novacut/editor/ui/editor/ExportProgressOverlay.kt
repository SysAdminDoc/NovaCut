package com.novacut.editor.ui.editor

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.engine.ExportState

private val Surface0 = Color(0xFF313244)
private val TextColor = Color(0xFFCDD6F4)
private val Subtext = Color(0xFFA6ADC8)
private val Mauve = Color(0xFFCBA6F7)
private val Red = Color(0xFFF38BA8)
private val Green = Color(0xFFA6E3A1)
private val Yellow = Color(0xFFF9E2AF)

/**
 * Floating export progress overlay that shows during background export.
 * Appears as a compact notification-style card in the top corner.
 */
@Composable
fun ExportProgressOverlay(
    exportState: ExportState,
    exportProgress: Float,
    exportStartTime: Long,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isExporting = exportState == ExportState.EXPORTING

    AnimatedVisibility(
        visible = isExporting,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Surface0)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Progress circle
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { exportProgress },
                    modifier = Modifier.size(36.dp),
                    color = Mauve,
                    strokeWidth = 3.dp,
                    trackColor = Mauve.copy(alpha = 0.1f)
                )
                Text(
                    "${(exportProgress * 100).toInt()}%",
                    color = Mauve,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text("Exporting...", color = TextColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)

                // Estimated time remaining
                val currentTime by rememberUpdatedState(System.currentTimeMillis())
                val elapsed = currentTime - exportStartTime
                val estimatedTotal = if (exportProgress > 0.05f) {
                    (elapsed / exportProgress).toLong()
                } else 0L
                val remaining = (estimatedTotal - elapsed).coerceAtLeast(0L)

                if (remaining > 0 && exportProgress > 0.05f) {
                    Text(
                        "~${formatEta(remaining)} remaining",
                        color = Subtext,
                        fontSize = 10.sp
                    )
                }
            }

            // Cancel button
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Close, "Cancel", tint = Red, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun formatEta(ms: Long): String {
    val seconds = ms / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}
