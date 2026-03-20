package com.novacut.editor.ui.editor

import androidx.compose.foundation.*
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

private val Surface0 = Color(0xFF313244)
private val TextColor = Color(0xFFCDD6F4)
private val Subtext = Color(0xFFA6ADC8)
private val Mauve = Color(0xFFCBA6F7)
private val Green = Color(0xFFA6E3A1)
private val Yellow = Color(0xFFF9E2AF)
private val Crust = Color(0xFF11111B)

enum class NormalizationMode(val label: String, val targetLufs: Float) {
    YOUTUBE("YouTube (-14 LUFS)", -14f),
    PODCAST("Podcast (-16 LUFS)", -16f),
    BROADCAST("Broadcast (-23 LUFS)", -23f),
    STREAMING("Streaming (-14 LUFS)", -14f),
    LOUD("Loud (-9 LUFS)", -9f),
    CUSTOM("Custom", -14f)
}

@Composable
fun AudioNormPanel(
    currentVolume: Float,
    onNormalize: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMode by remember { mutableStateOf(NormalizationMode.YOUTUBE) }
    var customLufs by remember { mutableFloatStateOf(-14f) }

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
            Text("Audio Normalization", color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Subtext, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "Adjust audio levels to a target loudness standard",
            color = Subtext,
            fontSize = 11.sp
        )

        Spacer(Modifier.height(12.dp))

        // Mode selector
        NormalizationMode.entries.forEach { mode ->
            val selected = selectedMode == mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) Mauve.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { selectedMode = mode }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { selectedMode = mode },
                        colors = RadioButtonDefaults.colors(selectedColor = Mauve)
                    )
                    Column {
                        Text(
                            mode.label,
                            color = if (selected) TextColor else Subtext,
                            fontSize = 13.sp
                        )
                    }
                }
                if (mode != NormalizationMode.CUSTOM) {
                    Text(
                        "${mode.targetLufs} LUFS",
                        color = if (selected) Mauve else Subtext,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Custom LUFS slider
        if (selectedMode == NormalizationMode.CUSTOM) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Target", color = Subtext, fontSize = 11.sp, modifier = Modifier.width(50.dp))
                Slider(
                    value = customLufs,
                    onValueChange = { customLufs = it },
                    valueRange = -30f..-5f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Mauve,
                        activeTrackColor = Mauve.copy(alpha = 0.6f),
                        inactiveTrackColor = Surface0
                    )
                )
                Text(
                    "%.0f LUFS".format(customLufs),
                    color = Mauve,
                    fontSize = 11.sp,
                    modifier = Modifier.width(60.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Current level info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface0, RoundedCornerShape(8.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Current Volume", color = Subtext, fontSize = 12.sp)
            Text("%.0f%%".format(currentVolume * 100f), color = TextColor, fontSize = 12.sp)
        }

        Spacer(Modifier.height(8.dp))

        // Apply button
        Button(
            onClick = {
                val targetLufs = if (selectedMode == NormalizationMode.CUSTOM) customLufs else selectedMode.targetLufs
                onNormalize(targetLufs)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Mauve),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Equalizer, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Normalize Audio")
        }
    }
}
