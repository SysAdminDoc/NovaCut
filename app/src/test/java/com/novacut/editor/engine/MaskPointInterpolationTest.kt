package com.novacut.editor.engine

import com.novacut.editor.model.MaskPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for mask outline morphing between keyframes with
 * different point counts. The previous implementation truncated to
 * minOf(a.size, b.size), silently dropping points and corrupting the
 * shape mid-morph in both preview and export.
 */
class MaskPointInterpolationTest {

    private fun pts(vararg xy: Pair<Float, Float>): List<MaskPoint> =
        xy.map { (x, y) -> MaskPoint(x = x, y = y) }

    @Test
    fun `equal point counts lerp pairwise`() {
        val a = pts(0f to 0f, 1f to 0f)
        val b = pts(0f to 1f, 1f to 1f)
        val mid = KeyframeEngine.interpolatePointLists(a, b, 0.5f)
        assertEquals(2, mid.size)
        assertEquals(0.5f, mid[0].y, 1e-4f)
        assertEquals(0.5f, mid[1].y, 1e-4f)
        assertEquals(0f, mid[0].x, 1e-4f)
        assertEquals(1f, mid[1].x, 1e-4f)
    }

    @Test
    fun `mismatched counts keep the larger outline size`() {
        val a = pts(0f to 0f, 0.5f to 0f, 1f to 0f, 1f to 1f, 0f to 1f) // 5 points
        val b = pts(0f to 0f, 1f to 0f, 0.5f to 1f)                     // 3 points
        val mid = KeyframeEngine.interpolatePointLists(a, b, 0.5f)
        assertEquals(5, mid.size)
    }

    @Test
    fun `endpoints of a mismatched morph match the keyframe shapes`() {
        val a = pts(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f)
        val b = pts(0f to 0f, 2f to 0f, 1f to 2f)
        val atStart = KeyframeEngine.interpolatePointLists(a, b, 0f)
        val atEnd = KeyframeEngine.interpolatePointLists(a, b, 1f)
        // First and last original points survive resampling exactly at t=0/1.
        assertEquals(a.first().x, atStart.first().x, 1e-4f)
        assertEquals(a.last().y, atStart.last().y, 1e-4f)
        assertEquals(b.first().x, atEnd.first().x, 1e-4f)
        assertEquals(b.last().y, atEnd.last().y, 1e-4f)
    }

    @Test
    fun `empty keyframe snaps to the nearer shape instead of erasing the mask`() {
        val a = pts(0f to 0f, 1f to 1f)
        val empty = emptyList<MaskPoint>()
        assertEquals(a, KeyframeEngine.interpolatePointLists(a, empty, 0.25f))
        assertTrue(KeyframeEngine.interpolatePointLists(a, empty, 0.75f).isEmpty())
        assertEquals(a, KeyframeEngine.interpolatePointLists(empty, a, 0.75f))
    }

    @Test
    fun `single point keyframe interpolates against every target point`() {
        val a = pts(0.5f to 0.5f)
        val b = pts(0f to 0f, 1f to 0f, 1f to 1f)
        val mid = KeyframeEngine.interpolatePointLists(a, b, 0.5f)
        assertEquals(3, mid.size)
        // All resampled source points are the single origin point, so every
        // result lies halfway between origin and the matching target point.
        assertEquals(0.25f, mid[0].x, 1e-4f)
        assertEquals(0.25f, mid[0].y, 1e-4f)
        assertEquals(0.75f, mid[2].x, 1e-4f)
        assertEquals(0.75f, mid[2].y, 1e-4f)
    }

    @Test
    fun `bezier handles interpolate alongside anchor points`() {
        val a = listOf(MaskPoint(x = 0f, y = 0f, handleInX = -1f, handleInY = 0f, handleOutX = 1f, handleOutY = 0f))
        val b = listOf(MaskPoint(x = 0f, y = 0f, handleInX = -3f, handleInY = 0f, handleOutX = 3f, handleOutY = 0f))
        val mid = KeyframeEngine.interpolatePointLists(a, b, 0.5f)
        assertEquals(-2f, mid[0].handleInX, 1e-4f)
        assertEquals(2f, mid[0].handleOutX, 1e-4f)
    }
}
