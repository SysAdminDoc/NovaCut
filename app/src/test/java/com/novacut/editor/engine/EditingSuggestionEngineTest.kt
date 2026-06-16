package com.novacut.editor.engine

import android.net.FakeUri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Effect
import com.novacut.editor.model.EffectType
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import com.novacut.editor.model.Transition
import com.novacut.editor.model.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EditingSuggestionEngineTest {

    private val engine = EditingSuggestionEngine()

    private fun clip(
        id: String = "c1",
        durationMs: Long = 5000L,
        effects: List<Effect> = emptyList(),
        headTransition: Transition? = null
    ) = Clip(
        id = id,
        sourceUri = FakeUri,
        sourceDurationMs = durationMs,
        trimStartMs = 0L,
        trimEndMs = durationMs,
        timelineStartMs = 0L,
        effects = effects,
        headTransition = headTransition
    )

    private fun track(index: Int = 0, type: TrackType = TrackType.VIDEO, vararg clips: Clip) = Track(
        id = "t$index",
        type = type,
        index = index,
        clips = clips.toList()
    )

    @Test
    fun emptyTimeline_returnsNull() {
        assertNull(engine.analyze(emptyList()))
    }

    @Test
    fun singleLongClip_suggestsAutoColor() {
        val t = track(0, TrackType.VIDEO, clip(durationMs = 15000L))
        val suggestion = engine.analyze(listOf(t))
        assertNotNull(suggestion)
        assertEquals("auto_color", suggestion!!.actionId)
    }

    @Test
    fun multipleClipsNoTransitions_suggestsTransitions() {
        val clips = (1..4).map { clip(id = "c$it", durationMs = 3000L) }
        val t = Track(id = "t1", type = TrackType.VIDEO, index = 0, clips = clips)
        val suggestion = engine.analyze(listOf(t))
        assertNotNull(suggestion)
        assertEquals("transitions", suggestion!!.actionId)
    }

    @Test
    fun multipleClipsWithTransitions_noTransitionSuggestion() {
        val clips = (1..4).map {
            clip(id = "c$it", durationMs = 3000L,
                headTransition = if (it > 1) Transition(TransitionType.DISSOLVE) else null)
        }
        val t = Track(id = "t1", type = TrackType.VIDEO, index = 0, clips = clips)
        val suggestion = engine.analyze(listOf(t))
        if (suggestion != null) {
            assert(suggestion.actionId != "transitions")
        }
    }

    @Test
    fun audioTrackWithoutBeats_suggestsBeatSync() {
        val videoTrack = track(0, TrackType.VIDEO, clip(durationMs = 5000L,
            effects = listOf(Effect(type = EffectType.BRIGHTNESS))))
        val audioTrack = track(1, TrackType.AUDIO, clip(id = "a1", durationMs = 10000L))
        val suggestion = engine.analyze(listOf(videoTrack, audioTrack), hasBeatMarkers = false)
        assertNotNull(suggestion)
        assertEquals("beat_sync", suggestion!!.actionId)
    }

    @Test
    fun longClip_suggestsSceneDetection() {
        val t = track(0, TrackType.VIDEO,
            clip(durationMs = 45000L, effects = listOf(Effect(type = EffectType.BRIGHTNESS))))
        val suggestion = engine.analyze(listOf(t))
        assertNotNull(suggestion)
        assertEquals("scene_detect", suggestion!!.actionId)
    }
}
