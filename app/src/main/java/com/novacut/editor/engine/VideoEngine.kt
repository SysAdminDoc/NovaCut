package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.*
import com.novacut.editor.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

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
            return
        }
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
        player?.seekTo(positionMs)
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
                ?: retriever.extractMetadata(24) // METADATA_KEY_VIDEO_FRAME_COUNT not always available
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

            val (targetW, targetH) = config.resolution.forAspect(AspectRatio.RATIO_16_9)

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
                    // Apply keyframe opacity if defined
                    if (clip.keyframes.any { it.property == KeyframeProperty.OPACITY }) {
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
                    }
                    if (clip.speed != 1.0f) {
                        add(SpeedChangeEffect(clip.speed))
                    }
                    add(Presentation.createForWidthAndHeight(
                        targetW, targetH, Presentation.LAYOUT_SCALE_TO_FIT
                    ))
                }

                EditedMediaItem.Builder(mediaItem)
                    .setEffects(Effects(emptyList(), videoEffects))
                    .build()
            }

            val sequence = EditedMediaItemSequence(editedItems)
            val composition = Composition.Builder(listOf(sequence)).build()

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
                        _exportState.value = ExportState.ERROR
                        onError(exportException)
                    }
                }

                transformer.addListener(listener)
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
            }
        } catch (e: Exception) {
            _exportState.value = ExportState.ERROR
            onError(e)
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun buildVideoEffect(effect: Effect): androidx.media3.common.Effect? {
        return when (effect.type) {
            EffectType.BRIGHTNESS -> {
                val value = effect.params["value"] ?: 0f
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
                val value = effect.params["value"] ?: 1f
                Contrast(value - 1f)
            }
            EffectType.SATURATION -> {
                val value = effect.params["value"] ?: 1f
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
                val value = effect.params["value"] ?: 0f
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        1f + value * 0.1f, 0f, 0f, 0f,
                        0f, 1f, 0f, 0f,
                        0f, 0f, 1f - value * 0.1f, 0f,
                        0f, 0f, 0f, 1f
                    )
                }
            }
            else -> null
        }
    }

    fun clearThumbnailCache() {
        synchronized(cacheLock) {
            thumbnailCache.values.forEach { it.recycle() }
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

enum class ExportState { IDLE, EXPORTING, COMPLETE, ERROR }
