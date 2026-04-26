package com.novacut.editor.engine

import com.novacut.editor.model.Effect
import com.novacut.editor.model.EffectType
import com.novacut.editor.model.TrackedObject
import com.novacut.editor.model.TrackedObjectKeyframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TrackedObjectEffectBindingTest {

    @Test
    fun sampleAt_interpolatesBetweenKeyframes() {
        val trackedObject = trackedObject(
            keyframes = listOf(
                TrackedObjectKeyframe(
                    clipTimeMs = 0L,
                    centerX = 0.2f,
                    centerY = 0.3f,
                    width = 0.1f,
                    height = 0.2f,
                    confidence = 0.5f
                ),
                TrackedObjectKeyframe(
                    clipTimeMs = 1_000L,
                    centerX = 0.6f,
                    centerY = 0.7f,
                    width = 0.3f,
                    height = 0.4f,
                    confidence = 0.9f
                )
            )
        )

        val sample = TrackedObjectEffectBinding.sampleAt(trackedObject, 500L)

        assertNotNull(sample)
        assertEquals(0.4f, sample!!.centerX, 0.0001f)
        assertEquals(0.5f, sample.centerY, 0.0001f)
        assertEquals(0.2f, sample.width, 0.0001f)
        assertEquals(0.3f, sample.height, 0.0001f)
        assertEquals(0.7f, sample.confidence, 0.0001f)
    }

    @Test
    fun resolveTarget_requiresTrackedMosaicEnabledTargetWithSamples() {
        val effect = Effect(
            type = EffectType.TRACKED_MOSAIC,
            targetTrackedObjectId = "target"
        )
        val disabled = trackedObject(id = "target", isEnabled = false)
        val empty = trackedObject(id = "target", keyframes = emptyList())
        val enabled = trackedObject(id = "target")

        assertNull(TrackedObjectEffectBinding.resolveTarget(effect, listOf(disabled)))
        assertNull(TrackedObjectEffectBinding.resolveTarget(effect, listOf(empty)))
        assertEquals(enabled, TrackedObjectEffectBinding.resolveTarget(effect, listOf(enabled)))
        assertNull(
            TrackedObjectEffectBinding.resolveTarget(
                effect.copy(type = EffectType.MOSAIC),
                listOf(enabled)
            )
        )
    }

    @Test
    fun uniformsForPresentationTime_addsClipTrimOffset() {
        val trackedObject = trackedObject(
            keyframes = listOf(
                TrackedObjectKeyframe(
                    clipTimeMs = 1_000L,
                    centerX = 0.1f,
                    centerY = 0.2f,
                    width = 0.3f,
                    height = 0.4f,
                    confidence = 0.5f
                ),
                TrackedObjectKeyframe(
                    clipTimeMs = 2_000L,
                    centerX = 0.9f,
                    centerY = 0.8f,
                    width = 0.7f,
                    height = 0.6f,
                    confidence = 1f
                )
            )
        )

        val uniforms = TrackedObjectEffectBinding.uniformsForPresentationTime(
            trackedObject = trackedObject,
            presentationTimeUs = 500_000L,
            sourceTimeOffsetMs = 1_000L
        )

        assertEquals(0.5f, uniforms.getValue("uCenterX"), 0.0001f)
        assertEquals(0.5f, uniforms.getValue("uCenterY"), 0.0001f)
        assertEquals(0.5f, uniforms.getValue("uObjectWidth"), 0.0001f)
        assertEquals(0.5f, uniforms.getValue("uObjectHeight"), 0.0001f)
        assertEquals(0.75f, uniforms.getValue("uObjectConfidence"), 0.0001f)
    }

    private fun trackedObject(
        id: String = "object",
        isEnabled: Boolean = true,
        keyframes: List<TrackedObjectKeyframe> = listOf(
            TrackedObjectKeyframe(
                clipTimeMs = 0L,
                centerX = 0.5f,
                centerY = 0.5f,
                width = 0.25f,
                height = 0.25f
            )
        )
    ) = TrackedObject(
        id = id,
        label = "Subject",
        sourceClipId = "clip",
        isEnabled = isEnabled,
        keyframes = keyframes
    )
}
