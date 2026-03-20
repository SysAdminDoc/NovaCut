package com.novacut.editor.ui.export

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.novacut.editor.model.*

private val Surface0 = Color(0xFF313244)
private val Surface1 = Color(0xFF45475A)
private val TextColor = Color(0xFFCDD6F4)
private val Subtext = Color(0xFFA6ADC8)
private val Mauve = Color(0xFFCBA6F7)
private val Red = Color(0xFFF38BA8)
private val Green = Color(0xFFA6E3A1)
private val Yellow = Color(0xFFF9E2AF)
private val Peach = Color(0xFFFAB387)
private val Crust = Color(0xFF11111B)

@Composable
fun BatchExportPanel(
    queue: List<BatchExportItem>,
    onAddItem: (ExportConfig, String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onStartBatch: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPresetPicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Crust, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Batch Export", color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row {
                IconButton(onClick = { showPresetPicker = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, "Add", tint = Green, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = Subtext, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Platform preset picker
        if (showPresetPicker) {
            Spacer(Modifier.height(8.dp))
            Text("Add Platform Preset", color = Subtext, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PlatformPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            val config = ExportConfig(
                                resolution = preset.resolution,
                                aspectRatio = preset.aspectRatio,
                                frameRate = preset.frameRate,
                                codec = preset.codec,
                                platformPreset = preset
                            )
                            onAddItem(config, preset.displayName)
                            showPresetPicker = false
                        },
                        label = { Text(preset.displayName, fontSize = 10.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            // Custom options
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = false,
                    onClick = {
                        onAddItem(ExportConfig(exportAudioOnly = true), "Audio Only")
                        showPresetPicker = false
                    },
                    label = { Text("Audio Only", fontSize = 10.sp) },
                    modifier = Modifier.height(28.dp)
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        onAddItem(ExportConfig(exportStemsOnly = true), "Audio Stems")
                        showPresetPicker = false
                    },
                    label = { Text("Audio Stems", fontSize = 10.sp) },
                    modifier = Modifier.height(28.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Queue
        if (queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No exports queued. Tap + to add.", color = Subtext, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(queue, key = { it.id }) { item ->
                    BatchExportItemRow(
                        item = item,
                        onRemove = { onRemoveItem(item.id) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onStartBatch,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Mauve),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.RocketLaunch, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export All (${queue.size})")
            }
        }
    }
}

@Composable
private fun BatchExportItemRow(
    item: BatchExportItem,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Surface0)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.outputName, color = TextColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                buildString {
                    append(item.config.resolution.label)
                    append(" | ")
                    append(item.config.codec.label)
                    if (item.config.exportAudioOnly) append(" | Audio Only")
                    if (item.config.exportStemsOnly) append(" | Stems")
                },
                color = Subtext,
                fontSize = 10.sp
            )
        }

        when (item.status) {
            BatchExportStatus.QUEUED -> {
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Remove", tint = Subtext, modifier = Modifier.size(16.dp))
                }
            }
            BatchExportStatus.IN_PROGRESS -> {
                CircularProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.size(24.dp),
                    color = Mauve,
                    strokeWidth = 2.dp
                )
            }
            BatchExportStatus.COMPLETED -> {
                Icon(Icons.Default.CheckCircle, "Done", tint = Green, modifier = Modifier.size(24.dp))
            }
            BatchExportStatus.FAILED -> {
                Icon(Icons.Default.Error, "Failed", tint = Red, modifier = Modifier.size(24.dp))
            }
            BatchExportStatus.CANCELLED -> {
                Icon(Icons.Default.Cancel, "Cancelled", tint = Yellow, modifier = Modifier.size(24.dp))
            }
        }
    }
}
