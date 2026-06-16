package com.novacut.editor.engine

import com.novacut.editor.model.Clip
import com.novacut.editor.model.EffectType
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditingSuggestionEngine @Inject constructor() {

    data class Suggestion(
        val id: String,
        val message: String,
        val actionId: String,
        val priority: Int = 0
    )

    fun analyze(
        tracks: List<Track>,
        hasTranscript: Boolean = false,
        hasBeatMarkers: Boolean = false
    ): Suggestion? {
        val videoClips = tracks
            .filter { it.type == TrackType.VIDEO && it.isVisible }
            .flatMap { it.clips }
            .filter { it.durationMs > 0L }

        if (videoClips.isEmpty()) return null

        val hasEffects = videoClips.any { it.effects.any { e -> e.enabled } }
        val hasTransitions = videoClips.any { it.headTransition != null || it.tailTransition != null }
        val hasColorGrade = videoClips.any { it.colorGrade?.enabled == true }
        val totalDurationMs = videoClips.maxOfOrNull { it.timelineEndMs } ?: 0L
        val audioClips = tracks
            .filter { it.type == TrackType.AUDIO && it.isVisible }
            .flatMap { it.clips }

        if (!hasEffects && videoClips.size == 1 && videoClips[0].durationMs > 10_000L) {
            return Suggestion(
                id = "auto_color",
                message = "Try Auto Color to enhance this clip",
                actionId = "auto_color",
                priority = 10
            )
        }

        if (!hasTransitions && videoClips.size >= 2) {
            return Suggestion(
                id = "add_transitions",
                message = "Add transitions between your ${videoClips.size} clips",
                actionId = "transitions",
                priority = 20
            )
        }

        if (audioClips.isNotEmpty() && !hasBeatMarkers) {
            return Suggestion(
                id = "beat_sync",
                message = "Sync cuts to the beat of your music",
                actionId = "beat_sync",
                priority = 15
            )
        }

        if (videoClips.size >= 3 && !hasTranscript) {
            return Suggestion(
                id = "auto_captions",
                message = "Add auto captions to make your video accessible",
                actionId = "auto_captions",
                priority = 12
            )
        }

        if (videoClips.any { it.durationMs > 30_000L }) {
            val longClip = videoClips.first { it.durationMs > 30_000L }
            return Suggestion(
                id = "scene_detect",
                message = "Split long clip into scenes automatically",
                actionId = "scene_detect",
                priority = 18
            )
        }

        if (!hasColorGrade && hasEffects) {
            return Suggestion(
                id = "color_grade",
                message = "Try color grading for a cinematic look",
                actionId = "color_grade",
                priority = 5
            )
        }

        if (totalDurationMs > 60_000L && videoClips.size >= 5) {
            return Suggestion(
                id = "auto_edit",
                message = "Generate a highlight reel from your clips",
                actionId = "auto_edit",
                priority = 8
            )
        }

        return null
    }
}
