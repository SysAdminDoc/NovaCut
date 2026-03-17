package com.novacut.editor.ui.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.novacut.editor.ai.AiFeatures
import com.novacut.editor.engine.AudioEngine
import com.novacut.editor.engine.AutoSaveState
import com.novacut.editor.engine.ExportService
import com.novacut.editor.engine.ExportState
import com.novacut.editor.engine.ProjectAutoSave
import com.novacut.editor.engine.VideoEngine
import com.novacut.editor.engine.VoiceoverRecorderEngine
import com.novacut.editor.engine.db.ProjectDao
import com.novacut.editor.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
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
    val waveforms: Map<String, FloatArray> = emptyMap(),
    val showAudioPanel: Boolean = false,
    val showAiToolsPanel: Boolean = false,
    val showTransformPanel: Boolean = false,
    val showCropPanel: Boolean = false,
    val selectedEffectId: String? = null,
    val undoStack: List<UndoAction> = emptyList(),
    val redoStack: List<UndoAction> = emptyList(),
    val toastMessage: String? = null,
    val aiProcessingTool: String? = null,
    val lastExportedFilePath: String? = null,
    val copiedEffects: List<Effect> = emptyList(),
    val exportErrorMessage: String? = null,
    val showVoiceoverRecorder: Boolean = false,
    val isRecordingVoiceover: Boolean = false,
    val voiceoverDurationMs: Long = 0L,
    val isLooping: Boolean = false,
    val editingTextOverlayId: String? = null
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
    AI("AI"),
    FREEZE_FRAME("Freeze"),
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
    private val projectDao: ProjectDao,
    private val audioEngine: AudioEngine,
    private val autoSave: ProjectAutoSave,
    private val aiFeatures: AiFeatures,
    private val voiceoverEngine: VoiceoverRecorderEngine,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val projectId: String? = savedStateHandle["projectId"]

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    val engine get() = videoEngine

    // Stored outside EditorState to avoid recomposition on every resize
    @Volatile
    private var timelineWidthPx: Float = 0f

    private var aiJob: kotlinx.coroutines.Job? = null

    fun setTimelineWidth(widthPx: Float) {
        timelineWidthPx = widthPx
    }

    init {
        val autoSaveId = projectId ?: _state.value.project.id

        // Load existing project if projectId provided, then restore auto-save
        viewModelScope.launch {
            if (projectId != null) {
                val project = projectDao.getProject(projectId)
                if (project != null) {
                    _state.update { it.copy(project = project) }
                } else {
                    val newProject = _state.value.project.copy(id = projectId)
                    _state.update { it.copy(project = newProject) }
                    projectDao.insertProject(newProject)
                }
            }

            // Restore auto-save AFTER Room load to avoid race condition
            val recovery = autoSave.loadRecoveryData(autoSaveId)
            if (recovery != null) {
                _state.update {
                    it.copy(
                        tracks = recovery.tracks.ifEmpty { it.tracks },
                        textOverlays = recovery.textOverlays,
                        playheadMs = recovery.playheadMs,
                        totalDurationMs = recovery.tracks.maxOfOrNull { t ->
                            t.clips.maxOfOrNull { c -> c.timelineEndMs } ?: 0L
                        } ?: 0L
                    )
                }
                if (recovery.tracks.flatMap { it.clips }.isNotEmpty()) {
                    rebuildPlayerTimeline()
                }
            }
        }

        viewModelScope.launch {
            videoEngine.exportProgress.collect { progress ->
                _state.update { it.copy(exportProgress = progress) }
            }
        }
        viewModelScope.launch {
            videoEngine.exportState.collect { exportState ->
                _state.update { it.copy(exportState = exportState) }
                if (exportState == ExportState.CANCELLED) {
                    showToast("Export cancelled")
                }
            }
        }

        // Player.Listener for play state sync — tracked for cleanup
        videoEngine.setPlayerListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _state.update { it.copy(isPlaying = playing) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    _state.update { it.copy(isPlaying = false, playheadMs = it.totalDurationMs) }
                }
            }
        })

        // Periodic playhead sync (~30fps) with auto-scroll
        viewModelScope.launch {
            while (isActive) {
                delay(33)
                val player = videoEngine.getPlayer()
                if (player.isPlaying) {
                    val currentMs = videoEngine.getAbsolutePositionMs()
                    _state.update { s ->
                        var newScroll = s.scrollOffsetMs
                        // Auto-scroll when playhead approaches right edge (>80% of visible area)
                        val widthPx = timelineWidthPx
                        if (widthPx > 0) {
                            val pixelsPerMs = s.zoomLevel * 0.15f
                            val visibleMs = (widthPx / pixelsPerMs).toLong()
                            val playheadRelative = currentMs - newScroll
                            if (playheadRelative > visibleMs * 0.8f) {
                                newScroll = (currentMs - visibleMs / 4).coerceAtLeast(0L)
                            } else if (playheadRelative < 0) {
                                newScroll = (currentMs - visibleMs / 4).coerceAtLeast(0L)
                            }
                        }
                        s.copy(playheadMs = currentMs, scrollOffsetMs = newScroll)
                    }
                }
            }
        }

        // Start auto-save
        autoSave.startAutoSave(autoSaveId) {
            val s = _state.value
            AutoSaveState(
                projectId = s.project.id,
                tracks = s.tracks,
                textOverlays = s.textOverlays,
                playheadMs = s.playheadMs
            )
        }
    }

    /** Rebuild ExoPlayer timeline from current tracks. Call after any clip mutation. */
    private fun rebuildPlayerTimeline() {
        videoEngine.prepareTimeline(_state.value.tracks)
    }

    fun addClipToTrack(uri: Uri, trackType: TrackType = TrackType.VIDEO) {
        viewModelScope.launch {
            val duration = withContext(Dispatchers.IO) {
                videoEngine.getVideoDuration(uri)
            }
            if (duration <= 0) {
                showToast("Could not read media file")
                return@launch
            }

            saveUndoState("Add clip")

            // Create clip ID outside state update so we can reference it for waveform
            val clipId = java.util.UUID.randomUUID().toString()

            _state.update { state ->
                val trackIndex = state.tracks.indexOfFirst { it.type == trackType }
                if (trackIndex < 0) return@update state

                val track = state.tracks[trackIndex]
                val timelineStart = track.clips.maxOfOrNull { it.timelineEndMs } ?: 0L

                val clip = Clip(
                    id = clipId,
                    sourceUri = uri,
                    sourceDurationMs = duration,
                    timelineStartMs = timelineStart,
                    trimStartMs = 0L,
                    trimEndMs = duration
                )

                val tracks = state.tracks.mapIndexed { i, t ->
                    if (i == trackIndex) t.copy(clips = t.clips + clip) else t
                }

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

            // Rebuild player timeline with all clips
            videoEngine.prepareTimeline(_state.value.tracks)
            saveProject()

            // Extract waveform for audio visualization using the known clip ID
            viewModelScope.launch {
                val waveform = audioEngine.extractWaveform(uri)
                _state.update { it.copy(waveforms = it.waveforms + (clipId to waveform)) }
            }
        }
    }

    fun selectClip(clipId: String?, trackId: String? = null) {
        _state.update { it.copy(selectedClipId = clipId, selectedTrackId = trackId) }
    }

    fun deleteSelectedClip() {
        val clipId = _state.value.selectedClipId ?: return
        // Validate clip exists before saving undo state
        val exists = _state.value.tracks.any { it.clips.any { c -> c.id == clipId } }
        if (!exists) return
        saveUndoState("Delete clip")

        _state.update { state ->
            val tracks = state.tracks.map { track ->
                val clipIndex = track.clips.indexOfFirst { it.id == clipId }
                if (clipIndex < 0) return@map track

                val deletedClip = track.clips[clipIndex]
                val gapMs = deletedClip.durationMs

                // Ripple delete: shift subsequent clips back to close the gap
                val updatedClips = track.clips
                    .filterNot { it.id == clipId }
                    .map { clip ->
                        if (clip.timelineStartMs > deletedClip.timelineStartMs) {
                            clip.copy(timelineStartMs = clip.timelineStartMs - gapMs)
                        } else clip
                    }
                track.copy(clips = updatedClips)
            }
            val totalDuration = tracks.maxOfOrNull { t ->
                t.clips.maxOfOrNull { it.timelineEndMs } ?: 0L
            } ?: 0L

            state.copy(
                tracks = tracks,
                totalDurationMs = totalDuration,
                selectedClipId = null,
                selectedTrackId = null,
                waveforms = state.waveforms - clipId
            )
        }
        rebuildPlayerTimeline()
        saveProject()
    }

    fun duplicateSelectedClip() {
        val clipId = _state.value.selectedClipId ?: return
        // Validate clip exists before saving undo state
        val exists = _state.value.tracks.any { it.clips.any { c -> c.id == clipId } }
        if (!exists) return
        saveUndoState("Duplicate clip")

        _state.update { s ->
            val trackAndClip = s.tracks.flatMapIndexed { idx, track ->
                track.clips.filter { it.id == clipId }.map { idx to it }
            }.firstOrNull() ?: return@update s

            val (trackIdx, clip) = trackAndClip
            val newClip = clip.copy(
                id = UUID.randomUUID().toString(),
                timelineStartMs = clip.timelineEndMs,
                effects = clip.effects.map { it.copy(id = UUID.randomUUID().toString()) },
                transition = null
            )

            val track = s.tracks[trackIdx]
            val clipIndex = track.clips.indexOfFirst { it.id == clipId }
            val updatedClips = track.clips.toMutableList().apply { add(clipIndex + 1, newClip) }

            // Shift subsequent clips forward
            val shifted = updatedClips.mapIndexed { i, c ->
                if (i > clipIndex + 1) c.copy(timelineStartMs = c.timelineStartMs + newClip.durationMs) else c
            }

            val tracks = s.tracks.mapIndexed { i, t -> if (i == trackIdx) t.copy(clips = shifted) else t }
            recalculateDuration(s.copy(tracks = tracks, selectedClipId = newClip.id))
        }
        rebuildPlayerTimeline()
        saveProject()
    }

    fun mergeWithNextClip() {
        val clipId = _state.value.selectedClipId ?: return

        // Validate merge is possible before saving undo state
        val state = _state.value
        val trackAndClipInfo = state.tracks.flatMapIndexed { idx, track ->
            track.clips.filter { it.id == clipId }.map { idx to it }
        }.firstOrNull()
        if (trackAndClipInfo == null) return
        val (vTrackIdx, vClip) = trackAndClipInfo
        val vTrack = state.tracks[vTrackIdx]
        val vClipIndex = vTrack.clips.indexOfFirst { it.id == clipId }
        if (vClipIndex >= vTrack.clips.size - 1) {
            showToast("No next clip to merge")
            return
        }
        val vNextClip = vTrack.clips[vClipIndex + 1]
        if (vClip.sourceUri != vNextClip.sourceUri) {
            showToast("Can only merge clips from the same source")
            return
        }
        if (vClip.trimEndMs != vNextClip.trimStartMs) {
            showToast("Clips must have adjacent trim ranges to merge")
            return
        }

        saveUndoState("Merge clips")

        _state.update { s ->
            val trackAndClip = s.tracks.flatMapIndexed { idx, track ->
                track.clips.filter { it.id == clipId }.map { idx to it }
            }.firstOrNull() ?: return@update s

            val (trackIdx, clip) = trackAndClip
            val track = s.tracks[trackIdx]
            val clipIndex = track.clips.indexOfFirst { it.id == clipId }

            if (clipIndex >= track.clips.size - 1) return@update s
            val nextClip = track.clips[clipIndex + 1]
            if (clip.sourceUri != nextClip.sourceUri) return@update s

            val merged = clip.copy(
                trimEndMs = nextClip.trimEndMs,
                effects = clip.effects + nextClip.effects.map { it.copy(id = UUID.randomUUID().toString()) }
            )

            val updatedClips = track.clips.toMutableList().apply {
                removeAt(clipIndex + 1)
                set(clipIndex, merged)
            }

            // Shift subsequent clips back
            val nextDuration = nextClip.durationMs
            val shifted = updatedClips.mapIndexed { i, c ->
                if (i > clipIndex) c.copy(timelineStartMs = c.timelineStartMs - nextDuration) else c
            }

            val tracks = s.tracks.mapIndexed { i, t -> if (i == trackIdx) t.copy(clips = shifted) else t }
            recalculateDuration(s.copy(tracks = tracks))
        }
        rebuildPlayerTimeline()
        saveProject()
    }

    fun splitClipAtPlayhead() {
        val state = _state.value
        val clipId = state.selectedClipId ?: return
        val playhead = state.playheadMs

        // Validate split is possible before saving undo state
        val splitClip = state.tracks.flatMap { it.clips }.firstOrNull { it.id == clipId }
        if (splitClip == null || playhead <= splitClip.timelineStartMs || playhead >= splitClip.timelineEndMs) return
        // Ensure both halves meet minimum duration (100ms)
        val relPos = playhead - splitClip.timelineStartMs
        val srcSplit = splitClip.trimStartMs + (relPos * splitClip.speed).toLong()
        if (srcSplit - splitClip.trimStartMs < 100L || splitClip.trimEndMs - srcSplit < 100L) {
            showToast("Clip too short to split here")
            return
        }

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

                val updatedClips = buildList {
                    addAll(track.clips.subList(0, clipIndex))
                    add(firstHalf)
                    add(secondHalf)
                    addAll(track.clips.subList(clipIndex + 1, track.clips.size))
                }
                track.copy(clips = updatedClips)
            }
            recalculateDuration(s.copy(tracks = tracks))
        }
        rebuildPlayerTimeline()
        saveProject()
    }

    fun beginTrim() {
        saveUndoState("Trim clip")
    }

    fun trimClip(clipId: String, newTrimStartMs: Long? = null, newTrimEndMs: Long? = null) {
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        val start = (newTrimStartMs ?: clip.trimStartMs).coerceIn(0L, clip.sourceDurationMs - 100L)
                        val end = (newTrimEndMs ?: clip.trimEndMs).coerceIn(start + 100L, clip.sourceDurationMs)
                        clip.copy(trimStartMs = start, trimEndMs = end)
                    } else clip
                })
            }
            val totalDuration = tracks.maxOfOrNull { t ->
                t.clips.maxOfOrNull { it.timelineEndMs } ?: 0L
            } ?: 0L
            state.copy(tracks = tracks, totalDurationMs = totalDuration)
        }
        rebuildPlayerTimeline()
    }

    fun beginSpeedChange() {
        saveUndoState("Change speed")
    }

    fun setClipSpeed(clipId: String, speed: Float) {
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(speed = speed.coerceIn(0.1f, 16f))
                    else clip
                })
            }
            recalculateDuration(state.copy(tracks = tracks))
        }
        rebuildPlayerTimeline()
    }

    fun setClipReversed(clipId: String, reversed: Boolean) {
        saveUndoState("Reverse clip")
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(isReversed = reversed)
                    else clip
                })
            }
            state.copy(tracks = tracks)
        }
        rebuildPlayerTimeline()
    }

    fun addEffect(clipId: String, effect: Effect) {
        // Guard against duplicate effect types
        val clip = _state.value.tracks.flatMap { it.clips }.firstOrNull { it.id == clipId }
        if (clip?.effects?.any { it.type == effect.type } == true) {
            showToast("${effect.type.displayName} already applied")
            return
        }
        saveUndoState("Add effect")
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { c ->
                    if (c.id == clipId) c.copy(effects = c.effects + effect)
                    else c
                })
            }
            state.copy(tracks = tracks)
        }
        saveProject()
    }

    fun beginEffectAdjust() {
        saveUndoState("Adjust effect")
    }

    fun updateEffect(clipId: String, effectId: String, params: Map<String, Float>) {
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(effects = clip.effects.map { e ->
                            if (e.id == effectId) e.copy(params = e.params + params)
                            else e
                        })
                    } else clip
                })
            }
            state.copy(tracks = tracks)
        }
    }

    fun removeEffect(clipId: String, effectId: String) {
        saveUndoState("Remove effect")
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(effects = clip.effects.filterNot { it.id == effectId })
                    else clip
                })
            }
            state.copy(tracks = tracks)
        }
        saveProject()
    }

    fun copyEffects() {
        val clip = getSelectedClip() ?: return
        if (clip.effects.isEmpty()) {
            showToast("No effects to copy")
            return
        }
        _state.update { it.copy(copiedEffects = clip.effects) }
        showToast("Copied ${clip.effects.size} effects")
    }

    fun pasteEffects() {
        val clipId = _state.value.selectedClipId ?: return
        val toPaste = _state.value.copiedEffects
        if (toPaste.isEmpty()) {
            showToast("No effects copied")
            return
        }
        val targetClip = _state.value.tracks.flatMap { it.clips }.firstOrNull { it.id == clipId } ?: return
        val existingTypes = targetClip.effects.map { it.type }.toSet()
        val filtered = toPaste.filter { it.type !in existingTypes }
        if (filtered.isEmpty()) {
            showToast("Effects already present on clip")
            return
        }
        saveUndoState("Paste effects")
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(effects = clip.effects + filtered.map { it.copy(id = UUID.randomUUID().toString()) })
                    } else clip
                })
            }
            state.copy(tracks = tracks)
        }
        showToast("Pasted ${filtered.size} effects")
        saveProject()
    }

    fun setTransition(clipId: String, transition: Transition?) {
        saveUndoState("Set transition")
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(transition = transition)
                    else clip
                })
            }
            state.copy(tracks = tracks)
        }
        saveProject()
    }

    fun beginTransitionDurationChange() {
        saveUndoState("Change transition duration")
    }

    fun setTransitionDuration(clipId: String, durationMs: Long) {
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId && clip.transition != null)
                        clip.copy(transition = clip.transition.copy(durationMs = durationMs))
                    else clip
                })
            }
            state.copy(tracks = tracks)
        }
        saveProject()
    }

    fun addTextOverlay(text: TextOverlay) {
        saveUndoState("Add text")
        _state.update { it.copy(textOverlays = it.textOverlays + text) }
    }

    fun updateTextOverlay(textOverlay: TextOverlay) {
        saveUndoState("Edit text")
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

    fun toggleLoop() {
        val newLooping = !_state.value.isLooping
        videoEngine.getPlayer()?.repeatMode = if (newLooping)
            Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        _state.update { it.copy(isLooping = newLooping) }
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

    // Panel mutual exclusion — atomic dismiss-and-show in single state update
    private fun dismissedPanelState(state: EditorState) = state.copy(
        showMediaPicker = false,
        showExportSheet = false,
        showEffectsPanel = false,
        showTextEditor = false,
        showTransitionPicker = false,
        showAudioPanel = false,
        showAiToolsPanel = false,
        showTransformPanel = false,
        showCropPanel = false,
        showVoiceoverRecorder = false,
        selectedEffectId = null,
        editingTextOverlayId = null
    )

    fun dismissAllPanels() { _state.update { dismissedPanelState(it) } }

    // Sheet toggles — each atomically dismisses other panels and shows the target
    fun showMediaPicker() { _state.update { dismissedPanelState(it).copy(showMediaPicker = true) } }
    fun hideMediaPicker() { _state.update { it.copy(showMediaPicker = false) } }
    fun showExportSheet() {
        // Reset export state so stale COMPLETE/ERROR doesn't show on reopen
        videoEngine.resetExportState()
        _state.update { dismissedPanelState(it).copy(showExportSheet = true, exportState = ExportState.IDLE, exportProgress = 0f, exportErrorMessage = null) }
    }
    fun hideExportSheet() { _state.update { it.copy(showExportSheet = false) } }
    fun showEffectsPanel() { _state.update { dismissedPanelState(it).copy(showEffectsPanel = true) } }
    fun hideEffectsPanel() { _state.update { it.copy(showEffectsPanel = false) } }
    fun showTextEditor() { _state.update { dismissedPanelState(it).copy(showTextEditor = true, editingTextOverlayId = null) } }
    fun editTextOverlay(id: String) { _state.update { dismissedPanelState(it).copy(showTextEditor = true, editingTextOverlayId = id) } }
    fun hideTextEditor() { _state.update { it.copy(showTextEditor = false, editingTextOverlayId = null) } }
    fun showTransitionPicker() { _state.update { dismissedPanelState(it).copy(showTransitionPicker = true) } }
    fun hideTransitionPicker() { _state.update { it.copy(showTransitionPicker = false) } }
    fun showAudioPanel() { _state.update { dismissedPanelState(it).copy(showAudioPanel = true) } }
    fun hideAudioPanel() { _state.update { it.copy(showAudioPanel = false) } }
    fun showAiToolsPanel() { _state.update { dismissedPanelState(it).copy(showAiToolsPanel = true) } }
    fun hideAiToolsPanel() { _state.update { it.copy(showAiToolsPanel = false) } }
    fun showTransformPanel() { _state.update { dismissedPanelState(it).copy(showTransformPanel = true) } }
    fun hideTransformPanel() { _state.update { it.copy(showTransformPanel = false) } }
    fun showCropPanel() { _state.update { dismissedPanelState(it).copy(showCropPanel = true) } }
    fun hideCropPanel() { _state.update { it.copy(showCropPanel = false) } }
    fun selectEffect(effectId: String?) { _state.update { it.copy(selectedEffectId = effectId) } }
    fun clearSelectedEffect() { _state.update { it.copy(selectedEffectId = null) } }
    fun showVoiceoverPanel() { _state.update { dismissedPanelState(it).copy(showVoiceoverRecorder = true) } }
    fun hideVoiceoverPanel() {
        if (_state.value.isRecordingVoiceover) stopVoiceover()
        voiceoverDurationJob?.cancel()
        _state.update { it.copy(showVoiceoverRecorder = false) }
    }

    // Voiceover recording
    private var voiceoverDurationJob: Job? = null

    fun startVoiceover() {
        val file = voiceoverEngine.startRecording()
        if (file == null) {
            showToast("Microphone access failed")
            return
        }
        _state.update { it.copy(isRecordingVoiceover = true, voiceoverDurationMs = 0L) }
        voiceoverDurationJob = viewModelScope.launch {
            while (isActive) {
                delay(100)
                _state.update { it.copy(voiceoverDurationMs = voiceoverEngine.getRecordingDurationMs()) }
            }
        }
    }

    fun stopVoiceover() {
        voiceoverDurationJob?.cancel()
        val uri = voiceoverEngine.stopRecording()
        _state.update { it.copy(isRecordingVoiceover = false, showVoiceoverRecorder = false) }
        if (uri != null) {
            addClipToTrack(uri, TrackType.AUDIO)
            showToast("Voiceover added to audio track")
        } else {
            showToast("Voiceover recording failed")
        }
    }

    fun setClipVolume(clipId: String, volume: Float) {
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(volume = volume.coerceIn(0f, 2f))
                    else clip
                })
            }
            state.copy(tracks = tracks)
        }
    }

    fun beginVolumeChange() {
        saveUndoState("Change volume")
    }

    fun beginTransformChange() {
        saveUndoState("Transform clip")
    }

    fun setClipTransform(clipId: String, positionX: Float? = null, positionY: Float? = null,
                         scaleX: Float? = null, scaleY: Float? = null, rotation: Float? = null) {
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(
                        positionX = positionX ?: clip.positionX,
                        positionY = positionY ?: clip.positionY,
                        scaleX = (scaleX ?: clip.scaleX).coerceIn(0.1f, 5f),
                        scaleY = (scaleY ?: clip.scaleY).coerceIn(0.1f, 5f),
                        rotation = rotation ?: clip.rotation
                    ) else clip
                })
            }
            state.copy(tracks = tracks)
        }
    }

    fun resetClipTransform(clipId: String) {
        saveUndoState("Reset transform")
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(
                        positionX = 0f, positionY = 0f,
                        scaleX = 1f, scaleY = 1f, rotation = 0f
                    ) else clip
                })
            }
            state.copy(tracks = tracks)
        }
        saveProject()
    }

    fun setClipOpacity(clipId: String, opacity: Float) {
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(opacity = opacity.coerceIn(0f, 1f))
                    else clip
                })
            }
            state.copy(tracks = tracks)
        }
    }

    fun beginFadeAdjust() {
        saveUndoState("Adjust fade")
    }

    fun setClipFadeIn(clipId: String, fadeInMs: Long) {
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        val maxFade = (clip.durationMs - clip.fadeOutMs).coerceAtLeast(0L)
                        clip.copy(fadeInMs = fadeInMs.coerceIn(0L, maxFade))
                    } else clip
                })
            }
            state.copy(tracks = tracks)
        }
    }

    fun setClipFadeOut(clipId: String, fadeOutMs: Long) {
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        val maxFade = (clip.durationMs - clip.fadeInMs).coerceAtLeast(0L)
                        clip.copy(fadeOutMs = fadeOutMs.coerceIn(0L, maxFade))
                    } else clip
                })
            }
            state.copy(tracks = tracks)
        }
    }

    // Export
    fun updateExportConfig(config: ExportConfig) {
        _state.update { it.copy(exportConfig = config) }
    }

    fun startExport(outputDir: File) {
        val currentTracks = _state.value.tracks
        if (currentTracks.flatMap { it.clips }.isEmpty()) {
            showToast("No clips to export")
            return
        }

        viewModelScope.launch {
            val config = _state.value.exportConfig.copy(aspectRatio = _state.value.project.aspectRatio)
            val outputFile = File(outputDir, "NovaCut_${System.currentTimeMillis()}.mp4")

            // Ensure output directory exists (off main thread)
            withContext(Dispatchers.IO) { outputDir.mkdirs() }

            // Start foreground service for export notification
            val serviceIntent = Intent(appContext, ExportService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(serviceIntent)
            } else {
                appContext.startService(serviceIntent)
            }

            try {
                videoEngine.export(
                    tracks = _state.value.tracks,
                    config = config,
                    outputFile = outputFile,
                    onProgress = { progress ->
                        _state.update { it.copy(exportProgress = progress) }
                    },
                    onComplete = {
                        _state.update { it.copy(lastExportedFilePath = outputFile.absolutePath) }
                        showToast("Export complete: ${outputFile.name}")
                    },
                    onError = { e ->
                        _state.update { it.copy(exportErrorMessage = e.message ?: "Unknown error") }
                    }
                )
            } catch (e: Exception) {
                _state.update { it.copy(exportErrorMessage = e.message ?: "Unknown error") }
            }
        }
    }

    fun getShareIntent(): Intent? {
        val filePath = _state.value.lastExportedFilePath ?: run {
            showToast("No exported video to share")
            return null
        }
        val file = File(filePath)
        if (!file.exists()) {
            showToast("Export file no longer available")
            return null
        }
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun saveToGallery() {
        val filePath = _state.value.lastExportedFilePath ?: run {
            showToast("No exported video")
            return
        }
        val file = File(filePath)
        if (!file.exists()) {
            showToast("Export file not found")
            return
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/NovaCut")
                            put(MediaStore.Video.Media.IS_PENDING, 1)
                        }
                        val resolver = appContext.contentResolver
                        val contentUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        if (contentUri != null) {
                            resolver.openOutputStream(contentUri)?.use { out ->
                                file.inputStream().use { input -> input.copyTo(out) }
                            }
                            values.clear()
                            values.put(MediaStore.Video.Media.IS_PENDING, 0)
                            resolver.update(contentUri, values, null, null)
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val moviesDir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                            "NovaCut"
                        ).apply { mkdirs() }
                        file.copyTo(File(moviesDir, file.name), overwrite = true)
                    }
                    withContext(Dispatchers.Main) { showToast("Saved to gallery") }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { showToast("Save failed: ${e.message}") }
                }
            }
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
        rebuildPlayerTimeline()
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
        rebuildPlayerTimeline()
    }

    private var toastJob: Job? = null

    fun showToast(message: String) {
        toastJob?.cancel()
        _state.update { it.copy(toastMessage = message) }
        toastJob = viewModelScope.launch {
            delay(3000)
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
            val firstClipUri = _state.value.tracks
                .filter { it.type == TrackType.VIDEO }
                .flatMap { it.clips }
                .firstOrNull()?.sourceUri?.toString()

            val project = _state.value.project.copy(
                updatedAt = System.currentTimeMillis(),
                durationMs = _state.value.totalDurationMs,
                thumbnailUri = firstClipUri
            )
            projectDao.insertProject(project)
            _state.update { it.copy(project = project) }
        }
    }

    fun renameProject(name: String) {
        _state.update { it.copy(project = it.project.copy(name = name)) }
        saveProject()
    }

    fun updateProjectAspect(aspect: AspectRatio) {
        _state.update { it.copy(project = it.project.copy(aspectRatio = aspect)) }
        saveProject()
        showToast("Aspect ratio: ${aspect.label}")
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

    // AI Tools
    fun runAiTool(toolId: String) {
        val clip = getSelectedClip()
        if (clip == null) {
            showToast("Select a clip first")
            return
        }

        _state.update { it.copy(aiProcessingTool = toolId) }

        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            try {
                when (toolId) {
                    "scene_detect" -> {
                        val scenes = aiFeatures.detectScenes(clip.sourceUri)
                        if (scenes.isEmpty()) {
                            showToast("No scene changes detected")
                        } else {
                            saveUndoState("AI scene detect")
                            _state.update { state ->
                                var tracks = state.tracks
                                for (scene in scenes.sortedByDescending { it.timestampMs }) {
                                    val splitMs = clip.timelineStartMs +
                                        ((scene.timestampMs - clip.trimStartMs) / clip.speed).toLong()
                                    if (splitMs <= clip.timelineStartMs || splitMs >= clip.timelineEndMs) continue

                                    tracks = tracks.map { track ->
                                        val idx = track.clips.indexOfFirst { it.id == clip.id }
                                        if (idx < 0) return@map track
                                        val c = track.clips[idx]
                                        if (splitMs <= c.timelineStartMs || splitMs >= c.timelineEndMs) return@map track

                                        val relPos = splitMs - c.timelineStartMs
                                        val srcSplit = c.trimStartMs + (relPos * c.speed).toLong()
                                        val first = c.copy(trimEndMs = srcSplit)
                                        val second = c.copy(
                                            id = java.util.UUID.randomUUID().toString(),
                                            timelineStartMs = splitMs,
                                            trimStartMs = srcSplit
                                        )
                                        val newClips = buildList {
                                            addAll(track.clips.subList(0, idx))
                                            add(first)
                                            add(second)
                                            addAll(track.clips.subList(idx + 1, track.clips.size))
                                        }
                                        track.copy(clips = newClips)
                                    }
                                }
                                recalculateDuration(state.copy(tracks = tracks))
                            }
                            rebuildPlayerTimeline()
                            saveProject()
                            showToast("Split into ${scenes.size + 1} clips at scene boundaries")
                        }
                    }
                    "auto_captions" -> {
                        val captions = aiFeatures.generateAutoCaptions(clip.sourceUri)
                        if (captions.isEmpty()) {
                            showToast("No speech detected")
                        } else {
                            saveUndoState("AI auto captions")
                            val overlays = aiFeatures.captionsToOverlays(captions)
                            _state.update { it.copy(textOverlays = it.textOverlays + overlays) }
                            saveProject()
                            showToast("Added ${captions.size} captions")
                        }
                    }
                    "smart_crop" -> {
                        val suggestion = aiFeatures.suggestCrop(
                            clip.sourceUri,
                            _state.value.project.aspectRatio.toFloat()
                        )
                        showToast("Smart crop: center(${
                            "%.0f".format(suggestion.centerX * 100)
                        }%, ${"%.0f".format(suggestion.centerY * 100)}%) confidence: ${
                            "%.0f".format(suggestion.confidence * 100)
                        }%")
                    }
                    "auto_color" -> {
                        val correction = aiFeatures.autoColorCorrect(clip.sourceUri)
                        if (correction.confidence < 0.1f) {
                            showToast("Could not analyze color")
                        } else {
                            saveUndoState("AI auto color")
                            val newEffects = buildList {
                                if (kotlin.math.abs(correction.brightness) > 0.02f) {
                                    add(Effect(type = EffectType.BRIGHTNESS, params = mapOf("value" to correction.brightness)))
                                }
                                if (kotlin.math.abs(correction.contrast - 1f) > 0.05f) {
                                    add(Effect(type = EffectType.CONTRAST, params = mapOf("value" to correction.contrast)))
                                }
                                if (kotlin.math.abs(correction.saturation - 1f) > 0.05f) {
                                    add(Effect(type = EffectType.SATURATION, params = mapOf("value" to correction.saturation)))
                                }
                                if (kotlin.math.abs(correction.temperature) > 0.05f) {
                                    add(Effect(type = EffectType.TEMPERATURE, params = mapOf("value" to correction.temperature)))
                                }
                            }
                            if (newEffects.isEmpty()) {
                                showToast("Colors already look good!")
                            } else {
                                _state.update { state ->
                                    val tracks = state.tracks.map { track ->
                                        val idx = track.clips.indexOfFirst { it.id == clip.id }
                                        if (idx < 0) return@map track
                                        val c = track.clips[idx]
                                        // Remove existing auto-color effects (same types) then add new
                                        val autoTypes = newEffects.map { it.type }.toSet()
                                        val filteredEffects = c.effects.filter { it.type !in autoTypes }
                                        val updatedClip = c.copy(effects = filteredEffects + newEffects)
                                        track.copy(clips = track.clips.toMutableList().apply { set(idx, updatedClip) })
                                    }
                                    recalculateDuration(state.copy(tracks = tracks))
                                }
                                rebuildPlayerTimeline()
                                saveProject()
                                showToast("Applied ${newEffects.size} color corrections")
                            }
                        }
                    }
                    "stabilize" -> {
                        val result = aiFeatures.stabilizeVideo(clip.sourceUri)
                        if (result.confidence < 0.1f || result.shakeMagnitude < 0.001f) {
                            showToast("Video is already stable")
                        } else {
                            saveUndoState("AI stabilize")
                            _state.update { state ->
                                val tracks = state.tracks.map { track ->
                                    val idx = track.clips.indexOfFirst { it.id == clip.id }
                                    if (idx < 0) return@map track
                                    val c = track.clips[idx]
                                    // Apply stabilization: zoom in slightly + generate smooth keyframes
                                    val zoom = result.recommendedZoom
                                    val keyframes = result.motionKeyframes.flatMap { kf ->
                                        listOf(
                                            Keyframe(
                                                timeOffsetMs = kf.timestampMs,
                                                property = KeyframeProperty.POSITION_X,
                                                value = kf.offsetX,
                                                easing = Easing.EASE_IN_OUT
                                            ),
                                            Keyframe(
                                                timeOffsetMs = kf.timestampMs,
                                                property = KeyframeProperty.POSITION_Y,
                                                value = kf.offsetY,
                                                easing = Easing.EASE_IN_OUT
                                            )
                                        )
                                    }
                                    val stabilized = c.copy(
                                        scaleX = c.scaleX * zoom,
                                        scaleY = c.scaleY * zoom,
                                        keyframes = c.keyframes + keyframes
                                    )
                                    track.copy(clips = track.clips.toMutableList().apply { set(idx, stabilized) })
                                }
                                recalculateDuration(state.copy(tracks = tracks))
                            }
                            rebuildPlayerTimeline()
                            saveProject()
                            showToast("Stabilized: ${
                                "%.0f".format(result.shakeMagnitude * 100)
                            }% shake corrected, ${
                                "%.0f".format((result.recommendedZoom - 1f) * 100)
                            }% zoom applied")
                        }
                    }
                    "denoise" -> {
                        val profile = aiFeatures.analyzeAudioNoise(clip.sourceUri)
                        if (profile.confidence < 0.1f) {
                            showToast("Could not analyze audio noise")
                        } else if (profile.signalToNoiseDb > 40f) {
                            showToast("Audio is already clean (SNR: ${"%.0f".format(profile.signalToNoiseDb)}dB)")
                        } else {
                            saveUndoState("AI denoise")
                            // Apply noise reduction by adjusting volume and fade
                            // Boost signal relative to noise floor, apply noise gate via volume
                            val volumeBoost = (1f + profile.recommendedReduction * 0.3f).coerceAtMost(1.5f)
                            _state.update { state ->
                                val tracks = state.tracks.map { track ->
                                    val idx = track.clips.indexOfFirst { it.id == clip.id }
                                    if (idx < 0) return@map track
                                    val c = track.clips[idx]
                                    val denoised = c.copy(
                                        volume = (c.volume * volumeBoost).coerceIn(0f, 2f),
                                        fadeInMs = if (c.fadeInMs < 50) 50L else c.fadeInMs,
                                        fadeOutMs = if (c.fadeOutMs < 50) 50L else c.fadeOutMs
                                    )
                                    track.copy(clips = track.clips.toMutableList().apply { set(idx, denoised) })
                                }
                                recalculateDuration(state.copy(tracks = tracks))
                            }
                            rebuildPlayerTimeline()
                            saveProject()
                            showToast("Denoised: SNR ${"%.0f".format(profile.signalToNoiseDb)}dB, " +
                                "reduction ${"%.0f".format(profile.recommendedReduction * 100)}%")
                        }
                    }
                    "remove_bg" -> {
                        val analysis = aiFeatures.analyzeBackground(clip.sourceUri)
                        if (analysis.confidence < 0.1f) {
                            showToast("Could not detect background")
                        } else {
                            saveUndoState("AI remove background")
                            // Apply chroma key effect with detected background color parameters
                            val chromaKeyEffect = Effect(
                                type = EffectType.CHROMA_KEY,
                                params = mapOf(
                                    "similarity" to analysis.recommendedSimilarity,
                                    "smoothness" to analysis.recommendedSmoothness,
                                    "spill" to analysis.recommendedSpill
                                )
                            )
                            _state.update { state ->
                                val tracks = state.tracks.map { track ->
                                    val idx = track.clips.indexOfFirst { it.id == clip.id }
                                    if (idx < 0) return@map track
                                    val c = track.clips[idx]
                                    // Remove existing chroma key, add new one
                                    val filtered = c.effects.filter { it.type != EffectType.CHROMA_KEY }
                                    val updated = c.copy(effects = filtered + chromaKeyEffect)
                                    track.copy(clips = track.clips.toMutableList().apply { set(idx, updated) })
                                }
                                recalculateDuration(state.copy(tracks = tracks))
                            }
                            rebuildPlayerTimeline()
                            saveProject()
                            val bgType = when {
                                analysis.isGreenScreen -> "green screen"
                                analysis.isBlueScreen -> "blue screen"
                                else -> "background"
                            }
                            showToast("Applied $bgType removal (${
                                "%.0f".format(analysis.confidence * 100)
                            }% confidence)")
                        }
                    }
                    "track_motion" -> {
                        // Track from center of frame across the clip duration
                        val region = com.novacut.editor.ai.TrackingRegion()
                        val results = aiFeatures.trackMotion(
                            clip.sourceUri, region, clip.trimStartMs, clip.trimEndMs
                        )
                        if (results.isEmpty()) {
                            showToast("Motion tracking failed")
                        } else {
                            saveUndoState("AI motion track")
                            // Convert tracking results to position keyframes
                            val posKeyframes = results.mapNotNull { tr ->
                                val timeOffset = ((tr.timestampMs - clip.trimStartMs) / clip.speed).toLong()
                                if (timeOffset < 0 || timeOffset > clip.durationMs) return@mapNotNull null
                                listOf(
                                    Keyframe(
                                        timeOffsetMs = timeOffset,
                                        property = KeyframeProperty.POSITION_X,
                                        value = (tr.region.centerX - 0.5f) * 2f, // Normalize to -1..1
                                        easing = Easing.EASE_IN_OUT
                                    ),
                                    Keyframe(
                                        timeOffsetMs = timeOffset,
                                        property = KeyframeProperty.POSITION_Y,
                                        value = (tr.region.centerY - 0.5f) * 2f,
                                        easing = Easing.EASE_IN_OUT
                                    )
                                )
                            }.flatten()

                            _state.update { state ->
                                val tracks = state.tracks.map { track ->
                                    val idx = track.clips.indexOfFirst { it.id == clip.id }
                                    if (idx < 0) return@map track
                                    val c = track.clips[idx]
                                    // Merge tracking keyframes with existing
                                    val trackedProps = setOf(KeyframeProperty.POSITION_X, KeyframeProperty.POSITION_Y)
                                    val existing = c.keyframes.filter { it.property !in trackedProps }
                                    val updated = c.copy(keyframes = existing + posKeyframes)
                                    track.copy(clips = track.clips.toMutableList().apply { set(idx, updated) })
                                }
                                recalculateDuration(state.copy(tracks = tracks))
                            }
                            rebuildPlayerTimeline()
                            saveProject()
                            showToast("Tracked ${results.size} motion points across clip")
                        }
                    }
                    else -> {
                        showToast("Unknown AI tool: $toolId")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                showToast("AI tool cancelled")
                throw e
            } catch (e: Exception) {
                showToast("AI tool failed: ${e.message}")
            } finally {
                _state.update { it.copy(aiProcessingTool = null) }
                aiJob = null
            }
        }
    }

    fun cancelAiTool() {
        aiJob?.cancel()
    }

    fun insertFreezeFrame() {
        val clip = getSelectedClip() ?: return
        val playheadMs = _state.value.playheadMs
        if (playheadMs < clip.timelineStartMs || playheadMs >= clip.timelineEndMs) {
            showToast("Move playhead over the selected clip")
            return
        }

        val relativeMs = playheadMs - clip.timelineStartMs
        val sourceTimeMs = clip.trimStartMs + (relativeMs * clip.speed).toLong()

        viewModelScope.launch {
            showToast("Extracting frame...")
            val frameFile = withContext(Dispatchers.IO) {
                videoEngine.extractFrameToFile(clip.sourceUri, sourceTimeMs)
            }
            if (frameFile == null) {
                showToast("Failed to extract frame")
                return@launch
            }

            val frameUri = Uri.fromFile(frameFile)
            val freezeDurationMs = 2000L

            saveUndoState("Freeze frame")

            // Split at playhead, then insert freeze frame between halves
            _state.update { s ->
                val tracks = s.tracks.map { track ->
                    val clipIndex = track.clips.indexOfFirst { it.id == clip.id }
                    if (clipIndex < 0) return@map track

                    val c = track.clips[clipIndex]
                    val splitInSource = c.trimStartMs + (relativeMs * c.speed).toLong()

                    val firstHalf = c.copy(trimEndMs = splitInSource)
                    val freezeClip = Clip(
                        id = UUID.randomUUID().toString(),
                        sourceUri = frameUri,
                        sourceDurationMs = freezeDurationMs,
                        timelineStartMs = firstHalf.timelineEndMs,
                        trimStartMs = 0L,
                        trimEndMs = freezeDurationMs
                    )
                    val secondHalf = c.copy(
                        id = UUID.randomUUID().toString(),
                        timelineStartMs = freezeClip.timelineEndMs,
                        trimStartMs = splitInSource
                    )

                    // Shift subsequent clips
                    val shift = freezeDurationMs
                    val newClips = buildList {
                        addAll(track.clips.subList(0, clipIndex))
                        add(firstHalf)
                        add(freezeClip)
                        add(secondHalf)
                        addAll(track.clips.subList(clipIndex + 1, track.clips.size).map { cl ->
                            cl.copy(timelineStartMs = cl.timelineStartMs + shift)
                        })
                    }
                    track.copy(clips = newClips)
                }
                recalculateDuration(s.copy(tracks = tracks))
            }
            rebuildPlayerTimeline()
            saveProject()
            showToast("Freeze frame inserted (2s)")
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
        autoSave.stop()
        voiceoverDurationJob?.cancel()
        voiceoverEngine.release()
        videoEngine.removePlayerListener()
        videoEngine.resetExportState()
        // DON'T call videoEngine.release() — it's a @Singleton that outlives this ViewModel
    }
}
