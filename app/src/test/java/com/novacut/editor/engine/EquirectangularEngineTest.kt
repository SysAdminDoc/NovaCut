package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EquirectangularEngineTest {

    private val engine = EquirectangularEngine()

    @Test
    fun poseAt_emptyKeyframes_returnsIdentity() {
        val pose = engine.poseAt(1000L, emptyList())
        assertEquals(0f, pose.yawDeg, 1e-3f)
        assertEquals(0f, pose.pitchDeg, 1e-3f)
    }

    @Test
    fun poseAt_beforeFirstKeyframe_clampsToFirst() {
        val kfs = listOf(
            EquirectangularEngine.KeyframedPose(1000L, EquirectangularEngine.Pose(yawDeg = 90f)),
            EquirectangularEngine.KeyframedPose(2000L, EquirectangularEngine.Pose(yawDeg = 180f))
        )
        val pose = engine.poseAt(500L, kfs)
        assertEquals(90f, pose.yawDeg, 1e-3f)
    }

    @Test
    fun poseAt_afterLastKeyframe_clampsToLast() {
        val kfs = listOf(
            EquirectangularEngine.KeyframedPose(1000L, EquirectangularEngine.Pose(yawDeg = 90f)),
            EquirectangularEngine.KeyframedPose(2000L, EquirectangularEngine.Pose(yawDeg = 180f))
        )
        val pose = engine.poseAt(3000L, kfs)
        assertEquals(180f, pose.yawDeg, 1e-3f)
    }

    @Test
    fun poseAt_linearLerp_midpoint() {
        val kfs = listOf(
            EquirectangularEngine.KeyframedPose(1000L, EquirectangularEngine.Pose(pitchDeg = 0f)),
            EquirectangularEngine.KeyframedPose(3000L, EquirectangularEngine.Pose(pitchDeg = 60f))
        )
        val pose = engine.poseAt(2000L, kfs)
        assertEquals(30f, pose.pitchDeg, 1e-3f) // halfway
    }

    @Test
    fun poseAt_unsortedKeyframes_sortedInternally() {
        // Regression guard -- callers should not have to pre-sort.
        val kfs = listOf(
            EquirectangularEngine.KeyframedPose(3000L, EquirectangularEngine.Pose(pitchDeg = 60f)),
            EquirectangularEngine.KeyframedPose(1000L, EquirectangularEngine.Pose(pitchDeg = 0f))
        )
        val pose = engine.poseAt(2000L, kfs)
        assertEquals(30f, pose.pitchDeg, 1e-3f)
    }

    @Test
    fun poseAt_yawWrapAround_takesShortestPath() {
        // From 179° to -179° the shortest angular distance is 2°, NOT 358°.
        val kfs = listOf(
            EquirectangularEngine.KeyframedPose(0L, EquirectangularEngine.Pose(yawDeg = 179f)),
            EquirectangularEngine.KeyframedPose(1000L, EquirectangularEngine.Pose(yawDeg = -179f))
        )
        val pose = engine.poseAt(500L, kfs)
        // Halfway through a 2° short-path sweep should land at ±180° (wraps).
        // Accept either representation of the wrap.
        val yaw = pose.yawDeg
        val isNear180 = kotlin.math.abs(yaw - 180f) < 1f || kotlin.math.abs(yaw + 180f) < 1f
        assertTrue("Yaw should be near ±180°, got $yaw", isNear180)
    }

    @Test
    fun poseAt_easeIn_notLinear() {
        val kfs = listOf(
            EquirectangularEngine.KeyframedPose(
                0L, EquirectangularEngine.Pose(pitchDeg = 0f),
                EquirectangularEngine.KeyframedPose.Easing.EASE_IN
            ),
            EquirectangularEngine.KeyframedPose(
                1000L, EquirectangularEngine.Pose(pitchDeg = 90f)
            )
        )
        val midLinear = 45f
        val pose = engine.poseAt(500L, kfs)
        // EASE_IN at t=0.5 is 0.25, so pitch should be 22.5, below linear midpoint
        assertTrue("EASE_IN should lag at midpoint", pose.pitchDeg < midLinear)
        assertEquals(22.5f, pose.pitchDeg, 1e-3f)
    }

    @Test
    fun pose_nanYaw_throws() {
        // Regression guard -- NaN must be rejected at construction because
        // `coerceIn` does not clamp NaN and would silently poison the GL pipeline.
        try {
            EquirectangularEngine.Pose(yawDeg = Float.NaN)
            assert(false) { "Should have thrown on NaN yaw" }
        } catch (_: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun pose_infinitePitch_throws() {
        try {
            EquirectangularEngine.Pose(pitchDeg = Float.POSITIVE_INFINITY)
            assert(false) { "Should have thrown on infinite pitch" }
        } catch (_: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun pose_outOfRangeYaw_throws() {
        try {
            EquirectangularEngine.Pose(yawDeg = 500f)
            assert(false) { "Should have thrown on out-of-range yaw" }
        } catch (_: IllegalArgumentException) { /* expected */ }
    }
}
