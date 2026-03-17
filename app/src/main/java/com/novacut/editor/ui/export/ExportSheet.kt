package com.novacut.editor.ui.export

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.engine.ExportState
import com.novacut.editor.model.*
import com.novacut.editor.ui.theme.Mocha

@Composable
fun ExportSheet(
    config: ExportConfig,
    exportState: ExportState,
    exportProgress: Float,
    aspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
    errorMessage: String? = null,
    onConfigChanged: (ExportConfig) -> Unit,
    onStartExport: () -> Unit,
    onShare: () -> Unit = {},
    onSaveToGallery: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Mantle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Export", color = Mocha.Text, fontSize = 18.sp)
            if (exportState != ExportState.EXPORTING) {
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (exportState == ExportState.EXPORTING) {
            // Export progress
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Exporting...",
                    color = Mocha.Text,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { exportProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = Mocha.Mauve,
                    trackColor = Mocha.Surface0,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${(exportProgress * 100).toInt()}%",
                    color = Mocha.Subtext0,
                    fontSize = 14.sp
                )
            }
            return
        }

        if (exportState == ExportState.COMPLETE) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Complete",
                    tint = Mocha.Green,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Export Complete!", color = Mocha.Green, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onShare,
                        colors = ButtonDefaults.buttonColors(containerColor = Mocha.Mauve)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", color = Mocha.Crust)
                    }
                    Button(
                        onClick = onSaveToGallery,
                        colors = ButtonDefaults.buttonColors(containerColor = Mocha.Green)
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save to Gallery", color = Mocha.Crust)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onClose) {
                    Text("Done", color = Mocha.Subtext0)
                }
            }
            return
        }

        if (exportState == ExportState.ERROR) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = "Error",
                    tint = Mocha.Red,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Export Failed", color = Mocha.Red, fontSize = 16.sp)
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        errorMessage,
                        color = Mocha.Subtext0,
                        fontSize = 12.sp,
                        maxLines = 3
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onStartExport,
                        colors = ButtonDefaults.buttonColors(containerColor = Mocha.Mauve)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry", color = Mocha.Crust)
                    }
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = Mocha.Surface1)
                    ) {
                        Text("Close", color = Mocha.Text)
                    }
                }
            }
            return
        }

        // Resolution
        Text("Resolution", color = Mocha.Subtext1, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Resolution.entries.forEach { res ->
                FilterChip(
                    onClick = { onConfigChanged(config.copy(resolution = res)) },
                    label = { Text(res.label, fontSize = 12.sp) },
                    selected = config.resolution == res,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.Surface0,
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.3f),
                        selectedLabelColor = Mocha.Mauve
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Frame rate
        Text("Frame Rate", color = Mocha.Subtext1, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(24, 30, 60).forEach { fps ->
                FilterChip(
                    onClick = { onConfigChanged(config.copy(frameRate = fps)) },
                    label = { Text("${fps}fps", fontSize = 12.sp) },
                    selected = config.frameRate == fps,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.Surface0,
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.3f),
                        selectedLabelColor = Mocha.Mauve
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Codec
        Text("Codec", color = Mocha.Subtext1, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VideoCodec.entries.forEach { codec ->
                FilterChip(
                    onClick = { onConfigChanged(config.copy(codec = codec)) },
                    label = { Text(codec.label, fontSize = 12.sp) },
                    selected = config.codec == codec,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.Surface0,
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.3f),
                        selectedLabelColor = Mocha.Mauve
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quality
        Text("Quality", color = Mocha.Subtext1, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportQuality.entries.forEach { quality ->
                FilterChip(
                    onClick = { onConfigChanged(config.copy(quality = quality)) },
                    label = { Text(quality.label, fontSize = 12.sp) },
                    selected = config.quality == quality,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.Surface0,
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.3f),
                        selectedLabelColor = Mocha.Mauve
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Estimated file info
        val (w, h) = config.resolution.forAspect(aspectRatio)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Mocha.Surface0)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Output Details", color = Mocha.Subtext1, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("${w}x${h} @ ${config.frameRate}fps", color = Mocha.Text, fontSize = 13.sp)
                Text("${config.codec.label} / ${config.quality.label}", color = Mocha.Text, fontSize = 13.sp)
                Text(
                    "Bitrate: ${config.videoBitrate / 1_000_000}Mbps",
                    color = Mocha.Subtext0,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Export button
        Button(
            onClick = onStartExport,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Mocha.Mauve,
                contentColor = Mocha.Crust
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export Video", fontSize = 15.sp)
        }
    }
}
