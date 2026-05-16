package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * R6.15 — WordEmphasisAnimator math contract tests.
 */
class WordEmphasisAnimatorTest {

    private fun near(actual: Float, expected: Float, eps: Float = 1e-4f) {
        assertTrue("Expected ~$expected got $actual", abs(actual - expected) < eps)
    }

    // --- NONE ---

    @Test
    fun none_alwaysReturnsIdentity() {
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { t ->
            val s = WordEmphasisAnimator.emphasisFor(WordEmphasisAnimator.Animation.NONE, t)
            assertEquals(1f, s.scale)
            assertEquals(0f, s.offsetXPx)
            assertEquals(0f, s.offsetYPx)
            assertEquals(1f, s.alpha)
            assertEquals(0f, s.emphasisMix)
        }
    }

    // --- POP ---

    @Test
    fun pop_peaksAtMidpoint() {
        val s = WordEmphasisAnimator.emphasisFor(WordEmphasisAnimator.Animation.POP, 0.5f)
        // 1 + 0.18 * sin(pi/2) = 1.18
        near(s.scale, 1.18f)
    }

    @Test
    fun pop_startsAndEndsAtIdentity() {
        val start = WordEmphasisAnimator.emphasisFor(WordEmphasisAnimator.Animation.POP, 0f)
        val end = WordEmphasisAnimator.emphasisFor(WordEmphasisAnimator.Animation.POP, 1f)
        near(start.scale, 1f)
        near(end.scale, 1f)
    }

    // --- BOUNCE ---

    @Test
    fun bounce_startsAtZeroOffset() {
        val s = WordEmphasisAnimator.emphasisFor(WordEmphasisAnimator.Animation.BOUNCE, 0f)
        near(s.offsetYPx, 0f)
    }

    @Test
    fun bounce_endsAtZeroOffset() {
        val s = WordEmphasisAnimator.emphasisFor(WordEmphasisAnimator.Animation.BOUNCE, 1f)
        near(s.offsetYPx, 0f)
    }

    @Test
    fun bounce_offsetIsNegativeAtPeak() {
        // The peak of -damp * sin(2*pi*t) is around t=0.25 where damp=0.75 and
        // sin(pi/2)=1, giving offset = -baselineFont * 0.35 * 0.75 = -0.2625 * font.
        val s = WordEmphasisAnimator.emphasisFor(
            WordEmphasisAnimator.Animation.BOUNCE,
            wordProgress = 0.25f,
            baselineFontSizePx = 100f,
        )
        assertTrue("Expected negative offset at peak, got ${s.offsetYPx}", s.offsetYPx < 0f)
    }

    // --- GLOW ---

    @Test
    fun glow_mixPeaksAtMidpoint() {
        val s = WordEmphasisAnimator.emphasisFor(
            animation = WordEmphasisAnimator.Animation.GLOW,
            wordProgress = 0.5f,
            emphasisColor = 0xFFFF6600L,
        )
        near(s.emphasisMix, 1f)
        assertEquals(0xFFFF6600L, s.emphasisColor)
    }

    @Test
    fun glow_mixIsZeroAtBoundaries() {
        val start = WordEmphasisAnimator.emphasisFor(WordEmphasisAnimator.Animation.GLOW, 0f)
        val end = WordEmphasisAnimator.emphasisFor(WordEmphasisAnimator.Animation.GLOW, 1f)
        near(start.emphasisMix, 0f)
        near(end.emphasisMix, 0f)
    }

    // --- SLIDE_IN ---

    @Test
    fun slideIn_startsOffscreenRightAndFades() {
        val s = WordEmphasisAnimator.emphasisFor(
            WordEmphasisAnimator.Animation.SLIDE_IN,
            wordProgress = 0f,
            baselineFontSizePx = 40f,
        )
        // ease = 0 at t=0 → offsetX = travel = 1.5 * 40 = 60.
        near(s.offsetXPx, 60f)
        near(s.alpha, 0f)
    }

    @Test
    fun slideIn_endsAtRest() {
        val s = WordEmphasisAnimator.emphasisFor(WordEmphasisAnimator.Animation.SLIDE_IN, 1f)
        near(s.offsetXPx, 0f)
        near(s.alpha, 1f)
    }

    // --- progress clamping ---

    @Test
    fun emphasisFor_clampsOutOfRangeProgress() {
        val below = WordEmphasisAnimator.emphasisFor(WordEmphasisAnimator.Animation.POP, -0.5f)
        val above = WordEmphasisAnimator.emphasisFor(WordEmphasisAnimator.Animation.POP, 1.5f)
        // Both clamp to identity (POP at t=0 and t=1 is 1.0).
        near(below.scale, 1f)
        near(above.scale, 1f)
    }

    // --- wordProgress helper ---

    @Test
    fun wordProgress_zeroBeforeStart() {
        val p = WordEmphasisAnimator.wordProgress(
            playheadMs = 50L,
            wordStartMs = 100L,
            wordEndMs = 300L,
            animationWindowMs = 200L,
        )
        near(p, 0f)
    }

    @Test
    fun wordProgress_halfwayThroughWindow() {
        val p = WordEmphasisAnimator.wordProgress(
            playheadMs = 200L,
            wordStartMs = 100L,
            wordEndMs = 400L,
            animationWindowMs = 200L,
        )
        // elapsed = 100, window = min(200, 300) = 200 → p = 0.5.
        near(p, 0.5f)
    }

    @Test
    fun wordProgress_atEndOfWindowIsOne() {
        val p = WordEmphasisAnimator.wordProgress(
            playheadMs = 350L,
            wordStartMs = 100L,
            wordEndMs = 600L,
            animationWindowMs = 200L,
        )
        // elapsed = 250 >= window 200 → 1.
        near(p, 1f)
    }

    @Test
    fun wordProgress_shorterThanWindow_usesWordDuration() {
        // 50 ms word with a 200 ms requested window — animator uses the word
        // duration so the animation completes within the spoken duration.
        val p = WordEmphasisAnimator.wordProgress(
            playheadMs = 125L,
            wordStartMs = 100L,
            wordEndMs = 150L,
            animationWindowMs = 200L,
        )
        // elapsed = 25, window = min(200, 50) = 50 → 0.5.
        near(p, 0.5f)
    }

    @Test
    fun wordProgress_invalidWindow_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            WordEmphasisAnimator.wordProgress(0L, 100L, 100L, 200L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WordEmphasisAnimator.wordProgress(0L, 100L, 200L, 0L)
        }
    }

    @Test
    fun maxConcurrentAnimatingWords_constantIsThree() {
        // R6.15b performance budget. Locking via test so an accidental bump
        // forces the author to re-evaluate the render cost.
        assertEquals(3, WordEmphasisAnimator.DEFAULT_MAX_CONCURRENT_ANIMATING_WORDS)
    }
}
