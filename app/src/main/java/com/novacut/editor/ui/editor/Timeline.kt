package com.novacut.editor.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.engine.VideoEngine
import com.novacut.editor.model.*
import com.novacut.editor.ui.theme.Mocha
import kotlinx.coroutines.launch

@Composable
fun Timeline(
    tracks: List<Track>,
    playheadMs: Long,
    totalDurationMs: Long,
    zoomLevel: Float,
    scrollOffsetMs: Long,
    selectedClipId: String?,
    waveforms: Map<String, FloatArray> = emptyMap(),
    onClipSelected: (String, String) -> Unit,
    onPlayheadMoved: (Long) -> Unit,
    onZoomChanged: (Float) -> Unit,
    onScrollChanged: (Long) -> Unit,
    onTrimChanged: (clipId: String, newTrimStartMs: Long?, newTrimEndMs: Long?) -> Unit = { _, _, _ -> },
    engine: VideoEngine,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val trackHeight = 60.dp
    val rulerHeight = 24.dp
    val pixelsPerMs = zoomLevel * 0.15f // base scale
    val coroutineScope = rememberCoroutineScope()

    // Thumbnail cache
    val thumbnails = remember { mutableStateMapOf<String, List<Bitmap>>() }

    // Load thumbnails for visible clips
    LaunchedEffect(tracks, zoomLevel) {
        tracks.forEach { track ->
            track.clips.forEach { clip ->
                val key = "${clip.id}_${zoomLevel}"
                if (!thumbnails.containsKey(key)) {
                    coroutineScope.launch {
                        val count = ((clip.durationMs * pixelsPerMs) / 80f).toInt().coerceIn(1, 20)
                        val strip = engine.extractThumbnailStrip(clip.sourceUri, count)
                        thumbnails[key] = strip
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mocha.Mantle)
    ) {
        // Track headers + timeline content
        Row(modifier = Modifier.fillMaxWidth()) {
            // Track headers
            Column(
                modifier = Modifier
                    .width(40.dp)
                    .background(Mocha.Crust)
            ) {
                // Ruler spacer
                Spacer(modifier = Modifier.height(rulerHeight))

                tracks.forEach { track ->
                    Box(
                        modifier = Modifier
                            .height(trackHeight)
                            .fillMaxWidth()
                            .background(Mocha.Crust)
                            .border(
                                width = 0.5.dp,
                                color = Mocha.Surface0,
                                shape = RoundedCornerShape(0.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (track.type) {
                                TrackType.VIDEO -> Icons.Default.Videocam
                                TrackType.AUDIO -> Icons.Default.MusicNote
                                TrackType.OVERLAY -> Icons.Default.Layers
                                TrackType.TEXT -> Icons.Default.Title
                            },
                            contentDescription = track.type.name,
                            tint = when (track.type) {
                                TrackType.VIDEO -> Mocha.Blue
                                TrackType.AUDIO -> Mocha.Green
                                TrackType.OVERLAY -> Mocha.Peach
                                TrackType.TEXT -> Mocha.Mauve
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Scrollable timeline area
            var timelineWidthPx by remember { mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clipToBounds()
                    .onSizeChanged { timelineWidthPx = it.width.toFloat() }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newZoom = (zoomLevel * zoom).coerceIn(0.1f, 10f)
                            onZoomChanged(newZoom)
                            val panMs = (pan.x / pixelsPerMs).toLong()
                            onScrollChanged((scrollOffsetMs - panMs).coerceAtLeast(0L))
                        }
                    }
            ) {
                val scrollState = rememberScrollState()

                Column {
                    // Time ruler
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rulerHeight)
                            .background(Mocha.Crust)
                    ) {
                        drawTimeRuler(
                            scrollOffsetMs = scrollOffsetMs,
                            pixelsPerMs = pixelsPerMs,
                            width = size.width,
                            height = size.height
                        )
                    }

                    // Tracks
                    tracks.forEach { track ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(trackHeight)
                                .background(Mocha.Base)
                                .border(
                                    width = 0.5.dp,
                                    color = Mocha.Surface0.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(0.dp)
                                )
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        // Convert tap to timeline position
                                        val tappedMs = scrollOffsetMs + (offset.x / pixelsPerMs).toLong()
                                        // Find clip at this position
                                        val clip = track.clips.firstOrNull {
                                            tappedMs in it.timelineStartMs..it.timelineEndMs
                                        }
                                        if (clip != null) {
                                            onClipSelected(clip.id, track.id)
                                        }
                                        onPlayheadMoved(tappedMs.coerceIn(0L, totalDurationMs))
                                    }
                                }
                        ) {
                            // Draw clips
                            track.clips.forEach { clip ->
                                val clipStartPx = ((clip.timelineStartMs - scrollOffsetMs) * pixelsPerMs)
                                val clipWidthPx = (clip.durationMs * pixelsPerMs)

                                if (clipStartPx + clipWidthPx > 0 && clipStartPx < timelineWidthPx) {
                                    val isSelected = clip.id == selectedClipId
                                    val clipColor = when (track.type) {
                                        TrackType.VIDEO -> Mocha.Blue
                                        TrackType.AUDIO -> Mocha.Green
                                        TrackType.OVERLAY -> Mocha.Peach
                                        TrackType.TEXT -> Mocha.Mauve
                                    }

                                    Box(
                                        modifier = Modifier
                                            .offset(x = with(density) { clipStartPx.toDp() })
                                            .width(with(density) { clipWidthPx.toDp() })
                                            .fillMaxHeight()
                                            .padding(vertical = 2.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (isSelected) clipColor.copy(alpha = 0.5f)
                                                else clipColor.copy(alpha = 0.3f)
                                            )
                                            .then(
                                                if (isSelected) Modifier.border(
                                                    2.dp,
                                                    clipColor,
                                                    RoundedCornerShape(6.dp)
                                                ) else Modifier
                                            )
                                    ) {
                                        // Thumbnail strip for video tracks
                                        if (track.type == TrackType.VIDEO) {
                                            val key = "${clip.id}_${zoomLevel}"
                                            val thumbs = thumbnails[key]
                                            if (thumbs != null && thumbs.isNotEmpty()) {
                                                Row(
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    thumbs.forEach { bitmap ->
                                                        Image(
                                                            bitmap = bitmap.asImageBitmap(),
                                                            contentDescription = null,
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .fillMaxHeight(),
                                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Audio waveform
                                        if (track.type == TrackType.AUDIO) {
                                            val waveform = waveforms[clip.id]
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                if (waveform != null && waveform.isNotEmpty()) {
                                                    drawWaveform(waveform, clipColor)
                                                } else {
                                                    drawWaveformPlaceholder(clipColor)
                                                }
                                            }
                                        }

                                        // Trim handles
                                        if (isSelected) {
                                            // Left trim handle
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.CenterStart)
                                                    .width(12.dp)
                                                    .fillMaxHeight()
                                                    .background(
                                                        clipColor,
                                                        RoundedCornerShape(
                                                            topStart = 6.dp,
                                                            bottomStart = 6.dp
                                                        )
                                                    )
                                                    .pointerInput(clip.id) {
                                                        detectHorizontalDragGestures { _, dragAmount ->
                                                            val deltaMs = (dragAmount / pixelsPerMs).toLong()
                                                            val newStart = (clip.trimStartMs + deltaMs)
                                                                .coerceAtLeast(0L)
                                                                .coerceAtMost(clip.trimEndMs - 100L)
                                                            onTrimChanged(clip.id, newStart, null)
                                                        }
                                                    }
                                            )
                                            // Right trim handle
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.CenterEnd)
                                                    .width(12.dp)
                                                    .fillMaxHeight()
                                                    .background(
                                                        clipColor,
                                                        RoundedCornerShape(
                                                            topEnd = 6.dp,
                                                            bottomEnd = 6.dp
                                                        )
                                                    )
                                                    .pointerInput(clip.id) {
                                                        detectHorizontalDragGestures { _, dragAmount ->
                                                            val deltaMs = (dragAmount / pixelsPerMs).toLong()
                                                            val newEnd = (clip.trimEndMs + deltaMs)
                                                                .coerceAtLeast(clip.trimStartMs + 100L)
                                                            onTrimChanged(clip.id, null, newEnd)
                                                        }
                                                    }
                                            )
                                        }

                                        // Transition indicator
                                        if (clip.transition != null) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(2.dp)
                                                    .size(12.dp)
                                                    .background(
                                                        Mocha.Yellow,
                                                        RoundedCornerShape(2.dp)
                                                    )
                                            )
                                        }
                                    }
                                }
                            }

                            // Playhead line
                            val playheadPx = ((playheadMs - scrollOffsetMs) * pixelsPerMs)
                            if (playheadPx in 0f..timelineWidthPx) {
                                Canvas(
                                    modifier = Modifier
                                        .offset(x = with(density) { playheadPx.toDp() })
                                        .width(2.dp)
                                        .fillMaxHeight()
                                ) {
                                    drawRect(
                                        color = Mocha.Red,
                                        size = Size(2f * density.density, size.height)
                                    )
                                }
                            }
                        }
                    }
                }

                // Playhead indicator on ruler
                val playheadPx = ((playheadMs - scrollOffsetMs) * pixelsPerMs)
                if (playheadPx >= 0) {
                    Canvas(
                        modifier = Modifier
                            .offset(x = with(density) { (playheadPx - 6).toDp() })
                            .size(12.dp, rulerHeight)
                    ) {
                        // Triangle playhead
                        val path = Path().apply {
                            moveTo(size.width / 2, size.height)
                            lineTo(0f, 0f)
                            lineTo(size.width, 0f)
                            close()
                        }
                        drawPath(path, Mocha.Red)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawTimeRuler(
    scrollOffsetMs: Long,
    pixelsPerMs: Float,
    width: Float,
    height: Float
) {
    val intervalMs = when {
        pixelsPerMs > 0.5f -> 1000L
        pixelsPerMs > 0.1f -> 5000L
        pixelsPerMs > 0.02f -> 10000L
        else -> 30000L
    }

    val startMs = (scrollOffsetMs / intervalMs) * intervalMs
    var currentMs = startMs

    while (true) {
        val x = (currentMs - scrollOffsetMs) * pixelsPerMs
        if (x > width) break

        if (x >= 0) {
            val isMajor = currentMs % (intervalMs * 5) == 0L
            val tickHeight = if (isMajor) height * 0.6f else height * 0.3f

            drawLine(
                color = if (isMajor) Color(0xFF7F849C) else Color(0xFF45475A),
                start = Offset(x, height - tickHeight),
                end = Offset(x, height),
                strokeWidth = if (isMajor) 1.5f else 0.5f
            )
        }
        currentMs += intervalMs
    }
}

private fun DrawScope.drawWaveform(samples: FloatArray, color: Color) {
    val steps = (size.width / 3f).toInt().coerceAtLeast(1)
    val samplesPerStep = samples.size.toFloat() / steps
    val centerY = size.height / 2f
    val maxAmp = size.height * 0.45f

    for (i in 0 until steps) {
        val sampleIndex = (i * samplesPerStep).toInt().coerceIn(0, samples.size - 1)
        val amplitude = samples[sampleIndex].coerceIn(0f, 1f)
        val barH = (amplitude * maxAmp).coerceAtLeast(1f)
        val x = i * 3f

        drawLine(
            color = color.copy(alpha = 0.7f),
            start = Offset(x, centerY - barH),
            end = Offset(x, centerY + barH),
            strokeWidth = 2f
        )
    }
}

private fun DrawScope.drawWaveformPlaceholder(color: Color) {
    val steps = (size.width / 4f).toInt().coerceAtLeast(1)
    for (i in 0 until steps) {
        val x = i * 4f
        val h = (Math.random() * size.height * 0.6f + size.height * 0.1f).toFloat()
        val top = (size.height - h) / 2f
        drawLine(
            color = color.copy(alpha = 0.6f),
            start = Offset(x, top),
            end = Offset(x, top + h),
            strokeWidth = 2f
        )
    }
}
