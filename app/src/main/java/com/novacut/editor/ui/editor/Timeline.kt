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
    isTrimMode: Boolean = false,
    waveforms: Map<String, FloatArray> = emptyMap(),
    onClipSelected: (String, String) -> Unit,
    onPlayheadMoved: (Long) -> Unit,
    onZoomChanged: (Float) -> Unit,
    onScrollChanged: (Long) -> Unit,
    onTrimChanged: (clipId: String, newTrimStartMs: Long?, newTrimEndMs: Long?) -> Unit = { _, _, _ -> },
    onTrimDragStarted: () -> Unit = {},
    onTimelineWidthChanged: (Float) -> Unit = {},
    engine: VideoEngine,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val trackHeight = 60.dp
    val rulerHeight = 24.dp
    val pixelsPerMs = zoomLevel * 0.15f // base scale
    val coroutineScope = rememberCoroutineScope()

    // Thumbnail cache — quantize zoom to prevent unbounded cache growth
    val thumbnails = remember { mutableStateMapOf<String, List<Bitmap>>() }
    val quantizedZoom = (zoomLevel * 4).toInt() / 4f // quantize to 0.25 steps

    // Load thumbnails for visible clips — evict stale zoom levels to prevent OOM
    LaunchedEffect(tracks, quantizedZoom) {
        thumbnails.keys.filter { !it.endsWith("_$quantizedZoom") }
            .forEach { thumbnails.remove(it) }
        tracks.forEach { track ->
            track.clips.forEach { clip ->
                val key = "${clip.id}_${quantizedZoom}"
                if (!thumbnails.containsKey(key)) {
                    launch {
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
        // Trim mode indicator
        if (isTrimMode && selectedClipId != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Mocha.Peach.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "TRIM MODE — Drag clip edges to adjust",
                    color = Mocha.Peach,
                    fontSize = 11.sp
                )
            }
        }

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
                    .onSizeChanged {
                        timelineWidthPx = it.width.toFloat()
                        onTimelineWidthChanged(timelineWidthPx)
                    }
                    .pointerInput(zoomLevel, scrollOffsetMs) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val currentPixelsPerMs = zoomLevel * 0.15f
                            val newZoom = (zoomLevel * zoom).coerceIn(0.1f, 10f)
                            onZoomChanged(newZoom)
                            val panMs = (pan.x / currentPixelsPerMs).toLong()
                            onScrollChanged((scrollOffsetMs - panMs).coerceAtLeast(0L))
                        }
                    }
            ) {
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
                                .pointerInput(scrollOffsetMs, zoomLevel) {
                                    detectTapGestures { offset ->
                                        // Convert tap to timeline position
                                        val currentPixelsPerMs = zoomLevel * 0.15f
                                        val tappedMs = scrollOffsetMs + (offset.x / currentPixelsPerMs).toLong()
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
                                            val key = "${clip.id}_${quantizedZoom}"
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
                                                    .pointerInput(clip.id, clip.trimStartMs, clip.trimEndMs, zoomLevel) {
                                                        val currentPixelsPerMs = zoomLevel * 0.15f
                                                        detectHorizontalDragGestures(
                                                            onDragStart = { onTrimDragStarted() },
                                                            onDragEnd = {},
                                                            onDragCancel = {},
                                                            onHorizontalDrag = { _, dragAmount ->
                                                                if (currentPixelsPerMs < 0.001f) return@detectHorizontalDragGestures
                                                                val deltaMs = (dragAmount / currentPixelsPerMs).toLong()
                                                                val newStart = (clip.trimStartMs + deltaMs)
                                                                    .coerceAtLeast(0L)
                                                                    .coerceAtMost(clip.trimEndMs - 100L)
                                                                onTrimChanged(clip.id, newStart, null)
                                                            }
                                                        )
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
                                                    .pointerInput(clip.id, clip.trimStartMs, clip.trimEndMs, zoomLevel) {
                                                        val currentPixelsPerMs = zoomLevel * 0.15f
                                                        detectHorizontalDragGestures(
                                                            onDragStart = { onTrimDragStarted() },
                                                            onDragEnd = {},
                                                            onDragCancel = {},
                                                            onHorizontalDrag = { _, dragAmount ->
                                                                if (currentPixelsPerMs < 0.001f) return@detectHorizontalDragGestures
                                                                val deltaMs = (dragAmount / currentPixelsPerMs).toLong()
                                                                val newEnd = (clip.trimEndMs + deltaMs)
                                                                    .coerceIn(clip.trimStartMs + 100L, clip.sourceDurationMs)
                                                                onTrimChanged(clip.id, null, newEnd)
                                                            }
                                                        )
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

                                        // Effects count badge
                                        if (clip.effects.isNotEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(2.dp)
                                                    .background(
                                                        Mocha.Mauve.copy(alpha = 0.8f),
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    "FX${clip.effects.size}",
                                                    color = Mocha.Crust,
                                                    fontSize = 7.sp,
                                                    lineHeight = 8.sp
                                                )
                                            }
                                        }

                                        // Keyframe dots
                                        if (clip.keyframes.isNotEmpty() && clipWidthPx > 20) {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                val clipDuration = clip.durationMs.toFloat()
                                                if (clipDuration <= 0) return@Canvas
                                                clip.keyframes.distinctBy { it.timeOffsetMs }.forEach { kf ->
                                                    val x = (kf.timeOffsetMs / clipDuration) * size.width
                                                    if (x in 0f..size.width) {
                                                        drawCircle(
                                                            color = Color(0xFFF5C2E7), // Mocha.Pink
                                                            radius = 3f,
                                                            center = Offset(x, size.height - 6f)
                                                        )
                                                    }
                                                }
                                            }
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
    // Deterministic pattern to avoid 30fps flicker from Math.random()
    val steps = (size.width / 4f).toInt().coerceAtLeast(1)
    val centerY = size.height / 2f
    for (i in 0 until steps) {
        val x = i * 4f
        val amplitude = (kotlin.math.sin(i * 0.7) * 0.3 + 0.4).toFloat()
        val barH = (amplitude * size.height * 0.45f).coerceAtLeast(1f)
        drawLine(
            color = color.copy(alpha = 0.4f),
            start = Offset(x, centerY - barH),
            end = Offset(x, centerY + barH),
            strokeWidth = 2f
        )
    }
}
