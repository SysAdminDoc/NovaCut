package com.novacut.editor.ui.editor

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.engine.whisper.WhisperModelState
import com.novacut.editor.ui.theme.Mocha

data class AiToolConfig(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val color: androidx.compose.ui.graphics.Color,
    val requiresClip: Boolean = true
)

val aiTools = listOf(
    AiToolConfig(
        "auto_captions", "Auto Captions",
        "Generate subtitles from speech",
        Icons.Default.ClosedCaption, Mocha.Blue
    ),
    AiToolConfig(
        "remove_bg", "Remove BG",
        "Remove video background",
        Icons.Default.Wallpaper, Mocha.Green
    ),
    AiToolConfig(
        "scene_detect", "Scene Detect",
        "Auto-detect scene changes",
        Icons.Default.ContentCut, Mocha.Peach
    ),
    AiToolConfig(
        "track_motion", "Track Motion",
        "Track objects across frames",
        Icons.Default.GpsFixed, Mocha.Mauve
    ),
    AiToolConfig(
        "smart_crop", "Smart Crop",
        "AI-powered framing",
        Icons.Default.Crop, Mocha.Teal
    ),
    AiToolConfig(
        "auto_color", "Auto Color",
        "AI color correction",
        Icons.Default.Palette, Mocha.Yellow
    ),
    AiToolConfig(
        "stabilize", "Stabilize",
        "Reduce camera shake",
        Icons.Default.Straighten, Mocha.Sapphire
    ),
    AiToolConfig(
        "denoise", "Denoise Audio",
        "Remove background noise",
        Icons.Default.VolumeOff, Mocha.Flamingo
    )
)

@Composable
fun AiToolsPanel(
    hasSelectedClip: Boolean,
    onToolSelected: (String) -> Unit,
    onDisabledToolTapped: (String) -> Unit = {},
    onCancelProcessing: () -> Unit = {},
    onClose: () -> Unit,
    processingTool: String? = null,
    whisperModelState: WhisperModelState = WhisperModelState.NOT_DOWNLOADED,
    whisperDownloadProgress: Float = 0f,
    onDownloadWhisper: () -> Unit = {},
    onDeleteWhisper: () -> Unit = {},
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Mocha.Mauve,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI Tools", color = Mocha.Text, fontSize = 16.sp)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            if (whisperModelState == WhisperModelState.READY)
                "Whisper speech-to-text active"
            else
                "On-device AI - no internet required",
            color = Mocha.Subtext0,
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Whisper model status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Mocha.Surface0)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = when (whisperModelState) {
                        WhisperModelState.READY -> Mocha.Green
                        WhisperModelState.DOWNLOADING -> Mocha.Yellow
                        WhisperModelState.ERROR -> Mocha.Red
                        else -> Mocha.Surface2
                    },
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when (whisperModelState) {
                            WhisperModelState.READY -> "Whisper (speech-to-text)"
                            WhisperModelState.DOWNLOADING -> "Downloading model..."
                            WhisperModelState.ERROR -> "Download failed"
                            else -> "Whisper model (~75 MB)"
                        },
                        color = Mocha.Text,
                        fontSize = 12.sp
                    )
                    if (whisperModelState == WhisperModelState.DOWNLOADING) {
                        LinearProgressIndicator(
                            progress = { whisperDownloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .height(3.dp),
                            color = Mocha.Blue,
                            trackColor = Mocha.Surface2
                        )
                    }
                    if (whisperModelState == WhisperModelState.NOT_DOWNLOADED) {
                        Text(
                            "Enables real transcription for captions",
                            color = Mocha.Subtext0,
                            fontSize = 10.sp
                        )
                    }
                }
                when (whisperModelState) {
                    WhisperModelState.NOT_DOWNLOADED, WhisperModelState.ERROR -> {
                        TextButton(
                            onClick = onDownloadWhisper,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp), tint = Mocha.Blue)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Get", color = Mocha.Blue, fontSize = 12.sp)
                        }
                    }
                    WhisperModelState.READY -> {
                        TextButton(
                            onClick = onDeleteWhisper,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Remove", color = Mocha.Subtext0, fontSize = 11.sp)
                        }
                    }
                    else -> {}
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Processing indicator
        if (processingTool != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Mocha.Surface0)
            ) {
                Row(
                    modifier = Modifier
                        .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Mocha.Mauve,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Processing: ${aiTools.find { it.id == processingTool }?.name}...",
                        color = Mocha.Text,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onCancelProcessing) {
                        Text("Cancel", color = Mocha.Red, fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // AI tool grid
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(aiTools) { tool ->
                val isEnabled = !tool.requiresClip || hasSelectedClip
                val isProcessing = processingTool == tool.id

                Card(
                    onClick = {
                        when {
                            isProcessing -> { /* ignore */ }
                            !isEnabled -> onDisabledToolTapped(tool.name)
                            else -> onToolSelected(tool.id)
                        }
                    },
                    modifier = Modifier
                        .width(90.dp)
                        .height(100.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isProcessing) tool.color.copy(alpha = 0.2f)
                        else if (!isEnabled) Mocha.Surface0.copy(alpha = 0.5f)
                        else Mocha.Surface0
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = tool.color,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                tool.icon,
                                contentDescription = tool.name,
                                tint = if (isEnabled) tool.color else Mocha.Surface2,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            tool.name,
                            fontSize = 10.sp,
                            color = if (isEnabled) Mocha.Text else Mocha.Surface2,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            lineHeight = 12.sp
                        )
                    }
                }
            }
        }
    }
}
