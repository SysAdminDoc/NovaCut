package com.novacut.editor.engine

import com.novacut.editor.model.*

/**
 * Analyzes the timeline to detect which segments need re-encoding vs pass-through.
 * Unchanged segments (no effects, no color grade, no transform, no speed change)
 * can be muxed directly without re-encoding, dramatically speeding up export.
 */
object SmartRenderEngine {

    data class RenderSegment(
        val clipId: String,
        val startMs: Long,
        val endMs: Long,
        val needsReEncode: Boolean,
        val reason: String
    )

    /**
     * Analyze all clips to determine which need re-encoding.
     * Returns a list of render segments with their encoding requirements.
     */
    fun analyzeTimeline(
        tracks: List<Track>,
        config: ExportConfig,
        textOverlays: List<TextOverlay> = emptyList()
    ): List<RenderSegment> {
        val segments = mutableListOf<RenderSegment>()

        for (track in tracks) {
            if (!track.isVisible) continue

            for (clip in track.clips) {
                val reasons = mutableListOf<String>()

                // Check if clip needs re-encoding
                if (clip.effects.any { it.enabled }) {
                    reasons.add("effects (${clip.effects.count { it.enabled }})")
                }
                if (clip.colorGrade != null && clip.colorGrade.enabled) {
                    reasons.add("color grade")
                }
                if (clip.transition != null) {
                    reasons.add("transition")
                }
                if (clip.speed != 1f || clip.speedCurve != null) {
                    reasons.add("speed change")
                }
                if (clip.isReversed) {
                    reasons.add("reversed")
                }
                if (clip.rotation != 0f || clip.scaleX != 1f || clip.scaleY != 1f ||
                    clip.positionX != 0f || clip.positionY != 0f
                ) {
                    reasons.add("transform")
                }
                if (clip.opacity != 1f) {
                    reasons.add("opacity")
                }
                if (clip.masks.isNotEmpty()) {
                    reasons.add("masks")
                }
                if (clip.blendMode != BlendMode.NORMAL) {
                    reasons.add("blend mode")
                }
                if (clip.keyframes.isNotEmpty()) {
                    reasons.add("keyframes")
                }
                if (clip.captions.isNotEmpty()) {
                    reasons.add("captions")
                }

                // Check for text overlays on this clip's time range
                val hasOverlay = textOverlays.any { overlay ->
                    overlay.startTimeMs < clip.timelineEndMs && overlay.endTimeMs > clip.timelineStartMs
                }
                if (hasOverlay) {
                    reasons.add("text overlay")
                }

                // Check if resolution/codec change is needed
                // (would need source media info to compare — simplified here)

                segments.add(
                    RenderSegment(
                        clipId = clip.id,
                        startMs = clip.timelineStartMs,
                        endMs = clip.timelineEndMs,
                        needsReEncode = reasons.isNotEmpty(),
                        reason = if (reasons.isEmpty()) "pass-through" else reasons.joinToString(", ")
                    )
                )
            }
        }

        return segments.sortedBy { it.startMs }
    }

    /**
     * Get a summary of the smart render analysis.
     */
    fun getSummary(segments: List<RenderSegment>): SmartRenderSummary {
        val totalMs = segments.sumOf { it.endMs - it.startMs }
        val reEncodeMs = segments.filter { it.needsReEncode }.sumOf { it.endMs - it.startMs }
        val passThroughMs = totalMs - reEncodeMs

        return SmartRenderSummary(
            totalSegments = segments.size,
            reEncodeSegments = segments.count { it.needsReEncode },
            passThroughSegments = segments.count { !it.needsReEncode },
            totalDurationMs = totalMs,
            reEncodeDurationMs = reEncodeMs,
            passThroughDurationMs = passThroughMs,
            estimatedSpeedup = if (reEncodeMs > 0) totalMs.toFloat() / reEncodeMs else Float.MAX_VALUE
        )
    }

    data class SmartRenderSummary(
        val totalSegments: Int,
        val reEncodeSegments: Int,
        val passThroughSegments: Int,
        val totalDurationMs: Long,
        val reEncodeDurationMs: Long,
        val passThroughDurationMs: Long,
        val estimatedSpeedup: Float
    )
}
