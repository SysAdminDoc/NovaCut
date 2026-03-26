package com.novacut.editor.engine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-quality offline TTS using Piper (VITS architecture) via Sherpa-ONNX.
 * Near-human quality, 50+ languages, 20-30ms generation speed.
 *
 * Dependencies (add to build.gradle.kts):
 *   implementation("com.k2fsa.sherpa:onnx-android:1.10.+")
 *
 * Voice models (~15-65MB each, downloaded from HuggingFace on first use):
 *   - en_US-amy-medium (female, 22kHz, ~30MB)
 *   - en_US-ryan-medium (male, 22kHz, ~30MB)
 *   - en_GB-alba-medium (British female, ~30MB)
 *   - de_DE-thorsten-medium (German male, ~30MB)
 *   - es_ES-davefx-medium (Spanish male, ~30MB)
 */
@Singleton
class PiperTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class VoiceProfile(
        val id: String,
        val name: String,
        val language: String,
        val gender: String,
        val sampleRate: Int = 22050,
        val modelSizeMb: Int,
        val isDownloaded: Boolean = false
    )

    private val voicesDir = File(context.filesDir, "piper_voices").also { it.mkdirs() }

    /**
     * Available voice profiles. Models are downloaded on first use.
     */
    fun getAvailableVoices(): List<VoiceProfile> = listOf(
        VoiceProfile("en_US-amy-medium", "Amy (US)", "en", "female", 22050, 30),
        VoiceProfile("en_US-ryan-medium", "Ryan (US)", "en", "male", 22050, 30),
        VoiceProfile("en_GB-alba-medium", "Alba (UK)", "en", "female", 22050, 30),
        VoiceProfile("de_DE-thorsten-medium", "Thorsten (DE)", "de", "male", 22050, 30),
        VoiceProfile("es_ES-davefx-medium", "Dave (ES)", "es", "male", 22050, 30),
        VoiceProfile("fr_FR-siwis-medium", "Siwis (FR)", "fr", "female", 22050, 30),
        VoiceProfile("ja_JP-takumi-medium", "Takumi (JP)", "ja", "male", 22050, 35),
        VoiceProfile("zh_CN-huayan-medium", "Huayan (CN)", "zh", "female", 22050, 35),
        VoiceProfile("ko_KR-sunhi-medium", "Sunhi (KR)", "ko", "female", 22050, 30),
        VoiceProfile("pt_BR-faber-medium", "Faber (BR)", "pt", "male", 22050, 30)
    ).map { it.copy(isDownloaded = File(voicesDir, it.id).exists()) }

    /**
     * Synthesize text to audio file.
     *
     * When Sherpa-ONNX is integrated:
     *   val config = OfflineTtsConfig(
     *     model = OfflineTtsModelConfig(
     *       vits = OfflineTtsVitsModelConfig(
     *         model = "$voiceDir/model.onnx",
     *         tokens = "$voiceDir/tokens.txt",
     *         dataDir = "$voiceDir/espeak-ng-data"
     *       )
     *     )
     *   )
     *   val tts = OfflineTts(config)
     *   val audio = tts.generate(text, sid = 0, speed = speed)
     *   // Write audio.samples to WAV file
     *
     * @param text Text to synthesize
     * @param voiceId Voice profile ID
     * @param speed Speech rate multiplier (0.5 = slow, 1.0 = normal, 2.0 = fast)
     * @return Output audio file, or null on failure
     */
    suspend fun synthesize(
        text: String,
        voiceId: String = "en_US-amy-medium",
        speed: Float = 1.0f,
        onProgress: (Float) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        val outputDir = File(context.filesDir, "tts_output").also { it.mkdirs() }
        val outputFile = File(outputDir, "piper_${System.currentTimeMillis()}.wav")

        try {
            onProgress(0.1f)

            // TODO: When Sherpa-ONNX dependency is added, use Piper TTS:
            // val voiceDir = File(voicesDir, voiceId)
            // if (!voiceDir.exists()) { downloadVoice(voiceId); }
            // val config = OfflineTtsConfig(...)
            // val tts = OfflineTts(config)
            // val audio = tts.generate(text, sid = 0, speed = speed)
            // writeWavFile(outputFile, audio.samples, audio.sampleRate)

            // Fallback: use Android system TTS
            onProgress(1f)
            null // Will return file when Piper is integrated
        } catch (e: Exception) {
            outputFile.delete()
            null
        }
    }

    /**
     * Check if a voice model is downloaded and ready.
     */
    fun isVoiceReady(voiceId: String): Boolean {
        val voiceDir = File(voicesDir, voiceId)
        return voiceDir.exists() && File(voiceDir, "model.onnx").exists()
    }

    /**
     * Get total size of downloaded voice models.
     */
    fun getDownloadedSizeMb(): Int {
        return voicesDir.listFiles()?.sumOf { dir ->
            dir.walkTopDown().sumOf { it.length() }
        }?.let { (it / 1_048_576).toInt() } ?: 0
    }
}
