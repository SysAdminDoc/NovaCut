package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioMasteringEngineTest {

    private val engine = AudioMasteringEngine()

    @Test
    fun presets_idsAreUnique() {
        val ids = engine.getPresets().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun presets_nonEmptyDisplayNames() {
        engine.getPresets().forEach {
            assertTrue("Empty displayName for ${it.id}", it.displayName.isNotBlank())
            assertTrue("Empty description for ${it.id}", it.description.isNotBlank())
        }
    }

    @Test
    fun presets_eqGainsWithinRange() {
        // Mastering chains should not have wild gain values -- cap at ±12 dB
        // which is the max a reasonable post-tuning chain would apply.
        engine.getPresets().forEach { chain ->
            chain.eqBands.forEach { band ->
                assertTrue(
                    "EQ gain out of range in ${chain.id}: ${band.gainDb} dB",
                    band.gainDb in -12f..12f
                )
                assertTrue(
                    "EQ frequency out of audible range in ${chain.id}: ${band.frequencyHz} Hz",
                    band.frequencyHz in 20f..20_000f
                )
                assertTrue(
                    "EQ Q out of reasonable range in ${chain.id}: ${band.q}",
                    band.q in 0.1f..10f
                )
            }
        }
    }

    @Test
    fun presets_lufsTargetsWithinRange() {
        engine.getPresets().forEach { chain ->
            assertTrue(
                "LUFS target unrealistic in ${chain.id}: ${chain.targetLufs}",
                chain.targetLufs in -30f..-6f
            )
            assertTrue(
                "True peak above 0 dBFS in ${chain.id}: ${chain.truePeakDb}",
                chain.truePeakDb <= 0f
            )
        }
    }

    @Test
    fun presets_compressorRatiosReasonable() {
        engine.getPresets().forEach { chain ->
            assertTrue(
                "Compressor ratio unrealistic in ${chain.id}: ${chain.compressorRatio}",
                chain.compressorRatio in 1f..20f
            )
        }
    }

    @Test
    fun getPreset_knownId_returnsChain() {
        assertNotNull(engine.getPreset("podcast_voice"))
    }

    @Test
    fun getPreset_unknownId_returnsNull() {
        assertNull(engine.getPreset("nonexistent-preset"))
    }
}
