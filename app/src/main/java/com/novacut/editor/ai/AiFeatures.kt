package com.novacut.editor.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.novacut.editor.engine.segmentation.SegmentationEngine
import com.novacut.editor.engine.whisper.WhisperEngine
import com.novacut.editor.model.TextOverlay
import com.novacut.editor.model.TextAlignment
import com.novacut.editor.model.TextAnimation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * AI-powered features for NovaCut.
 * Uses on-device analysis algorithms for real-time video/audio intelligence.
 *
 * Implemented:
 * - Scene detection (frame difference analysis)
 * - Auto color correction (histogram analysis)
 * - Video stabilization (motion vector estimation)
 * - Audio denoise (noise floor analysis + gating)
 * - Auto captions (audio energy segmentation)
 * - Background removal (luminance/chroma keying)
 * - Motion tracking (template matching)
 * - Smart crop (visual weight analysis)
 */
@Singleton
class AiFeatures @Inject constructor(
    @ApplicationContext private val context: Context,
    val whisperEngine: WhisperEngine,
    val segmentationEngine: SegmentationEngine
) {
    // ---- Auto Captions ----

    /**
     * Generates timed caption segments. Uses Whisper ONNX for real speech-to-text
     * when the model is downloaded, otherwise falls back to audio energy segmentation.
     */
    suspend fun generateAutoCaptions(
        videoUri: Uri,
        languageCode: String = "en",
        onProgress: (Float) -> Unit = {}
    ): List<CaptionEntry> {
        // Use Whisper when model is available
        if (whisperEngine.isReady()) {
            return generateWhisperCaptions(videoUri, onProgress)
        }
        // Fallback to energy segmentation
        return generateEnergyCaptions(videoUri, onProgress)
    }

    private suspend fun generateWhisperCaptions(
        videoUri: Uri,
        onProgress: (Float) -> Unit
    ): List<CaptionEntry> = withContext(Dispatchers.IO) {
        try {
            val segments = whisperEngine.transcribe(videoUri, onProgress)
            segments.map { seg ->
                CaptionEntry(
                    startMs = seg.startMs,
                    endMs = seg.endMs,
                    text = seg.text,
                    confidence = 0.95f
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun generateEnergyCaptions(
        videoUri: Uri,
        onProgress: (Float) -> Unit
    ): List<CaptionEntry> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, videoUri, null)

            var audioIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val tf = extractor.getTrackFormat(i)
                if (tf.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioIndex = i
                    format = tf
                    break
                }
            }
            if (audioIndex < 0 || format == null) return@withContext emptyList()

            extractor.selectTrack(audioIndex)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext emptyList()

            // Decode audio to PCM amplitudes
            val decoder = MediaCodec.createDecoderByType(mime)
            val amplitudeChunks = mutableListOf<FloatArray>()
            var totalSamples = 0

            try {
                decoder.configure(format, null, null, 0)
                decoder.start()
                val bufferInfo = MediaCodec.BufferInfo()
                var eos = false

                while (!eos) {
                    val inIdx = decoder.dequeueInputBuffer(10000)
                    if (inIdx >= 0) {
                        val buf = decoder.getInputBuffer(inIdx) ?: continue
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eos = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                    var outIdx = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                    while (outIdx >= 0) {
                        val outBuf = decoder.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val samples = ShortArray(shortBuf.remaining())
                            shortBuf.get(samples)
                            // Convert to mono float amplitude
                            val mono = FloatArray(samples.size / channels)
                            for (i in mono.indices) {
                                var sum = 0f
                                for (ch in 0 until channels) {
                                    val idx = i * channels + ch
                                    if (idx < samples.size) sum += abs(samples[idx].toFloat())
                                }
                                mono[i] = sum / channels / 32768f
                            }
                            amplitudeChunks.add(mono)
                            totalSamples += mono.size
                        }
                        decoder.releaseOutputBuffer(outIdx, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            eos = true; break
                        }
                        outIdx = decoder.dequeueOutputBuffer(bufferInfo, 0)
                    }
                }
            } finally {
                try { decoder.stop() } catch (_: Exception) {}
                decoder.release()
            }

            if (totalSamples == 0) return@withContext emptyList()

            // Flatten into single array
            val allSamples = FloatArray(totalSamples)
            var offset = 0
            for (chunk in amplitudeChunks) {
                System.arraycopy(chunk, 0, allSamples, offset, chunk.size)
                offset += chunk.size
            }

            // Compute RMS energy in 100ms windows
            val windowSamples = sampleRate / 10 // 100ms
            val windowCount = totalSamples / windowSamples
            val energy = FloatArray(windowCount)
            var maxEnergy = 0f
            for (w in 0 until windowCount) {
                var sum = 0.0
                val base = w * windowSamples
                for (s in 0 until windowSamples) {
                    val v = allSamples[base + s]
                    sum += v * v
                }
                energy[w] = sqrt(sum / windowSamples).toFloat()
                maxEnergy = max(maxEnergy, energy[w])
            }

            if (maxEnergy < 0.001f) return@withContext emptyList()

            // Normalize and find speech segments (above 15% threshold)
            val threshold = maxEnergy * 0.15f
            val captions = mutableListOf<CaptionEntry>()
            var segmentStart = -1
            var captionIndex = 1

            for (w in 0 until windowCount) {
                val isSpeech = energy[w] > threshold
                if (isSpeech && segmentStart < 0) {
                    segmentStart = w
                } else if (!isSpeech && segmentStart >= 0) {
                    val startMs = segmentStart * 100L
                    val endMs = w * 100L
                    if (endMs - startMs >= 300) { // Min 300ms segment
                        captions.add(CaptionEntry(
                            startMs = startMs,
                            endMs = endMs,
                            text = "[Speech segment $captionIndex]",
                            confidence = energy.slice(segmentStart until w).average().toFloat() / maxEnergy
                        ))
                        captionIndex++
                    }
                    segmentStart = -1
                }
            }
            // Close final segment
            if (segmentStart >= 0) {
                val startMs = segmentStart * 100L
                val endMs = windowCount * 100L
                if (endMs - startMs >= 300) {
                    captions.add(CaptionEntry(
                        startMs = startMs,
                        endMs = endMs,
                        text = "[Speech segment $captionIndex]",
                        confidence = energy.slice(segmentStart until windowCount).average().toFloat() / maxEnergy
                    ))
                }
            }

            captions
        } catch (_: Exception) {
            emptyList()
        } finally {
            extractor.release()
        }
    }

    /**
     * Convert caption entries to TextOverlay objects for the timeline.
     */
    fun captionsToOverlays(
        captions: List<CaptionEntry>,
        style: CaptionStyle = CaptionStyle()
    ): List<TextOverlay> {
        return captions.map { caption ->
            TextOverlay(
                text = caption.text,
                fontSize = style.fontSize,
                color = style.textColor,
                backgroundColor = style.backgroundColor,
                strokeColor = style.strokeColor,
                strokeWidth = style.strokeWidth,
                bold = style.bold,
                alignment = TextAlignment.CENTER,
                positionX = 0.5f,
                positionY = style.positionY,
                startTimeMs = caption.startMs,
                endTimeMs = caption.endMs,
                animationIn = TextAnimation.FADE,
                animationOut = TextAnimation.FADE
            )
        }
    }

    // ---- Auto Color Correction ----

    /**
     * Analyzes multiple frames from the video to compute optimal color corrections.
     * Uses histogram analysis across R/G/B channels to determine:
     * - Brightness offset (shift mid-tone toward 128)
     * - Contrast multiplier (expand histogram to fill 0-255 range)
     * - Saturation adjustment (based on chroma spread)
     * - Temperature bias (R/B channel imbalance)
     */
    suspend fun autoColorCorrect(
        videoUri: Uri
    ): ColorCorrection = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: return@withContext ColorCorrection()

            // Sample 5 frames evenly across the clip
            val sampleCount = 5
            val histR = IntArray(256)
            val histG = IntArray(256)
            val histB = IntArray(256)
            var totalPixels = 0L

            for (i in 0 until sampleCount) {
                val timeMs = durationMs * (i * 2 + 1) / (sampleCount * 2)
                val frame = retriever.getFrameAtTime(
                    timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: continue

                val scaled = Bitmap.createScaledBitmap(frame, 128, 72, true)
                if (scaled !== frame) frame.recycle()

                for (y in 0 until scaled.height) {
                    for (x in 0 until scaled.width) {
                        val pixel = scaled.getPixel(x, y)
                        histR[Color.red(pixel)]++
                        histG[Color.green(pixel)]++
                        histB[Color.blue(pixel)]++
                        totalPixels++
                    }
                }
                scaled.recycle()
            }

            if (totalPixels == 0L) return@withContext ColorCorrection()

            // Calculate channel statistics
            val avgR = channelAverage(histR, totalPixels)
            val avgG = channelAverage(histG, totalPixels)
            val avgB = channelAverage(histB, totalPixels)
            val overallAvg = (avgR + avgG + avgB) / 3f

            // Brightness: how far the average luminance is from mid-gray (128)
            val brightnessOffset = (128f - overallAvg) / 255f // -1..1

            // Contrast: find 1st and 99th percentile across all channels
            val low1 = percentile(histR, histG, histB, totalPixels, 0.01f)
            val high99 = percentile(histR, histG, histB, totalPixels, 0.99f)
            val range = (high99 - low1).coerceAtLeast(1f)
            val contrastMult = (220f / range).coerceIn(0.5f, 2.5f) // Target 220 of 256 range

            // Saturation: analyze chroma spread
            val chromaSpread = abs(avgR - avgG) + abs(avgG - avgB) + abs(avgR - avgB)
            val saturationAdj = if (chromaSpread < 15f) 1.2f // Undersaturated
                else if (chromaSpread > 80f) 0.85f // Oversaturated
                else 1f

            // Temperature: R/B imbalance
            val tempBias = ((avgR - avgB) / 255f).coerceIn(-1f, 1f)
            val tempCorrection = -tempBias * 0.3f // Counter the bias

            ColorCorrection(
                brightness = brightnessOffset.coerceIn(-0.5f, 0.5f),
                contrast = contrastMult,
                saturation = saturationAdj,
                temperature = tempCorrection,
                confidence = min(1f, totalPixels / 40000f)
            )
        } catch (_: Exception) {
            ColorCorrection()
        } finally {
            retriever.release()
        }
    }

    private fun channelAverage(hist: IntArray, total: Long): Float {
        var sum = 0L
        for (i in hist.indices) sum += i.toLong() * hist[i]
        return sum.toFloat() / total
    }

    private fun percentile(hR: IntArray, hG: IntArray, hB: IntArray, total: Long, p: Float): Float {
        val target = (total * 3 * p).toLong()
        var cumulative = 0L
        for (i in 0..255) {
            cumulative += hR[i] + hG[i] + hB[i]
            if (cumulative >= target) return i.toFloat()
        }
        return 255f
    }

    // ---- Video Stabilization ----

    /**
     * Analyzes consecutive frames to estimate camera motion (shake).
     * Computes motion vectors by comparing frame regions, then returns
     * counter-motion transform parameters to stabilize the video.
     *
     * Strategy: sample frames at ~10fps, compute XY offset between consecutive
     * frames using block matching on downscaled versions. Return smoothed
     * stabilization transforms.
     */
    suspend fun stabilizeVideo(
        videoUri: Uri
    ): StabilizationResult = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: return@withContext StabilizationResult()

            val intervalMs = 100L // 10fps analysis
            val motionX = mutableListOf<Float>()
            val motionY = mutableListOf<Float>()
            var prevFrame: Bitmap? = null
            var currentMs = 0L
            val analysisWidth = 64
            val analysisHeight = 36

            while (currentMs < durationMs && currentMs < 30000L) { // Cap at 30s analysis
                val frame = retriever.getFrameAtTime(
                    currentMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (frame != null) {
                    val scaled = Bitmap.createScaledBitmap(frame, analysisWidth, analysisHeight, true)
                    if (scaled !== frame) frame.recycle()

                    if (prevFrame != null) {
                        val (dx, dy) = estimateMotion(prevFrame, scaled)
                        motionX.add(dx)
                        motionY.add(dy)
                        prevFrame.recycle()
                    }
                    prevFrame = scaled
                }
                currentMs += intervalMs
            }
            prevFrame?.recycle()

            if (motionX.isEmpty()) return@withContext StabilizationResult()

            // Calculate shake magnitude (standard deviation of motion)
            val avgX = motionX.average().toFloat()
            val avgY = motionY.average().toFloat()
            val stdX = sqrt(motionX.map { (it - avgX) * (it - avgX) }.average()).toFloat()
            val stdY = sqrt(motionY.map { (it - avgY) * (it - avgY) }.average()).toFloat()
            val shakeMagnitude = sqrt(stdX * stdX + stdY * stdY)

            // Recommended zoom to compensate (more shake = more zoom needed)
            val zoomFactor = 1f + (shakeMagnitude * 3f).coerceIn(0.02f, 0.15f)

            // Generate smoothed counter-motion keyframes
            val smoothed = smoothMotion(motionX, motionY, windowSize = 5)

            StabilizationResult(
                shakeMagnitude = shakeMagnitude,
                recommendedZoom = zoomFactor,
                motionKeyframes = smoothed.mapIndexed { i, (sx, sy) ->
                    StabilizationKeyframe(
                        timestampMs = i * intervalMs,
                        offsetX = -sx * 0.5f, // Counter-motion at 50% strength
                        offsetY = -sy * 0.5f
                    )
                },
                confidence = min(1f, motionX.size / 50f)
            )
        } catch (_: Exception) {
            StabilizationResult()
        } finally {
            retriever.release()
        }
    }

    /**
     * Block-matching motion estimation between two downscaled frames.
     * Returns estimated XY pixel offset (normalized 0..1).
     */
    private fun estimateMotion(prev: Bitmap, curr: Bitmap): Pair<Float, Float> {
        val w = min(prev.width, curr.width)
        val h = min(prev.height, curr.height)
        if (w < 8 || h < 8) return 0f to 0f

        // Compare center region with shifted versions
        val blockSize = min(w, h) / 4
        val cx = w / 2
        val cy = h / 2
        val searchRange = 4

        var bestDx = 0
        var bestDy = 0
        var bestDiff = Long.MAX_VALUE

        for (dy in -searchRange..searchRange) {
            for (dx in -searchRange..searchRange) {
                var diff = 0L
                var count = 0
                for (by in -blockSize..blockSize step 2) {
                    for (bx in -blockSize..blockSize step 2) {
                        val px = cx + bx
                        val py = cy + by
                        val sx = px + dx
                        val sy = py + dy
                        if (px in 0 until w && py in 0 until h &&
                            sx in 0 until w && sy in 0 until h) {
                            val p1 = prev.getPixel(px, py)
                            val p2 = curr.getPixel(sx, sy)
                            diff += abs((p1 shr 16 and 0xFF) - (p2 shr 16 and 0xFF)) +
                                    abs((p1 shr 8 and 0xFF) - (p2 shr 8 and 0xFF)) +
                                    abs((p1 and 0xFF) - (p2 and 0xFF))
                            count++
                        }
                    }
                }
                if (count > 0) {
                    val avgDiff = diff / count
                    if (avgDiff < bestDiff) {
                        bestDiff = avgDiff
                        bestDx = dx
                        bestDy = dy
                    }
                }
            }
        }

        return bestDx.toFloat() / w to bestDy.toFloat() / h
    }

    /**
     * Smooth motion vectors with a moving average window.
     */
    private fun smoothMotion(
        mx: List<Float>, my: List<Float>, windowSize: Int
    ): List<Pair<Float, Float>> {
        val half = windowSize / 2
        return mx.indices.map { i ->
            val startIdx = max(0, i - half)
            val endIdx = min(mx.size, i + half + 1)
            val sx = mx.subList(startIdx, endIdx).average().toFloat()
            val sy = my.subList(startIdx, endIdx).average().toFloat()
            sx to sy
        }
    }

    // ---- Audio Denoise ----

    /**
     * Analyzes audio to detect noise characteristics.
     * Samples the first and last 500ms (typically ambient noise) to build a noise profile.
     * Returns a noise reduction profile with recommended settings.
     */
    suspend fun analyzeAudioNoise(
        videoUri: Uri
    ): NoiseProfile = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, videoUri, null)

            var audioIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val tf = extractor.getTrackFormat(i)
                if (tf.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioIndex = i; format = tf; break
                }
            }
            if (audioIndex < 0 || format == null) return@withContext NoiseProfile()

            extractor.selectTrack(audioIndex)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext NoiseProfile()

            // Decode full audio
            val decoder = MediaCodec.createDecoderByType(mime)
            val chunks = mutableListOf<ShortArray>()
            var totalSamples = 0

            try {
                decoder.configure(format, null, null, 0)
                decoder.start()
                val bufferInfo = MediaCodec.BufferInfo()
                var eos = false

                while (!eos) {
                    val inIdx = decoder.dequeueInputBuffer(10000)
                    if (inIdx >= 0) {
                        val buf = decoder.getInputBuffer(inIdx) ?: continue
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eos = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                    var outIdx = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                    while (outIdx >= 0) {
                        val outBuf = decoder.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val arr = ShortArray(shortBuf.remaining())
                            shortBuf.get(arr)
                            chunks.add(arr)
                            totalSamples += arr.size
                        }
                        decoder.releaseOutputBuffer(outIdx, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            eos = true; break
                        }
                        outIdx = decoder.dequeueOutputBuffer(bufferInfo, 0)
                    }
                }
            } finally {
                try { decoder.stop() } catch (_: Exception) {}
                decoder.release()
            }

            if (totalSamples == 0) return@withContext NoiseProfile()

            // Flatten
            val allSamples = ShortArray(totalSamples)
            var off = 0
            for (chunk in chunks) {
                System.arraycopy(chunk, 0, allSamples, off, chunk.size)
                off += chunk.size
            }

            val monoSamples = totalSamples / channels

            // Analyze first and last 500ms for noise floor
            val noiseSampleCount = min(sampleRate / 2, monoSamples / 4) // 500ms or 25% of clip
            var noiseRmsSum = 0.0
            var noiseCount = 0

            // First 500ms
            for (i in 0 until min(noiseSampleCount, monoSamples)) {
                val v = allSamples[i * channels].toFloat() / 32768f
                noiseRmsSum += v * v
                noiseCount++
            }
            // Last 500ms
            val lastStart = max(0, monoSamples - noiseSampleCount)
            for (i in lastStart until monoSamples) {
                val idx = i * channels
                if (idx < allSamples.size) {
                    val v = allSamples[idx].toFloat() / 32768f
                    noiseRmsSum += v * v
                    noiseCount++
                }
            }

            val noiseFloorRms = if (noiseCount > 0) sqrt(noiseRmsSum / noiseCount).toFloat() else 0f

            // Calculate overall signal RMS
            var signalRmsSum = 0.0
            for (i in 0 until min(monoSamples, sampleRate * 10)) { // Sample up to 10s
                val idx = i * channels
                if (idx < allSamples.size) {
                    val v = allSamples[idx].toFloat() / 32768f
                    signalRmsSum += v * v
                }
            }
            val signalRms = sqrt(signalRmsSum / min(monoSamples, sampleRate * 10)).toFloat()

            val snrDb = if (noiseFloorRms > 0.0001f) {
                20f * kotlin.math.log10(signalRms / noiseFloorRms)
            } else 60f // Very clean audio

            // Determine reduction strength based on SNR
            val reductionStrength = when {
                snrDb < 10f -> 0.8f  // Very noisy
                snrDb < 20f -> 0.6f  // Noisy
                snrDb < 30f -> 0.4f  // Moderate noise
                snrDb < 40f -> 0.2f  // Light noise
                else -> 0.1f         // Clean
            }

            NoiseProfile(
                noiseFloorDb = 20f * kotlin.math.log10(max(noiseFloorRms, 0.0001f)),
                signalToNoiseDb = snrDb,
                recommendedReduction = reductionStrength,
                noiseGateThreshold = noiseFloorRms * 1.5f,
                confidence = if (noiseCount > sampleRate / 4) 0.9f else 0.5f
            )
        } catch (_: Exception) {
            NoiseProfile()
        } finally {
            extractor.release()
        }
    }

    // ---- Background Removal ----

    /**
     * Analyzes a frame to determine the dominant background color and returns
     * chroma key parameters to remove it. Samples edge regions (top/bottom/left/right
     * border strips) to identify the background color.
     *
     * @return BackgroundAnalysis with detected bg color and recommended chroma key settings
     */
    suspend fun analyzeBackground(
        videoUri: Uri
    ): BackgroundAnalysis = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: return@withContext BackgroundAnalysis()

            // Sample frame from middle of clip
            val frame = retriever.getFrameAtTime(
                (durationMs / 2) * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return@withContext BackgroundAnalysis()

            val scaled = Bitmap.createScaledBitmap(frame, 128, 72, true)
            if (scaled !== frame) frame.recycle()
            val w = scaled.width
            val h = scaled.height

            // Sample edge pixels (15% border strips)
            val borderW = max(1, (w * 0.15f).toInt())
            val borderH = max(1, (h * 0.15f).toInt())
            var sumR = 0L; var sumG = 0L; var sumB = 0L; var count = 0L

            for (y in 0 until h) {
                for (x in 0 until w) {
                    if (x < borderW || x >= w - borderW || y < borderH || y >= h - borderH) {
                        val p = scaled.getPixel(x, y)
                        sumR += Color.red(p)
                        sumG += Color.green(p)
                        sumB += Color.blue(p)
                        count++
                    }
                }
            }
            scaled.recycle()

            if (count == 0L) return@withContext BackgroundAnalysis()

            val avgR = (sumR / count).toInt()
            val avgG = (sumG / count).toInt()
            val avgB = (sumB / count).toInt()

            // Detect if background is a solid-ish color (low variance)
            val bgColor = Color.rgb(avgR, avgG, avgB)

            // Determine if green screen, blue screen, or general background
            val isGreenScreen = avgG > avgR * 1.3f && avgG > avgB * 1.3f && avgG > 80
            val isBlueScreen = avgB > avgR * 1.3f && avgB > avgG * 1.1f && avgB > 80

            val similarity = when {
                isGreenScreen -> 0.35f
                isBlueScreen -> 0.35f
                else -> 0.45f // General background needs wider tolerance
            }

            BackgroundAnalysis(
                backgroundColor = bgColor,
                isGreenScreen = isGreenScreen,
                isBlueScreen = isBlueScreen,
                recommendedSimilarity = similarity,
                recommendedSmoothness = 0.12f,
                recommendedSpill = 0.15f,
                confidence = if (isGreenScreen || isBlueScreen) 0.9f else 0.5f
            )
        } catch (_: Exception) {
            BackgroundAnalysis()
        } finally {
            retriever.release()
        }
    }

    // ---- Scene Detection ----

    /**
     * Analyzes video to detect scene changes (cuts, transitions).
     * Uses frame difference analysis with adaptive thresholding.
     */
    suspend fun detectScenes(
        videoUri: Uri,
        sensitivity: Float = 0.5f
    ): List<SceneChange> = withContext(Dispatchers.IO) {
        val scenes = mutableListOf<SceneChange>()
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: return@withContext emptyList()

            val intervalMs = 500L
            var previousFrame: Bitmap? = null
            var currentMs = 0L

            while (currentMs < durationMs) {
                val frame = retriever.getFrameAtTime(
                    currentMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (frame != null && previousFrame != null) {
                    val difference = calculateFrameDifference(previousFrame, frame)
                    val threshold = 0.3f + (1f - sensitivity) * 0.5f
                    if (difference > threshold) {
                        scenes.add(SceneChange(
                            timestampMs = currentMs,
                            confidence = difference,
                            type = if (difference > 0.8f) SceneChangeType.HARD_CUT
                                   else SceneChangeType.TRANSITION
                        ))
                    }
                    previousFrame.recycle()
                }
                previousFrame = frame
                currentMs += intervalMs
            }
            previousFrame?.recycle()
        } finally {
            retriever.release()
        }

        scenes
    }

    // ---- Motion Tracking ----

    /**
     * Tracks a region of interest across video frames using template matching.
     * Extracts the initial region as a template, then searches for it in
     * subsequent frames using block matching with adaptive search window.
     */
    suspend fun trackMotion(
        videoUri: Uri,
        initialRegion: TrackingRegion,
        startMs: Long,
        endMs: Long
    ): List<TrackingResult> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val results = mutableListOf<TrackingResult>()
            val stepMs = 100L // 10fps tracking
            val analysisW = 128
            val analysisH = 72

            var prevCenterX = initialRegion.centerX
            var prevCenterY = initialRegion.centerY
            var prevFrame: Bitmap? = null
            var currentMs = startMs

            while (currentMs <= endMs) {
                val frame = retriever.getFrameAtTime(
                    currentMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (frame != null) {
                    val scaled = Bitmap.createScaledBitmap(frame, analysisW, analysisH, true)
                    if (scaled !== frame) frame.recycle()

                    if (prevFrame != null) {
                        // Estimate motion from previous frame
                        val (dx, dy) = estimateRegionMotion(
                            prevFrame, scaled,
                            (prevCenterX * analysisW).toInt(),
                            (prevCenterY * analysisH).toInt(),
                            (initialRegion.width * analysisW / 2).toInt().coerceAtLeast(4)
                        )
                        prevCenterX = (prevCenterX + dx).coerceIn(0f, 1f)
                        prevCenterY = (prevCenterY + dy).coerceIn(0f, 1f)
                        prevFrame.recycle()
                    }

                    results.add(TrackingResult(
                        timestampMs = currentMs,
                        region = TrackingRegion(
                            centerX = prevCenterX,
                            centerY = prevCenterY,
                            width = initialRegion.width,
                            height = initialRegion.height
                        ),
                        confidence = 0.9f - (currentMs - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1L) * 0.3f
                    ))
                    prevFrame = scaled
                }
                currentMs += stepMs
            }
            prevFrame?.recycle()
            results
        } catch (_: Exception) {
            emptyList()
        } finally {
            retriever.release()
        }
    }

    /**
     * Estimate motion of a specific region between frames using block matching.
     */
    private fun estimateRegionMotion(
        prev: Bitmap, curr: Bitmap,
        centerX: Int, centerY: Int, radius: Int
    ): Pair<Float, Float> {
        val w = min(prev.width, curr.width)
        val h = min(prev.height, curr.height)
        if (w < 8 || h < 8) return 0f to 0f
        val searchRange = max(2, radius / 2)

        var bestDx = 0
        var bestDy = 0
        var bestDiff = Long.MAX_VALUE

        for (dy in -searchRange..searchRange) {
            for (dx in -searchRange..searchRange) {
                var diff = 0L
                var count = 0
                for (by in -radius..radius step 2) {
                    for (bx in -radius..radius step 2) {
                        val px = centerX + bx
                        val py = centerY + by
                        val sx = px + dx
                        val sy = py + dy
                        if (px in 0 until w && py in 0 until h &&
                            sx in 0 until w && sy in 0 until h) {
                            val p1 = prev.getPixel(px, py)
                            val p2 = curr.getPixel(sx, sy)
                            diff += abs((p1 shr 16 and 0xFF) - (p2 shr 16 and 0xFF)) +
                                    abs((p1 shr 8 and 0xFF) - (p2 shr 8 and 0xFF)) +
                                    abs((p1 and 0xFF) - (p2 and 0xFF))
                            count++
                        }
                    }
                }
                if (count > 0 && diff / count < bestDiff) {
                    bestDiff = diff / count
                    bestDx = dx
                    bestDy = dy
                }
            }
        }

        return bestDx.toFloat() / w to bestDy.toFloat() / h
    }

    // ---- Smart Crop ----

    /**
     * Analyzes the video to find the visual center of interest using
     * edge density and luminance weighting (saliency approximation).
     */
    suspend fun suggestCrop(
        videoUri: Uri,
        targetAspectRatio: Float
    ): CropSuggestion = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: return@withContext CropSuggestion()

            // Analyze 3 frames
            var weightedX = 0f
            var weightedY = 0f
            var totalWeight = 0f

            for (i in 0 until 3) {
                val timeMs = durationMs * (i * 2 + 1) / 6
                val frame = retriever.getFrameAtTime(
                    timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: continue

                val scaled = Bitmap.createScaledBitmap(frame, 64, 36, true)
                if (scaled !== frame) frame.recycle()

                // Compute edge density per region (3x3 grid)
                val gridW = scaled.width / 3
                val gridH = scaled.height / 3
                for (gy in 0 until 3) {
                    for (gx in 0 until 3) {
                        var edgeSum = 0f
                        var count = 0
                        for (y in gy * gridH + 1 until min((gy + 1) * gridH, scaled.height - 1)) {
                            for (x in gx * gridW + 1 until min((gx + 1) * gridW, scaled.width - 1)) {
                                // Simple Sobel edge detection approximation
                                val c = luminance(scaled.getPixel(x, y))
                                val l = luminance(scaled.getPixel(x - 1, y))
                                val r = luminance(scaled.getPixel(x + 1, y))
                                val t = luminance(scaled.getPixel(x, y - 1))
                                val b = luminance(scaled.getPixel(x, y + 1))
                                edgeSum += abs(r - l) + abs(b - t)
                                count++
                            }
                        }
                        if (count > 0) {
                            val density = edgeSum / count
                            val regionCenterX = (gx + 0.5f) / 3f
                            val regionCenterY = (gy + 0.5f) / 3f
                            // Apply rule-of-thirds weighting (center-bias + thirds intersections)
                            val thirdsBoost = when {
                                gx == 1 && gy == 1 -> 1.2f
                                (gx == 0 || gx == 2) && (gy == 0 || gy == 2) -> 1.1f
                                else -> 1.0f
                            }
                            val weight = density * thirdsBoost
                            weightedX += regionCenterX * weight
                            weightedY += regionCenterY * weight
                            totalWeight += weight
                        }
                    }
                }
                scaled.recycle()
            }

            val safeRatio = targetAspectRatio.coerceAtLeast(0.01f)

            if (totalWeight < 0.001f) {
                return@withContext CropSuggestion(
                    centerX = 0.5f, centerY = 0.5f,
                    width = 1f, height = (1f / safeRatio).coerceAtMost(1f),
                    confidence = 0.3f
                )
            }

            val cx = (weightedX / totalWeight).coerceIn(0.2f, 0.8f)
            val cy = (weightedY / totalWeight).coerceIn(0.2f, 0.8f)

            CropSuggestion(
                centerX = cx,
                centerY = cy,
                width = 1f,
                height = (1f / safeRatio).coerceAtMost(1f),
                confidence = min(1f, totalWeight / 3f)
            )
        } catch (_: Exception) {
            val safeRatio = targetAspectRatio.coerceAtLeast(0.01f)
            CropSuggestion(
                centerX = 0.5f, centerY = 0.5f,
                width = 1f, height = (1f / safeRatio).coerceAtMost(1f),
                confidence = 0.3f
            )
        } finally {
            retriever.release()
        }
    }

    private fun luminance(pixel: Int): Float {
        return (Color.red(pixel) * 0.299f + Color.green(pixel) * 0.587f + Color.blue(pixel) * 0.114f) / 255f
    }

    // ---- Style Transfer ----

    /**
     * Analyzes a video frame and generates a cinematic color grade based on
     * the frame's existing color characteristics. Returns a set of effects
     * that transform the look into a professional color-graded style.
     */
    suspend fun analyzeAndApplyStyle(
        videoUri: Uri
    ): StyleTransferResult = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: return@withContext StyleTransferResult()

            // Sample 3 frames across the clip for stable analysis
            val frames = listOf(durationMs / 4, durationMs / 2, durationMs * 3 / 4)
            var avgLum = 0f; var avgSat = 0f; var avgTemp = 0f; var frameCount = 0

            for (ms in frames) {
                val frame = retriever.getFrameAtTime(
                    ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: continue
                val scaled = Bitmap.createScaledBitmap(frame, 64, 36, true)
                if (scaled !== frame) frame.recycle()

                var lumSum = 0f; var satSum = 0f; var tempSum = 0f
                val n = scaled.width * scaled.height
                for (y in 0 until scaled.height) {
                    for (x in 0 until scaled.width) {
                        val p = scaled.getPixel(x, y)
                        val r = Color.red(p) / 255f
                        val g = Color.green(p) / 255f
                        val b = Color.blue(p) / 255f
                        lumSum += r * 0.299f + g * 0.587f + b * 0.114f
                        val cMax = max(r, max(g, b))
                        val cMin = min(r, min(g, b))
                        satSum += if (cMax > 0f) (cMax - cMin) / cMax else 0f
                        tempSum += (r - b) // positive = warm, negative = cool
                    }
                }
                scaled.recycle()
                avgLum += lumSum / n
                avgSat += satSum / n
                avgTemp += tempSum / n
                frameCount++
            }

            if (frameCount == 0) return@withContext StyleTransferResult()
            avgLum /= frameCount
            avgSat /= frameCount
            avgTemp /= frameCount

            // Determine cinematic adjustments based on analysis
            val effects = mutableListOf<Pair<String, Float>>()

            // Contrast: boost if flat, reduce if too harsh
            val contrastAdj = when {
                avgLum in 0.35f..0.65f -> 1.15f // mid-tone: slight boost
                avgLum < 0.35f -> 1.10f // dark: gentle boost
                else -> 0.95f // bright: slight reduce
            }
            effects.add("contrast" to contrastAdj)

            // Temperature: push toward cinematic teal/orange split
            val tempAdj = when {
                avgTemp > 0.1f -> -0.08f // already warm: cool shadows slightly
                avgTemp < -0.1f -> 0.05f // already cool: warm highlights slightly
                else -> -0.03f // neutral: slight cool shift (cinematic)
            }
            effects.add("temperature" to tempAdj)

            // Saturation: cinematic = slightly desaturated
            val satAdj = when {
                avgSat > 0.4f -> 0.82f // over-saturated: pull back
                avgSat < 0.15f -> 1.1f // too flat: slight boost
                else -> 0.92f // normal: slight desat for cinema look
            }
            effects.add("saturation" to satAdj)

            // Exposure: normalize mid-tones
            val exposureAdj = when {
                avgLum < 0.3f -> 0.15f // dark: lift slightly
                avgLum > 0.7f -> -0.1f // bright: pull down
                else -> 0f
            }
            if (abs(exposureAdj) > 0.01f) effects.add("exposure" to exposureAdj)

            // Vignette for cinematic framing
            effects.add("vignette_intensity" to 0.3f)
            effects.add("vignette_radius" to 0.8f)

            // Film grain for organic texture
            effects.add("film_grain" to 0.04f)

            val styleName = when {
                avgTemp < -0.1f && avgSat < 0.25f -> "Noir"
                avgTemp > 0.15f && avgSat > 0.35f -> "Warm Cinematic"
                avgLum < 0.35f -> "Moody"
                avgSat > 0.4f -> "Vibrant Film"
                else -> "Cinematic"
            }

            StyleTransferResult(
                styleName = styleName,
                contrast = contrastAdj,
                temperature = tempAdj,
                saturation = satAdj,
                exposure = exposureAdj,
                vignetteIntensity = 0.3f,
                vignetteRadius = 0.8f,
                filmGrain = 0.04f,
                confidence = 0.85f
            )
        } catch (_: Exception) {
            StyleTransferResult()
        } finally {
            retriever.release()
        }
    }

    // ---- Neural Upscale ----

    /**
     * Analyzes source video resolution and returns recommended upscale settings.
     * Applies sharpening to compensate for upscaling artifacts.
     */
    suspend fun analyzeForUpscale(
        videoUri: Uri
    ): UpscaleResult = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val width = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: return@withContext UpscaleResult()
            val height = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: return@withContext UpscaleResult()

            val sourcePixels = width * height
            val targetResolution = when {
                sourcePixels <= 480 * 360 -> com.novacut.editor.model.Resolution.HD_720P
                sourcePixels <= 1280 * 720 -> com.novacut.editor.model.Resolution.FHD_1080P
                sourcePixels <= 1920 * 1080 -> com.novacut.editor.model.Resolution.QHD_1440P
                sourcePixels <= 2560 * 1440 -> com.novacut.editor.model.Resolution.UHD_4K
                else -> null // Already 4K+
            }

            // Sharpen strength inversely proportional to source resolution
            val sharpenStrength = when {
                sourcePixels <= 480 * 360 -> 0.8f
                sourcePixels <= 1280 * 720 -> 0.6f
                sourcePixels <= 1920 * 1080 -> 0.4f
                else -> 0.3f
            }

            UpscaleResult(
                sourceWidth = width,
                sourceHeight = height,
                targetResolution = targetResolution,
                sharpenStrength = sharpenStrength,
                confidence = if (targetResolution != null) 0.9f else 0f
            )
        } catch (_: Exception) {
            UpscaleResult()
        } finally {
            retriever.release()
        }
    }

    // ---- Shared Utilities ----

    /**
     * Calculate the visual difference between two frames.
     * Returns a value between 0 (identical) and 1 (completely different).
     */
    private fun calculateFrameDifference(frame1: Bitmap, frame2: Bitmap): Float {
        val width = minOf(frame1.width, frame2.width, 64)
        val height = minOf(frame1.height, frame2.height, 36)

        val scaled1 = Bitmap.createScaledBitmap(frame1, width, height, true)
        val scaled2 = Bitmap.createScaledBitmap(frame2, width, height, true)

        var totalDiff = 0L
        val totalPixels = width * height

        for (y in 0 until height) {
            for (x in 0 until width) {
                val p1 = scaled1.getPixel(x, y)
                val p2 = scaled2.getPixel(x, y)
                val dr = abs((p1 shr 16 and 0xFF) - (p2 shr 16 and 0xFF))
                val dg = abs((p1 shr 8 and 0xFF) - (p2 shr 8 and 0xFF))
                val db = abs((p1 and 0xFF) - (p2 and 0xFF))
                totalDiff += (dr + dg + db) / 3
            }
        }

        if (scaled1 !== frame1) scaled1.recycle()
        if (scaled2 !== frame2) scaled2.recycle()

        return (totalDiff.toFloat() / totalPixels / 255f).coerceIn(0f, 1f)
    }
}

// Data classes for AI features

data class CaptionEntry(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val confidence: Float = 1f,
    val languageCode: String = "en"
)

data class CaptionStyle(
    val fontSize: Float = 36f,
    val textColor: Long = 0xFFFFFFFF,
    val backgroundColor: Long = 0xCC000000,
    val strokeColor: Long = 0xFF000000,
    val strokeWidth: Float = 1.5f,
    val bold: Boolean = true,
    val positionY: Float = 0.85f
)

data class SceneChange(
    val timestampMs: Long,
    val confidence: Float,
    val type: SceneChangeType
)

enum class SceneChangeType {
    HARD_CUT, TRANSITION, FADE
}

data class TrackingRegion(
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val width: Float = 0.3f,
    val height: Float = 0.3f
)

data class TrackingResult(
    val timestampMs: Long,
    val region: TrackingRegion,
    val confidence: Float
)

data class CropSuggestion(
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val width: Float = 1f,
    val height: Float = 1f,
    val confidence: Float = 0.5f
)

data class ColorCorrection(
    val brightness: Float = 0f,      // -0.5..0.5
    val contrast: Float = 1f,        // 0.5..2.5
    val saturation: Float = 1f,      // 0.5..2.0
    val temperature: Float = 0f,     // -1..1
    val confidence: Float = 0f
)

data class StabilizationResult(
    val shakeMagnitude: Float = 0f,
    val recommendedZoom: Float = 1f,
    val motionKeyframes: List<StabilizationKeyframe> = emptyList(),
    val confidence: Float = 0f
)

data class StabilizationKeyframe(
    val timestampMs: Long,
    val offsetX: Float,
    val offsetY: Float
)

data class NoiseProfile(
    val noiseFloorDb: Float = -60f,
    val signalToNoiseDb: Float = 60f,
    val recommendedReduction: Float = 0f,
    val noiseGateThreshold: Float = 0f,
    val confidence: Float = 0f
)

data class StyleTransferResult(
    val styleName: String = "Unknown",
    val contrast: Float = 1f,
    val temperature: Float = 0f,
    val saturation: Float = 1f,
    val exposure: Float = 0f,
    val vignetteIntensity: Float = 0f,
    val vignetteRadius: Float = 0.8f,
    val filmGrain: Float = 0f,
    val confidence: Float = 0f
)

data class UpscaleResult(
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    val targetResolution: com.novacut.editor.model.Resolution? = null,
    val sharpenStrength: Float = 0.5f,
    val confidence: Float = 0f
)

data class BackgroundAnalysis(
    val backgroundColor: Int = 0,
    val isGreenScreen: Boolean = false,
    val isBlueScreen: Boolean = false,
    val recommendedSimilarity: Float = 0.4f,
    val recommendedSmoothness: Float = 0.1f,
    val recommendedSpill: Float = 0.1f,
    val confidence: Float = 0f
)
