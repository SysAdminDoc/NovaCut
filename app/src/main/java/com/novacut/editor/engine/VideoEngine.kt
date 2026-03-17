package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.*
import com.novacut.editor.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "VideoEngine"

@Singleton
class VideoEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var player: ExoPlayer? = null
    private var playerListener: Player.Listener? = null
    // Thread-safe cache without accessOrder to avoid ConcurrentModificationException
    // Don't recycle evicted bitmaps — they may still be referenced by Compose Image nodes
    private val thumbnailCache = object : LinkedHashMap<String, Bitmap>(100, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean {
            return size > 200
        }
    }
    private val cacheLock = Any()

    // Clip durations for multi-clip seek/playhead calculations
    private var clipDurationsMs: List<Long> = emptyList()

    // Active Transformer for export cancellation
    @Volatile private var activeTransformer: Transformer? = null

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress

    private val _exportState = MutableStateFlow(ExportState.IDLE)
    val exportState: StateFlow<ExportState> = _exportState

    /**
     * Get or create ExoPlayer. Must be called from main thread.
     * ExoPlayer requires a Looper for creation and all API calls.
     */
    fun getPlayer(): ExoPlayer {
        if (player == null) {
            player = ExoPlayer.Builder(context).build()
        }
        return player!!
    }

    fun setPlayerListener(listener: Player.Listener) {
        playerListener?.let { player?.removeListener(it) }
        playerListener = listener
        player?.addListener(listener)
    }

    fun removePlayerListener() {
        playerListener?.let { player?.removeListener(it) }
        playerListener = null
    }

    fun prepareClip(uri: Uri) {
        val p = getPlayer()
        p.setMediaItem(MediaItem.fromUri(uri))
        p.prepare()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun prepareTimeline(tracks: List<Track>) {
        val p = getPlayer()
        val videoClips = tracks.filter { it.type == TrackType.VIDEO }.flatMap { it.clips }
        if (videoClips.isEmpty()) {
            p.clearMediaItems()
            clipDurationsMs = emptyList()
            return
        }
        clipDurationsMs = videoClips.map { it.durationMs }
        val mediaItems = videoClips.map { clip ->
            MediaItem.Builder()
                .setUri(clip.sourceUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.trimStartMs)
                        .setEndPositionMs(clip.trimEndMs)
                        .build()
                )
                .build()
        }
        p.setMediaItems(mediaItems)
        p.prepare()
    }

    fun seekTo(positionMs: Long) {
        val p = player ?: return
        if (clipDurationsMs.size <= 1) {
            p.seekTo(positionMs)
            return
        }
        var remaining = positionMs
        for (i in clipDurationsMs.indices) {
            if (remaining < clipDurationsMs[i] || i == clipDurationsMs.lastIndex) {
                p.seekTo(i, remaining.coerceAtLeast(0L))
                return
            }
            remaining -= clipDurationsMs[i]
        }
    }

    fun getAbsolutePositionMs(): Long {
        val p = player ?: return 0L
        val index = p.currentMediaItemIndex
        val offset = clipDurationsMs.take(index).sum()
        return offset + p.currentPosition
    }

    fun play() { player?.play() }
    fun pause() { player?.pause() }
    fun isPlaying(): Boolean = player?.isPlaying ?: false

    fun getVideoDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    fun getVideoResolution(uri: Uri): Pair<Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            w to h
        } catch (e: Exception) {
            0 to 0
        } finally {
            retriever.release()
        }
    }

    fun getVideoFrameRate(uri: Uri): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            // Try CAPTURE_FRAMERATE first (camera recordings), fall back to parsing bitrate
            val rate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            rate?.toFloatOrNull()?.toInt()?.coerceIn(1, 120) ?: 30
        } catch (e: Exception) {
            30
        } finally {
            retriever.release()
        }
    }

    fun extractThumbnail(uri: Uri, timeUs: Long, width: Int = 160, height: Int = 90): Bitmap? {
        val key = "${uri}_${timeUs}_${width}x${height}"
        synchronized(cacheLock) {
            thumbnailCache[key]?.let { return it }
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            frame?.let {
                val scaled = Bitmap.createScaledBitmap(it, width, height, true)
                if (scaled !== it) it.recycle()
                synchronized(cacheLock) {
                    // removeEldestEntry handles eviction automatically
                    thumbnailCache[key] = scaled
                }
                scaled
            }
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    suspend fun extractThumbnailStrip(
        uri: Uri,
        count: Int,
        width: Int = 80,
        height: Int = 45
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val duration = getVideoDuration(uri)
        if (duration <= 0 || count <= 0) return@withContext emptyList()

        val interval = duration * 1000L / count
        (0 until count).mapNotNull { i ->
            extractThumbnail(uri, i * interval, width, height)
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    suspend fun export(
        tracks: List<Track>,
        config: ExportConfig,
        outputFile: File,
        textOverlays: List<com.novacut.editor.model.TextOverlay> = emptyList(),
        onProgress: (Float) -> Unit = {},
        onComplete: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        // Reset from any previous export state
        _exportState.value = ExportState.EXPORTING
        _exportProgress.value = 0f

        try {
            val videoTrack = tracks.firstOrNull { it.type == TrackType.VIDEO }
            if (videoTrack == null || videoTrack.clips.isEmpty()) {
                throw IllegalStateException("No video clips to export")
            }

            val (targetW, targetH) = config.resolution.forAspect(config.aspectRatio)

            val editedItems = videoTrack.clips.map { clip ->
                val mediaItem = MediaItem.Builder()
                    .setUri(clip.sourceUri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.trimStartMs)
                            .setEndPositionMs(clip.trimEndMs)
                            .build()
                    )
                    .build()

                val videoEffects = buildList<androidx.media3.common.Effect> {
                    for (effect in clip.effects) {
                        if (!effect.enabled) continue
                        buildVideoEffect(effect)?.let { add(it) }
                    }
                    // Apply static opacity (if no keyframe opacity overrides)
                    val hasKeyframeOpacity = clip.keyframes.any { it.property == KeyframeProperty.OPACITY }
                    if (hasKeyframeOpacity) {
                        add(RgbMatrix { presentationTimeUs, _ ->
                            val timeMs = presentationTimeUs / 1000L
                            val opacity = KeyframeEngine.getValueAt(
                                clip.keyframes, KeyframeProperty.OPACITY, timeMs
                            ) ?: 1f
                            floatArrayOf(
                                opacity, 0f, 0f, 0f,
                                0f, opacity, 0f, 0f,
                                0f, 0f, opacity, 0f,
                                0f, 0f, 0f, 1f
                            )
                        })
                    } else if (clip.opacity != 1f) {
                        val o = clip.opacity.coerceIn(0f, 1f)
                        add(RgbMatrix { _, _ ->
                            floatArrayOf(
                                o, 0f, 0f, 0f,
                                0f, o, 0f, 0f,
                                0f, 0f, o, 0f,
                                0f, 0f, 0f, 1f
                            )
                        })
                    }
                    // Apply clip transform (rotation, scale, position) — keyframe-animated or static
                    val hasKfScale = clip.keyframes.any {
                        it.property == KeyframeProperty.SCALE_X || it.property == KeyframeProperty.SCALE_Y
                    }
                    val hasKfRotation = clip.keyframes.any { it.property == KeyframeProperty.ROTATION }
                    val hasKfPosition = clip.keyframes.any {
                        it.property == KeyframeProperty.POSITION_X || it.property == KeyframeProperty.POSITION_Y
                    }
                    val needsStaticTransform = clip.rotation != 0f || clip.scaleX != 1f || clip.scaleY != 1f || clip.positionX != 0f || clip.positionY != 0f
                    if (hasKfScale || hasKfRotation || hasKfPosition) {
                        // Per-frame animated transform via MatrixTransformation
                        val kfs = clip.keyframes
                        val staticSx = clip.scaleX; val staticSy = clip.scaleY
                        val staticRot = clip.rotation
                        val staticPx = clip.positionX; val staticPy = clip.positionY
                        add(MatrixTransformation { presentationTimeUs ->
                            val timeMs = presentationTimeUs / 1000L
                            val sx = KeyframeEngine.getValueAt(kfs, KeyframeProperty.SCALE_X, timeMs) ?: staticSx
                            val sy = KeyframeEngine.getValueAt(kfs, KeyframeProperty.SCALE_Y, timeMs) ?: staticSy
                            val rot = KeyframeEngine.getValueAt(kfs, KeyframeProperty.ROTATION, timeMs) ?: staticRot
                            val px = KeyframeEngine.getValueAt(kfs, KeyframeProperty.POSITION_X, timeMs) ?: staticPx
                            val py = KeyframeEngine.getValueAt(kfs, KeyframeProperty.POSITION_Y, timeMs) ?: staticPy
                            android.graphics.Matrix().apply {
                                postScale(sx, sy)
                                postRotate(rot)
                                postTranslate(px, -py)
                            }
                        })
                    } else if (needsStaticTransform) {
                        // Static transform
                        val m = android.graphics.Matrix().apply {
                            postScale(clip.scaleX, clip.scaleY)
                            postRotate(clip.rotation)
                            postTranslate(clip.positionX, -clip.positionY)
                        }
                        add(MatrixTransformation { m })
                    }
                    if (clip.speed != 1.0f) {
                        add(SpeedChangeEffect(clip.speed))
                    }
                    // Text overlays that overlap this clip's timeline range
                    val clipStart = clip.timelineStartMs
                    val clipEnd = clip.timelineEndMs
                    val overlapping = textOverlays.filter { overlay ->
                        overlay.startTimeMs < clipEnd && overlay.endTimeMs > clipStart
                    }
                    if (overlapping.isNotEmpty()) {
                        val overlayList = overlapping.map { overlay ->
                            val relStart = (overlay.startTimeMs - clipStart).coerceAtLeast(0L)
                            val relEnd = (overlay.endTimeMs - clipStart).coerceAtMost(clip.durationMs)
                            ExportTextOverlay(overlay, relStart, relEnd)
                        }
                        @Suppress("UNCHECKED_CAST")
                        val typed = overlayList as List<TextureOverlay>
                        add(OverlayEffect(com.google.common.collect.ImmutableList.copyOf(typed)))
                    }
                    add(Presentation.createForWidthAndHeight(
                        targetW, targetH, Presentation.LAYOUT_SCALE_TO_FIT
                    ))
                }

                val audioProcessors = buildList<AudioProcessor> {
                    val hasKfVolume = clip.keyframes.any { it.property == KeyframeProperty.VOLUME }
                    val needsVolume = clip.volume != 1.0f
                    val needsFade = clip.fadeInMs > 0L || clip.fadeOutMs > 0L
                    if (hasKfVolume || needsVolume || needsFade) {
                        add(VolumeAudioProcessor(
                            volume = clip.volume,
                            fadeInMs = clip.fadeInMs,
                            fadeOutMs = clip.fadeOutMs,
                            clipDurationMs = clip.durationMs,
                            keyframes = if (hasKfVolume) clip.keyframes else emptyList()
                        ))
                    }
                }

                EditedMediaItem.Builder(mediaItem)
                    .setEffects(Effects(audioProcessors, videoEffects))
                    .build()
            }

            val videoSequence = EditedMediaItemSequence.Builder(editedItems).build()

            // Build audio track sequence (background music, voiceovers, etc.)
            val audioTrack = tracks.firstOrNull { it.type == TrackType.AUDIO }
            val sequences = buildList {
                add(videoSequence)
                if (audioTrack != null && audioTrack.clips.isNotEmpty()) {
                    val audioItems = audioTrack.clips.map { clip ->
                        val mediaItem = MediaItem.Builder()
                            .setUri(clip.sourceUri)
                            .setClippingConfiguration(
                                MediaItem.ClippingConfiguration.Builder()
                                    .setStartPositionMs(clip.trimStartMs)
                                    .setEndPositionMs(clip.trimEndMs)
                                    .build()
                            )
                            .build()
                        val processors = buildList<AudioProcessor> {
                            val hasKfVol = clip.keyframes.any { it.property == KeyframeProperty.VOLUME }
                            val needsVolume = clip.volume != 1.0f
                            val needsFade = clip.fadeInMs > 0L || clip.fadeOutMs > 0L
                            if (hasKfVol || needsVolume || needsFade) {
                                add(VolumeAudioProcessor(
                                    volume = clip.volume,
                                    fadeInMs = clip.fadeInMs,
                                    fadeOutMs = clip.fadeOutMs,
                                    clipDurationMs = clip.durationMs,
                                    keyframes = if (hasKfVol) clip.keyframes else emptyList()
                                ))
                            }
                        }
                        EditedMediaItem.Builder(mediaItem)
                            .setEffects(Effects(processors, emptyList()))
                            .setRemoveVideo(true)
                            .build()
                    }
                    add(EditedMediaItemSequence.Builder(audioItems).build())
                }
            }

            val composition = Composition.Builder(sequences)
                .setTransmuxAudio(audioTrack == null || audioTrack.clips.isEmpty())
                .build()

            val mimeType = when (config.codec) {
                VideoCodec.HEVC -> MimeTypes.VIDEO_H265
                VideoCodec.H264 -> MimeTypes.VIDEO_H264
            }

            // Transformer.start() requires a Looper — must run on Main thread
            withContext(Dispatchers.Main) {
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(mimeType)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(
                        DefaultEncoderFactory.Builder(context)
                            .setRequestedVideoEncoderSettings(
                                VideoEncoderSettings.Builder()
                                    .setBitrate(config.videoBitrate)
                                    .build()
                            )
                            .build()
                    )
                    .build()

                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        _exportState.value = ExportState.COMPLETE
                        _exportProgress.value = 1f
                        onComplete()
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        Log.e(TAG, "Export failed", exportException)
                        _exportState.value = ExportState.ERROR
                        _exportProgress.value = 0f
                        onError(exportException)
                    }
                }

                transformer.addListener(listener)
                activeTransformer = transformer
                transformer.start(composition, outputFile.absolutePath)

                val holder = ProgressHolder()
                while (_exportState.value == ExportState.EXPORTING) {
                    val state = transformer.getProgress(holder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        _exportProgress.value = holder.progress / 100f
                        onProgress(holder.progress / 100f)
                    }
                    delay(250)
                }
                activeTransformer = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Export setup failed", e)
            _exportState.value = ExportState.ERROR
            _exportProgress.value = 0f
            activeTransformer = null
            onError(e)
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun cancelExport() {
        if (_exportState.value != ExportState.EXPORTING) return
        Log.d(TAG, "Cancelling export")
        _exportState.value = ExportState.CANCELLED
        _exportProgress.value = 0f
        activeTransformer?.cancel()
        activeTransformer = null
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun buildVideoEffect(effect: Effect): androidx.media3.common.Effect? {
        return when (effect.type) {
            EffectType.BRIGHTNESS -> {
                val value = (effect.params["value"] ?: 0f).coerceIn(-1f, 1f)
                RgbMatrix { _, _ ->
                    val b = value
                    floatArrayOf(
                        1f, 0f, 0f, b,
                        0f, 1f, 0f, b,
                        0f, 0f, 1f, b,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.CONTRAST -> {
                val value = (effect.params["value"] ?: 1f).coerceIn(0f, 2f)
                Contrast(value - 1f)
            }
            EffectType.SATURATION -> {
                val value = (effect.params["value"] ?: 1f).coerceIn(0f, 3f)
                RgbMatrix { presentationTimeUs, useHdr ->
                    val s = value
                    val sr = (1 - s) * 0.2126f
                    val sg = (1 - s) * 0.7152f
                    val sb = (1 - s) * 0.0722f
                    floatArrayOf(
                        sr + s, sg, sb, 0f,
                        sr, sg + s, sb, 0f,
                        sr, sg, sb + s, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.GRAYSCALE -> {
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        0.2126f, 0.7152f, 0.0722f, 0f,
                        0.2126f, 0.7152f, 0.0722f, 0f,
                        0.2126f, 0.7152f, 0.0722f, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.SEPIA -> {
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        0.393f, 0.769f, 0.189f, 0f,
                        0.349f, 0.686f, 0.168f, 0f,
                        0.272f, 0.534f, 0.131f, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.INVERT -> {
                // Row-major 4x4: out.R = row0 dot [R,G,B,A]
                // Invert: out.rgb = 1 - in.rgb, using alpha (=1) as offset
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        -1f, 0f, 0f, 1f,
                        0f, -1f, 0f, 1f,
                        0f, 0f, -1f, 1f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.TEMPERATURE -> {
                val value = (effect.params["value"] ?: 0f).coerceIn(-5f, 5f)
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        1f + value * 0.1f, 0f, 0f, 0f,
                        0f, 1f, 0f, 0f,
                        0f, 0f, 1f - value * 0.1f, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.TINT -> {
                val value = (effect.params["value"] ?: 0f).coerceIn(-1f, 1f)
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        1f, 0f, 0f, 0f,
                        0f, 1f + value * 0.1f, 0f, 0f,
                        0f, 0f, 1f, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.EXPOSURE -> {
                // Approximate exposure: multiply all channels by 2^value
                val value = (effect.params["value"] ?: 0f).coerceIn(-2f, 2f)
                val mul = Math.pow(2.0, value.toDouble()).toFloat()
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        mul, 0f, 0f, 0f,
                        0f, mul, 0f, 0f,
                        0f, 0f, mul, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.GAMMA -> {
                // Gamma approximation: use linear scale (true gamma needs pow per-pixel)
                val value = (effect.params["value"] ?: 1f).coerceIn(0.2f, 5f)
                val inv = 1f / value
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        inv, 0f, 0f, 0f,
                        0f, inv, 0f, 0f,
                        0f, 0f, inv, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.HIGHLIGHTS -> {
                // Boost/reduce bright areas: scale toward white
                val value = (effect.params["value"] ?: 0f).coerceIn(-1f, 1f)
                val scale = 1f + value * 0.3f
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        scale, 0f, 0f, 0f,
                        0f, scale, 0f, 0f,
                        0f, 0f, scale, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.SHADOWS -> {
                // Lift/crush shadow areas: offset toward black
                val value = (effect.params["value"] ?: 0f).coerceIn(-1f, 1f)
                val offset = value * 0.15f
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        1f, 0f, 0f, offset,
                        0f, 1f, 0f, offset,
                        0f, 0f, 1f, offset,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.VIBRANCE -> {
                // Selective saturation: boost less-saturated colors more
                val value = (effect.params["value"] ?: 0f).coerceIn(-1f, 1f)
                val s = 1f + value * 0.5f
                val sr = (1 - s) * 0.2126f
                val sg = (1 - s) * 0.7152f
                val sb = (1 - s) * 0.0722f
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        sr + s, sg, sb, 0f,
                        sr, sg + s, sb, 0f,
                        sr, sg, sb + s, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.POSTERIZE -> {
                // Approximate posterize by reducing contrast then boosting
                val levels = (effect.params["levels"] ?: 6f).coerceIn(2f, 16f)
                val scale = levels / 8f
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        scale, 0f, 0f, (1f - scale) * 0.5f,
                        0f, scale, 0f, (1f - scale) * 0.5f,
                        0f, 0f, scale, (1f - scale) * 0.5f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.COOL_TONE -> {
                val intensity = (effect.params["intensity"] ?: 0.5f).coerceIn(0f, 1f)
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        1f - intensity * 0.1f, 0f, 0f, 0f,
                        0f, 1f, 0f, 0f,
                        0f, 0f, 1f + intensity * 0.15f, intensity * 0.02f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.WARM_TONE -> {
                val intensity = (effect.params["intensity"] ?: 0.5f).coerceIn(0f, 1f)
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        1f + intensity * 0.15f, 0f, 0f, intensity * 0.02f,
                        0f, 1f + intensity * 0.05f, 0f, 0f,
                        0f, 0f, 1f - intensity * 0.1f, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.CYBERPUNK -> {
                // Teal shadows + magenta highlights
                val intensity = (effect.params["intensity"] ?: 0.7f).coerceIn(0f, 1f)
                val s = 1f + intensity * 0.3f
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        s, 0f, 0f, intensity * 0.05f,
                        0f, 1f - intensity * 0.1f, 0f, -intensity * 0.02f,
                        0f, 0f, s, intensity * 0.08f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.NOIR -> {
                // High contrast desaturated with slight warm tint
                val intensity = (effect.params["intensity"] ?: 0.7f).coerceIn(0f, 1f)
                val gray = intensity
                val tint = intensity * 0.03f
                val lr = 0.2126f * gray + (1f - gray)
                val lg = 0.7152f * gray
                val lb = 0.0722f * gray
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        lr, lg, lb, tint,
                        0.2126f * gray, 0.7152f * gray + (1f - gray), 0.0722f * gray, 0f,
                        0.2126f * gray, 0.7152f * gray, 0.0722f * gray + (1f - gray), -tint,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.VINTAGE -> {
                // Faded warm look: reduced contrast + sepia blend
                val intensity = (effect.params["intensity"] ?: 0.7f).coerceIn(0f, 1f)
                val i = intensity
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        1f - i * 0.3f + i * 0.393f * 0.5f, i * 0.769f * 0.5f, i * 0.189f * 0.5f, i * 0.03f,
                        i * 0.349f * 0.5f, 1f - i * 0.2f + i * 0.686f * 0.5f, i * 0.168f * 0.5f, i * 0.01f,
                        i * 0.272f * 0.5f, i * 0.534f * 0.5f, 1f - i * 0.4f + i * 0.131f * 0.5f, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            EffectType.MIRROR -> {
                ScaleAndRotateTransformation.Builder()
                    .setScale(-1f, 1f)
                    .build()
            }
            // Effects requiring custom GL shaders — not yet implementable with RgbMatrix
            EffectType.VIGNETTE, EffectType.SHARPEN, EffectType.FILM_GRAIN,
            EffectType.GAUSSIAN_BLUR, EffectType.RADIAL_BLUR, EffectType.MOTION_BLUR,
            EffectType.TILT_SHIFT, EffectType.MOSAIC, EffectType.FISHEYE,
            EffectType.GLITCH, EffectType.PIXELATE, EffectType.WAVE,
            EffectType.CHROMATIC_ABERRATION, EffectType.CHROMA_KEY,
            EffectType.SPEED, EffectType.REVERSE -> null
        }
    }

    fun extractFrameToFile(uri: Uri, timeMs: Long): File? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(
                timeMs * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return null
            val dir = File(context.filesDir, "freeze_frames").also { it.mkdirs() }
            val file = File(dir, "freeze_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out ->
                frame.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            frame.recycle()
            file
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    fun clearThumbnailCache() {
        synchronized(cacheLock) {
            thumbnailCache.clear()
        }
    }

    fun resetExportState() {
        _exportState.value = ExportState.IDLE
        _exportProgress.value = 0f
    }

    fun release() {
        removePlayerListener()
        player?.release()
        player = null
        clearThumbnailCache()
    }
}

enum class ExportState { IDLE, EXPORTING, COMPLETE, ERROR, CANCELLED }

/**
 * Audio processor that applies volume scaling and fade in/out envelope.
 * Operates on 16-bit PCM audio samples.
 */
@UnstableApi
private class VolumeAudioProcessor(
    private val volume: Float,
    private val fadeInMs: Long,
    private val fadeOutMs: Long,
    private val clipDurationMs: Long,
    private val keyframes: List<com.novacut.editor.model.Keyframe> = emptyList()
) : BaseAudioProcessor() {

    private var processedFrames: Long = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.sampleRate == 0 ||
            inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        val sampleRate = inputAudioFormat.sampleRate
        val channelCount = inputAudioFormat.channelCount

        while (inputBuffer.hasRemaining()) {
            val sample = inputBuffer.short
            val frameIndex = processedFrames / channelCount
            val timeMs = frameIndex * 1000L / sampleRate

            // Use keyframe volume if available, otherwise static volume
            var gain = if (keyframes.isNotEmpty()) {
                KeyframeEngine.getValueAt(
                    keyframes, com.novacut.editor.model.KeyframeProperty.VOLUME, timeMs
                ) ?: volume
            } else {
                volume
            }

            // Fade in envelope
            if (fadeInMs > 0 && timeMs < fadeInMs) {
                gain *= timeMs.toFloat() / fadeInMs
            }

            // Fade out envelope
            if (fadeOutMs > 0 && timeMs > clipDurationMs - fadeOutMs) {
                val remaining = (clipDurationMs - timeMs).coerceAtLeast(0L)
                gain *= remaining.toFloat() / fadeOutMs
            }

            val scaled = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            outputBuffer.putShort(scaled.toShort())
            processedFrames++
        }

        outputBuffer.flip()
    }

    override fun onFlush() {
        super.onFlush()
        processedFrames = 0L
    }

    override fun onReset() {
        super.onReset()
        processedFrames = 0L
    }
}

/**
 * Text overlay that renders within a specific time range during export.
 * Converts model TextOverlay properties to Media3 SpannableString styling.
 */
@UnstableApi
private class ExportTextOverlay(
    private val overlay: com.novacut.editor.model.TextOverlay,
    private val relStartMs: Long,
    private val relEndMs: Long
) : androidx.media3.effect.TextOverlay() {

    private val animDurationMs = 500L

    override fun getText(presentationTimeUs: Long): SpannableString {
        val timeMs = presentationTimeUs / 1000L
        if (timeMs < relStartMs || timeMs > relEndMs) {
            return SpannableString("")
        }
        val fullText = overlay.text
        // Typewriter animation: reveal characters progressively
        val displayText = if (overlay.animationIn == com.novacut.editor.model.TextAnimation.TYPEWRITER) {
            val elapsed = timeMs - relStartMs
            val charCount = ((elapsed.toFloat() / animDurationMs) * fullText.length)
                .toInt().coerceIn(0, fullText.length)
            fullText.substring(0, charCount)
        } else {
            fullText
        }
        val text = SpannableString(displayText)
        if (displayText.isNotEmpty()) {
            text.setSpan(
                ForegroundColorSpan(overlay.color.toInt()),
                0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            text.setSpan(
                AbsoluteSizeSpan(overlay.fontSize.toInt(), true),
                0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            val style = when {
                overlay.bold && overlay.italic -> Typeface.BOLD_ITALIC
                overlay.bold -> Typeface.BOLD
                overlay.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            if (style != Typeface.NORMAL) {
                text.setSpan(StyleSpan(style), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return text
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val timeMs = presentationTimeUs / 1000L
        if (timeMs < relStartMs || timeMs > relEndMs) {
            return OverlaySettings.Builder().setAlphaScale(0f).build()
        }

        val baseAnchorX = overlay.positionX * 2f - 1f
        val baseAnchorY = -(overlay.positionY * 2f - 1f)

        // Compute animation-in progress (0..1)
        val inProgress = if (overlay.animationIn != com.novacut.editor.model.TextAnimation.NONE) {
            ((timeMs - relStartMs).toFloat() / animDurationMs).coerceIn(0f, 1f)
        } else 1f

        // Compute animation-out progress (1..0)
        val outProgress = if (overlay.animationOut != com.novacut.editor.model.TextAnimation.NONE) {
            ((relEndMs - timeMs).toFloat() / animDurationMs).coerceIn(0f, 1f)
        } else 1f

        // Apply animation effects
        var alpha = 1f
        var offsetX = 0f
        var offsetY = 0f
        var scale = 1f
        var rotation = 0f

        // Animation in
        when (overlay.animationIn) {
            com.novacut.editor.model.TextAnimation.FADE -> alpha *= easeOut(inProgress)
            com.novacut.editor.model.TextAnimation.SLIDE_UP -> offsetY -= (1f - easeOut(inProgress)) * 0.3f
            com.novacut.editor.model.TextAnimation.SLIDE_DOWN -> offsetY += (1f - easeOut(inProgress)) * 0.3f
            com.novacut.editor.model.TextAnimation.SLIDE_LEFT -> offsetX -= (1f - easeOut(inProgress)) * 0.3f
            com.novacut.editor.model.TextAnimation.SLIDE_RIGHT -> offsetX += (1f - easeOut(inProgress)) * 0.3f
            com.novacut.editor.model.TextAnimation.SCALE -> scale *= easeOut(inProgress)
            com.novacut.editor.model.TextAnimation.SPIN -> rotation += (1f - easeOut(inProgress)) * 360f
            com.novacut.editor.model.TextAnimation.BOUNCE -> {
                val t = easeOut(inProgress)
                offsetY -= (1f - bounceEase(t)) * 0.3f
            }
            com.novacut.editor.model.TextAnimation.TYPEWRITER -> { /* handled in getText() */ }
            com.novacut.editor.model.TextAnimation.NONE -> { }
        }

        // Animation out
        when (overlay.animationOut) {
            com.novacut.editor.model.TextAnimation.FADE -> alpha *= easeOut(outProgress)
            com.novacut.editor.model.TextAnimation.SLIDE_UP -> offsetY += (1f - easeOut(outProgress)) * 0.3f
            com.novacut.editor.model.TextAnimation.SLIDE_DOWN -> offsetY -= (1f - easeOut(outProgress)) * 0.3f
            com.novacut.editor.model.TextAnimation.SLIDE_LEFT -> offsetX += (1f - easeOut(outProgress)) * 0.3f
            com.novacut.editor.model.TextAnimation.SLIDE_RIGHT -> offsetX -= (1f - easeOut(outProgress)) * 0.3f
            com.novacut.editor.model.TextAnimation.SCALE -> scale *= easeOut(outProgress)
            com.novacut.editor.model.TextAnimation.SPIN -> rotation -= (1f - easeOut(outProgress)) * 360f
            com.novacut.editor.model.TextAnimation.BOUNCE -> {
                val t = easeOut(outProgress)
                offsetY += (1f - bounceEase(t)) * 0.3f
            }
            com.novacut.editor.model.TextAnimation.TYPEWRITER -> alpha *= outProgress
            com.novacut.editor.model.TextAnimation.NONE -> { }
        }

        return OverlaySettings.Builder()
            .setOverlayFrameAnchor(baseAnchorX + offsetX, baseAnchorY + offsetY)
            .setBackgroundFrameAnchor(baseAnchorX + offsetX, baseAnchorY + offsetY)
            .setAlphaScale(alpha)
            .setScale(scale, scale)
            .setRotationDegrees(rotation)
            .build()
    }

    private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)

    private fun bounceEase(t: Float): Float {
        return when {
            t < 0.3636f -> 7.5625f * t * t
            t < 0.7273f -> 7.5625f * (t - 0.5455f) * (t - 0.5455f) + 0.75f
            t < 0.9091f -> 7.5625f * (t - 0.8182f) * (t - 0.8182f) + 0.9375f
            else -> 7.5625f * (t - 0.9545f) * (t - 0.9545f) + 0.984375f
        }
    }
}
