package com.novacut.editor.engine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FFmpeg integration for advanced operations beyond Media3 Transformer's capabilities.
 *
 * Dependency (add to build.gradle.kts):
 *   implementation("io.github.nicholasryan:ffmpegx-android:6.1.+")
 *
 * Replaces the now-archived ffmpeg-kit (retired Jan 2025, archived June 2025).
 * Supports Android 10-15+, arm64-v8a/x86_64, 300+ filters.
 *
 * Use cases beyond Media3 Transformer:
 * - Complex audio filter chains (loudnorm two-pass, audio ducking via sidechaincompress)
 * - Subtitle burning with libass (ASS/SSA styling)
 * - Format conversions (WebM/VP9, AV1 software encode)
 * - Audio extraction to separate file
 * - Concat demuxer for seamless joins
 * - Video speed change with audio pitch correction (atempo)
 */
@Singleton
class FFmpegEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Execute an FFmpeg command.
     * Returns exit code (0 = success).
     *
     * When FFmpegX-Android is integrated:
     *   FFmpegX.execute(command)
     *
     * Common commands for NovaCut:
     *
     * Two-pass loudness normalization:
     *   Pass 1: "-i input.mp4 -af loudnorm=I=-14:TP=-1:LRA=11:print_format=json -f null -"
     *   Pass 2: "-i input.mp4 -af loudnorm=I=-14:TP=-1:LRA=11:measured_I=<val>:measured_TP=<val>... output.mp4"
     *
     * Subtitle burning with libass:
     *   "-i input.mp4 -vf ass=subtitles.ass output.mp4"
     *
     * Audio extraction:
     *   "-i input.mp4 -vn -acodec pcm_s16le -ar 16000 -ac 1 output.wav"
     *
     * Speed change with pitch correction:
     *   "-i input.mp4 -filter_complex [0:v]setpts=0.5*PTS[v];[0:a]atempo=2.0[a] -map [v] -map [a] output.mp4"
     *
     * AV1 software encode (SVT-AV1):
     *   "-i input.mp4 -c:v libsvtav1 -preset 8 -crf 30 -c:a libopus output.webm"
     */
    suspend fun execute(
        command: String,
        onProgress: (Float) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        // TODO: When FFmpegX-Android is integrated:
        // FFmpegX.executeAsync(command) { progress -> onProgress(progress) }
        -1  // Not yet integrated
    }

    /**
     * Extract audio from video to PCM WAV for processing.
     */
    suspend fun extractAudioToWav(
        inputUri: String,
        outputFile: File,
        sampleRate: Int = 16000,
        channels: Int = 1
    ): Boolean = withContext(Dispatchers.IO) {
        val cmd = "-i \"$inputUri\" -vn -acodec pcm_s16le -ar $sampleRate -ac $channels \"${outputFile.absolutePath}\""
        execute(cmd) == 0
    }

    /**
     * Burn ASS/SSA subtitles into video.
     */
    suspend fun burnSubtitles(
        inputFile: File,
        subtitleFile: File,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val cmd = "-i \"${inputFile.absolutePath}\" -vf \"ass=${subtitleFile.absolutePath}\" " +
            "-c:a copy \"${outputFile.absolutePath}\""
        execute(cmd, onProgress) == 0
    }

    /**
     * Two-pass loudness normalization via FFmpeg loudnorm filter.
     */
    suspend fun normalizeLoudness(
        inputFile: File,
        outputFile: File,
        targetLufs: Float = -14f,
        truePeakDb: Float = -1f,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        // Pass 1: measure
        val measureCmd = "-i \"${inputFile.absolutePath}\" " +
            "-af loudnorm=I=$targetLufs:TP=$truePeakDb:LRA=11:print_format=json -f null -"
        // TODO: Parse JSON output for measured values
        // Pass 2: apply with measured values
        // For now, single-pass (less accurate but functional):
        val cmd = "-i \"${inputFile.absolutePath}\" " +
            "-af loudnorm=I=$targetLufs:TP=$truePeakDb:LRA=11 " +
            "-c:v copy \"${outputFile.absolutePath}\""
        execute(cmd, onProgress) == 0
    }

    /**
     * Check if FFmpegX-Android is available at runtime.
     */
    fun isAvailable(): Boolean {
        return try {
            Class.forName("io.github.nicholasryan.ffmpegx.FFmpegX")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }
}
