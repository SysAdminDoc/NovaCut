package com.novacut.editor.engine

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub engine -- 360 / VR equirectangular editing. See ROADMAP.md Tier C.10.
 *
 * Supplies yaw/pitch/roll pose metadata and an equirectangular-aware reframe
 * transform. Targets Insta360 / GoPro Max footage. Pose metadata travels through
 * the spatial-media XMP GPano namespace.
 *
 * GL pipeline (when wired):
 *  1. Source frame sampled as 2:1 equirectangular texture.
 *  2. Fragment shader rotates sample direction by yaw/pitch/roll before the
 *     spherical -> UV lookup.
 *  3. Output is either (a) cropped rectilinear view (VR stills / flat export)
 *     or (b) 2:1 equirectangular re-export for continued 360 workflow.
 */
@Singleton
class EquirectangularEngine @Inject constructor() {

    data class Pose(
        val yawDeg: Float = 0f,
        val pitchDeg: Float = 0f,
        val rollDeg: Float = 0f,
        /** Field of view for rectilinear output. Ignored for equirectangular output. */
        val fovDeg: Float = 90f
    ) {
        init {
            // NaN fails `in` range checks silently (NaN comparisons are always false
            // in Kotlin/Java float semantics), so range checks must be preceded by
            // finite checks or a corrupt Float.NaN from a tampered JSON could
            // propagate through [poseAt] and poison every GL uniform the render
            // pipeline derives from this pose.
            require(yawDeg.isFinite()) { "yawDeg must be finite" }
            require(pitchDeg.isFinite()) { "pitchDeg must be finite" }
            require(rollDeg.isFinite()) { "rollDeg must be finite" }
            require(fovDeg.isFinite()) { "fovDeg must be finite" }
            require(yawDeg in -180f..180f) { "yawDeg must be in [-180, 180]" }
            require(pitchDeg in -90f..90f) { "pitchDeg must be in [-90, 90]" }
            require(rollDeg in -180f..180f) { "rollDeg must be in [-180, 180]" }
            require(fovDeg in 10f..150f) { "fovDeg must be in [10, 150]" }
        }
    }

    enum class OutputProjection {
        /** Keep 360 -- write 2:1 equirectangular back out. */
        EQUIRECTANGULAR,
        /** Flatten to rectilinear (2D) view at the current pose + FOV. */
        RECTILINEAR,
        /** Stereographic "little planet" projection. */
        LITTLE_PLANET
    }

    data class KeyframedPose(
        val timeMs: Long,
        val pose: Pose,
        val easing: Easing = Easing.LINEAR
    ) {
        enum class Easing { LINEAR, EASE_IN_OUT, EASE_IN, EASE_OUT }
    }

    /** Whether the source file declares the GPano equirectangular XMP namespace. */
    fun isSource360(containerFormatHint: String?): Boolean = false

    /** Build GL fragment-shader uniforms for a given pose + projection. */
    fun buildShaderUniforms(
        pose: Pose,
        projection: OutputProjection
    ): Map<String, FloatArray> {
        Log.d(TAG, "buildShaderUniforms: stub -- 360 pipeline not wired (${projection.name})")
        return emptyMap()
    }

    /**
     * Interpolate a pose between keyframes at [timeMs]. Accepts unsorted input --
     * keyframes are sorted by timeMs internally so callers don't silently get garbage
     * from a misordered list.
     *
     * Yaw and roll use shortest-path angular interpolation so a transition from 179°
     * to -179° moves 2° the short way rather than 358° the long way. Pitch is a
     * [-90, 90] range (no wrap) so it's a straight lerp.
     */
    fun poseAt(timeMs: Long, keyframes: List<KeyframedPose>): Pose {
        if (keyframes.isEmpty()) return Pose()
        val sorted = if (keyframes.size > 1) keyframes.sortedBy { it.timeMs } else keyframes
        if (sorted.size == 1 || timeMs <= sorted.first().timeMs) return sorted.first().pose
        if (timeMs >= sorted.last().timeMs) return sorted.last().pose
        val next = sorted.indexOfFirst { it.timeMs > timeMs }.coerceAtLeast(1)
        val prev = next - 1
        val a = sorted[prev]
        val b = sorted[next]
        val span = (b.timeMs - a.timeMs).coerceAtLeast(1L)
        val raw = ((timeMs - a.timeMs).toFloat() / span).coerceIn(0f, 1f)
        val t = when (a.easing) {
            KeyframedPose.Easing.LINEAR -> raw
            KeyframedPose.Easing.EASE_IN -> raw * raw
            KeyframedPose.Easing.EASE_OUT -> 1f - (1f - raw) * (1f - raw)
            KeyframedPose.Easing.EASE_IN_OUT -> if (raw < 0.5f) 2f * raw * raw else 1f - 2f * (1f - raw) * (1f - raw)
        }
        return Pose(
            yawDeg = lerpAngle(a.pose.yawDeg, b.pose.yawDeg, t),
            pitchDeg = a.pose.pitchDeg + (b.pose.pitchDeg - a.pose.pitchDeg) * t,
            rollDeg = lerpAngle(a.pose.rollDeg, b.pose.rollDeg, t),
            fovDeg = a.pose.fovDeg + (b.pose.fovDeg - a.pose.fovDeg) * t
        )
    }

    /** Shortest-arc lerp for angles that wrap at ±180°. */
    private fun lerpAngle(fromDeg: Float, toDeg: Float, t: Float): Float {
        var delta = (toDeg - fromDeg) % 360f
        if (delta > 180f) delta -= 360f else if (delta < -180f) delta += 360f
        val result = fromDeg + delta * t
        // Re-wrap into [-180, 180] so downstream consumers see a consistent range.
        return ((result + 540f) % 360f) - 180f
    }

    companion object {
        private const val TAG = "Equirect"
    }
}
