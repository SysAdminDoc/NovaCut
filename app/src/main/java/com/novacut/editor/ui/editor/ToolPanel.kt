package com.novacut.editor.ui.editor

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.model.*
import com.novacut.editor.ui.theme.Mocha

data class ToolItem(
    val tool: EditorTool,
    val icon: ImageVector,
    val label: String,
    val requiresSelection: Boolean = false
)

val mainTools = listOf(
    ToolItem(EditorTool.TRIM, Icons.Default.ContentCut, "Trim", true),
    ToolItem(EditorTool.SPLIT, Icons.Default.CallSplit, "Split", true),
    ToolItem(EditorTool.SPEED, Icons.Default.Speed, "Speed", true),
    ToolItem(EditorTool.EFFECTS, Icons.Default.AutoFixHigh, "Effects", true),
    ToolItem(EditorTool.TEXT, Icons.Default.Title, "Text"),
    ToolItem(EditorTool.AUDIO, Icons.Default.MusicNote, "Audio"),
    ToolItem(EditorTool.TRANSITION, Icons.Default.SwapHoriz, "Transition", true),
    ToolItem(EditorTool.TRANSFORM, Icons.Default.Transform, "Transform", true),
    ToolItem(EditorTool.CROP, Icons.Default.Crop, "Crop", true),
    ToolItem(EditorTool.EXPORT, Icons.Default.FileUpload, "Export")
)

@Composable
fun ToolPanel(
    currentTool: EditorTool,
    selectedClipId: String?,
    onToolSelected: (EditorTool) -> Unit,
    onAddMedia: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDelete: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Crust)
    ) {
        // Top action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Undo/Redo
            Row {
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) Mocha.Text else Mocha.Surface2,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) Mocha.Text else Mocha.Surface2,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Add media button
            FilledTonalButton(
                onClick = onAddMedia,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Mocha.Mauve.copy(alpha = 0.2f),
                    contentColor = Mocha.Mauve
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add", fontSize = 13.sp)
            }

            // Delete selected
            IconButton(
                onClick = onDelete,
                enabled = selectedClipId != null,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = if (selectedClipId != null) Mocha.Red else Mocha.Surface2,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Tool strip
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            items(mainTools) { tool ->
                val isActive = currentTool == tool.tool
                val isEnabled = !tool.requiresSelection || selectedClipId != null

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = isEnabled) { onToolSelected(tool.tool) }
                        .background(
                            if (isActive) Mocha.Mauve.copy(alpha = 0.2f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        tool.icon,
                        contentDescription = tool.label,
                        tint = when {
                            isActive -> Mocha.Mauve
                            !isEnabled -> Mocha.Surface2
                            else -> Mocha.Subtext0
                        },
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tool.label,
                        fontSize = 10.sp,
                        color = when {
                            isActive -> Mocha.Mauve
                            !isEnabled -> Mocha.Surface2
                            else -> Mocha.Subtext0
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun EffectsPanel(
    selectedClip: Clip?,
    onAddEffect: (EffectType) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(EffectCategory.COLOR) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .background(Mocha.Mantle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Effects", color = Mocha.Text, fontSize = 16.sp)
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Category tabs
        ScrollableTabRow(
            selectedTabIndex = EffectCategory.entries.indexOf(selectedCategory),
            containerColor = Color.Transparent,
            contentColor = Mocha.Mauve,
            edgePadding = 0.dp,
            divider = {}
        ) {
            EffectCategory.entries.forEach { category ->
                Tab(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    text = {
                        Text(
                            category.displayName,
                            fontSize = 12.sp,
                            color = if (selectedCategory == category) Mocha.Mauve else Mocha.Subtext0
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Effects grid
        val effects = EffectType.entries.filter { it.category == selectedCategory }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(effects) { effectType ->
                val isApplied = selectedClip?.effects?.any { it.type == effectType } == true

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onAddEffect(effectType) }
                        .background(
                            if (isApplied) Mocha.Mauve.copy(alpha = 0.2f)
                            else Mocha.Surface0
                        )
                        .padding(12.dp)
                        .width(70.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = when (selectedCategory) {
                            EffectCategory.COLOR -> Icons.Default.Palette
                            EffectCategory.FILTER -> Icons.Default.FilterVintage
                            EffectCategory.BLUR -> Icons.Default.BlurOn
                            EffectCategory.DISTORTION -> Icons.Default.Waves
                            EffectCategory.KEYING -> Icons.Default.Wallpaper
                            EffectCategory.SPEED -> Icons.Default.Speed
                        },
                        contentDescription = effectType.displayName,
                        tint = if (isApplied) Mocha.Mauve else Mocha.Subtext0,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        effectType.displayName,
                        fontSize = 10.sp,
                        color = if (isApplied) Mocha.Mauve else Mocha.Text,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isApplied) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Applied",
                            tint = Mocha.Green,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EffectAdjustmentPanel(
    effect: Effect,
    onUpdateParams: (Map<String, Float>) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Mantle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(effect.type.displayName, color = Mocha.Text, fontSize = 16.sp)
            Row {
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, "Remove", tint = Mocha.Red, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Parameter sliders based on effect type
        when (effect.type) {
            EffectType.BRIGHTNESS -> {
                EffectSlider("Brightness", effect.params["value"] ?: 0f, -1f, 1f) {
                    onUpdateParams(mapOf("value" to it))
                }
            }
            EffectType.CONTRAST -> {
                EffectSlider("Contrast", effect.params["value"] ?: 1f, 0f, 3f) {
                    onUpdateParams(mapOf("value" to it))
                }
            }
            EffectType.SATURATION -> {
                EffectSlider("Saturation", effect.params["value"] ?: 1f, 0f, 3f) {
                    onUpdateParams(mapOf("value" to it))
                }
            }
            EffectType.TEMPERATURE -> {
                EffectSlider("Temperature", effect.params["value"] ?: 0f, -5f, 5f) {
                    onUpdateParams(mapOf("value" to it))
                }
            }
            EffectType.EXPOSURE -> {
                EffectSlider("Exposure", effect.params["value"] ?: 0f, -2f, 2f) {
                    onUpdateParams(mapOf("value" to it))
                }
            }
            EffectType.VIGNETTE -> {
                EffectSlider("Intensity", effect.params["intensity"] ?: 0.5f, 0f, 1f) {
                    onUpdateParams(mapOf("intensity" to it))
                }
                EffectSlider("Radius", effect.params["radius"] ?: 0.7f, 0.1f, 1f) {
                    onUpdateParams(mapOf("radius" to it))
                }
            }
            EffectType.GAUSSIAN_BLUR -> {
                EffectSlider("Radius", effect.params["radius"] ?: 5f, 0f, 25f) {
                    onUpdateParams(mapOf("radius" to it))
                }
            }
            EffectType.CHROMA_KEY -> {
                EffectSlider("Similarity", effect.params["similarity"] ?: 0.4f, 0f, 1f) {
                    onUpdateParams(mapOf("similarity" to it))
                }
                EffectSlider("Smoothness", effect.params["smoothness"] ?: 0.1f, 0f, 0.5f) {
                    onUpdateParams(mapOf("smoothness" to it))
                }
                EffectSlider("Spill", effect.params["spill"] ?: 0.1f, 0f, 1f) {
                    onUpdateParams(mapOf("spill" to it))
                }
            }
            EffectType.FILM_GRAIN -> {
                EffectSlider("Intensity", effect.params["intensity"] ?: 0.1f, 0f, 0.5f) {
                    onUpdateParams(mapOf("intensity" to it))
                }
            }
            EffectType.SHARPEN -> {
                EffectSlider("Strength", effect.params["strength"] ?: 0.5f, 0f, 2f) {
                    onUpdateParams(mapOf("strength" to it))
                }
            }
            EffectType.GLITCH -> {
                EffectSlider("Intensity", effect.params["intensity"] ?: 0.5f, 0f, 1f) {
                    onUpdateParams(mapOf("intensity" to it))
                }
            }
            EffectType.PIXELATE -> {
                EffectSlider("Size", effect.params["size"] ?: 10f, 2f, 50f) {
                    onUpdateParams(mapOf("size" to it))
                }
            }
            EffectType.CHROMATIC_ABERRATION -> {
                EffectSlider("Intensity", effect.params["intensity"] ?: 0.5f, 0f, 2f) {
                    onUpdateParams(mapOf("intensity" to it))
                }
            }
            EffectType.CYBERPUNK, EffectType.NOIR, EffectType.VINTAGE, EffectType.COOL_TONE, EffectType.WARM_TONE -> {
                EffectSlider("Intensity", effect.params["intensity"] ?: 0.7f, 0f, 1f) {
                    onUpdateParams(mapOf("intensity" to it))
                }
            }
            EffectType.TILT_SHIFT -> {
                EffectSlider("Blur", effect.params["blur"] ?: 0.01f, 0f, 0.05f) {
                    onUpdateParams(mapOf("blur" to it))
                }
                EffectSlider("Focus Y", effect.params["focusY"] ?: 0.5f, 0f, 1f) {
                    onUpdateParams(mapOf("focusY" to it))
                }
                EffectSlider("Width", effect.params["width"] ?: 0.1f, 0.01f, 0.5f) {
                    onUpdateParams(mapOf("width" to it))
                }
            }
            EffectType.SPEED -> {
                EffectSlider("Speed", effect.params["value"] ?: 1f, 0.1f, 16f) {
                    onUpdateParams(mapOf("value" to it))
                }
            }
            else -> {
                Text("No adjustable parameters", color = Mocha.Subtext0, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun EffectSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Mocha.Subtext1, fontSize = 12.sp)
            Text("%.2f".format(value), color = Mocha.Subtext0, fontSize = 12.sp)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            colors = SliderDefaults.colors(
                thumbColor = Mocha.Mauve,
                activeTrackColor = Mocha.Mauve,
                inactiveTrackColor = Mocha.Surface1
            )
        )
    }
}

@Composable
fun SpeedPanel(
    currentSpeed: Float,
    isReversed: Boolean,
    onSpeedChanged: (Float) -> Unit,
    onReversedChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val presetSpeeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 4f, 8f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Mantle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Speed", color = Mocha.Text, fontSize = 16.sp)
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Speed presets
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(presetSpeeds) { speed ->
                val isActive = currentSpeed == speed
                FilterChip(
                    onClick = { onSpeedChanged(speed) },
                    label = { Text("${speed}x", fontSize = 12.sp) },
                    selected = isActive,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.Surface0,
                        labelColor = Mocha.Text,
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.3f),
                        selectedLabelColor = Mocha.Mauve
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Custom speed slider
        EffectSlider("Custom Speed", currentSpeed, 0.1f, 16f) { onSpeedChanged(it) }

        // Reverse toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Reverse", color = Mocha.Text, fontSize = 14.sp)
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = isReversed,
                onCheckedChange = onReversedChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Mocha.Mauve,
                    checkedTrackColor = Mocha.Mauve.copy(alpha = 0.3f)
                )
            )
        }
    }
}

@Composable
fun TransitionPicker(
    onTransitionSelected: (TransitionType) -> Unit,
    onRemoveTransition: () -> Unit,
    onClose: () -> Unit,
    currentTransition: Transition?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Mantle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Transitions", color = Mocha.Text, fontSize = 16.sp)
            Row {
                if (currentTransition != null) {
                    TextButton(onClick = onRemoveTransition) {
                        Text("Remove", color = Mocha.Red, fontSize = 12.sp)
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(TransitionType.entries.toList()) { type ->
                val isActive = currentTransition?.type == type
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onTransitionSelected(type) }
                        .background(
                            if (isActive) Mocha.Mauve.copy(alpha = 0.2f) else Mocha.Surface0
                        )
                        .padding(12.dp)
                        .width(70.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = type.displayName,
                        tint = if (isActive) Mocha.Mauve else Mocha.Subtext0,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        type.displayName,
                        fontSize = 10.sp,
                        color = if (isActive) Mocha.Mauve else Mocha.Text,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }
        }
    }
}
