package com.novacut.editor.ui.editor

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.util.Log
import com.novacut.editor.engine.ExportState
import com.novacut.editor.model.*
import com.novacut.editor.ui.export.BatchExportPanel
import com.novacut.editor.ui.export.ExportSheet
import com.novacut.editor.ui.mediapicker.MediaPickerSheet
import com.novacut.editor.ui.theme.Mocha
import androidx.activity.compose.BackHandler
import java.io.File

@Composable
fun EditorScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val whisperState by viewModel.whisperModelState.collectAsStateWithLifecycle()
    val whisperProgress by viewModel.whisperDownloadProgress.collectAsStateWithLifecycle()
    val segmentationState by viewModel.segmentationModelState.collectAsStateWithLifecycle()
    val segmentationProgress by viewModel.segmentationDownloadProgress.collectAsStateWithLifecycle()
    val scopeFrame by viewModel.scopeFrame.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val hasOpenPanel = state.showMediaPicker || state.showExportSheet || state.showEffectsPanel ||
        state.showTextEditor || state.showTransitionPicker || state.showAudioPanel ||
        state.showAiToolsPanel || state.showTransformPanel || state.showCropPanel ||
        state.showVoiceoverRecorder || state.selectedEffectId != null || state.editingTextOverlayId != null ||
        state.showColorGrading || state.showAudioMixer || state.showKeyframeEditor ||
        state.showSpeedCurveEditor || state.showMaskEditor || state.showBlendModeSelector ||
        state.showBatchExport || state.showPipPresets || state.showChromaKey ||
        state.showCaptionEditor || state.showChapterMarkers || state.showSnapshotHistory ||
        state.showTextTemplates || state.showMediaManager || state.showAudioNorm ||
        state.showRenderPreview || state.showCloudBackup

    BackHandler(enabled = hasOpenPanel || state.currentTool != EditorTool.NONE || state.selectedClipId != null) {
        when {
            hasOpenPanel -> viewModel.dismissAllPanels()
            state.currentTool != EditorTool.NONE -> viewModel.setTool(EditorTool.NONE)
            state.selectedClipId != null -> viewModel.selectClip(null)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Mocha.Base)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar (Home / Undo / Redo / Delete / More / Export)
            EditorTopBar(
                projectName = state.project.name,
                onRename = viewModel::renameProject,
                onBack = onBack,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                canUndo = state.undoStack.isNotEmpty(),
                canRedo = state.redoStack.isNotEmpty(),
                selectedClipId = state.selectedClipId,
                onDelete = viewModel::deleteSelectedClip,
                onAddMedia = viewModel::showMediaPicker,
                onAddTrack = viewModel::addTrack,
                onExport = viewModel::showExportSheet,
                onSaveTemplate = viewModel::saveAsTemplate
            )

            // Preview panel
            PreviewPanel(
                engine = viewModel.engine,
                playheadMs = state.playheadMs,
                totalDurationMs = state.totalDurationMs,
                isPlaying = state.isPlaying,
                isLooping = state.isLooping,
                aspectRatio = state.project.aspectRatio,
                onTogglePlayback = viewModel::togglePlayback,
                onToggleLoop = viewModel::toggleLoop,
                onSeek = viewModel::seekTo,
                showScopesButton = true,
                onToggleScopes = viewModel::toggleScopes,
                modifier = Modifier.weight(0.45f)
            )

            // Timeline
            Timeline(
                tracks = state.tracks,
                playheadMs = state.playheadMs,
                totalDurationMs = state.totalDurationMs,
                zoomLevel = state.zoomLevel,
                scrollOffsetMs = state.scrollOffsetMs,
                selectedClipId = state.selectedClipId,
                isTrimMode = state.currentTool == EditorTool.TRIM,
                waveforms = state.waveforms,
                onClipSelected = viewModel::selectClip,
                onPlayheadMoved = viewModel::seekTo,
                onZoomChanged = viewModel::setZoomLevel,
                onScrollChanged = viewModel::setScrollOffset,
                onTrimChanged = viewModel::trimClip,
                onTrimDragStarted = viewModel::beginTrim,
                onTimelineWidthChanged = viewModel::setTimelineWidth,
                onToggleTrackMute = viewModel::toggleTrackMute,
                onToggleTrackVisible = viewModel::toggleTrackVisibility,
                onToggleTrackLock = viewModel::toggleTrackLock,
                beatMarkers = state.beatMarkers,
                engine = viewModel.engine,
                modifier = Modifier.weight(0.55f)
            )

            // Bottom tool area (PowerDirector-style tab bar + sub-menu grids)
            BottomToolArea(
                selectedClipId = state.selectedClipId,
                hasCopiedEffects = state.copiedEffects.isNotEmpty(),
                textOverlays = state.textOverlays,
                onEditTextOverlay = { id -> viewModel.editTextOverlay(id) },
                onDeleteTextOverlay = { id ->
                    viewModel.removeTextOverlay(id)
                },
                onAction = { actionId ->
                    when (actionId) {
                        "edit" -> viewModel.showMediaPicker()
                        "audio_add" -> viewModel.showMediaPicker()
                        "audio_tool" -> viewModel.showAudioPanel()
                        "speed" -> viewModel.showSpeedCurveEditor()
                        "transform" -> viewModel.showTransformPanel()
                        "effects" -> viewModel.showEffectsPanel()
                        "effects_disabled" -> viewModel.showToast("Select a clip to use Effects")
                        "transition" -> viewModel.showTransitionPicker()
                        "aspect" -> viewModel.showCropPanel()
                        "back" -> { viewModel.dismissAllPanels(); viewModel.selectClip(null) }
                        "add_text" -> viewModel.showTextEditor()
                        "split" -> { viewModel.splitClipAtPlayhead(); viewModel.setTool(EditorTool.NONE) }
                        "trim" -> { viewModel.setTool(EditorTool.TRIM); viewModel.dismissAllPanels() }
                        "merge" -> viewModel.mergeWithNextClip()
                        "duplicate" -> viewModel.duplicateSelectedClip()
                        "freeze" -> { viewModel.insertFreezeFrame(); viewModel.setTool(EditorTool.NONE) }
                        "copy_fx" -> viewModel.copyEffects()
                        "paste_fx" -> viewModel.pasteEffects()
                        // New features
                        "color_grade" -> viewModel.showColorGrading()
                        "color_grade_disabled" -> viewModel.showToast("Select a clip to color grade")
                        "keyframes" -> viewModel.showKeyframeEditor()
                        "keyframes_disabled" -> viewModel.showToast("Select a clip for keyframes")
                        "masks" -> viewModel.showMaskEditor()
                        "masks_disabled" -> viewModel.showToast("Select a clip for masks")
                        "blend_mode" -> viewModel.showBlendModeSelector()
                        "blend_mode_disabled" -> viewModel.showToast("Select a clip for blend mode")
                        "pip" -> viewModel.showPipPresets()
                        "pip_disabled" -> viewModel.showToast("Select a clip for PiP")
                        "chroma_key" -> viewModel.showChromaKey()
                        "chroma_key_disabled" -> viewModel.showToast("Select a clip for chroma key")
                        "auto_duck" -> viewModel.autoDuck()
                        "scopes" -> viewModel.toggleScopes()
                        "audio_mixer" -> viewModel.showAudioMixer()
                        "beat_detect" -> viewModel.detectBeats()
                        "adjustment_layer" -> viewModel.addAdjustmentLayer()
                        "snapshot" -> viewModel.createSnapshot()
                        "captions" -> viewModel.showCaptionEditor()
                        "captions_disabled" -> viewModel.showToast("Select a clip for captions")
                        "chapters" -> viewModel.showChapterMarkers()
                        "history" -> viewModel.showSnapshotHistory()
                        "export_srt" -> viewModel.exportSubtitles(SubtitleFormat.SRT)
                        "export_vtt" -> viewModel.exportSubtitles(SubtitleFormat.VTT)
                        "text_templates" -> viewModel.showTextTemplates()
                        "media_manager" -> viewModel.showMediaManager()
                        "audio_norm" -> viewModel.showAudioNorm()
                        "audio_norm_disabled" -> viewModel.showToast("Select a clip to normalize")
                        "compound" -> viewModel.createCompoundClip()
                        "render_preview" -> viewModel.showRenderPreview()
                        "cloud_backup" -> viewModel.showCloudBackup()
                        "archive" -> viewModel.exportProjectArchive()
                        "unlink_av" -> viewModel.unlinkAudioVideo()
                        "multi_delete" -> viewModel.deleteMultiSelectedClips()
                        "batch_export" -> viewModel.showBatchExport()
                        "proxy_toggle" -> viewModel.setProxyEnabled(!state.proxySettings.enabled)
                        // AI tools
                        "auto_captions" -> viewModel.runAiTool("auto_captions")
                        "scene_detect" -> viewModel.runAiTool("scene_detect")
                        "smart_crop" -> viewModel.runAiTool("smart_crop")
                        "auto_color" -> viewModel.runAiTool("auto_color")
                        "stabilize" -> viewModel.runAiTool("stabilize")
                        "denoise" -> viewModel.runAiTool("denoise")
                        "remove_bg" -> viewModel.runAiTool("remove_bg")
                        "track_motion" -> viewModel.runAiTool("track_motion")
                        "style_transfer" -> viewModel.runAiTool("style_transfer")
                        "face_track" -> viewModel.runAiTool("face_track")
                        "upscale" -> viewModel.runAiTool("upscale")
                        "frame_interp" -> viewModel.runAiTool("frame_interp")
                        "object_remove" -> viewModel.runAiTool("object_remove")
                        "bg_replace" -> viewModel.runAiTool("bg_replace")
                        "smart_reframe" -> viewModel.runAiTool("smart_reframe")
                        else -> Log.w("EditorScreen", "Unknown action: $actionId")
                    }
                }
            )
        }

        // Bottom sheets / overlays
        AnimatedVisibility(
            visible = state.showMediaPicker,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MediaPickerSheet(
                onMediaSelected = { uri, mediaType ->
                    val trackType = when (mediaType) {
                        "audio" -> TrackType.AUDIO
                        else -> TrackType.VIDEO
                    }
                    viewModel.addClipToTrack(uri, trackType)
                },
                onClose = viewModel::hideMediaPicker
            )
        }

        AnimatedVisibility(
            visible = state.showEffectsPanel,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val selectedClip = viewModel.getSelectedClip()
            EffectsPanel(
                selectedClip = selectedClip,
                onAddEffect = { effectType ->
                    val clipId = state.selectedClipId ?: return@EffectsPanel
                    val effect = Effect(type = effectType, params = EffectType.defaultParams(effectType))
                    viewModel.addEffect(clipId, effect)
                    viewModel.selectEffect(effect.id)
                    viewModel.hideEffectsPanel()
                },
                onClose = viewModel::hideEffectsPanel
            )
        }

        // Speed panel
        AnimatedVisibility(
            visible = state.currentTool == EditorTool.SPEED && state.selectedClipId != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val clip = viewModel.getSelectedClip()
            if (clip != null) {
                SpeedPanel(
                    currentSpeed = clip.speed,
                    isReversed = clip.isReversed,
                    onSpeedDragStarted = viewModel::beginSpeedChange,
                    onSpeedChanged = { viewModel.setClipSpeed(clip.id, it) },
                    onReversedChanged = { viewModel.setClipReversed(clip.id, it) },
                    onClose = { viewModel.setTool(EditorTool.NONE) }
                )
            }
        }

        // Transition picker
        AnimatedVisibility(
            visible = state.showTransitionPicker,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val clip = viewModel.getSelectedClip()
            TransitionPicker(
                onTransitionSelected = { type ->
                    val clipId = state.selectedClipId ?: return@TransitionPicker
                    viewModel.setTransition(clipId, Transition(type = type))
                },
                onRemoveTransition = {
                    val clipId = state.selectedClipId ?: return@TransitionPicker
                    viewModel.setTransition(clipId, null)
                },
                onDurationChanged = { durationMs ->
                    val clipId = state.selectedClipId ?: return@TransitionPicker
                    viewModel.setTransitionDuration(clipId, durationMs)
                },
                onDurationDragStarted = viewModel::beginTransitionDurationChange,
                onClose = viewModel::hideTransitionPicker,
                currentTransition = clip?.transition
            )
        }

        // Text editor
        AnimatedVisibility(
            visible = state.showTextEditor,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val editingOverlay = state.editingTextOverlayId?.let { id ->
                state.textOverlays.firstOrNull { it.id == id }
            }
            TextEditorSheet(
                existingOverlay = editingOverlay,
                playheadMs = state.playheadMs,
                onSave = { overlay ->
                    if (editingOverlay != null) {
                        viewModel.updateTextOverlay(overlay)
                    } else {
                        viewModel.addTextOverlay(overlay)
                    }
                    viewModel.hideTextEditor()
                },
                onClose = viewModel::hideTextEditor
            )
        }

        // Export sheet
        AnimatedVisibility(
            visible = state.showExportSheet,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ExportSheet(
                config = state.exportConfig,
                exportState = state.exportState,
                exportProgress = state.exportProgress,
                aspectRatio = state.project.aspectRatio,
                errorMessage = state.exportErrorMessage,
                onConfigChanged = viewModel::updateExportConfig,
                onStartExport = {
                    // Use app-private external dir — works on all Android versions including 11+
                    val moviesDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)
                    val outputDir = File(moviesDir ?: context.filesDir, "NovaCut").apply { mkdirs() }
                    viewModel.startExport(outputDir)
                },
                onShare = {
                    viewModel.getShareIntent()?.let { intent ->
                        context.startActivity(Intent.createChooser(intent, "Share video"))
                    }
                },
                onSaveToGallery = viewModel::saveToGallery,
                onCancel = { viewModel.engine.cancelExport() },
                onClose = viewModel::hideExportSheet
            )
        }

        // Audio panel
        AnimatedVisibility(
            visible = state.showAudioPanel,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val clip = viewModel.getSelectedClip()
            AudioPanel(
                clip = clip,
                waveform = clip?.let { state.waveforms[it.id] },
                onVolumeChanged = { volume ->
                    val clipId = state.selectedClipId ?: return@AudioPanel
                    viewModel.setClipVolume(clipId, volume)
                },
                onVolumeDragStarted = viewModel::beginVolumeChange,
                onFadeInChanged = { fadeMs ->
                    val clipId = state.selectedClipId ?: return@AudioPanel
                    viewModel.setClipFadeIn(clipId, fadeMs)
                },
                onFadeOutChanged = { fadeMs ->
                    val clipId = state.selectedClipId ?: return@AudioPanel
                    viewModel.setClipFadeOut(clipId, fadeMs)
                },
                onFadeDragStarted = viewModel::beginFadeAdjust,
                onStartVoiceover = viewModel::showVoiceoverPanel,
                onClose = viewModel::hideAudioPanel
            )
        }

        // Voiceover recorder
        AnimatedVisibility(
            visible = state.showVoiceoverRecorder,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            VoiceoverRecorder(
                isRecording = state.isRecordingVoiceover,
                recordingDurationMs = state.voiceoverDurationMs,
                onStartRecording = viewModel::startVoiceover,
                onStopRecording = viewModel::stopVoiceover,
                onClose = viewModel::hideVoiceoverPanel
            )
        }

        // Transform panel
        AnimatedVisibility(
            visible = state.showTransformPanel,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val clip = viewModel.getSelectedClip()
            if (clip != null) {
                TransformPanel(
                    clip = clip,
                    onTransformDragStarted = viewModel::beginTransformChange,
                    onTransformChanged = { px, py, sx, sy, rot ->
                        viewModel.setClipTransform(clip.id, px, py, sx, sy, rot)
                    },
                    onOpacityChanged = { viewModel.setClipOpacity(clip.id, it) },
                    onReset = { viewModel.resetClipTransform(clip.id) },
                    onClose = viewModel::hideTransformPanel
                )
            }
        }

        // Crop panel
        AnimatedVisibility(
            visible = state.showCropPanel,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            CropPanel(
                currentAspect = state.project.aspectRatio,
                onCropSelected = { ratio ->
                    viewModel.updateProjectAspect(ratio)
                },
                onClose = viewModel::hideCropPanel
            )
        }

        // AI tools panel
        AnimatedVisibility(
            visible = state.showAiToolsPanel,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            AiToolsPanel(
                hasSelectedClip = state.selectedClipId != null,
                onToolSelected = { toolId -> viewModel.runAiTool(toolId) },
                onDisabledToolTapped = { toolName -> viewModel.showToast("Select a clip to use $toolName") },
                onCancelProcessing = viewModel::cancelAiTool,
                onClose = viewModel::hideAiToolsPanel,
                processingTool = state.aiProcessingTool,
                whisperModelState = whisperState,
                whisperDownloadProgress = whisperProgress,
                onDownloadWhisper = viewModel::downloadWhisperModel,
                onDeleteWhisper = viewModel::deleteWhisperModel,
                segmentationModelState = segmentationState,
                segmentationDownloadProgress = segmentationProgress,
                onDownloadSegmentation = viewModel::downloadSegmentationModel,
                onDeleteSegmentation = viewModel::deleteSegmentationModel
            )
        }

        // Effect adjustment panel
        AnimatedVisibility(
            visible = state.selectedEffectId != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val clip = viewModel.getSelectedClip()
            val effect = clip?.effects?.firstOrNull { it.id == state.selectedEffectId }
            if (effect != null) {
                EffectAdjustmentPanel(
                    effect = effect,
                    onUpdateParams = { params ->
                        val clipId = state.selectedClipId ?: return@EffectAdjustmentPanel
                        viewModel.updateEffect(clipId, effect.id, params)
                    },
                    onEffectDragStarted = viewModel::beginEffectAdjust,
                    onToggleEnabled = {
                        val clipId = state.selectedClipId ?: return@EffectAdjustmentPanel
                        viewModel.toggleEffectEnabled(clipId, effect.id)
                    },
                    onRemove = {
                        val clipId = state.selectedClipId ?: return@EffectAdjustmentPanel
                        viewModel.removeEffect(clipId, effect.id)
                        viewModel.clearSelectedEffect()
                    },
                    onClose = viewModel::clearSelectedEffect
                )
            }
        }

        // Color Grading panel
        AnimatedVisibility(
            visible = state.showColorGrading,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val clip = state.tracks.flatMap { it.clips }.find { it.id == state.selectedClipId }
            ColorGradingPanel(
                colorGrade = clip?.colorGrade ?: ColorGrade(),
                onColorGradeChanged = viewModel::updateClipColorGrade,
                onLutImport = viewModel::importLut,
                onClose = viewModel::hideColorGrading
            )
        }

        // Audio Mixer panel
        AnimatedVisibility(
            visible = state.showAudioMixer,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            AudioMixerPanel(
                tracks = state.tracks,
                onTrackVolumeChanged = viewModel::setTrackVolume,
                onTrackPanChanged = viewModel::setTrackPan,
                onTrackMuteToggled = { viewModel.toggleTrackMute(it) },
                onTrackSoloToggled = viewModel::toggleTrackSolo,
                onTrackAudioEffectAdded = viewModel::addTrackAudioEffect,
                onTrackAudioEffectRemoved = viewModel::removeTrackAudioEffect,
                onTrackAudioEffectParamChanged = viewModel::updateTrackAudioEffectParam,
                vuLevels = state.vuLevels,
                onClose = viewModel::hideAudioMixer
            )
        }

        // Keyframe Curve Editor
        AnimatedVisibility(
            visible = state.showKeyframeEditor,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val clip = state.tracks.flatMap { it.clips }.find { it.id == state.selectedClipId }
            if (clip != null) {
                KeyframeCurveEditor(
                    keyframes = clip.keyframes,
                    clipDurationMs = clip.durationMs,
                    playheadMs = (state.playheadMs - clip.timelineStartMs).coerceAtLeast(0L),
                    activeProperties = state.activeKeyframeProperties,
                    onKeyframesChanged = viewModel::updateClipKeyframes,
                    onPropertyToggled = viewModel::toggleKeyframeProperty,
                    onAddKeyframe = viewModel::addKeyframe,
                    onDeleteKeyframe = viewModel::deleteKeyframe,
                    onClose = viewModel::hideKeyframeEditor
                )
            }
        }

        // Speed Curve Editor
        AnimatedVisibility(
            visible = state.showSpeedCurveEditor,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val clip = state.tracks.flatMap { it.clips }.find { it.id == state.selectedClipId }
            if (clip != null) {
                SpeedCurveEditor(
                    speedCurve = clip.speedCurve,
                    constantSpeed = clip.speed,
                    clipDurationMs = clip.durationMs,
                    onSpeedCurveChanged = viewModel::setClipSpeedCurve,
                    onConstantSpeedChanged = { speed -> state.selectedClipId?.let { viewModel.setClipSpeed(it, speed) } },
                    isReversed = clip.isReversed,
                    onReversedChanged = { rev -> state.selectedClipId?.let { viewModel.setClipReversed(it, rev) } },
                    onClose = viewModel::hideSpeedCurveEditor
                )
            }
        }

        // Mask Editor panel
        AnimatedVisibility(
            visible = state.showMaskEditor,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val clip = state.tracks.flatMap { it.clips }.find { it.id == state.selectedClipId }
            MaskEditorPanel(
                masks = clip?.masks ?: emptyList(),
                selectedMaskId = state.selectedMaskId,
                onMaskSelected = viewModel::selectMask,
                onMaskAdded = viewModel::addMask,
                onMaskUpdated = viewModel::updateMask,
                onMaskDeleted = viewModel::deleteMask,
                onClose = viewModel::hideMaskEditor
            )
        }

        // Blend Mode selector
        AnimatedVisibility(
            visible = state.showBlendModeSelector,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BlendModeSelector(
                currentMode = state.tracks.flatMap { it.clips }
                    .find { it.id == state.selectedClipId }?.blendMode ?: BlendMode.NORMAL,
                onModeSelected = viewModel::setClipBlendMode,
                onClose = viewModel::hideBlendModeSelector
            )
        }

        // Mask preview overlay on the video preview (when mask editor is open)
        if (state.showMaskEditor) {
            val clip = state.tracks.flatMap { it.clips }.find { it.id == state.selectedClipId }
            if (clip != null) {
                MaskPreviewOverlay(
                    masks = clip.masks,
                    selectedMaskId = state.selectedMaskId,
                    previewWidth = 1920f, // Will be overridden by actual preview size
                    previewHeight = 1080f,
                    onMaskPointMoved = viewModel::updateMaskPoint,
                    onFreehandDraw = viewModel::setFreehandMaskPoints,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // PiP Presets
        AnimatedVisibility(
            visible = state.showPipPresets,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            PipPresetsPanel(
                onPresetSelected = { preset ->
                    viewModel.applyPipPreset(preset)
                    viewModel.hidePipPresets()
                },
                onClose = viewModel::hidePipPresets
            )
        }

        // Chroma Key Refinement
        AnimatedVisibility(
            visible = state.showChromaKey,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val clip = state.tracks.flatMap { it.clips }.find { it.id == state.selectedClipId }
            val chromaEffect = clip?.effects?.find { it.type == EffectType.CHROMA_KEY }
            ChromaKeyPanel(
                similarity = chromaEffect?.params?.get("similarity") ?: 0.4f,
                smoothness = chromaEffect?.params?.get("smoothness") ?: 0.1f,
                spillSuppression = chromaEffect?.params?.get("spill") ?: 0.1f,
                keyColorR = chromaEffect?.params?.get("keyR") ?: 0f,
                keyColorG = chromaEffect?.params?.get("keyG") ?: 1f,
                keyColorB = chromaEffect?.params?.get("keyB") ?: 0f,
                onSimilarityChanged = { v ->
                    state.selectedClipId?.let { cid ->
                        chromaEffect?.let { viewModel.updateEffect(cid, it.id, it.params + ("similarity" to v)) }
                    }
                },
                onSmoothnessChanged = { v ->
                    state.selectedClipId?.let { cid ->
                        chromaEffect?.let { viewModel.updateEffect(cid, it.id, it.params + ("smoothness" to v)) }
                    }
                },
                onSpillChanged = { v ->
                    state.selectedClipId?.let { cid ->
                        chromaEffect?.let { viewModel.updateEffect(cid, it.id, it.params + ("spill" to v)) }
                    }
                },
                onKeyColorChanged = { r, g, b ->
                    state.selectedClipId?.let { cid ->
                        chromaEffect?.let { viewModel.updateEffect(cid, it.id, it.params + ("keyR" to r) + ("keyG" to g) + ("keyB" to b)) }
                    }
                },
                onShowAlphaMatte = { viewModel.showToast("Alpha matte preview") },
                onClose = viewModel::hideChromaKey
            )
        }

        // Transform overlay on preview (when clip selected and transform visible)
        if (state.selectedClipId != null && state.showTransformPanel) {
            val clip = state.tracks.flatMap { it.clips }.find { it.id == state.selectedClipId }
            if (clip != null) {
                TransformOverlay(
                    positionX = clip.positionX,
                    positionY = clip.positionY,
                    scaleX = clip.scaleX,
                    scaleY = clip.scaleY,
                    rotation = clip.rotation,
                    anchorX = clip.anchorX,
                    anchorY = clip.anchorY,
                    opacity = clip.opacity,
                    previewWidth = 400f,
                    previewHeight = 225f,
                    onPositionChanged = { x, y -> state.selectedClipId?.let { viewModel.setClipTransform(it, positionX = x, positionY = y) } },
                    onScaleChanged = { sx, sy -> state.selectedClipId?.let { viewModel.setClipTransform(it, scaleX = sx, scaleY = sy) } },
                    onRotationChanged = { r -> state.selectedClipId?.let { viewModel.setClipTransform(it, rotation = r) } },
                    onAnchorChanged = viewModel::setClipAnchor,
                    onTransformStarted = viewModel::beginEffectAdjust,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // Caption Editor
        AnimatedVisibility(
            visible = state.showCaptionEditor,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val clip = state.tracks.flatMap { it.clips }.find { it.id == state.selectedClipId }
            CaptionEditorPanel(
                captions = clip?.captions ?: emptyList(),
                playheadMs = (state.playheadMs - (clip?.timelineStartMs ?: 0L)).coerceAtLeast(0L),
                clipDurationMs = clip?.durationMs ?: 0L,
                onAddCaption = viewModel::addCaption,
                onUpdateCaption = viewModel::updateCaption,
                onDeleteCaption = viewModel::removeCaption,
                onGenerateAutoCaption = viewModel::generateAutoCaption,
                onClose = viewModel::hideCaptionEditor
            )
        }

        // Chapter Markers
        AnimatedVisibility(
            visible = state.showChapterMarkers,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ChapterMarkerPanel(
                chapters = state.chapterMarkers,
                playheadMs = state.playheadMs,
                onAddChapter = viewModel::addChapterMarker,
                onUpdateChapter = viewModel::updateChapterMarker,
                onDeleteChapter = viewModel::deleteChapterMarker,
                onJumpTo = viewModel::seekTo,
                onClose = viewModel::hideChapterMarkers
            )
        }

        // Snapshot History
        AnimatedVisibility(
            visible = state.showSnapshotHistory,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SnapshotHistoryPanel(
                snapshots = state.projectSnapshots,
                onCreateSnapshot = viewModel::createSnapshot,
                onRestoreSnapshot = viewModel::restoreSnapshot,
                onDeleteSnapshot = viewModel::deleteSnapshot,
                onClose = viewModel::hideSnapshotHistory
            )
        }

        // Media Manager
        AnimatedVisibility(
            visible = state.showMediaManager,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MediaManagerPanel(
                tracks = state.tracks,
                onJumpToClip = viewModel::jumpToClip,
                onRelinkMedia = { _, _ -> },
                onRemoveUnused = { viewModel.showToast("Remove unused: not yet implemented") },
                onClose = viewModel::hideMediaManager
            )
        }

        // Audio Normalization
        AnimatedVisibility(
            visible = state.showAudioNorm,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val clip = state.tracks.flatMap { it.clips }.find { it.id == state.selectedClipId }
            AudioNormPanel(
                currentVolume = clip?.volume ?: 1f,
                onNormalize = viewModel::normalizeAudio,
                onClose = viewModel::hideAudioNorm
            )
        }

        // Render Preview / Smart Render
        AnimatedVisibility(
            visible = state.showRenderPreview,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            state.renderSummary?.let { summary ->
                RenderPreviewSheet(
                    segments = state.renderSegments,
                    summary = summary,
                    onRenderPreview = viewModel::renderQuickPreview,
                    onRenderFull = {
                        viewModel.hideRenderPreview()
                        viewModel.showExportSheet()
                    },
                    onClose = viewModel::hideRenderPreview
                )
            }
        }

        // Cloud Backup
        AnimatedVisibility(
            visible = state.showCloudBackup,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            CloudBackupPanel(
                isSignedIn = false,
                lastBackupTime = null,
                backupProgress = null,
                onSignIn = { viewModel.showToast("Google Sign-In required") },
                onBackupNow = { viewModel.showToast("Sign in first") },
                onRestore = { viewModel.showToast("Sign in first") },
                onAutoBackupToggled = { },
                autoBackupEnabled = false,
                onClose = viewModel::hideCloudBackup
            )
        }

        // Batch Export
        AnimatedVisibility(
            visible = state.showBatchExport,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BatchExportPanel(
                queue = state.batchExportQueue,
                onAddItem = viewModel::addBatchExportItem,
                onRemoveItem = viewModel::removeBatchExportItem,
                onStartBatch = viewModel::startBatchExport,
                onClose = viewModel::hideBatchExport
            )
        }

        // Export progress floating overlay
        ExportProgressOverlay(
            exportState = state.exportState,
            exportProgress = state.exportProgress,
            exportStartTime = state.exportStartTime,
            onCancel = viewModel::cancelExport,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        )

        // Text Template Gallery
        AnimatedVisibility(
            visible = state.showTextTemplates,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            TextTemplateGallery(
                playheadMs = state.playheadMs,
                onTemplateSelected = { template -> viewModel.applyTextTemplate(template) },
                onClose = viewModel::hideTextTemplates
            )
        }

        // Caption preview on video (always show when captions exist)
        val allCaptions = state.tracks.flatMap { it.clips }.flatMap { clip ->
            clip.captions.map { caption ->
                caption.copy(
                    startTimeMs = caption.startTimeMs + clip.timelineStartMs,
                    endTimeMs = caption.endTimeMs + clip.timelineStartMs
                )
            }
        }
        if (allCaptions.isNotEmpty()) {
            CaptionPreviewOverlay(
                captions = allCaptions,
                currentTimeMs = state.playheadMs,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Motion path overlay on preview (when keyframe editor is open and position keyframes exist)
        if (state.showKeyframeEditor && state.selectedClipId != null) {
            val clip = state.tracks.flatMap { it.clips }.find { it.id == state.selectedClipId }
            if (clip != null && clip.keyframes.any { it.property == KeyframeProperty.POSITION_X || it.property == KeyframeProperty.POSITION_Y }) {
                MotionPathOverlay(
                    keyframes = clip.keyframes,
                    clipDurationMs = clip.durationMs,
                    currentTimeMs = (state.playheadMs - clip.timelineStartMs).coerceAtLeast(0L),
                    previewWidth = 400f,
                    previewHeight = 225f,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // Video scopes overlay
        if (state.showScopes) {
            VideoScopesOverlay(
                frameBitmap = scopeFrame,
                activeScope = state.activeScopeType,
                onScopeChanged = viewModel::setScopeType,
                onClose = viewModel::toggleScopes,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
        }

        // Toast messages
        state.toastMessage?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp, start = 16.dp, end = 16.dp)
                    .zIndex(10f),
                containerColor = Mocha.Surface0,
                contentColor = Mocha.Text,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(message, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun EditorTopBar(
    projectName: String,
    onRename: (String) -> Unit,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    selectedClipId: String?,
    onDelete: () -> Unit,
    onAddMedia: () -> Unit,
    onAddTrack: (TrackType) -> Unit,
    onExport: () -> Unit,
    onSaveTemplate: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showOverflow by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showSaveTemplateDialog by remember { mutableStateOf(false) }
    var showAddTrackMenu by remember { mutableStateOf(false) }

    if (showSaveTemplateDialog) {
        var templateName by remember { mutableStateOf("$projectName Template") }
        AlertDialog(
            onDismissRequest = { showSaveTemplateDialog = false },
            title = { Text("Save as Template", color = Mocha.Text) },
            text = {
                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it },
                    singleLine = true,
                    label = { Text("Template Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Mocha.Text,
                        unfocusedTextColor = Mocha.Text,
                        cursorColor = Mocha.Mauve,
                        focusedBorderColor = Mocha.Mauve,
                        unfocusedBorderColor = Mocha.Surface1,
                        focusedLabelColor = Mocha.Mauve,
                        unfocusedLabelColor = Mocha.Subtext0
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (templateName.isNotBlank()) onSaveTemplate(templateName.trim())
                    showSaveTemplateDialog = false
                }) { Text("Save", color = Mocha.Mauve) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveTemplateDialog = false }) {
                    Text("Cancel", color = Mocha.Subtext0)
                }
            },
            containerColor = Mocha.Mantle
        )
    }

    if (showRenameDialog) {
        var nameText by remember { mutableStateOf(projectName) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Project", color = Mocha.Text) },
            text = {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Mocha.Text,
                        unfocusedTextColor = Mocha.Text,
                        cursorColor = Mocha.Mauve,
                        focusedBorderColor = Mocha.Mauve,
                        unfocusedBorderColor = Mocha.Surface1
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (nameText.isNotBlank()) onRename(nameText.trim())
                    showRenameDialog = false
                }) { Text("Save", color = Mocha.Mauve) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = Mocha.Subtext0)
                }
            },
            containerColor = Mocha.Mantle
        )
    }

    Surface(
        color = Mocha.Crust,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = "Home",
                    tint = Mocha.Text,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) Mocha.Text else Mocha.Surface2,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onRedo,
                enabled = canRedo,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) Mocha.Text else Mocha.Surface2,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Project name (tap to rename)
            Text(
                text = projectName,
                color = Mocha.Subtext1,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .clickable { showRenameDialog = true }
            )

            if (selectedClipId != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Mocha.Red,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { showOverflow = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = Mocha.Text,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = showOverflow,
                    onDismissRequest = { showOverflow = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Add Media") },
                        onClick = {
                            showOverflow = false
                            onAddMedia()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Add, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add Track") },
                        onClick = {
                            showOverflow = false
                            showAddTrackMenu = true
                        },
                        leadingIcon = {
                            Icon(Icons.Default.VideoLibrary, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename Project") },
                        onClick = {
                            showOverflow = false
                            showRenameDialog = true
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Save as Template") },
                        onClick = {
                            showOverflow = false
                            showSaveTemplateDialog = true
                        },
                        leadingIcon = {
                            Icon(Icons.Default.SaveAs, contentDescription = null)
                        }
                    )
                }
                DropdownMenu(
                    expanded = showAddTrackMenu,
                    onDismissRequest = { showAddTrackMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Video Track") },
                        onClick = { showAddTrackMenu = false; onAddTrack(TrackType.VIDEO) },
                        leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Audio Track") },
                        onClick = { showAddTrackMenu = false; onAddTrack(TrackType.AUDIO) },
                        leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Overlay Track") },
                        onClick = { showAddTrackMenu = false; onAddTrack(TrackType.OVERLAY) },
                        leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Text Track") },
                        onClick = { showAddTrackMenu = false; onAddTrack(TrackType.TEXT) },
                        leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) }
                    )
                }
            }

            Button(
                onClick = onExport,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mocha.Mauve,
                    contentColor = Mocha.Crust
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Export", fontSize = 13.sp)
            }
        }
    }
}

