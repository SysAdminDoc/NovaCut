package com.novacut.editor.ui.editor

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.model.*

private val Surface0 = Color(0xFF313244)
private val Surface1 = Color(0xFF45475A)
private val Surface2 = Color(0xFF585B70)
private val TextColor = Color(0xFFCDD6F4)
private val Subtext = Color(0xFFA6ADC8)
private val Mauve = Color(0xFFCBA6F7)
private val Red = Color(0xFFF38BA8)
private val Green = Color(0xFFA6E3A1)
private val Blue = Color(0xFF89B4FA)
private val Yellow = Color(0xFFF9E2AF)
private val Peach = Color(0xFFFAB387)
private val Crust = Color(0xFF11111B)

@Composable
fun AudioMixerPanel(
    tracks: List<Track>,
    onTrackVolumeChanged: (String, Float) -> Unit,
    onTrackPanChanged: (String, Float) -> Unit,
    onTrackMuteToggled: (String) -> Unit,
    onTrackSoloToggled: (String) -> Unit,
    onTrackAudioEffectAdded: (String, AudioEffectType) -> Unit,
    onTrackAudioEffectRemoved: (String, String) -> Unit,
    onTrackAudioEffectParamChanged: (String, String, String, Float) -> Unit,
    vuLevels: Map<String, Pair<Float, Float>>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedEffectTrack by remember { mutableStateOf<String?>(null) }
    var selectedEffectId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Crust, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Audio Mixer", color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Subtext, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Channel strips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(tracks, key = { it.id }) { track ->
                ChannelStrip(
                    track = track,
                    vuLevel = vuLevels[track.id] ?: (0f to 0f),
                    onVolumeChanged = { onTrackVolumeChanged(track.id, it) },
                    onPanChanged = { onTrackPanChanged(track.id, it) },
                    onMuteToggled = { onTrackMuteToggled(track.id) },
                    onSoloToggled = { onTrackSoloToggled(track.id) },
                    onEffectsClicked = {
                        selectedEffectTrack = if (selectedEffectTrack == track.id) null else track.id
                        selectedEffectId = null
                    },
                    isEffectsExpanded = selectedEffectTrack == track.id
                )
            }

            // Master bus
            item {
                MasterBusStrip()
            }
        }

        // Audio effects section
        AnimatedVisibility(
            visible = selectedEffectTrack != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            selectedEffectTrack?.let { trackId ->
                val track = tracks.find { it.id == trackId } ?: return@let

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Divider(color = Surface1, thickness = 1.dp)
                    Spacer(Modifier.height(8.dp))

                    // Effect chain
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Effects: ${track.type.name} Track ${tracks.indexOf(track) + 1}",
                            color = TextColor, fontSize = 13.sp
                        )
                        // Add effect dropdown
                        var showAddMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showAddMenu = true }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Add, "Add Effect", tint = Green, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(
                                expanded = showAddMenu,
                                onDismissRequest = { showAddMenu = false }
                            ) {
                                AudioEffectType.entries.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type.displayName, fontSize = 13.sp) },
                                        onClick = {
                                            onTrackAudioEffectAdded(trackId, type)
                                            showAddMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Effect chain list
                    if (track.audioEffects.isEmpty()) {
                        Text("No effects", color = Subtext, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(track.audioEffects, key = { it.id }) { effect ->
                                AudioEffectChip(
                                    effect = effect,
                                    isSelected = selectedEffectId == effect.id,
                                    onClick = {
                                        selectedEffectId = if (selectedEffectId == effect.id) null else effect.id
                                    },
                                    onRemove = { onTrackAudioEffectRemoved(trackId, effect.id) }
                                )
                            }
                        }
                    }

                    // Effect parameter editor
                    selectedEffectId?.let { effectId ->
                        val effect = track.audioEffects.find { it.id == effectId } ?: return@let
                        Spacer(Modifier.height(8.dp))
                        AudioEffectParams(
                            effect = effect,
                            onParamChanged = { param, value ->
                                onTrackAudioEffectParamChanged(trackId, effectId, param, value)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelStrip(
    track: Track,
    vuLevel: Pair<Float, Float>,
    onVolumeChanged: (Float) -> Unit,
    onPanChanged: (Float) -> Unit,
    onMuteToggled: () -> Unit,
    onSoloToggled: () -> Unit,
    onEffectsClicked: () -> Unit,
    isEffectsExpanded: Boolean
) {
    val trackLabel = when (track.type) {
        TrackType.VIDEO -> "V${track.index + 1}"
        TrackType.AUDIO -> "A${track.index + 1}"
        TrackType.OVERLAY -> "OV${track.index + 1}"
        TrackType.TEXT -> "T${track.index + 1}"
        TrackType.ADJUSTMENT -> "ADJ"
    }

    Column(
        modifier = Modifier
            .width(56.dp)
            .fillMaxHeight()
            .background(Surface0, RoundedCornerShape(8.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Track label
        Text(trackLabel, color = TextColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)

        // VU Meter
        VUMeter(
            left = vuLevel.first,
            right = vuLevel.second,
            modifier = Modifier
                .width(20.dp)
                .weight(1f)
                .padding(vertical = 4.dp)
        )

        // Volume value
        Text(
            "${(track.volume * 100).toInt()}%",
            color = Subtext,
            fontSize = 9.sp
        )

        // Pan knob indicator
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Surface1)
                .clickable { onPanChanged(0f) }, // double-click to reset
            contentAlignment = Alignment.Center
        ) {
            Text(
                when {
                    track.pan < -0.1f -> "L"
                    track.pan > 0.1f -> "R"
                    else -> "C"
                },
                color = Subtext,
                fontSize = 9.sp
            )
        }

        Spacer(Modifier.height(2.dp))

        // Mute button
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (track.isMuted) Red.copy(alpha = 0.3f) else Surface1)
                .clickable { onMuteToggled() },
            contentAlignment = Alignment.Center
        ) {
            Text("M", color = if (track.isMuted) Red else Subtext, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        // Solo button
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (track.isSolo) Yellow.copy(alpha = 0.3f) else Surface1)
                .clickable { onSoloToggled() },
            contentAlignment = Alignment.Center
        ) {
            Text("S", color = if (track.isSolo) Yellow else Subtext, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        // FX button
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isEffectsExpanded) Mauve.copy(alpha = 0.3f) else Surface1)
                .clickable { onEffectsClicked() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "FX",
                color = if (track.audioEffects.isNotEmpty()) Mauve else Subtext,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun VUMeter(
    left: Float,
    right: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barWidth = w * 0.35f
        val gap = w * 0.1f

        // Background
        drawRoundRect(Surface1, cornerRadius = CornerRadius(2f, 2f))

        // Left bar
        val leftHeight = h * left.coerceIn(0f, 1f)
        val leftColor = when {
            left > 0.9f -> Red
            left > 0.7f -> Yellow
            else -> Green
        }
        drawRect(
            leftColor,
            topLeft = Offset(gap, h - leftHeight),
            size = Size(barWidth, leftHeight)
        )

        // Right bar
        val rightHeight = h * right.coerceIn(0f, 1f)
        val rightColor = when {
            right > 0.9f -> Red
            right > 0.7f -> Yellow
            else -> Green
        }
        drawRect(
            rightColor,
            topLeft = Offset(gap + barWidth + gap, h - rightHeight),
            size = Size(barWidth, rightHeight)
        )

        // Tick marks
        for (i in 0..4) {
            val y = h * i / 4f
            drawLine(Surface2, Offset(0f, y), Offset(w, y), 0.5f)
        }
    }
}

@Composable
private fun MasterBusStrip() {
    Column(
        modifier = Modifier
            .width(56.dp)
            .fillMaxHeight()
            .background(Surface0.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .border(1.dp, Mauve.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("MST", color = Mauve, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Icon(Icons.Default.GraphicEq, "Master", tint = Mauve, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun AudioEffectChip(
    effect: AudioEffect,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) Mauve.copy(alpha = 0.2f) else Surface1)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(if (effect.enabled) Green else Red, RoundedCornerShape(3.dp))
        )
        Text(
            effect.type.displayName,
            color = if (isSelected) Mauve else TextColor,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            Icons.Default.Close,
            "Remove",
            tint = Subtext,
            modifier = Modifier
                .size(14.dp)
                .clickable(onClick = onRemove)
        )
    }
}

@Composable
private fun AudioEffectParams(
    effect: AudioEffect,
    onParamChanged: (String, Float) -> Unit
) {
    val defaults = AudioEffectType.defaultParams(effect.type)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface0, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text(effect.type.displayName, color = TextColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))

        effect.params.forEach { (param, value) ->
            val range = getParamRange(effect.type, param)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatParamName(param),
                    color = Subtext,
                    fontSize = 10.sp,
                    modifier = Modifier.width(60.dp)
                )
                Slider(
                    value = value,
                    onValueChange = { onParamChanged(param, it) },
                    valueRange = range.first..range.second,
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Mauve,
                        activeTrackColor = Mauve.copy(alpha = 0.6f),
                        inactiveTrackColor = Surface1
                    )
                )
                Text(
                    formatParamValue(param, value),
                    color = Subtext,
                    fontSize = 9.sp,
                    modifier = Modifier.width(40.dp)
                )
            }
        }
    }
}

private fun getParamRange(type: AudioEffectType, param: String): Pair<Float, Float> {
    return when {
        param.endsWith("_freq") || param == "frequency" -> 20f to 20000f
        param.endsWith("_gain") || param == "gain" || param == "makeupGain" -> -24f to 24f
        param.endsWith("_q") || param == "resonance" -> 0.1f to 10f
        param == "threshold" -> -60f to 0f
        param == "ratio" -> 1f to 20f
        param == "attack" -> 0.1f to 200f
        param == "release" -> 10f to 2000f
        param == "knee" -> 0f to 30f
        param == "ceiling" -> -20f to 0f
        param == "roomSize" || param == "damping" || param == "wetDry" || param == "depth" -> 0f to 1f
        param == "feedback" -> 0f to 0.95f
        param == "delayMs" || param == "preDelay" -> 1f to 2000f
        param == "decay" -> 0.1f to 10f
        param == "rate" -> 0.1f to 20f
        param == "semitones" -> -12f to 12f
        param == "cents" -> -100f to 100f
        param == "targetLufs" -> -30f to -5f
        param == "hold" -> 1f to 500f
        param == "bandwidth" -> 0.1f to 5f
        param == "mode" -> 0f to 2f
        param == "pingPong" -> 0f to 1f
        else -> 0f to 1f
    }
}

private fun formatParamName(param: String): String {
    return param.replace("_", " ")
        .split(" ")
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}

private fun formatParamValue(param: String, value: Float): String {
    return when {
        param.endsWith("_freq") || param == "frequency" -> "${value.toInt()}Hz"
        param.endsWith("_gain") || param == "gain" || param == "makeupGain" -> "%.1fdB".format(value)
        param == "threshold" || param == "ceiling" || param == "targetLufs" -> "%.1fdB".format(value)
        param == "ratio" -> "%.1f:1".format(value)
        param == "attack" || param == "release" || param == "delayMs" || param == "hold" || param == "preDelay" -> "${value.toInt()}ms"
        param == "decay" -> "%.1fs".format(value)
        param == "semitones" -> "${value.toInt()}st"
        param == "cents" -> "${value.toInt()}c"
        param == "rate" -> "%.1fHz".format(value)
        else -> "%.2f".format(value)
    }
}
