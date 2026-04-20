package com.novacut.editor.engine

import com.novacut.editor.model.Effect
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adjustment-layer composition helper. See ROADMAP.md Tier C.11.
 *
 * An "adjustment layer" is a track-level construct that applies a list of effects
 * to every clip beneath it across its time range, without needing per-clip effect
 * duplication. Pro-NLE staple.
 *
 * Data model (to be added to [com.novacut.editor.model.Track]):
 *   data class AdjustmentLayer(
 *       val id: String,
 *       val trackId: String,
 *       val startTimeMs: Long,
 *       val endTimeMs: Long,
 *       val effects: List<Effect>,
 *       val opacity: Float = 1f,
 *       val enabled: Boolean = true
 *   )
 *
 * Engine computes, per clip, the effect list contribution of overlapping adjustment
 * layers. Output feeds [EffectBuilder] which already consumes List<Effect>.
 */
@Singleton
class AdjustmentLayerEngine @Inject constructor() {

    data class AdjustmentLayer(
        val id: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val effects: List<Effect>,
        val opacity: Float = 1f,
        val enabled: Boolean = true
    ) {
        init {
            require(startTimeMs >= 0L)
            require(endTimeMs > startTimeMs) { "AdjustmentLayer must span a non-zero range" }
            require(opacity in 0f..1f)
        }

        val durationMs: Long get() = endTimeMs - startTimeMs
    }

    /**
     * Return the effect contributions from every adjustment layer that overlaps the
     * clip's timeline range. Layers later in [layers] compose on top of earlier ones.
     */
    fun effectsForClip(
        clipStartMs: Long,
        clipEndMs: Long,
        layers: List<AdjustmentLayer>
    ): List<Effect> {
        if (layers.isEmpty() || clipEndMs <= clipStartMs) return emptyList()
        val out = mutableListOf<Effect>()
        for (layer in layers) {
            if (!layer.enabled) continue
            if (layer.endTimeMs <= clipStartMs || layer.startTimeMs >= clipEndMs) continue
            out += layer.effects
        }
        return out
    }

    /**
     * Partition the clip range into sub-ranges by overlapping adjustment-layer boundaries.
     * Used by [EffectBuilder] when adjustment layers start/end mid-clip -- each sub-range
     * gets its own effect-chain segment.
     *
     * Example: clip 0-10s, layer 3-7s -> returns [(0,3), (3,7), (7,10)].
     */
    fun partitionByLayerBoundaries(
        clipStartMs: Long,
        clipEndMs: Long,
        layers: List<AdjustmentLayer>
    ): List<LongRange> {
        if (clipEndMs <= clipStartMs) return emptyList()
        val boundaries = sortedSetOf(clipStartMs, clipEndMs)
        for (layer in layers) {
            if (!layer.enabled) continue
            if (layer.endTimeMs <= clipStartMs || layer.startTimeMs >= clipEndMs) continue
            if (layer.startTimeMs > clipStartMs) boundaries += layer.startTimeMs
            if (layer.endTimeMs < clipEndMs) boundaries += layer.endTimeMs
        }
        return boundaries.toList().zipWithNext { a, b -> a until b }
    }
}
