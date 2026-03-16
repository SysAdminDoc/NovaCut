package com.novacut.editor.engine

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceoverRecorderEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTime: Long = 0L

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    fun startRecording(): File? {
        val file = File(context.cacheDir, "voiceover_${System.currentTimeMillis()}.m4a")
        outputFile = file

        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        return try {
            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(256000)
                setAudioChannels(2)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            startTime = System.currentTimeMillis()
            _isRecording.value = true
            file
        } catch (e: Exception) {
            rec.release()
            outputFile = null
            null
        }
    }

    fun stopRecording(): Uri? {
        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            _isRecording.value = false
            outputFile?.let { Uri.fromFile(it) }
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            _isRecording.value = false
            null
        }
    }

    fun getRecordingDurationMs(): Long {
        return if (_isRecording.value) {
            System.currentTimeMillis() - startTime
        } else 0L
    }

    fun release() {
        recorder?.release()
        recorder = null
        _isRecording.value = false
    }
}
