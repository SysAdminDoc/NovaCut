package com.novacut.editor.ui.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacut.editor.engine.ExportState
import com.novacut.editor.engine.VideoEngine
import com.novacut.editor.engine.db.ProjectDao
import com.novacut.editor.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class EditorState(
    val project: Project = Project(),
    val tracks: List<Track> = listOf(
        Track(type = TrackType.VIDEO, index = 0),
        Track(type = TrackType.AUDIO, index = 1)
    ),
    val selectedClipId: String? = null,
    val selectedTrackId: String? = null,
    val playheadMs: Long = 0L,
    val isPlaying: Boolean = false,
    val zoomLevel: Float = 1f,
    val scrollOffsetMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val currentTool: EditorTool = EditorTool.NONE,
    val showMediaPicker: Boolean = false,
    val showExportSheet: Boolean = false,
    val showEffectsPanel: Boolean = false,
    val showTextEditor: Boolean = false,
    val showTransitionPicker: Boolean = false,
    val exportConfig: ExportConfig = ExportConfig(),
    val exportProgress: Float = 0f,
    val exportState: ExportState = ExportState.IDLE,
    val textOverlays: List<TextOverlay> = emptyList(),
    val undoStack: List<UndoAction> = emptyList(),
    val redoStack: List<UndoAction> = emptyList(),
    val toastMessage: String? = null
)

enum class EditorTool(val displayName: String) {
    NONE(""),
    TRIM("Trim"),
    SPLIT("Split"),
    SPEED("Speed"),
    EFFECTS("Effects"),
    TEXT("Text"),
    AUDIO("Audio"),
    TRANSITION("Transition"),
    TRANSFORM("Transform"),
    CROP("Crop"),
    EXPORT("Export")
}

data class UndoAction(
    val description: String,
    val tracks: List<Track>,
    val textOverlays: List<TextOverlay>
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val videoEngine: VideoEngine,
    private val projectDao: ProjectDao
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    val engine get() = videoEngine

    init {
        viewModelScope.launch {
            videoEngine.exportProgress.collect { progress ->
                _state.update { it.copy(exportProgress = progress) }
            }
        }
        viewModelScope.launch {
            videoEngine.exportState.collect { exportState ->
                _state.update { it.copy(exportState = exportState) }
            }
        }
    }

    fun addClipToTrack(uri: Uri, trackType: TrackType = TrackType.VIDEO) {
        viewModelScope.launch {
            val duration = withContext(Dispatchers.IO) {
                videoEngine.getVideoDuration(uri)
            }
            if (duration <= 0) {
                showToast("Could not read video file")
                return@launch
            }

            saveUndoState("Add clip")

            _state.update { state ->
                val tracks = state.tracks.toMutableList()
                val trackIndex = tracks.indexOfFirst { it.type == trackType }
                if (trackIndex < 0) return@update state

                val track = tracks[trackIndex]
                val timelineStart = track.clips.maxOfOrNull { it.timelineEndMs } ?: 0L

                val clip = Clip(
                    sourceUri = uri,
                    sourceDurationMs = duration,
                    timelineStartMs = timelineStart,
                    trimStartMs = 0L,
                    trimEndMs = duration
                )

                val updatedClips = track.clips.toMutableList().apply { add(clip) }
                tracks[trackIndex] = track.copy(clips = updatedClips)

                val totalDuration = tracks.maxOfOrNull { t ->
                    t.clips.maxOfOrNull { it.timelineEndMs } ?: 0L
                } ?: 0L

                state.copy(
                    tracks = tracks,
                    totalDurationMs = totalDuration,
                    selectedClipId = clip.id,
                    selectedTrackId = track.id,
                    showMediaPicker = false
                )
            }

            // Prepare the player with the first clip if this is the first one
            val clips = _state.value.tracks.flatMap { it.clips }
            if (clips.size == 1) {
                videoEngine.prepareClip(uri)
            }
        }
    }

    fun selectClip(clipId: String?, trackId: String? = null) {
        _state.update { it.copy(selectedClipId = clipId, selectedTrackId = trackId) }
    }

    fun deleteSelectedClip() {
        val clipId = _state.value.selectedClipId ?: return
        saveUndoState("Delete clip")

        _state.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.filterNot { it.id == clipId }.toMutableList())
            }
            val totalDuration = tracks.maxOfOrNull { t ->
                t.clips.maxOfOrNull { it.timelineEndMs } ?: 0L
            } ?: 0L

            state.copy(
                tracks = tracks,
                totalDurationMs = totalDuration,
                selectedClipId = null,
                selectedTrackId = null
            )
        }
    }

    fun splitClipAtPlayhead() {
        val state = _state.value
        val clipId = state.selectedClipId ?: return
        val playhead = state.playheadMs

        saveUndoState("Split clip")

        _state.update { s ->
            val tracks = s.tracks.map { track ->
                val clipIndex = track.clips.indexOfFirst { it.id == clipId }
                if (clipIndex < 0) return@map track

                val clip = track.clips[clipIndex]
                if (playhead <= clip.timelineStartMs || playhead >= clip.timelineEndMs) return@map track

                val relativePosition = playhead - clip.timelineStartMs
                val splitPointInSource = clip.trimStartMs + (relativePosition * clip.speed).toLong()

                val firstHalf = clip.copy(
                    trimEndMs = splitPointInSource
                )
                val secondHalf = clip.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    timelineStartMs = playhead,
                    trimStartMs = splitPointInSource
                )

                val updatedClips = track.clips.toMutableList().apply {
                    removeAt(clipIndex)
                    add(clipIndex, firstHalf)
                    add(clipIndex + 1, secondHalf)
                }
                track.copy(clips = updatedClips)
            }
            s.copy(tracks = tracks)
        }
    }

    fun trimClip(clipId: String, newTrimStartMs: Long? = null, newTrimEndMs: Long? = null) {
        saveUndoState("Trim clip")
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(
                            trimStartMs = newTrimStartMs ?: clip.trimStartMs,
                            trimEndMs = newTrimEndMs ?: clip.trimEndMs
                        )
                    } else clip
                }.toMutableList()
                track.copy(clips = updatedClips)
            }
            val totalDuration = tracks.maxOfOrNull { t ->
                t.clips.maxOfOrNull { it.timelineEndMs } ?: 0L
            } ?: 0L
            state.copy(tracks = tracks, totalDurationMs = totalDuration)
        }
    }

    fun setClipSpeed(clipId: String, speed: Float) {
        saveUndoState("Change speed")
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(speed = speed.coerceIn(0.1f, 16f))
                    else clip
                }.toMutableList()
                track.copy(clips = updatedClips)
            }
            recalculateDuration(state.copy(tracks = tracks))
        }
    }

    fun setClipReversed(clipId: String, reversed: Boolean) {
        saveUndoState("Reverse clip")
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(isReversed = reversed)
                    else clip
                }.toMutableList()
                track.copy(clips = updatedClips)
            }
            state.copy(tracks = tracks)
        }
    }

    fun addEffect(clipId: String, effect: Effect) {
        saveUndoState("Add effect")
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(effects = (clip.effects + effect).toMutableList())
                    } else clip
                }.toMutableList()
                track.copy(clips = updatedClips)
            }
            state.copy(tracks = tracks)
        }
    }

    fun updateEffect(clipId: String, effectId: String, params: Map<String, Float>) {
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        val effects = clip.effects.map { e ->
                            if (e.id == effectId) e.copy(params = (e.params + params).toMutableMap())
                            else e
                        }.toMutableList()
                        clip.copy(effects = effects)
                    } else clip
                }.toMutableList()
                track.copy(clips = updatedClips)
            }
            state.copy(tracks = tracks)
        }
    }

    fun removeEffect(clipId: String, effectId: String) {
        saveUndoState("Remove effect")
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(effects = clip.effects.filterNot { it.id == effectId }.toMutableList())
                    } else clip
                }.toMutableList()
                track.copy(clips = updatedClips)
            }
            state.copy(tracks = tracks)
        }
    }

    fun setTransition(clipId: String, transition: Transition?) {
        saveUndoState("Set transition")
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(transition = transition)
                    else clip
                }.toMutableList()
                track.copy(clips = updatedClips)
            }
            state.copy(tracks = tracks)
        }
    }

    fun addTextOverlay(text: TextOverlay) {
        saveUndoState("Add text")
        _state.update { it.copy(textOverlays = it.textOverlays + text) }
    }

    fun updateTextOverlay(textOverlay: TextOverlay) {
        _state.update { state ->
            state.copy(
                textOverlays = state.textOverlays.map {
                    if (it.id == textOverlay.id) textOverlay else it
                }
            )
        }
    }

    fun removeTextOverlay(id: String) {
        saveUndoState("Remove text")
        _state.update { state ->
            state.copy(textOverlays = state.textOverlays.filterNot { it.id == id })
        }
    }

    fun addTrack(type: TrackType) {
        saveUndoState("Add track")
        _state.update { state ->
            val nextIndex = state.tracks.size
            state.copy(tracks = state.tracks + Track(type = type, index = nextIndex))
        }
    }

    fun toggleTrackMute(trackId: String) {
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                if (track.id == trackId) track.copy(isMuted = !track.isMuted) else track
            }
            state.copy(tracks = tracks)
        }
    }

    fun toggleTrackVisibility(trackId: String) {
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                if (track.id == trackId) track.copy(isVisible = !track.isVisible) else track
            }
            state.copy(tracks = tracks)
        }
    }

    fun toggleTrackLock(trackId: String) {
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                if (track.id == trackId) track.copy(isLocked = !track.isLocked) else track
            }
            state.copy(tracks = tracks)
        }
    }

    // Playback
    fun togglePlayback() {
        if (videoEngine.isPlaying()) {
            videoEngine.pause()
            _state.update { it.copy(isPlaying = false) }
        } else {
            videoEngine.play()
            _state.update { it.copy(isPlaying = true) }
        }
    }

    fun seekTo(positionMs: Long) {
        videoEngine.seekTo(positionMs)
        _state.update { it.copy(playheadMs = positionMs) }
    }

    fun updatePlayheadPosition(positionMs: Long) {
        _state.update { it.copy(playheadMs = positionMs) }
    }

    // Zoom
    fun setZoomLevel(zoom: Float) {
        _state.update { it.copy(zoomLevel = zoom.coerceIn(0.1f, 10f)) }
    }

    fun setScrollOffset(offsetMs: Long) {
        _state.update { it.copy(scrollOffsetMs = offsetMs.coerceAtLeast(0L)) }
    }

    // Tool selection
    fun setTool(tool: EditorTool) {
        _state.update { it.copy(currentTool = tool) }
    }

    // Sheet toggles
    fun showMediaPicker() { _state.update { it.copy(showMediaPicker = true) } }
    fun hideMediaPicker() { _state.update { it.copy(showMediaPicker = false) } }
    fun showExportSheet() { _state.update { it.copy(showExportSheet = true) } }
    fun hideExportSheet() { _state.update { it.copy(showExportSheet = false) } }
    fun showEffectsPanel() { _state.update { it.copy(showEffectsPanel = true) } }
    fun hideEffectsPanel() { _state.update { it.copy(showEffectsPanel = false) } }
    fun showTextEditor() { _state.update { it.copy(showTextEditor = true) } }
    fun hideTextEditor() { _state.update { it.copy(showTextEditor = false) } }
    fun showTransitionPicker() { _state.update { it.copy(showTransitionPicker = true) } }
    fun hideTransitionPicker() { _state.update { it.copy(showTransitionPicker = false) } }

    // Export
    fun updateExportConfig(config: ExportConfig) {
        _state.update { it.copy(exportConfig = config) }
    }

    fun startExport(outputDir: File) {
        viewModelScope.launch {
            val config = _state.value.exportConfig
            val outputFile = File(outputDir, "NovaCut_${System.currentTimeMillis()}.mp4")

            videoEngine.export(
                tracks = _state.value.tracks,
                config = config,
                outputFile = outputFile,
                onProgress = { progress ->
                    _state.update { it.copy(exportProgress = progress) }
                },
                onComplete = {
                    showToast("Export complete: ${outputFile.name}")
                },
                onError = { e ->
                    showToast("Export failed: ${e.message}")
                }
            )
        }
    }

    // Undo/Redo
    fun undo() {
        val undoStack = _state.value.undoStack
        if (undoStack.isEmpty()) return

        val action = undoStack.last()
        val currentAction = UndoAction(
            "Redo",
            _state.value.tracks.map { it.copy() },
            _state.value.textOverlays.toList()
        )

        _state.update {
            it.copy(
                tracks = action.tracks,
                textOverlays = action.textOverlays,
                undoStack = undoStack.dropLast(1),
                redoStack = it.redoStack + currentAction
            )
        }
    }

    fun redo() {
        val redoStack = _state.value.redoStack
        if (redoStack.isEmpty()) return

        val action = redoStack.last()
        val currentAction = UndoAction(
            "Undo",
            _state.value.tracks.map { it.copy() },
            _state.value.textOverlays.toList()
        )

        _state.update {
            it.copy(
                tracks = action.tracks,
                textOverlays = action.textOverlays,
                redoStack = redoStack.dropLast(1),
                undoStack = it.undoStack + currentAction
            )
        }
    }

    fun showToast(message: String) {
        _state.update { it.copy(toastMessage = message) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            _state.update { it.copy(toastMessage = null) }
        }
    }

    fun getSelectedClip(): Clip? {
        val clipId = _state.value.selectedClipId ?: return null
        return _state.value.tracks.flatMap { it.clips }.firstOrNull { it.id == clipId }
    }

    fun getSelectedTrack(): Track? {
        val trackId = _state.value.selectedTrackId ?: return null
        return _state.value.tracks.firstOrNull { it.id == trackId }
    }

    // Project persistence
    fun saveProject() {
        viewModelScope.launch {
            val project = _state.value.project.copy(
                updatedAt = System.currentTimeMillis(),
                durationMs = _state.value.totalDurationMs
            )
            projectDao.insertProject(project)
            _state.update { it.copy(project = project) }
        }
    }

    private fun saveUndoState(description: String) {
        _state.update { state ->
            val action = UndoAction(
                description = description,
                tracks = state.tracks.map { it.copy() },
                textOverlays = state.textOverlays.toList()
            )
            state.copy(
                undoStack = (state.undoStack + action).takeLast(50),
                redoStack = emptyList()
            )
        }
    }

    private fun recalculateDuration(state: EditorState): EditorState {
        val totalDuration = state.tracks.maxOfOrNull { t ->
            t.clips.maxOfOrNull { it.timelineEndMs } ?: 0L
        } ?: 0L
        return state.copy(totalDurationMs = totalDuration)
    }

    override fun onCleared() {
        super.onCleared()
        videoEngine.release()
    }
}
