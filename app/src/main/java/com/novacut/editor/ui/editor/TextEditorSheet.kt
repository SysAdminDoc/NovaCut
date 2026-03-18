package com.novacut.editor.ui.editor

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.model.*
import com.novacut.editor.ui.theme.Mocha

@Composable
fun TextEditorSheet(
    existingOverlay: TextOverlay? = null,
    playheadMs: Long,
    onSave: (TextOverlay) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf(existingOverlay?.text ?: "Your Text") }
    var fontSize by remember { mutableFloatStateOf(existingOverlay?.fontSize ?: 48f) }
    var bold by remember { mutableStateOf(existingOverlay?.bold ?: false) }
    var italic by remember { mutableStateOf(existingOverlay?.italic ?: false) }
    var alignment by remember { mutableStateOf(existingOverlay?.alignment ?: TextAlignment.CENTER) }
    var selectedColor by remember { mutableLongStateOf(existingOverlay?.color ?: 0xFFFFFFFF) }
    var strokeWidth by remember { mutableFloatStateOf(existingOverlay?.strokeWidth ?: 0f) }
    var animIn by remember { mutableStateOf(existingOverlay?.animationIn ?: TextAnimation.FADE) }
    var animOut by remember { mutableStateOf(existingOverlay?.animationOut ?: TextAnimation.FADE) }
    var fontFamily by remember { mutableStateOf(existingOverlay?.fontFamily ?: "sans-serif") }
    var duration by remember { mutableFloatStateOf((existingOverlay?.let { it.endTimeMs - it.startTimeMs } ?: 3000L).toFloat()) }
    var positionX by remember { mutableFloatStateOf(existingOverlay?.positionX ?: 0.5f) }
    var positionY by remember { mutableFloatStateOf(existingOverlay?.positionY ?: 0.5f) }

    val fontFamilies = listOf(
        "sans-serif" to "Sans Serif",
        "serif" to "Serif",
        "monospace" to "Monospace",
        "cursive" to "Cursive",
        "sans-serif-condensed" to "Condensed",
        "sans-serif-medium" to "Medium"
    )

    val colorOptions = listOf(
        0xFFFFFFFF, 0xFF000000, 0xFFF38BA8, 0xFFFAB387,
        0xFFF9E2AF, 0xFFA6E3A1, 0xFF89B4FA, 0xFFCBA6F7,
        0xFFF5C2E7, 0xFF94E2D5, 0xFF89DCEB, 0xFFB4BEFE
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Mantle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Text Overlay", color = Mocha.Text, fontSize = 16.sp)
            Row {
                TextButton(
                    onClick = {
                        val overlay = TextOverlay(
                            id = existingOverlay?.id ?: java.util.UUID.randomUUID().toString(),
                            text = text,
                            fontSize = fontSize,
                            fontFamily = fontFamily,
                            color = selectedColor,
                            bold = bold,
                            italic = italic,
                            alignment = alignment,
                            strokeWidth = strokeWidth,
                            startTimeMs = existingOverlay?.startTimeMs ?: playheadMs,
                            endTimeMs = (existingOverlay?.startTimeMs ?: playheadMs) + duration.toLong(),
                            animationIn = animIn,
                            animationOut = animOut,
                            positionX = positionX,
                            positionY = positionY
                        )
                        onSave(overlay)
                    },
                    enabled = text.isNotBlank()
                ) {
                    Text("Save", color = if (text.isNotBlank()) Mocha.Mauve else Mocha.Surface1)
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Text input
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Text") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Mocha.Mauve,
                unfocusedBorderColor = Mocha.Surface1,
                focusedTextColor = Mocha.Text,
                unfocusedTextColor = Mocha.Text,
                focusedLabelColor = Mocha.Mauve,
                unfocusedLabelColor = Mocha.Subtext0,
                cursorColor = Mocha.Mauve
            ),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Font size
        EffectSlider("Font Size", fontSize, 12f, 120f) { fontSize = it }

        // Font family
        Text("Font", color = Mocha.Subtext1, fontSize = 12.sp)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            items(fontFamilies) { (family, label) ->
                FilterChip(
                    onClick = { fontFamily = family },
                    label = { Text(label, fontSize = 11.sp) },
                    selected = fontFamily == family,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.Surface0,
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.3f),
                        selectedLabelColor = Mocha.Mauve
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Style buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                onClick = { bold = !bold },
                label = { Text("B", fontWeight = FontWeight.Bold) },
                selected = bold,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Mocha.Mauve.copy(alpha = 0.3f)
                )
            )
            FilterChip(
                onClick = { italic = !italic },
                label = { Text("I") },
                selected = italic,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Mocha.Mauve.copy(alpha = 0.3f)
                )
            )
            FilterChip(
                onClick = { alignment = TextAlignment.LEFT },
                label = { Icon(Icons.Default.FormatAlignLeft, null, modifier = Modifier.size(16.dp)) },
                selected = alignment == TextAlignment.LEFT,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Mocha.Mauve.copy(alpha = 0.3f)
                )
            )
            FilterChip(
                onClick = { alignment = TextAlignment.CENTER },
                label = { Icon(Icons.Default.FormatAlignCenter, null, modifier = Modifier.size(16.dp)) },
                selected = alignment == TextAlignment.CENTER,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Mocha.Mauve.copy(alpha = 0.3f)
                )
            )
            FilterChip(
                onClick = { alignment = TextAlignment.RIGHT },
                label = { Icon(Icons.Default.FormatAlignRight, null, modifier = Modifier.size(16.dp)) },
                selected = alignment == TextAlignment.RIGHT,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Mocha.Mauve.copy(alpha = 0.3f)
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Color picker
        Text("Color", color = Mocha.Subtext1, fontSize = 12.sp)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            items(colorOptions) { color ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .then(
                            if (selectedColor == color) Modifier.border(2.dp, Mocha.Mauve, CircleShape)
                            else Modifier.border(1.dp, Mocha.Surface1, CircleShape)
                        )
                        .clickable { selectedColor = color }
                )
            }
        }

        // Position
        Text("Position", color = Mocha.Subtext1, fontSize = 12.sp)
        EffectSlider("Horizontal", positionX, 0f, 1f) { positionX = it }
        EffectSlider("Vertical", positionY, 0f, 1f) { positionY = it }

        // Duration (display as seconds for readability)
        EffectSlider("Duration (sec)", duration / 1000f, 0.5f, 10f) { duration = it * 1000f }

        // Animation In
        Text("Enter Animation", color = Mocha.Subtext1, fontSize = 12.sp)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            items(TextAnimation.entries.toList()) { anim ->
                FilterChip(
                    onClick = { animIn = anim },
                    label = { Text(anim.displayName, fontSize = 11.sp) },
                    selected = animIn == anim,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.Surface0,
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.3f),
                        selectedLabelColor = Mocha.Mauve
                    )
                )
            }
        }

        // Animation Out
        Text("Exit Animation", color = Mocha.Subtext1, fontSize = 12.sp)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            items(TextAnimation.entries.toList()) { anim ->
                FilterChip(
                    onClick = { animOut = anim },
                    label = { Text(anim.displayName, fontSize = 11.sp) },
                    selected = animOut == anim,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Mocha.Surface0,
                        selectedContainerColor = Mocha.Mauve.copy(alpha = 0.3f),
                        selectedLabelColor = Mocha.Mauve
                    )
                )
            }
        }
    }
}
