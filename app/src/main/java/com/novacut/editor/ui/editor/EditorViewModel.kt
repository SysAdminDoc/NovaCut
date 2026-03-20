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
import com.novacut.editor.engine.SmartRenderEngine
import com.novacut.editor.engine.SubtitleExporter
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
    val editingTextOverlayId: String? = null,
    // New panels
    val showColorGrading: Boolean = false,
    val showAudioMixer: Boolean = false,
    val showKeyframeEditor: Boolean = false,
    val showSpeedCurveEditor: Boolean = false,
    val showMaskEditor: Boolean = false,
    val showBlendModeSelector: Boolean = false,
    val showBatchExport: Boolean = false,
    val showPipPresets: Boolean = false,
    val showChromaKey: Boolean = false,
    val showScopes: Boolean = false,
    val activeScopeType: com.novacut.editor.ui.editor.ScopeType = com.novacut.editor.ui.editor.ScopeType.HISTOGRAM,
    val showCaptionEditor: Boolean = false,
    val showChapterMarkers: Boolean = false,
    val showSnapshotHistory: Boolean = false,
    val showTextTemplates: Boolean = false,
    val showMediaManager: Boolean = false,
    val showAudioNorm: Boolean = false,
    val showRenderPreview: Boolean = false,
    val showCloudBackup: Boolean = false,
    val exportStartTime: Long = 0L,
    val renderSegments: List<com.novacut.editor.engine.SmartRenderEngine.RenderSegment> = emptyList(),
    val renderSummary: com.novacut.editor.engine.SmartRenderEngine.SmartRenderSummary? = null,
    // Chapter markers
    val chapterMarkers: List<ChapterMarker> = emptyList(),
    // Multi-select
    val selectedClipIds: Set<String> = emptySet(),
    // Mask state
    val selectedMaskId: String? = null,
    // Keyframe state
    val activeKeyframeProperties: Set<KeyframeProperty> = setOf(
        KeyframeProperty.POSITION_X, KeyframeProperty.POSITION_Y,
        KeyframeProperty.SCALE_X, KeyframeProperty.SCALE_Y,
        KeyframeProperty.OPACITY
    ),
    // Audio mixer state
    val vuLevels: Map<String, Pair<Float, Float>> = emptyMap(),
    // Beat markers
    val beatMarkers: List<Long> = emptyList(),
    // Batch export
    val batchExportQueue: List<BatchExportItem> = emptyList(),
    // Project snapshots
    val projectSnapshots: List<ProjectSnapshot> = emptyList(),
    // Proxy
    val proxySettings: ProxySettings = ProxySettings()
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
                // Extract waveforms for all recovered clips
                for (track in recovery.tracks) {
                    for (clip in track.clips) {
                        viewModelScope.launch {
                            val waveform = audioEngine.extractWaveform(clip.sourceUri)
                            _state.update { it.copy(waveforms = it.waveforms + (clip.id to waveform)) }
                        }
                    }
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
                        val pixelsPerMs = s.zoomLevel * 0.15f
                        if (widthPx > 0 && pixelsPerMs >= 0.001f) {
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
        showToast("Clip duplicated")
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
        showToast("Clips merged")
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
        showToast("Clip split")
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

    fun toggleEffectEnabled(clipId: String, effectId: String) {
        saveUndoState("Toggle effect")
        _state.update { state ->
            val tracks = state.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(effects = clip.effects.map { e ->
                            if (e.id == effectId) e.copy(enabled = !e.enabled)
                            else e
                        })
                    } else clip
                })
            }
            state.copy(tracks = tracks)
        }
        saveProject()
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
        if (text.startTimeMs >= text.endTimeMs) { showToast("Invalid text overlay duration"); return }
        saveUndoState("Add text")
        _state.update { it.copy(textOverlays = it.textOverlays + text) }
    }

    fun updateTextOverlay(textOverlay: TextOverlay) {
        if (textOverlay.startTimeMs >= textOverlay.endTimeMs) { showToast("Invalid text overlay duration"); return }
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
    private fun pauseIfPlaying() {
        if (videoEngine.isPlaying()) {
            videoEngine.pause()
            _state.update { it.copy(isPlaying = false) }
        }
    }

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
        showColorGrading = false,
        showAudioMixer = false,
        showKeyframeEditor = false,
        showSpeedCurveEditor = false,
        showMaskEditor = false,
        showBlendModeSelector = false,
        showBatchExport = false,
        showPipPresets = false,
        showChromaKey = false,
        showCaptionEditor = false,
        showChapterMarkers = false,
        showSnapshotHistory = false,
        showTextTemplates = false,
        showMediaManager = false,
        showAudioNorm = false,
        showRenderPreview = false,
        showCloudBackup = false,
        selectedEffectId = null,
        editingTextOverlayId = null,
        selectedMaskId = null
    )

    fun dismissAllPanels() { _state.update { dismissedPanelState(it) } }

    // Sheet toggles — each atomically dismisses other panels and shows the target
    // All show methods pause playback so users can adjust settings without video moving
    fun showMediaPicker() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showMediaPicker = true) } }
    fun hideMediaPicker() { _state.update { it.copy(showMediaPicker = false) } }
    fun showExportSheet() {
        pauseIfPlaying()
        videoEngine.resetExportState()
        _state.update { dismissedPanelState(it).copy(showExportSheet = true, exportState = ExportState.IDLE, exportProgress = 0f, exportErrorMessage = null) }
    }
    fun hideExportSheet() { _state.update { it.copy(showExportSheet = false) } }
    fun showEffectsPanel() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showEffectsPanel = true) } }
    fun hideEffectsPanel() { _state.update { it.copy(showEffectsPanel = false) } }
    fun showTextEditor() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showTextEditor = true, editingTextOverlayId = null) } }
    fun editTextOverlay(id: String) { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showTextEditor = true, editingTextOverlayId = id) } }
    fun hideTextEditor() { _state.update { it.copy(showTextEditor = false, editingTextOverlayId = null) } }
    fun showTransitionPicker() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showTransitionPicker = true) } }
    fun hideTransitionPicker() { _state.update { it.copy(showTransitionPicker = false) } }
    fun showAudioPanel() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showAudioPanel = true) } }
    fun hideAudioPanel() { _state.update { it.copy(showAudioPanel = false) } }
    fun showAiToolsPanel() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showAiToolsPanel = true) } }
    fun hideAiToolsPanel() { _state.update { it.copy(showAiToolsPanel = false) } }
    fun showTransformPanel() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showTransformPanel = true) } }
    fun hideTransformPanel() { _state.update { it.copy(showTransformPanel = false) } }
    fun showCropPanel() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showCropPanel = true) } }
    fun hideCropPanel() { _state.update { it.copy(showCropPanel = false) } }
    fun selectEffect(effectId: String?) { _state.update { it.copy(selectedEffectId = effectId) } }
    fun clearSelectedEffect() { _state.update { it.copy(selectedEffectId = null) } }
    fun showVoiceoverPanel() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showVoiceoverRecorder = true) } }
    fun hideVoiceoverPanel() {
        if (_state.value.isRecordingVoiceover) stopVoiceover()
        voiceoverDurationJob?.cancel()
        _state.update { it.copy(showVoiceoverRecorder = false) }
    }

    // --- Color Grading ---
    fun showColorGrading() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showColorGrading = true) } }
    fun hideColorGrading() { _state.update { it.copy(showColorGrading = false) } }

    fun updateClipColorGrade(colorGrade: ColorGrade) {
        val clipId = _state.value.selectedClipId ?: return
        saveUndoState("Color grade")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(colorGrade = colorGrade) else clip
                })
            })
        }
    }

    fun importLut() {
        // Trigger file picker via activity result — handled in EditorScreen
        showToast("Select a .cube or .3dl LUT file")
    }

    fun setClipLut(lutPath: String) {
        val clipId = _state.value.selectedClipId ?: return
        val currentGrade = getSelectedClip()?.colorGrade ?: ColorGrade()
        updateClipColorGrade(currentGrade.copy(lutPath = lutPath))
    }

    // --- Audio Mixer ---
    fun showAudioMixer() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showAudioMixer = true) } }
    fun hideAudioMixer() { _state.update { it.copy(showAudioMixer = false) } }

    fun setTrackVolume(trackId: String, volume: Float) {
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.id == trackId) track.copy(volume = volume.coerceIn(0f, 2f)) else track
            })
        }
    }

    fun setTrackPan(trackId: String, pan: Float) {
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.id == trackId) track.copy(pan = pan.coerceIn(-1f, 1f)) else track
            })
        }
    }

    fun toggleTrackSolo(trackId: String) {
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.id == trackId) track.copy(isSolo = !track.isSolo) else track
            })
        }
    }

    fun addTrackAudioEffect(trackId: String, type: AudioEffectType) {
        saveUndoState("Add audio effect")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.id == trackId) {
                    val effect = AudioEffect(
                        type = type,
                        params = AudioEffectType.defaultParams(type)
                    )
                    track.copy(audioEffects = track.audioEffects + effect)
                } else track
            })
        }
    }

    fun removeTrackAudioEffect(trackId: String, effectId: String) {
        saveUndoState("Remove audio effect")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.id == trackId) {
                    track.copy(audioEffects = track.audioEffects.filter { it.id != effectId })
                } else track
            })
        }
    }

    fun updateTrackAudioEffectParam(trackId: String, effectId: String, param: String, value: Float) {
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.id == trackId) {
                    track.copy(audioEffects = track.audioEffects.map { effect ->
                        if (effect.id == effectId) {
                            effect.copy(params = effect.params + (param to value))
                        } else effect
                    })
                } else track
            })
        }
    }

    fun detectBeats() {
        val s = _state.value
        val audioClips = s.tracks
            .filter { it.type == TrackType.AUDIO || it.type == TrackType.VIDEO }
            .flatMap { it.clips }
        if (audioClips.isEmpty()) {
            showToast("No audio clips to analyze")
            return
        }
        viewModelScope.launch {
            showToast("Detecting beats...")
            val waveform = audioEngine.extractWaveform(audioClips.first().sourceUri, 44100)
            val pcm = waveform.map { (it * 32767).toInt().toShort() }.toShortArray()
            val beats = com.novacut.editor.engine.AudioEffectsEngine.detectBeats(pcm, 44100, 1)
            _state.update { it.copy(beatMarkers = beats) }
            showToast("Found ${beats.size} beats")
        }
    }

    // --- Keyframe Editor ---
    fun showKeyframeEditor() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showKeyframeEditor = true) } }
    fun hideKeyframeEditor() { _state.update { it.copy(showKeyframeEditor = false) } }

    fun toggleKeyframeProperty(property: KeyframeProperty) {
        _state.update { s ->
            val current = s.activeKeyframeProperties
            val updated = if (property in current) current - property else current + property
            s.copy(activeKeyframeProperties = updated)
        }
    }

    fun updateClipKeyframes(keyframes: List<Keyframe>) {
        val clipId = _state.value.selectedClipId ?: return
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(keyframes = keyframes) else clip
                })
            })
        }
    }

    fun addKeyframe(property: KeyframeProperty, timeOffsetMs: Long, value: Float) {
        val clipId = _state.value.selectedClipId ?: return
        saveUndoState("Add keyframe")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        val kf = Keyframe(timeOffsetMs, property, value, interpolation = KeyframeInterpolation.BEZIER)
                        clip.copy(keyframes = clip.keyframes + kf)
                    } else clip
                })
            })
        }
    }

    fun deleteKeyframe(keyframe: Keyframe) {
        val clipId = _state.value.selectedClipId ?: return
        saveUndoState("Delete keyframe")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(keyframes = clip.keyframes.filter {
                            !(it.timeOffsetMs == keyframe.timeOffsetMs && it.property == keyframe.property && it.value == keyframe.value)
                        })
                    } else clip
                })
            })
        }
    }

    // --- Speed Curve ---
    fun showSpeedCurveEditor() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showSpeedCurveEditor = true) } }
    fun hideSpeedCurveEditor() { _state.update { it.copy(showSpeedCurveEditor = false) } }

    fun setClipSpeedCurve(speedCurve: SpeedCurve?) {
        val clipId = _state.value.selectedClipId ?: return
        saveUndoState("Speed curve")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(speedCurve = speedCurve) else clip
                })
            })
        }
        rebuildPlayerTimeline()
    }

    // --- Mask Editor ---
    fun showMaskEditor() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showMaskEditor = true) } }
    fun hideMaskEditor() { _state.update { it.copy(showMaskEditor = false, selectedMaskId = null) } }

    fun selectMask(maskId: String?) {
        _state.update { it.copy(selectedMaskId = maskId) }
    }

    fun addMask(type: MaskType) {
        val clipId = _state.value.selectedClipId ?: return
        saveUndoState("Add mask")
        val defaultPoints = when (type) {
            MaskType.RECTANGLE -> listOf(MaskPoint(0.25f, 0.25f), MaskPoint(0.75f, 0.75f))
            MaskType.ELLIPSE -> listOf(MaskPoint(0.5f, 0.5f), MaskPoint(0.25f, 0.25f))
            MaskType.LINEAR_GRADIENT -> listOf(MaskPoint(0.5f, 0.3f), MaskPoint(0.5f, 0.7f))
            MaskType.RADIAL_GRADIENT -> listOf(MaskPoint(0.5f, 0.5f), MaskPoint(0.3f, 0.3f))
            MaskType.FREEHAND -> emptyList()
        }
        val mask = Mask(type = type, points = defaultPoints)
        _state.update { s ->
            s.copy(
                tracks = s.tracks.map { track ->
                    track.copy(clips = track.clips.map { clip ->
                        if (clip.id == clipId) clip.copy(masks = clip.masks + mask) else clip
                    })
                },
                selectedMaskId = mask.id
            )
        }
    }

    fun updateMask(mask: Mask) {
        val clipId = _state.value.selectedClipId ?: return
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(masks = clip.masks.map { if (it.id == mask.id) mask else it })
                    } else clip
                })
            })
        }
    }

    fun deleteMask(maskId: String) {
        val clipId = _state.value.selectedClipId ?: return
        saveUndoState("Delete mask")
        _state.update { s ->
            s.copy(
                tracks = s.tracks.map { track ->
                    track.copy(clips = track.clips.map { clip ->
                        if (clip.id == clipId) clip.copy(masks = clip.masks.filter { it.id != maskId }) else clip
                    })
                },
                selectedMaskId = null
            )
        }
    }

    fun updateMaskPoint(maskId: String, pointIndex: Int, x: Float, y: Float) {
        val clipId = _state.value.selectedClipId ?: return
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(masks = clip.masks.map { mask ->
                            if (mask.id == maskId && pointIndex in mask.points.indices) {
                                mask.copy(points = mask.points.toMutableList().apply {
                                    set(pointIndex, get(pointIndex).copy(x = x, y = y))
                                })
                            } else mask
                        })
                    } else clip
                })
            })
        }
    }

    fun setFreehandMaskPoints(maskId: String, points: List<MaskPoint>) {
        val clipId = _state.value.selectedClipId ?: return
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(masks = clip.masks.map { mask ->
                            if (mask.id == maskId) mask.copy(points = points) else mask
                        })
                    } else clip
                })
            })
        }
    }

    // --- Blend Mode ---
    fun showBlendModeSelector() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showBlendModeSelector = true) } }
    fun hideBlendModeSelector() { _state.update { it.copy(showBlendModeSelector = false) } }

    fun setClipBlendMode(blendMode: BlendMode) {
        val clipId = _state.value.selectedClipId ?: return
        saveUndoState("Blend mode")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(blendMode = blendMode) else clip
                })
            })
        }
    }

    fun setTrackBlendMode(trackId: String, blendMode: BlendMode) {
        saveUndoState("Track blend mode")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.id == trackId) track.copy(blendMode = blendMode) else track
            })
        }
    }

    fun setTrackOpacity(trackId: String, opacity: Float) {
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                if (track.id == trackId) track.copy(opacity = opacity.coerceIn(0f, 1f)) else track
            })
        }
    }

    // --- Batch Export ---
    fun showBatchExport() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showBatchExport = true) } }
    fun hideBatchExport() { _state.update { it.copy(showBatchExport = false) } }

    fun addBatchExportItem(config: ExportConfig, name: String) {
        val item = BatchExportItem(config = config, outputName = name)
        _state.update { it.copy(batchExportQueue = it.batchExportQueue + item) }
    }

    fun removeBatchExportItem(id: String) {
        _state.update { it.copy(batchExportQueue = it.batchExportQueue.filter { it.id != id }) }
    }

    // --- Effect Keyframes ---
    fun addEffectKeyframe(effectId: String, paramName: String, timeOffsetMs: Long, value: Float) {
        val clipId = _state.value.selectedClipId ?: return
        saveUndoState("Effect keyframe")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(effects = clip.effects.map { effect ->
                            if (effect.id == effectId) {
                                val kf = EffectKeyframe(timeOffsetMs, paramName, value)
                                effect.copy(keyframes = effect.keyframes + kf)
                            } else effect
                        })
                    } else clip
                })
            })
        }
    }

    // --- Adjustment Layers ---
    fun addAdjustmentLayer() {
        saveUndoState("Add adjustment layer")
        _state.update { s ->
            val newTrack = Track(
                type = TrackType.ADJUSTMENT,
                index = s.tracks.size
            )
            s.copy(tracks = s.tracks + newTrack)
        }
    }

    // --- Captions ---
    fun addCaption(caption: Caption) {
        val clipId = _state.value.selectedClipId ?: return
        saveUndoState("Add caption")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(captions = clip.captions + caption) else clip
                })
            })
        }
    }

    fun updateCaption(caption: Caption) {
        val clipId = _state.value.selectedClipId ?: return
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(captions = clip.captions.map { if (it.id == caption.id) caption else it })
                    } else clip
                })
            })
        }
    }

    fun removeCaption(captionId: String) {
        val clipId = _state.value.selectedClipId ?: return
        saveUndoState("Remove caption")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(captions = clip.captions.filter { it.id != captionId })
                    } else clip
                })
            })
        }
    }

    // --- Project Snapshots ---
    fun createSnapshot(label: String = "") {
        val s = _state.value
        val autoSaveState = AutoSaveState(projectId = s.project.id, tracks = s.tracks, textOverlays = s.textOverlays, playheadMs = s.playheadMs)
        val json = autoSaveState.serialize()
        val snapshot = ProjectSnapshot(
            projectId = s.project.id,
            timestamp = System.currentTimeMillis(),
            label = label.ifEmpty { "Snapshot ${s.projectSnapshots.size + 1}" },
            stateJson = json
        )
        _state.update { it.copy(projectSnapshots = it.projectSnapshots + snapshot) }
        showToast("Snapshot saved: ${snapshot.label}")
    }

    fun restoreSnapshot(snapshotId: String) {
        val snapshot = _state.value.projectSnapshots.find { it.id == snapshotId } ?: return
        val recovery = AutoSaveState.deserialize(snapshot.stateJson)
        saveUndoState("Restore snapshot")
        _state.update {
            it.copy(
                tracks = recovery.tracks,
                textOverlays = recovery.textOverlays,
                playheadMs = recovery.playheadMs
            )
        }
        rebuildPlayerTimeline()
        showToast("Restored: ${snapshot.label}")
    }

    // --- Proxy ---
    fun setProxyEnabled(enabled: Boolean) {
        _state.update { it.copy(proxySettings = it.proxySettings.copy(enabled = enabled)) }
    }

    // --- Render Preview + Smart Render ---
    fun showRenderPreview() {
        pauseIfPlaying()
        val s = _state.value
        val segments = SmartRenderEngine.analyzeTimeline(s.tracks, s.exportConfig, s.textOverlays)
        val summary = SmartRenderEngine.getSummary(segments)
        _state.update { dismissedPanelState(it).copy(
            showRenderPreview = true,
            renderSegments = segments,
            renderSummary = summary
        ) }
    }
    fun hideRenderPreview() { _state.update { it.copy(showRenderPreview = false) } }

    fun renderQuickPreview() {
        // Export at 480p for quick review
        val previewConfig = _state.value.exportConfig.copy(
            resolution = com.novacut.editor.model.Resolution.SD_480P,
            quality = com.novacut.editor.model.ExportQuality.LOW
        )
        _state.update { it.copy(exportConfig = previewConfig) }
        hideRenderPreview()
        showExportSheet()
        showToast("Rendering preview at 480p...")
    }

    // --- Cloud Backup ---
    fun showCloudBackup() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showCloudBackup = true) } }
    fun hideCloudBackup() { _state.update { it.copy(showCloudBackup = false) } }

    // --- Slip/Slide Edit ---
    fun slipClip(clipId: String, slipAmountMs: Long) {
        saveUndoState("Slip edit")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        val newTrimStart = (clip.trimStartMs + slipAmountMs).coerceIn(0L, clip.sourceDurationMs - 100)
                        val duration = clip.trimEndMs - clip.trimStartMs
                        val newTrimEnd = (newTrimStart + duration).coerceAtMost(clip.sourceDurationMs)
                        clip.copy(trimStartMs = newTrimStart, trimEndMs = newTrimEnd)
                    } else clip
                })
            })
        }
        rebuildPlayerTimeline()
    }

    fun slideClip(clipId: String, slideAmountMs: Long) {
        saveUndoState("Slide edit")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                val clipIndex = track.clips.indexOfFirst { it.id == clipId }
                if (clipIndex < 0) return@map track

                val clip = track.clips[clipIndex]
                val newStart = (clip.timelineStartMs + slideAmountMs).coerceAtLeast(0L)

                // Adjust neighbors
                val updatedClips = track.clips.toMutableList()
                updatedClips[clipIndex] = clip.copy(timelineStartMs = newStart)

                // Ensure no overlap with adjacent clips
                if (clipIndex > 0) {
                    val prevClip = updatedClips[clipIndex - 1]
                    if (newStart < prevClip.timelineEndMs) {
                        val trimAmount = prevClip.timelineEndMs - newStart
                        updatedClips[clipIndex - 1] = prevClip.copy(
                            trimEndMs = (prevClip.trimEndMs - (trimAmount * prevClip.speed).toLong()).coerceAtLeast(prevClip.trimStartMs + 100)
                        )
                    }
                }
                if (clipIndex < updatedClips.size - 1) {
                    val nextClip = updatedClips[clipIndex + 1]
                    val newEnd = newStart + clip.durationMs
                    if (newEnd > nextClip.timelineStartMs) {
                        val overlap = newEnd - nextClip.timelineStartMs
                        updatedClips[clipIndex + 1] = nextClip.copy(
                            timelineStartMs = newEnd,
                            trimStartMs = (nextClip.trimStartMs + (overlap * nextClip.speed).toLong()).coerceAtMost(nextClip.trimEndMs - 100)
                        )
                    }
                }

                track.copy(clips = updatedClips)
            })
        }
        rebuildPlayerTimeline()
    }

    // --- Export ---
    fun cancelExport() {
        videoEngine.cancelExport()
    }

    // --- Media Manager ---
    fun showMediaManager() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showMediaManager = true) } }
    fun hideMediaManager() { _state.update { it.copy(showMediaManager = false) } }

    fun jumpToClip(clipId: String) {
        val clip = _state.value.tracks.flatMap { it.clips }.find { it.id == clipId } ?: return
        val trackId = _state.value.tracks.find { it.clips.any { c -> c.id == clipId } }?.id
        seekTo(clip.timelineStartMs)
        selectClip(clipId, trackId)
        hideMediaManager()
    }

    // --- Audio Normalization ---
    fun showAudioNorm() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showAudioNorm = true) } }
    fun hideAudioNorm() { _state.update { it.copy(showAudioNorm = false) } }

    fun normalizeAudio(targetLufs: Float) {
        val clipId = _state.value.selectedClipId ?: return
        saveUndoState("Normalize audio")
        // Calculate gain adjustment from target LUFS
        // Simplified: map LUFS target to volume multiplier
        val volumeMultiplier = Math.pow(10.0, (targetLufs + 14.0) / 20.0).toFloat().coerceIn(0.1f, 3f)
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(volume = volumeMultiplier) else clip
                })
            })
        }
        hideAudioNorm()
        showToast("Audio normalized to %.0f LUFS".format(targetLufs))
    }

    // --- Color Match ---
    fun colorMatchToReference(referenceClipId: String) {
        val targetClipId = _state.value.selectedClipId ?: return
        val refClip = _state.value.tracks.flatMap { it.clips }.find { it.id == referenceClipId } ?: return
        val targetClip = _state.value.tracks.flatMap { it.clips }.find { it.id == targetClipId } ?: return

        viewModelScope.launch {
            showToast("Analyzing colors...")
            val refStats = com.novacut.editor.engine.ColorMatchEngine.analyzeFrame(
                appContext, refClip.sourceUri, refClip.trimStartMs + refClip.durationMs / 2
            )
            val targetStats = com.novacut.editor.engine.ColorMatchEngine.analyzeFrame(
                appContext, targetClip.sourceUri, targetClip.trimStartMs + targetClip.durationMs / 2
            )

            if (refStats != null && targetStats != null) {
                saveUndoState("Color match")
                val grade = com.novacut.editor.engine.ColorMatchEngine.generateColorMatch(refStats, targetStats)
                updateClipColorGrade(grade)
                showToast("Color matched to reference clip")
            } else {
                showToast("Could not analyze frames")
            }
        }
    }

    // --- Compound Clips ---
    fun createCompoundClip() {
        val selectedIds = _state.value.selectedClipIds
        if (selectedIds.size < 2) {
            showToast("Select at least 2 clips to create compound")
            return
        }
        saveUndoState("Create compound clip")

        _state.update { s ->
            val allClips = s.tracks.flatMap { it.clips }
            val selectedClips = allClips.filter { it.id in selectedIds }.sortedBy { it.timelineStartMs }
            if (selectedClips.isEmpty()) return@update s

            val compoundStart = selectedClips.minOf { it.timelineStartMs }
            val compoundEnd = selectedClips.maxOf { it.timelineEndMs }

            // Create compound clip containing the selected clips
            val compoundClip = selectedClips.first().copy(
                id = java.util.UUID.randomUUID().toString(),
                timelineStartMs = compoundStart,
                trimEndMs = selectedClips.first().trimStartMs + (compoundEnd - compoundStart),
                isCompound = true,
                compoundClips = selectedClips.map { it.copy() }
            )

            // Remove original clips and insert compound
            val tracks = s.tracks.map { track ->
                val remainingClips = track.clips.filter { it.id !in selectedIds }
                val hadSelected = track.clips.any { it.id in selectedIds }
                if (hadSelected) {
                    track.copy(clips = (remainingClips + compoundClip).sortedBy { it.timelineStartMs })
                } else track
            }

            recalculateDuration(s.copy(
                tracks = tracks,
                selectedClipIds = emptySet(),
                selectedClipId = compoundClip.id
            ))
        }
        rebuildPlayerTimeline()
        showToast("Compound clip created")
    }

    // --- Text Templates ---
    fun showTextTemplates() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showTextTemplates = true) } }
    fun hideTextTemplates() { _state.update { it.copy(showTextTemplates = false) } }

    fun applyTextTemplate(template: com.novacut.editor.model.TextTemplate) {
        saveUndoState("Apply text template")
        val playhead = _state.value.playheadMs
        template.layers.forEachIndexed { index, layer ->
            val overlay = layer.copy(
                id = UUID.randomUUID().toString(),
                startTimeMs = playhead + index * 100L,
                endTimeMs = playhead + template.durationMs + index * 100L
            )
            _state.update { s -> s.copy(textOverlays = s.textOverlays + overlay) }
        }
        hideTextTemplates()
        showToast("Template applied: ${template.name}")
    }

    // --- Project Archive ---
    fun exportProjectArchive() {
        viewModelScope.launch {
            showToast("Exporting project archive...")
            val s = _state.value
            val dir = java.io.File(appContext.getExternalFilesDir(null), "archives")
            dir.mkdirs()
            val file = java.io.File(dir, "${s.project.name}.novacut")
            val success = com.novacut.editor.engine.ProjectArchive.exportArchive(
                context = appContext,
                projectId = s.project.id,
                tracks = s.tracks,
                textOverlays = s.textOverlays,
                playheadMs = s.playheadMs,
                outputFile = file
            )
            showToast(if (success) "Archive saved: ${file.name}" else "Archive export failed")
        }
    }

    // --- Linked A/V ---
    fun unlinkAudioVideo() {
        val clipId = _state.value.selectedClipId ?: return
        saveUndoState("Unlink A/V")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(linkedClipId = null) else clip
                })
            })
        }
        showToast("Audio/video unlinked")
    }

    // --- Captions ---
    fun showCaptionEditor() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showCaptionEditor = true) } }
    fun hideCaptionEditor() { _state.update { it.copy(showCaptionEditor = false) } }

    fun generateAutoCaption() {
        val clipId = _state.value.selectedClipId ?: return
        viewModelScope.launch {
            showToast("Generating captions...")
            runAiTool("auto_captions")
        }
    }

    // --- Chapter Markers ---
    fun showChapterMarkers() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showChapterMarkers = true) } }
    fun hideChapterMarkers() { _state.update { it.copy(showChapterMarkers = false) } }

    fun addChapterMarker(marker: ChapterMarker) {
        _state.update { s ->
            val updated = (s.chapterMarkers + marker).sortedBy { it.timeMs }
            s.copy(chapterMarkers = updated)
        }
        showToast("Chapter added at ${formatTime(marker.timeMs)}")
    }

    fun updateChapterMarker(index: Int, marker: ChapterMarker) {
        _state.update { s ->
            if (index in s.chapterMarkers.indices) {
                val updated = s.chapterMarkers.toMutableList()
                updated[index] = marker
                s.copy(chapterMarkers = updated)
            } else s
        }
    }

    fun deleteChapterMarker(index: Int) {
        _state.update { s ->
            if (index in s.chapterMarkers.indices) {
                s.copy(chapterMarkers = s.chapterMarkers.toMutableList().also { it.removeAt(index) })
            } else s
        }
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000
        val m = s / 60
        return "%d:%02d".format(m, s % 60)
    }

    // --- Snapshot History ---
    fun showSnapshotHistory() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showSnapshotHistory = true) } }
    fun hideSnapshotHistory() { _state.update { it.copy(showSnapshotHistory = false) } }

    fun deleteSnapshot(snapshotId: String) {
        _state.update { it.copy(projectSnapshots = it.projectSnapshots.filter { s -> s.id != snapshotId }) }
    }

    // --- Multi-select ---
    fun toggleClipMultiSelect(clipId: String) {
        _state.update { s ->
            val current = s.selectedClipIds
            val updated = if (clipId in current) current - clipId else current + clipId
            s.copy(selectedClipIds = updated)
        }
    }

    fun clearMultiSelect() {
        _state.update { it.copy(selectedClipIds = emptySet()) }
    }

    fun deleteMultiSelectedClips() {
        val clipIds = _state.value.selectedClipIds
        if (clipIds.isEmpty()) return
        saveUndoState("Delete ${clipIds.size} clips")
        _state.update { s ->
            val tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.filter { it.id !in clipIds })
            }
            recalculateDuration(s.copy(
                tracks = tracks,
                selectedClipIds = emptySet(),
                selectedClipId = null,
                selectedTrackId = null
            ))
        }
        rebuildPlayerTimeline()
        showToast("Deleted ${clipIds.size} clips")
    }

    // --- Subtitle Export ---
    fun exportSubtitles(format: SubtitleFormat) {
        val captions = _state.value.tracks.flatMap { it.clips }.flatMap { it.captions }
        if (captions.isEmpty()) {
            showToast("No captions to export")
            return
        }
        viewModelScope.launch {
            val dir = java.io.File(appContext.getExternalFilesDir(null), "subtitles")
            dir.mkdirs()
            val file = java.io.File(dir, "${_state.value.project.name}.${format.extension}")
            val success = SubtitleExporter.export(captions, format, file)
            if (success) {
                showToast("Exported to ${file.name}")
            } else {
                showToast("Export failed")
            }
        }
    }

    // --- PiP ---
    fun applyPipPreset(preset: com.novacut.editor.ui.editor.PipPreset) {
        val clipId = _state.value.selectedClipId ?: return
        saveUndoState("PiP preset")
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(
                        positionX = preset.posX,
                        positionY = preset.posY,
                        scaleX = preset.scaleX,
                        scaleY = preset.scaleY
                    ) else clip
                })
            })
        }
    }

    fun showPipPresets() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showPipPresets = true) } }
    fun hidePipPresets() { _state.update { it.copy(showPipPresets = false) } }

    fun showChromaKey() { pauseIfPlaying(); _state.update { dismissedPanelState(it).copy(showChromaKey = true) } }
    fun hideChromaKey() { _state.update { it.copy(showChromaKey = false) } }

    // --- Video Scopes ---
    fun toggleScopes() {
        _state.update { it.copy(showScopes = !it.showScopes) }
    }

    fun setScopeType(type: com.novacut.editor.ui.editor.ScopeType) {
        _state.update { it.copy(activeScopeType = type) }
    }

    // --- Transform overlay ---
    fun setClipAnchor(x: Float, y: Float) {
        val clipId = _state.value.selectedClipId ?: return
        _state.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(anchorX = x, anchorY = y) else clip
                })
            })
        }
    }

    // --- Auto-ducking ---
    fun autoDuck() {
        val s = _state.value
        val musicTracks = s.tracks.filter { it.type == TrackType.AUDIO }
        val voiceTracks = s.tracks.filter { it.type == TrackType.VIDEO }

        if (musicTracks.isEmpty() || voiceTracks.isEmpty()) {
            showToast("Need both voice and music tracks for ducking")
            return
        }

        viewModelScope.launch {
            showToast("Analyzing speech regions...")
            val voiceClip = voiceTracks.flatMap { it.clips }.firstOrNull() ?: return@launch
            val waveform = audioEngine.extractWaveform(voiceClip.sourceUri, 44100)
            val pcm = waveform.map { (it * 32767).toInt().toShort() }.toShortArray()
            val speechRegions = com.novacut.editor.engine.AudioEffectsEngine.detectSpeechRegions(pcm, 44100, 1)

            if (speechRegions.isEmpty()) {
                showToast("No speech detected")
                return@launch
            }

            saveUndoState("Auto duck")

            // Create volume keyframes on music tracks
            _state.update { state ->
                state.copy(tracks = state.tracks.map { track ->
                    if (track.type == TrackType.AUDIO) {
                        track.copy(clips = track.clips.map { clip ->
                            val duckKeyframes = mutableListOf<com.novacut.editor.model.Keyframe>()
                            for ((start, end) in speechRegions) {
                                duckKeyframes.addAll(
                                    com.novacut.editor.engine.KeyframeEngine.createVolumeDuck(
                                        startMs = start, endMs = end,
                                        normalVolume = clip.volume, duckVolume = clip.volume * 0.15f
                                    )
                                )
                            }
                            clip.copy(keyframes = clip.keyframes + duckKeyframes)
                        })
                    } else track
                })
            }
            showToast("Ducking applied: ${speechRegions.size} regions")
        }
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
        val currentState = _state.value
        if (currentState.tracks.flatMap { it.clips }.isEmpty()) {
            showToast("No clips to export")
            return
        }

        val config = currentState.exportConfig.copy(aspectRatio = currentState.project.aspectRatio)
        val tracks = currentState.tracks
        val textOverlays = currentState.textOverlays

        viewModelScope.launch {
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
                    tracks = tracks,
                    config = config,
                    outputFile = outputFile,
                    textOverlays = textOverlays,
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
                        } else {
                            withContext(Dispatchers.Main) { showToast("Failed to save to gallery") }
                            return@withContext
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
            val restored = recalculateDuration(it.copy(
                tracks = action.tracks,
                textOverlays = action.textOverlays,
                undoStack = undoStack.dropLast(1),
                redoStack = it.redoStack + currentAction
            ))
            val clipExists = it.selectedClipId != null &&
                restored.tracks.any { t -> t.clips.any { c -> c.id == it.selectedClipId } }
            dismissedPanelState(restored).copy(
                selectedClipId = if (clipExists) it.selectedClipId else null,
                currentTool = EditorTool.NONE
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
            val restored = recalculateDuration(it.copy(
                tracks = action.tracks,
                textOverlays = action.textOverlays,
                redoStack = redoStack.dropLast(1),
                undoStack = it.undoStack + currentAction
            ))
            val clipExists = it.selectedClipId != null &&
                restored.tracks.any { t -> t.clips.any { c -> c.id == it.selectedClipId } }
            dismissedPanelState(restored).copy(
                selectedClipId = if (clipExists) it.selectedClipId else null,
                currentTool = EditorTool.NONE
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
                        if (suggestion.confidence < 0.1f) {
                            showToast("Could not analyze frame for crop")
                        } else {
                            saveUndoState("AI smart crop")
                            setClipTransform(
                                clip.id,
                                positionX = suggestion.centerX - 0.5f,
                                positionY = suggestion.centerY - 0.5f
                            )
                            showToast("Smart crop applied (${"%.0f".format(suggestion.confidence * 100)}% confidence)")
                        }
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
