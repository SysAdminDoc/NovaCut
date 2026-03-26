package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Smart reframing engine for auto-cropping video to different aspect ratios.
 * Uses face/subject detection to keep important content in frame.
 *
 * Backend: MediaPipe Face Detection (BlazeFace ~400KB, <1ms/frame)
 *          + BlazePose (~3-8MB) for full body tracking
 *
 * Dependency (add to build.gradle.kts):
 *   implementation("com.google.mediapipe:tasks-vision:0.10.+")
 *
 * Algorithm (replicates YouTube Shorts / Instagram Reels auto-crop):
 * 1. Detect faces/poses per frame at low resolution
 * 2. Compute saliency-weighted bounding box (face center = highest weight)
 * 3. Smooth crop window trajectory using EMA (exponential moving average)
 * 4. Choose strategy: stationary (no motion), pan (slow follow), track (fast follow)
 */
@Singleton
class SmartReframeEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    enum class ReframeStrategy {
        STATIONARY,  // Fixed crop, centered on average subject position
        PAN,         // Slow smooth pan following subject
        TRACK        // Fast tracking, keeps subject centered
    }

    data class ReframeConfig(
        val targetAspectRatio: Float = 9f / 16f,  // 9:16 for vertical
        val smoothingFactor: Float = 0.08f,        // EMA alpha (lower = smoother)
        val strategy: ReframeStrategy = ReframeStrategy.PAN,
        val padding: Float = 0.1f                  // Extra padding around detected subject
    )

    data class CropWindow(
        val centerX: Float,  // 0-1 normalized
        val centerY: Float,
        val width: Float,
        val height: Float
    )

    data class ReframeResult(
        val cropWindows: List<CropWindow>,  // One per frame
        val frameRate: Float,
        val strategy: ReframeStrategy
    )

    /**
     * Analyze video and compute per-frame crop windows.
     *
     * When MediaPipe is integrated:
     *   val faceDetector = FaceDetector.createFromOptions(context, options)
     *   for each frame:
     *     val result = faceDetector.detect(mpImage)
     *     val faces = result.detections()
     *     // Compute saliency center from face bounding boxes
     *     // Apply EMA smoothing to crop window position
     */
    suspend fun analyzeForReframe(
        uri: Uri,
        config: ReframeConfig = ReframeConfig(),
        onProgress: (Float) -> Unit = {}
    ): ReframeResult = withContext(Dispatchers.Default) {
        onProgress(0.1f)

        // TODO: When MediaPipe is integrated, detect faces per frame
        // For now, use center-crop as fallback
        val numFrames = 300  // Assume 10 seconds at 30fps
        val cropWindows = mutableListOf<CropWindow>()

        // Compute crop dimensions based on target aspect ratio
        val cropW = if (config.targetAspectRatio < 1f) config.targetAspectRatio else 1f
        val cropH = if (config.targetAspectRatio < 1f) 1f else 1f / config.targetAspectRatio

        // Center crop (fallback when no face detection)
        for (i in 0 until numFrames) {
            ensureActive()
            cropWindows.add(CropWindow(
                centerX = 0.5f,
                centerY = 0.5f,
                width = cropW,
                height = cropH
            ))
            if (i % 30 == 0) onProgress(0.1f + 0.8f * i / numFrames)
        }

        onProgress(1f)
        ReframeResult(cropWindows, 30f, ReframeStrategy.STATIONARY)
    }

    /**
     * Apply EMA smoothing to a series of crop centers.
     * Prevents jittery crop movement.
     */
    fun smoothCropTrajectory(
        centers: List<Pair<Float, Float>>,
        alpha: Float = 0.08f
    ): List<Pair<Float, Float>> {
        if (centers.isEmpty()) return centers
        val smoothed = mutableListOf(centers.first())
        for (i in 1 until centers.size) {
            val prevX = smoothed.last().first
            val prevY = smoothed.last().second
            val newX = prevX + alpha * (centers[i].first - prevX)
            val newY = prevY + alpha * (centers[i].second - prevY)
            smoothed.add(Pair(newX, newY))
        }
        return smoothed
    }
}
