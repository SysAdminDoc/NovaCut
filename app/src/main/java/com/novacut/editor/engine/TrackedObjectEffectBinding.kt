package com.novacut.editor.engine

import com.novacut.editor.model.Effect
import com.novacut.editor.model.EffectType
import com.novacut.editor.model.TrackedObject

internal object TrackedObjectEffectBinding {
    data class Sample(
        val centerX: Float,
        val centerY: Float,
        val width: Float,
        val height: Float,
        val confidence: Float
    )

    fun resolveTarget(effect: Effect, trackedObjects: List<TrackedObject>): TrackedObject? {
        if (effect.type != EffectType.TRACKED_MOSAIC) return null
        val targetId = effect.targetTrackedObjectId?.takeIf { it.isNotBlank() } ?: return null
        return trackedObjects.firstOrNull { trackedObject ->
            trackedObject.id == targetId &&
                trackedObject.isEnabled &&
                trackedObject.keyframes.isNotEmpty()
        }
    }

    fun sampleAt(trackedObject: TrackedObject, sourceTimeMs: Long): Sample? {
        val keyframes = trackedObject.keyframes.sortedBy { it.clipTimeMs }
        if (keyframes.isEmpty()) return null
        val first = keyframes.first()
        if (sourceTimeMs <= first.clipTimeMs) {
            return first.toSample()
        }
        val last = keyframes.last()
        if (sourceTimeMs >= last.clipTimeMs) {
            return last.toSample()
        }
        val nextIndex = keyframes.indexOfFirst { it.clipTimeMs >= sourceTimeMs }
        if (nextIndex <= 0) return keyframes.first().toSample()
        val previous = keyframes[nextIndex - 1]
        val next = keyframes[nextIndex]
        val span = (next.clipTimeMs - previous.clipTimeMs).coerceAtLeast(1L)
        val t = ((sourceTimeMs - previous.clipTimeMs).toFloat() / span).coerceIn(0f, 1f)
        return Sample(
            centerX = lerp(previous.centerX, next.centerX, t).coerceIn(0f, 1f),
            centerY = lerp(previous.centerY, next.centerY, t).coerceIn(0f, 1f),
            width = lerp(previous.width, next.width, t).coerceIn(0.001f, 1f),
            height = lerp(previous.height, next.height, t).coerceIn(0.001f, 1f),
            confidence = lerp(previous.confidence, next.confidence, t).coerceIn(0f, 1f)
        )
    }

    fun uniformsForPresentationTime(
        trackedObject: TrackedObject,
        presentationTimeUs: Long,
        sourceTimeOffsetMs: Long
    ): Map<String, Float> {
        val sourceTimeMs = presentationTimeUs / 1000L + sourceTimeOffsetMs
        val sample = sampleAt(trackedObject, sourceTimeMs)
        return mapOf(
            "uCenterX" to (sample?.centerX ?: 0f),
            "uCenterY" to (sample?.centerY ?: 0f),
            "uObjectWidth" to (sample?.width ?: 0f),
            "uObjectHeight" to (sample?.height ?: 0f),
            "uObjectConfidence" to (sample?.confidence ?: 0f)
        )
    }

    private fun com.novacut.editor.model.TrackedObjectKeyframe.toSample() = Sample(
        centerX = centerX.coerceIn(0f, 1f),
        centerY = centerY.coerceIn(0f, 1f),
        width = width.coerceIn(0.001f, 1f),
        height = height.coerceIn(0.001f, 1f),
        confidence = confidence.coerceIn(0f, 1f)
    )

    private fun lerp(start: Float, end: Float, t: Float): Float = start + (end - start) * t
}
