package com.novacut.editor.ui.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.novacut.editor.engine.AudioEngine
import com.novacut.editor.engine.AutoSaveState
import com.novacut.editor.engine.ExportService
import com.novacut.editor.engine.ExportState
import com.novacut.editor.engine.ProjectAutoSave
import com.novacut.editor.engine.VideoEngine
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
import android.os.Build
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
    val waveforms: Map<String, FloatArray> = emptyMap(),
    val showAudioPanel: Boolean = false,
    val showAiToolsPanel: Boolean = false,
    val showTransformPanel: Boolean = false,
    val showCropPanel: Boolean = false,
    val selectedEffectId: String? = null,
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
    private val projectDao: ProjectDao,
    private val audioEngine: AudioEngine,
    private val autoSave: ProjectAutoSave,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val projectId: String? = savedStateHandle["projectId"]

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    val engine get() = videoEngine

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

        // Periodic playhead sync (~30fps)
        viewModelScope.launch {
            while (isActive) {
                delay(33)
                val player = videoEngine.getPlayer()
                if (player.isPlaying) {
                    _state.update { it.copy(playheadMs = player.currentPosition) }
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
                        val start = (newTrimStartMs ?: clip.trimStartMs).coerceAtLeast(0L)
                        val end = (newTrimEndMs ?: clip.trimEndMs).coerceAtLeast(start + 100L)
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
        selectedEffectId = null
    )

    fun dismissAllPanels() { _state.update { dismissedPanelState(it) } }

    // Sheet toggles — each atomically dismisses other panels and shows the target
    fun showMediaPicker() { _state.update { dismissedPanelState(it).copy(showMediaPicker = true) } }
    fun hideMediaPicker() { _state.update { it.copy(showMediaPicker = false) } }
    fun showExportSheet() {
        // Reset export state so stale COMPLETE/ERROR doesn't show on reopen
        videoEngine.resetExportState()
        _state.update { dismissedPanelState(it).copy(showExportSheet = true, exportState = ExportState.IDLE, exportProgress = 0f) }
    }
    fun hideExportSheet() { _state.update { it.copy(showExportSheet = false) } }
    fun showEffectsPanel() { _state.update { dismissedPanelState(it).copy(showEffectsPanel = true) } }
    fun hideEffectsPanel() { _state.update { it.copy(showEffectsPanel = false) } }
    fun showTextEditor() { _state.update { dismissedPanelState(it).copy(showTextEditor = true) } }
    fun hideTextEditor() { _state.update { it.copy(showTextEditor = false) } }
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

    fun setClipFadeIn(clipId: String, fadeInMs: Long) {
        // Fade in/out are metadata only — no undo spam needed
        showToast("Fade in: ${fadeInMs}ms (applied on export)")
    }

    fun setClipFadeOut(clipId: String, fadeOutMs: Long) {
        showToast("Fade out: ${fadeOutMs}ms (applied on export)")
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
            val config = _state.value.exportConfig
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

            videoEngine.export(
                tracks = _state.value.tracks,
                config = config,
                outputFile = outputFile,
                onProgress = { progress ->
                    _state.update { it.copy(exportProgress = progress) }
                },
                onComplete = {
                    showToast("Export complete: ${outputFile.name}")
                    appContext.stopService(serviceIntent)
                },
                onError = { e ->
                    showToast("Export failed: ${e.message}")
                    appContext.stopService(serviceIntent)
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
            val project = _state.value.project.copy(
                updatedAt = System.currentTimeMillis(),
                durationMs = _state.value.totalDurationMs
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

    private fun recalculateDuration(state: EditorState): EditorState {
        val totalDuration = state.tracks.maxOfOrNull { t ->
            t.clips.maxOfOrNull { it.timelineEndMs } ?: 0L
        } ?: 0L
        return state.copy(totalDurationMs = totalDuration)
    }

    override fun onCleared() {
        super.onCleared()
        autoSave.stop()
        videoEngine.removePlayerListener()
        videoEngine.resetExportState()
        // DON'T call videoEngine.release() — it's a @Singleton that outlives this ViewModel
    }
}
