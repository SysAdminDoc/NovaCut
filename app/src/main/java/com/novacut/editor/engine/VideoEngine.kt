package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
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
    private val thumbnailCache = LinkedHashMap<String, Bitmap>(100, 0.75f, true)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress

    private val _exportState = MutableStateFlow(ExportState.IDLE)
    val exportState: StateFlow<ExportState> = _exportState

    fun getPlayer(): ExoPlayer {
        if (player == null) {
            player = ExoPlayer.Builder(context).build()
        }
        return player!!
    }

    fun prepareClip(uri: Uri) {
        val p = getPlayer()
        p.setMediaItem(MediaItem.fromUri(uri))
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
            val rate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            rate?.toFloatOrNull()?.toInt() ?: 30
        } catch (e: Exception) {
            30
        } finally {
            retriever.release()
        }
    }

    fun extractThumbnail(uri: Uri, timeUs: Long, width: Int = 160, height: Int = 90): Bitmap? {
        val key = "${uri}_${timeUs}_${width}x${height}"
        thumbnailCache[key]?.let { return it }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            frame?.let {
                val scaled = Bitmap.createScaledBitmap(it, width, height, true)
                if (scaled !== it) it.recycle()
                synchronized(thumbnailCache) {
                    if (thumbnailCache.size > 500) {
                        val oldest = thumbnailCache.entries.first()
                        oldest.value.recycle()
                        thumbnailCache.remove(oldest.key)
                    }
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
    ) = withContext(Dispatchers.IO) {
        _exportState.value = ExportState.EXPORTING
        _exportProgress.value = 0f

        try {
            val videoTrack = tracks.firstOrNull { it.type == TrackType.VIDEO }
            if (videoTrack == null || videoTrack.clips.isEmpty()) {
                throw IllegalStateException("No video clips to export")
            }

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

                val videoEffects = mutableListOf<androidx.media3.common.Effect>()
                val audioProcessors = mutableListOf<androidx.media3.common.audio.AudioProcessor>()

                for (effect in clip.effects) {
                    if (!effect.enabled) continue
                    buildVideoEffect(effect)?.let { videoEffects.add(it) }
                }

                if (clip.speed != 1.0f) {
                    videoEffects.add(SpeedChangeEffect(clip.speed))
                }

                EditedMediaItem.Builder(mediaItem)
                    .setEffects(Effects(audioProcessors, videoEffects))
                    .build()
            }

            val sequence = EditedMediaItemSequence(editedItems)
            val composition = Composition.Builder(listOf(sequence)).build()

            val mimeType = when (config.codec) {
                VideoCodec.HEVC -> MimeTypes.VIDEO_H265
                VideoCodec.H264 -> MimeTypes.VIDEO_H264
            }

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(mimeType)
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

            while (_exportState.value == ExportState.EXPORTING) {
                val progress = transformer.getProgress(ProgressHolder())
                if (progress != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    val holder = ProgressHolder()
                    transformer.getProgress(holder)
                    _exportProgress.value = holder.progress / 100f
                    onProgress(holder.progress / 100f)
                }
                delay(100)
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
                RgbMatrix { _, _ ->
                    floatArrayOf(
                        -1f, 0f, 0f, 0f,
                        0f, -1f, 0f, 0f,
                        0f, 0f, -1f, 0f,
                        1f, 1f, 1f, 1f
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
        synchronized(thumbnailCache) {
            thumbnailCache.values.forEach { it.recycle() }
            thumbnailCache.clear()
        }
    }

    fun release() {
        scope.cancel()
        player?.release()
        player = null
        clearThumbnailCache()
    }
}

enum class ExportState { IDLE, EXPORTING, COMPLETE, ERROR }
