package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud-based temporally coherent video inpainting using ProPainter.
 *
 * ProPainter: github.com/sczhou/ProPainter
 * - Dual-domain propagation (image + feature warping) with efficient Transformer
 * - Temporal coherence across frames (no flickering)
 * - Requires significant GPU memory (~662MB per additional input frame)
 * - Not feasible for on-device processing — cloud API required
 *
 * Architecture:
 * 1. User draws mask on key frames in the app
 * 2. App uploads video segment + mask frames to cloud API
 * 3. Cloud runs ProPainter inference
 * 4. App downloads the inpainted video segment
 * 5. App replaces the segment in the timeline
 *
 * Cloud endpoint (configure via Settings or environment):
 *   POST /api/v1/inpaint
 *   Body: multipart/form-data with video + mask_frames + config
 *   Response: processed video file
 */
@Singleton
class CloudInpaintingEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    data class CloudConfig(
        val apiEndpoint: String = "",
        val apiKey: String = "",
        val maxDurationSeconds: Int = 30,
        val maxResolution: Int = 1080
    )

    data class InpaintJob(
        val jobId: String,
        val status: JobStatus,
        val progress: Float = 0f,
        val resultUri: Uri? = null,
        val errorMessage: String? = null
    )

    enum class JobStatus {
        UPLOADING, QUEUED, PROCESSING, COMPLETE, FAILED
    }

    /**
     * Submit a video inpainting job to the cloud.
     * @param videoUri Source video URI
     * @param maskFrames Map of frame index to mask bitmap file
     * @param startMs Start time of the segment to inpaint
     * @param endMs End time of the segment
     * @return Job ID for tracking progress
     */
    suspend fun submitJob(
        videoUri: Uri,
        maskFrames: Map<Int, File>,
        startMs: Long,
        endMs: Long,
        config: CloudConfig = CloudConfig()
    ): String? = withContext(Dispatchers.IO) {
        if (config.apiEndpoint.isEmpty()) {
            Log.w(TAG, "Cloud inpainting not configured — set API endpoint in Settings")
            return@withContext null
        }

        val durationSec = (endMs - startMs) / 1000f
        if (durationSec > config.maxDurationSeconds) {
            Log.w(TAG, "Segment too long (${durationSec}s > ${config.maxDurationSeconds}s max)")
            return@withContext null
        }

        if (maskFrames.isEmpty()) {
            Log.w(TAG, "No mask frames provided for inpainting")
            return@withContext null
        }

        Log.i(TAG, "Cloud inpainting: ${maskFrames.size} mask frames, ${durationSec}s segment")

        try {
            // Extract video segment to a temp file
            val videoFile = File(context.cacheDir, "inpaint_segment_${System.currentTimeMillis()}.mp4")
            context.contentResolver.openInputStream(videoUri)?.use { input ->
                videoFile.outputStream().use { output -> input.copyTo(output) }
            } ?: run {
                Log.e(TAG, "Failed to open video URI: $videoUri")
                return@withContext null
            }

            // Build multipart request body with video and mask frames
            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "video", videoFile.name,
                    videoFile.asRequestBody("video/mp4".toMediaType())
                )
                .addFormDataPart("start_ms", startMs.toString())
                .addFormDataPart("end_ms", endMs.toString())

            maskFrames.forEach { (frameIndex, maskFile) ->
                multipartBuilder.addFormDataPart(
                    "mask_frames", "mask_$frameIndex.png",
                    maskFile.asRequestBody("image/png".toMediaType())
                )
            }

            val requestBody = multipartBuilder.build()

            val request = Request.Builder()
                .url("${config.apiEndpoint}/api/v1/inpaint")
                .header("Authorization", "Bearer ${config.apiKey}")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Submit job failed: HTTP ${response.code} — ${response.message}")
                return@withContext null
            }

            val responseBody = response.body?.string()
            if (responseBody == null) {
                Log.e(TAG, "Submit job returned empty response body")
                return@withContext null
            }

            val json = JSONObject(responseBody)
            val jobId = json.getString("job_id")
            Log.i(TAG, "Job submitted successfully: $jobId")
            jobId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to submit inpainting job", e)
            null
        }
    }

    /**
     * Check job status.
     */
    suspend fun checkJobStatus(
        jobId: String,
        config: CloudConfig = CloudConfig()
    ): InpaintJob = withContext(Dispatchers.IO) {
        if (config.apiEndpoint.isEmpty()) {
            return@withContext InpaintJob(jobId, JobStatus.FAILED, errorMessage = "API endpoint not configured")
        }
        Log.d(TAG, "Checking job status: $jobId")

        try {
            val request = Request.Builder()
                .url("${config.apiEndpoint}/api/v1/inpaint/$jobId/status")
                .header("Authorization", "Bearer ${config.apiKey}")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Check status failed: HTTP ${response.code} — ${response.message}")
                return@withContext InpaintJob(jobId, JobStatus.FAILED, errorMessage = "HTTP ${response.code}")
            }

            val responseBody = response.body?.string()
            if (responseBody == null) {
                Log.e(TAG, "Check status returned empty response body")
                return@withContext InpaintJob(jobId, JobStatus.FAILED, errorMessage = "Empty response")
            }

            val json = JSONObject(responseBody)
            val statusStr = json.getString("status")
            val progress = json.optDouble("progress", 0.0).toFloat()
            val errorMsg = json.optString("error", null)

            val status = when (statusStr.uppercase()) {
                "UPLOADING" -> JobStatus.UPLOADING
                "QUEUED" -> JobStatus.QUEUED
                "PROCESSING" -> JobStatus.PROCESSING
                "COMPLETE" -> JobStatus.COMPLETE
                "FAILED" -> JobStatus.FAILED
                else -> {
                    Log.w(TAG, "Unknown job status: $statusStr")
                    JobStatus.QUEUED
                }
            }

            Log.d(TAG, "Job $jobId status: $status, progress: $progress")
            InpaintJob(jobId, status, progress, errorMessage = errorMsg)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check job status", e)
            InpaintJob(jobId, JobStatus.FAILED, errorMessage = e.message)
        }
    }

    /**
     * Download completed result.
     */
    suspend fun downloadResult(
        jobId: String,
        config: CloudConfig = CloudConfig()
    ): File? = withContext(Dispatchers.IO) {
        if (config.apiEndpoint.isEmpty()) {
            Log.w(TAG, "Cannot download result — API endpoint not configured")
            return@withContext null
        }
        Log.d(TAG, "Downloading result for job: $jobId")

        try {
            val request = Request.Builder()
                .url("${config.apiEndpoint}/api/v1/inpaint/$jobId/result")
                .header("Authorization", "Bearer ${config.apiKey}")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Download result failed: HTTP ${response.code} — ${response.message}")
                return@withContext null
            }

            val body = response.body
            if (body == null) {
                Log.e(TAG, "Download result returned empty body")
                return@withContext null
            }

            val outputFile = File(context.cacheDir, "inpaint_result_${jobId}.mp4")
            body.byteStream().use { input ->
                outputFile.outputStream().use { output -> input.copyTo(output) }
            }

            Log.i(TAG, "Result downloaded: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download inpainting result", e)
            null
        }
    }

    /**
     * Check if cloud inpainting is configured and available.
     */
    fun isAvailable(): Boolean = getConfig().apiEndpoint.isNotEmpty()

    /**
     * Get the current cloud configuration from shared preferences.
     */
    fun getConfig(): CloudConfig {
        val prefs = context.getSharedPreferences("novacut_cloud", Context.MODE_PRIVATE)
        return CloudConfig(
            apiEndpoint = prefs.getString("inpaint_endpoint", "") ?: "",
            apiKey = prefs.getString("inpaint_api_key", "") ?: "",
            maxDurationSeconds = prefs.getInt("inpaint_max_duration", 30),
            maxResolution = prefs.getInt("inpaint_max_resolution", 1080)
        )
    }

    /**
     * Save cloud configuration.
     */
    fun saveConfig(config: CloudConfig) {
        context.getSharedPreferences("novacut_cloud", Context.MODE_PRIVATE).edit()
            .putString("inpaint_endpoint", config.apiEndpoint)
            .putString("inpaint_api_key", config.apiKey)
            .putInt("inpaint_max_duration", config.maxDurationSeconds)
            .putInt("inpaint_max_resolution", config.maxResolution)
            .apply()
    }

    companion object {
        private const val TAG = "CloudInpainting"
    }
}
