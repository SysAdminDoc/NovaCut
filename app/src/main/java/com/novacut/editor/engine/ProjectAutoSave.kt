package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import com.novacut.editor.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ProjectAutoSave"

@Singleton
class ProjectAutoSave @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val autoSaveDir = File(context.filesDir, "autosave").apply { mkdirs() }
    private var autoSaveJob: Job? = null

    fun startAutoSave(
        projectId: String,
        getState: () -> AutoSaveState
    ) {
        autoSaveJob?.cancel()
        autoSaveJob = scope.launch {
            while (isActive) {
                delay(30_000)
                try {
                    saveState(projectId, getState())
                } catch (e: Exception) {
                    Log.e(TAG, "Auto-save failed for $projectId", e)
                }
            }
        }
    }

    fun saveNow(projectId: String, state: AutoSaveState) {
        scope.launch {
            try {
                saveState(projectId, state)
            } catch (e: Exception) {
                Log.e(TAG, "Manual save failed for $projectId", e)
            }
        }
    }

    fun hasRecoveryData(projectId: String): Boolean {
        return getAutoSaveFile(projectId).exists()
    }

    fun loadRecoveryData(projectId: String): AutoSaveState? {
        val file = getAutoSaveFile(projectId)
        if (!file.exists()) return null
        return try {
            AutoSaveState.deserialize(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun clearRecoveryData(projectId: String) {
        getAutoSaveFile(projectId).delete()
    }

    fun copyAutoSave(fromProjectId: String, toProjectId: String) {
        val fromFile = getAutoSaveFile(fromProjectId)
        if (!fromFile.exists()) return
        try {
            val json = JSONObject(fromFile.readText())
            json.put("projectId", toProjectId)
            json.put("timestamp", System.currentTimeMillis())
            val toFile = getAutoSaveFile(toProjectId)
            toFile.writeText(json.toString(2))
        } catch (_: Exception) { }
    }

    fun stop() {
        autoSaveJob?.cancel()
        autoSaveJob = null
    }

    private fun saveState(projectId: String, state: AutoSaveState) {
        val file = getAutoSaveFile(projectId)
        val tempFile = File(autoSaveDir, "${projectId}.tmp")
        tempFile.writeText(state.serialize())
        // renameTo can fail on some filesystems — fallback to copy+delete
        if (!tempFile.renameTo(file)) {
            tempFile.copyTo(file, overwrite = true)
            tempFile.delete()
        }
    }

    private fun getAutoSaveFile(projectId: String): File {
        return File(autoSaveDir, "${projectId}.json")
    }
}

data class AutoSaveState(
    val projectId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tracks: List<Track> = emptyList(),
    val textOverlays: List<TextOverlay> = emptyList(),
    val playheadMs: Long = 0L
) {
    fun serialize(): String {
        val json = JSONObject().apply {
            put("projectId", projectId)
            put("timestamp", timestamp)
            put("playheadMs", playheadMs)
            put("tracks", serializeTracks(tracks))
            put("textOverlays", serializeTextOverlays(textOverlays))
        }
        return json.toString(2)
    }

    companion object {
        // Safe enum valueOf with fallback — prevents crashes from stale/unknown enum values
        private inline fun <reified T : Enum<T>> safeValueOf(name: String, default: T): T {
            return try { enumValueOf<T>(name) } catch (_: IllegalArgumentException) { default }
        }

        fun deserialize(raw: String): AutoSaveState {
            val json = JSONObject(raw)
            return AutoSaveState(
                projectId = json.optString("projectId", ""),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                playheadMs = json.optLong("playheadMs", 0L),
                tracks = deserializeTracks(json.optJSONArray("tracks") ?: JSONArray()),
                textOverlays = deserializeTextOverlays(json.optJSONArray("textOverlays") ?: JSONArray())
            )
        }

        // --- Track serialization ---

        private fun serializeTracks(tracks: List<Track>): JSONArray {
            return JSONArray().apply {
                tracks.forEach { put(serializeTrack(it)) }
            }
        }

        private fun serializeTrack(track: Track): JSONObject {
            return JSONObject().apply {
                put("id", track.id)
                put("type", track.type.name)
                put("index", track.index)
                put("isLocked", track.isLocked)
                put("isVisible", track.isVisible)
                put("isMuted", track.isMuted)
                put("clips", JSONArray().apply {
                    track.clips.forEach { put(serializeClip(it)) }
                })
            }
        }

        private fun serializeClip(clip: Clip): JSONObject {
            return JSONObject().apply {
                put("id", clip.id)
                put("sourceUri", clip.sourceUri.toString())
                put("sourceDurationMs", clip.sourceDurationMs)
                put("timelineStartMs", clip.timelineStartMs)
                put("trimStartMs", clip.trimStartMs)
                put("trimEndMs", clip.trimEndMs)
                put("volume", clip.volume.toDouble())
                put("speed", clip.speed.toDouble())
                put("isReversed", clip.isReversed)
                put("opacity", clip.opacity.toDouble())
                put("rotation", clip.rotation.toDouble())
                put("scaleX", clip.scaleX.toDouble())
                put("scaleY", clip.scaleY.toDouble())
                put("positionX", clip.positionX.toDouble())
                put("positionY", clip.positionY.toDouble())
                put("fadeInMs", clip.fadeInMs)
                put("fadeOutMs", clip.fadeOutMs)
                put("effects", JSONArray().apply {
                    clip.effects.forEach { put(serializeEffect(it)) }
                })
                put("keyframes", JSONArray().apply {
                    clip.keyframes.forEach { put(serializeKeyframe(it)) }
                })
                clip.transition?.let { put("transition", serializeTransition(it)) }
            }
        }

        private fun serializeEffect(effect: Effect): JSONObject {
            return JSONObject().apply {
                put("id", effect.id)
                put("type", effect.type.name)
                put("enabled", effect.enabled)
                put("params", JSONObject().apply {
                    effect.params.forEach { (k, v) -> put(k, v.toDouble()) }
                })
            }
        }

        private fun serializeKeyframe(kf: Keyframe): JSONObject {
            return JSONObject().apply {
                put("timeOffsetMs", kf.timeOffsetMs)
                put("property", kf.property.name)
                put("value", kf.value.toDouble())
                put("easing", kf.easing.name)
            }
        }

        private fun serializeTransition(t: Transition): JSONObject {
            return JSONObject().apply {
                put("type", t.type.name)
                put("durationMs", t.durationMs)
            }
        }

        // --- TextOverlay serialization ---

        private fun serializeTextOverlays(overlays: List<TextOverlay>): JSONArray {
            return JSONArray().apply {
                overlays.forEach { put(serializeTextOverlay(it)) }
            }
        }

        private fun serializeTextOverlay(t: TextOverlay): JSONObject {
            return JSONObject().apply {
                put("id", t.id)
                put("text", t.text)
                put("fontFamily", t.fontFamily)
                put("fontSize", t.fontSize.toDouble())
                put("color", t.color)
                put("backgroundColor", t.backgroundColor)
                put("strokeColor", t.strokeColor)
                put("strokeWidth", t.strokeWidth.toDouble())
                put("bold", t.bold)
                put("italic", t.italic)
                put("alignment", t.alignment.name)
                put("positionX", t.positionX.toDouble())
                put("positionY", t.positionY.toDouble())
                put("startTimeMs", t.startTimeMs)
                put("endTimeMs", t.endTimeMs)
                put("animationIn", t.animationIn.name)
                put("animationOut", t.animationOut.name)
            }
        }

        // --- Deserialization ---

        private fun deserializeTracks(arr: JSONArray): List<Track> {
            return (0 until arr.length()).map { deserializeTrack(arr.getJSONObject(it)) }
        }

        private fun deserializeTrack(json: JSONObject): Track {
            val clipsArr = json.optJSONArray("clips") ?: JSONArray()
            return Track(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                type = safeValueOf(json.optString("type", "VIDEO"), TrackType.VIDEO),
                index = json.optInt("index", 0),
                isLocked = json.optBoolean("isLocked", false),
                isVisible = json.optBoolean("isVisible", true),
                isMuted = json.optBoolean("isMuted", false),
                clips = (0 until clipsArr.length()).mapNotNull {
                    try { deserializeClip(clipsArr.getJSONObject(it)) } catch (_: Exception) { null }
                }
            )
        }

        private fun deserializeClip(json: JSONObject): Clip {
            val effectsArr = json.optJSONArray("effects") ?: JSONArray()
            val keyframesArr = json.optJSONArray("keyframes") ?: JSONArray()
            return Clip(
                id = json.getString("id"),
                sourceUri = Uri.parse(json.getString("sourceUri")),
                sourceDurationMs = json.getLong("sourceDurationMs"),
                timelineStartMs = json.getLong("timelineStartMs"),
                trimStartMs = json.getLong("trimStartMs"),
                trimEndMs = json.getLong("trimEndMs"),
                volume = json.optDouble("volume", 1.0).toFloat(),
                speed = json.optDouble("speed", 1.0).toFloat(),
                isReversed = json.optBoolean("isReversed", false),
                opacity = json.optDouble("opacity", 1.0).toFloat(),
                rotation = json.optDouble("rotation", 0.0).toFloat(),
                scaleX = json.optDouble("scaleX", 1.0).toFloat(),
                scaleY = json.optDouble("scaleY", 1.0).toFloat(),
                positionX = json.optDouble("positionX", 0.0).toFloat(),
                positionY = json.optDouble("positionY", 0.0).toFloat(),
                fadeInMs = json.optLong("fadeInMs", 0L),
                fadeOutMs = json.optLong("fadeOutMs", 0L),
                effects = (0 until effectsArr.length()).mapNotNull {
                    try { deserializeEffect(effectsArr.getJSONObject(it)) } catch (_: Exception) { null }
                },
                keyframes = (0 until keyframesArr.length()).mapNotNull {
                    try { deserializeKeyframe(keyframesArr.getJSONObject(it)) } catch (_: Exception) { null }
                },
                transition = if (json.has("transition")) deserializeTransition(json.getJSONObject("transition")) else null
            )
        }

        private fun deserializeEffect(json: JSONObject): Effect {
            val paramsJson = json.optJSONObject("params")
            val params = buildMap {
                paramsJson?.keys()?.forEach { key ->
                    put(key, paramsJson.optDouble(key, 0.0).toFloat())
                }
            }
            return Effect(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                type = safeValueOf(json.optString("type", "BRIGHTNESS"), EffectType.BRIGHTNESS),
                enabled = json.optBoolean("enabled", true),
                params = params
            )
        }

        private fun deserializeKeyframe(json: JSONObject): Keyframe {
            return Keyframe(
                timeOffsetMs = json.optLong("timeOffsetMs", 0L),
                property = safeValueOf(json.optString("property", "OPACITY"), KeyframeProperty.OPACITY),
                value = json.optDouble("value", 1.0).toFloat(),
                easing = safeValueOf(json.optString("easing", "LINEAR"), Easing.LINEAR)
            )
        }

        private fun deserializeTransition(json: JSONObject): Transition {
            return Transition(
                type = safeValueOf(json.optString("type", "DISSOLVE"), TransitionType.DISSOLVE),
                durationMs = json.optLong("durationMs", 500L)
            )
        }

        private fun deserializeTextOverlays(arr: JSONArray): List<TextOverlay> {
            return (0 until arr.length()).mapNotNull {
                try { deserializeTextOverlay(arr.getJSONObject(it)) } catch (_: Exception) { null }
            }
        }

        private fun deserializeTextOverlay(json: JSONObject): TextOverlay {
            return TextOverlay(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                text = json.optString("text", ""),
                fontFamily = json.optString("fontFamily", "sans-serif"),
                fontSize = json.optDouble("fontSize", 48.0).toFloat(),
                color = json.optLong("color", 0xFFFFFFFF),
                backgroundColor = json.optLong("backgroundColor", 0x00000000),
                strokeColor = json.optLong("strokeColor", 0xFF000000),
                strokeWidth = json.optDouble("strokeWidth", 0.0).toFloat(),
                bold = json.optBoolean("bold", false),
                italic = json.optBoolean("italic", false),
                alignment = safeValueOf(json.optString("alignment", "CENTER"), TextAlignment.CENTER),
                positionX = json.optDouble("positionX", 0.5).toFloat(),
                positionY = json.optDouble("positionY", 0.5).toFloat(),
                startTimeMs = json.optLong("startTimeMs", 0L),
                endTimeMs = json.optLong("endTimeMs", 3000L),
                animationIn = safeValueOf(json.optString("animationIn", "NONE"), TextAnimation.NONE),
                animationOut = safeValueOf(json.optString("animationOut", "NONE"), TextAnimation.NONE)
            )
        }
    }
}
