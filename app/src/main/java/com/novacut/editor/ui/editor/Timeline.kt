package com.novacut.editor.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextOverflow
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
    onToggleTrackMute: (String) -> Unit = {},
    onToggleTrackVisible: (String) -> Unit = {},
    onToggleTrackLock: (String) -> Unit = {},
    beatMarkers: List<Long> = emptyList(),
    engine: VideoEngine,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val trackHeight = 60.dp
    val rulerHeight = 28.dp
    val pixelsPerMs = zoomLevel * 0.15f // base scale
    val coroutineScope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()

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

        // Zoom controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${(zoomLevel * 100).toInt()}%",
                color = Mocha.Subtext0,
                fontSize = 10.sp,
                modifier = Modifier.padding(end = 6.dp)
            )
            IconButton(
                onClick = { onZoomChanged((zoomLevel * 0.75f).coerceAtLeast(0.1f)) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.ZoomOut, "Zoom Out", tint = Mocha.Subtext0, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = { onZoomChanged(1f) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.FitScreen, "Fit", tint = Mocha.Subtext0, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = { onZoomChanged((zoomLevel * 1.33f).coerceAtMost(10f)) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.ZoomIn, "Zoom In", tint = Mocha.Subtext0, modifier = Modifier.size(16.dp))
            }
        }

        // Track headers + timeline content
        Row(modifier = Modifier.fillMaxWidth()) {
            // Track headers
            Column(
                modifier = Modifier
                    .width(44.dp)
                    .background(Mocha.Crust)
            ) {
                // Ruler spacer
                Spacer(modifier = Modifier.height(rulerHeight))

                tracks.forEach { track ->
                    val trackColor = when (track.type) {
                        TrackType.VIDEO -> Mocha.Blue
                        TrackType.AUDIO -> Mocha.Green
                        TrackType.OVERLAY -> Mocha.Peach
                        TrackType.TEXT -> Mocha.Mauve
                        TrackType.ADJUSTMENT -> Mocha.Yellow
                    }
                    Column(
                        modifier = Modifier
                            .height(trackHeight)
                            .fillMaxWidth()
                            .background(Mocha.Crust)
                            .border(0.5.dp, Mocha.Surface0),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = when (track.type) {
                                TrackType.VIDEO -> Icons.Default.Videocam
                                TrackType.AUDIO -> Icons.Default.MusicNote
                                TrackType.OVERLAY -> Icons.Default.Layers
                                TrackType.TEXT -> Icons.Default.Title
                                TrackType.ADJUSTMENT -> Icons.Default.Tune
                            },
                            contentDescription = track.type.name,
                            tint = if (track.isVisible) trackColor else Mocha.Surface2,
                            modifier = Modifier.size(14.dp)
                        )
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Icon(
                                if (track.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (track.isMuted) "Unmute" else "Mute",
                                tint = if (track.isMuted) Mocha.Red.copy(alpha = 0.7f) else Mocha.Surface2,
                                modifier = Modifier.size(11.dp).clickable { onToggleTrackMute(track.id) }
                            )
                            Icon(
                                if (track.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (track.isVisible) "Hide" else "Show",
                                tint = if (!track.isVisible) Mocha.Red.copy(alpha = 0.7f) else Mocha.Surface2,
                                modifier = Modifier.size(11.dp).clickable { onToggleTrackVisible(track.id) }
                            )
                            Icon(
                                if (track.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = if (track.isLocked) "Unlock" else "Lock",
                                tint = if (track.isLocked) Mocha.Peach.copy(alpha = 0.7f) else Mocha.Surface2,
                                modifier = Modifier.size(11.dp).clickable { onToggleTrackLock(track.id) }
                            )
                        }
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
                    // Time ruler — tap and drag to position playhead
                    var rulerDragX by remember { mutableFloatStateOf(0f) }
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rulerHeight)
                            .background(Mocha.Crust)
                            .pointerInput(scrollOffsetMs, zoomLevel, totalDurationMs) {
                                detectTapGestures { offset ->
                                    val currentPixelsPerMs = zoomLevel * 0.15f
                                    val tappedMs = scrollOffsetMs + (offset.x / currentPixelsPerMs).toLong()
                                    onPlayheadMoved(tappedMs.coerceIn(0L, totalDurationMs))
                                }
                            }
                            .pointerInput(scrollOffsetMs, zoomLevel, totalDurationMs) {
                                detectDragGestures(
                                    onDragStart = { offset -> rulerDragX = offset.x },
                                    onDrag = { _, dragAmount ->
                                        rulerDragX += dragAmount.x
                                        val currentPixelsPerMs = zoomLevel * 0.15f
                                        if (currentPixelsPerMs < 0.001f) return@detectDragGestures
                                        val posMs = scrollOffsetMs + (rulerDragX / currentPixelsPerMs).toLong()
                                        onPlayheadMoved(posMs.coerceIn(0L, totalDurationMs))
                                    }
                                )
                            }
                    ) {
                        drawTimeRuler(
                            scrollOffsetMs = scrollOffsetMs,
                            pixelsPerMs = pixelsPerMs,
                            width = size.width,
                            height = size.height,
                            textMeasurer = textMeasurer
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
                                        TrackType.ADJUSTMENT -> Mocha.Yellow
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

                                        // Audio waveform + volume envelope
                                        if (track.type == TrackType.AUDIO) {
                                            val waveform = waveforms[clip.id]
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                if (waveform != null && waveform.isNotEmpty()) {
                                                    drawWaveform(waveform, clipColor)
                                                } else {
                                                    drawWaveformPlaceholder(clipColor)
                                                }

                                                // Volume envelope (keyframe-based volume line)
                                                val volumeKfs = clip.keyframes.filter {
                                                    it.property == KeyframeProperty.VOLUME
                                                }.sortedBy { it.timeOffsetMs }
                                                if (volumeKfs.size >= 2) {
                                                    val path = Path()
                                                    val steps = 100
                                                    for (i in 0..steps) {
                                                        val t = i.toFloat() / steps
                                                        val timeMs = (t * clip.durationMs).toLong()
                                                        val vol = com.novacut.editor.engine.KeyframeEngine.getValueAt(
                                                            volumeKfs, KeyframeProperty.VOLUME, timeMs
                                                        ) ?: clip.volume
                                                        val x = t * size.width
                                                        val y = size.height * (1f - (vol / 2f).coerceIn(0f, 1f))
                                                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                                    }
                                                    drawPath(
                                                        path,
                                                        Color(0xFFF9E2AF), // Yellow
                                                        style = Stroke(width = 1.5f)
                                                    )
                                                    // Draw keyframe dots on envelope
                                                    volumeKfs.forEach { kf ->
                                                        val x = (kf.timeOffsetMs.toFloat() / clip.durationMs) * size.width
                                                        val y = size.height * (1f - (kf.value / 2f).coerceIn(0f, 1f))
                                                        drawCircle(Color(0xFFF9E2AF), 3f, Offset(x, y))
                                                    }
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

                                        // Clip filename label
                                        if (clipWidthPx > 60) {
                                            val fileName = clip.sourceUri.lastPathSegment
                                                ?.substringAfterLast('/')
                                                ?.substringBeforeLast('.') ?: ""
                                            if (fileName.isNotEmpty()) {
                                                Text(
                                                    text = fileName,
                                                    color = Mocha.Text.copy(alpha = 0.7f),
                                                    fontSize = 8.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .align(Alignment.BottomStart)
                                                        .padding(start = 4.dp, bottom = 2.dp)
                                                        .background(
                                                            Mocha.Crust.copy(alpha = 0.6f),
                                                            RoundedCornerShape(2.dp)
                                                        )
                                                        .padding(horizontal = 2.dp)
                                                )
                                            }
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

                            // Beat markers
                            beatMarkers.forEach { beatMs ->
                                val beatPx = ((beatMs - scrollOffsetMs) * pixelsPerMs)
                                if (beatPx in 0f..timelineWidthPx) {
                                    Canvas(
                                        modifier = Modifier
                                            .offset(x = with(density) { beatPx.toDp() })
                                            .width(1.dp)
                                            .fillMaxHeight()
                                    ) {
                                        drawRect(
                                            color = Color(0x40F9E2AF), // Yellow semi-transparent
                                            size = Size(1f * density.density, size.height)
                                        )
                                    }
                                }
                            }

                            // Magnetic snap indicator (shows when clip edges align)
                            // Snap lines are drawn at clip boundaries for visual feedback
                            val allClipEdges = track.clips.flatMap { listOf(it.timelineStartMs, it.timelineEndMs) }.distinct()
                            val selectedClipObj = track.clips.find { it.id == selectedClipId }
                            if (selectedClipObj != null) {
                                allClipEdges.forEach { edgeMs ->
                                    if (edgeMs != selectedClipObj.timelineStartMs && edgeMs != selectedClipObj.timelineEndMs) {
                                        val selStart = selectedClipObj.timelineStartMs
                                        val selEnd = selectedClipObj.timelineEndMs
                                        val snapThreshold = (5 / pixelsPerMs).toLong() // 5px snap distance
                                        if (kotlin.math.abs(selStart - edgeMs) < snapThreshold || kotlin.math.abs(selEnd - edgeMs) < snapThreshold) {
                                            val snapPx = ((edgeMs - scrollOffsetMs) * pixelsPerMs)
                                            if (snapPx in 0f..timelineWidthPx) {
                                                Canvas(
                                                    modifier = Modifier
                                                        .offset(x = with(density) { snapPx.toDp() })
                                                        .width(1.dp)
                                                        .fillMaxHeight()
                                                ) {
                                                    drawRect(
                                                        color = Color(0xFF89B4FA), // Blue snap line
                                                        size = Size(1.5f * density.density, size.height)
                                                    )
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
    height: Float,
    textMeasurer: TextMeasurer
) {
    val intervalMs = when {
        pixelsPerMs > 0.5f -> 1000L
        pixelsPerMs > 0.1f -> 5000L
        pixelsPerMs > 0.02f -> 10000L
        else -> 30000L
    }

    val startMs = (scrollOffsetMs / intervalMs) * intervalMs
    var currentMs = startMs
    val labelStyle = TextStyle(
        color = Color(0xFFA6ADC8), // Mocha.Subtext0
        fontSize = 9.sp
    )

    while (true) {
        val x = (currentMs - scrollOffsetMs) * pixelsPerMs
        if (x > width) break

        if (x >= 0) {
            val isMajor = currentMs % (intervalMs * 5) == 0L
            val tickHeight = if (isMajor) height * 0.4f else height * 0.25f

            drawLine(
                color = if (isMajor) Color(0xFF7F849C) else Color(0xFF45475A),
                start = Offset(x, height - tickHeight),
                end = Offset(x, height),
                strokeWidth = if (isMajor) 1.5f else 0.5f
            )

            // Time labels at major ticks
            if (isMajor) {
                val totalSeconds = (currentMs / 1000).toInt()
                val min = totalSeconds / 60
                val sec = totalSeconds % 60
                val label = if (min > 0) "$min:${"%02d".format(sec)}" else "${sec}s"
                val measured = textMeasurer.measure(label, labelStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(x - measured.size.width / 2f, 1f)
                )
            }
        }
        currentMs += intervalMs
    }
}

private fun DrawScope.drawWaveform(samples: FloatArray, color: Color) {
    if (samples.isEmpty()) return
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
