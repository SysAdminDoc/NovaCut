package com.novacut.editor.engine

import com.novacut.editor.model.*
import kotlin.math.*

/**
 * Interpolation engine for keyframe-based animation of clip properties.
 * Supports position, scale, rotation, opacity, and volume.
 */
object KeyframeEngine {

    /**
     * Get the interpolated value for a property at a given time offset within a clip.
     * Returns null if no keyframes exist for this property.
     */
    fun getValueAt(
        keyframes: List<Keyframe>,
        property: KeyframeProperty,
        timeOffsetMs: Long
    ): Float? {
        val relevant = keyframes
            .filter { it.property == property }
            .sortedBy { it.timeOffsetMs }

        if (relevant.isEmpty()) return null
        if (relevant.size == 1) return relevant[0].value

        // Before first keyframe
        if (timeOffsetMs <= relevant.first().timeOffsetMs) return relevant.first().value
        // After last keyframe
        if (timeOffsetMs >= relevant.last().timeOffsetMs) return relevant.last().value

        // Find surrounding keyframes
        var prev = relevant.first()
        var next = relevant.last()

        for (i in 0 until relevant.size - 1) {
            if (timeOffsetMs >= relevant[i].timeOffsetMs && timeOffsetMs <= relevant[i + 1].timeOffsetMs) {
                prev = relevant[i]
                next = relevant[i + 1]
                break
            }
        }

        val duration = (next.timeOffsetMs - prev.timeOffsetMs).toFloat()
        if (duration <= 0f) return prev.value

        val t = (timeOffsetMs - prev.timeOffsetMs).toFloat() / duration
        val easedT = applyEasing(t, next.easing)

        return lerp(prev.value, next.value, easedT)
    }

    /**
     * Get all animated property values at a given time.
     */
    fun getAnimatedState(
        keyframes: List<Keyframe>,
        timeOffsetMs: Long
    ): AnimatedState {
        return AnimatedState(
            positionX = getValueAt(keyframes, KeyframeProperty.POSITION_X, timeOffsetMs),
            positionY = getValueAt(keyframes, KeyframeProperty.POSITION_Y, timeOffsetMs),
            scaleX = getValueAt(keyframes, KeyframeProperty.SCALE_X, timeOffsetMs),
            scaleY = getValueAt(keyframes, KeyframeProperty.SCALE_Y, timeOffsetMs),
            rotation = getValueAt(keyframes, KeyframeProperty.ROTATION, timeOffsetMs),
            opacity = getValueAt(keyframes, KeyframeProperty.OPACITY, timeOffsetMs),
            volume = getValueAt(keyframes, KeyframeProperty.VOLUME, timeOffsetMs)
        )
    }

    /**
     * Apply easing function to normalized time t (0..1).
     */
    private fun applyEasing(t: Float, easing: Easing): Float {
        return when (easing) {
            Easing.LINEAR -> t
            Easing.EASE_IN -> t * t
            Easing.EASE_OUT -> 1f - (1f - t) * (1f - t)
            Easing.EASE_IN_OUT -> {
                if (t < 0.5f) 2f * t * t
                else 1f - (-2f * t + 2f).pow(2) / 2f
            }
            Easing.SPRING -> {
                val c4 = (2f * PI / 3f).toFloat()
                val raw = if (t == 0f || t == 1f) t
                    else -(2f.pow(-10f * t)) * sin((t * 10f - 0.75f) * c4) + 1f
                raw.coerceIn(0f, 1f) // Clamp elastic oscillation
            }
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /**
     * Create a set of keyframes for a "Ken Burns" style zoom effect.
     */
    fun createKenBurnsKeyframes(
        durationMs: Long,
        startScale: Float = 1f,
        endScale: Float = 1.3f,
        startX: Float = 0f,
        startY: Float = 0f,
        endX: Float = 0.1f,
        endY: Float = 0.05f
    ): List<Keyframe> {
        return listOf(
            Keyframe(0L, KeyframeProperty.SCALE_X, startScale, Easing.EASE_IN_OUT),
            Keyframe(0L, KeyframeProperty.SCALE_Y, startScale, Easing.EASE_IN_OUT),
            Keyframe(0L, KeyframeProperty.POSITION_X, startX, Easing.EASE_IN_OUT),
            Keyframe(0L, KeyframeProperty.POSITION_Y, startY, Easing.EASE_IN_OUT),
            Keyframe(durationMs, KeyframeProperty.SCALE_X, endScale, Easing.EASE_IN_OUT),
            Keyframe(durationMs, KeyframeProperty.SCALE_Y, endScale, Easing.EASE_IN_OUT),
            Keyframe(durationMs, KeyframeProperty.POSITION_X, endX, Easing.EASE_IN_OUT),
            Keyframe(durationMs, KeyframeProperty.POSITION_Y, endY, Easing.EASE_IN_OUT),
        )
    }

    /**
     * Create fade-in keyframes.
     */
    fun createFadeIn(durationMs: Long = 500L): List<Keyframe> {
        return listOf(
            Keyframe(0L, KeyframeProperty.OPACITY, 0f, Easing.EASE_OUT),
            Keyframe(durationMs, KeyframeProperty.OPACITY, 1f, Easing.EASE_OUT)
        )
    }

    /**
     * Create fade-out keyframes.
     */
    fun createFadeOut(clipDurationMs: Long, fadeDurationMs: Long = 500L): List<Keyframe> {
        return listOf(
            Keyframe(clipDurationMs - fadeDurationMs, KeyframeProperty.OPACITY, 1f, Easing.EASE_IN),
            Keyframe(clipDurationMs, KeyframeProperty.OPACITY, 0f, Easing.EASE_IN)
        )
    }

    /**
     * Create volume ducking keyframes (lower volume in the middle).
     */
    fun createVolumeDuck(
        startMs: Long,
        endMs: Long,
        normalVolume: Float = 1f,
        duckVolume: Float = 0.2f,
        fadeMs: Long = 300L
    ): List<Keyframe> {
        return listOf(
            Keyframe(startMs, KeyframeProperty.VOLUME, normalVolume, Easing.EASE_IN),
            Keyframe(startMs + fadeMs, KeyframeProperty.VOLUME, duckVolume, Easing.EASE_IN),
            Keyframe(endMs - fadeMs, KeyframeProperty.VOLUME, duckVolume, Easing.EASE_OUT),
            Keyframe(endMs, KeyframeProperty.VOLUME, normalVolume, Easing.EASE_OUT)
        )
    }
}

data class AnimatedState(
    val positionX: Float? = null,
    val positionY: Float? = null,
    val scaleX: Float? = null,
    val scaleY: Float? = null,
    val rotation: Float? = null,
    val opacity: Float? = null,
    val volume: Float? = null
)
