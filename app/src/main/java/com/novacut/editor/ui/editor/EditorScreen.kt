package com.novacut.editor.ui.editor

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import com.novacut.editor.engine.ExportState
import com.novacut.editor.model.*
import com.novacut.editor.ui.export.ExportSheet
import com.novacut.editor.ui.mediapicker.MediaPickerSheet
import com.novacut.editor.ui.theme.Mocha
import java.io.File

@Composable
fun EditorScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(Mocha.Base)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Preview panel (top)
            PreviewPanel(
                engine = viewModel.engine,
                playheadMs = state.playheadMs,
                totalDurationMs = state.totalDurationMs,
                isPlaying = state.isPlaying,
                onTogglePlayback = viewModel::togglePlayback,
                onSeek = viewModel::seekTo,
                modifier = Modifier.weight(0.4f)
            )

            // Timeline (middle)
            Timeline(
                tracks = state.tracks,
                playheadMs = state.playheadMs,
                totalDurationMs = state.totalDurationMs,
                zoomLevel = state.zoomLevel,
                scrollOffsetMs = state.scrollOffsetMs,
                selectedClipId = state.selectedClipId,
                waveforms = state.waveforms,
                onClipSelected = viewModel::selectClip,
                onPlayheadMoved = viewModel::seekTo,
                onZoomChanged = viewModel::setZoomLevel,
                onScrollChanged = viewModel::setScrollOffset,
                onTrimChanged = viewModel::trimClip,
                onTrimDragStarted = viewModel::beginTrim,
                engine = viewModel.engine,
                modifier = Modifier.weight(0.35f)
            )

            // Tool panel (bottom)
            ToolPanel(
                currentTool = state.currentTool,
                selectedClipId = state.selectedClipId,
                onToolSelected = { tool ->
                    viewModel.setTool(tool)
                    when (tool) {
                        EditorTool.EFFECTS -> viewModel.showEffectsPanel()
                        EditorTool.TEXT -> viewModel.showTextEditor()
                        EditorTool.TRANSITION -> viewModel.showTransitionPicker()
                        EditorTool.EXPORT -> viewModel.showExportSheet()
                        EditorTool.AUDIO -> viewModel.showAudioPanel()
                        EditorTool.TRANSFORM -> viewModel.showTransformPanel()
                        EditorTool.CROP -> viewModel.showCropPanel()
                        EditorTool.AI -> viewModel.showAiToolsPanel()
                        EditorTool.SPEED -> viewModel.dismissAllPanels()
                        EditorTool.SPLIT -> {
                            viewModel.dismissAllPanels()
                            viewModel.splitClipAtPlayhead()
                            viewModel.setTool(EditorTool.NONE)
                        }
                        EditorTool.TRIM -> viewModel.dismissAllPanels()
                        EditorTool.FREEZE_FRAME -> {
                            viewModel.dismissAllPanels()
                            viewModel.insertFreezeFrame()
                            viewModel.setTool(EditorTool.NONE)
                        }
                        else -> {}
                    }
                },
                onDisabledToolTap = { label ->
                    viewModel.showToast("Select a clip to use $label")
                },
                onAddMedia = viewModel::showMediaPicker,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onDelete = viewModel::deleteSelectedClip,
                onDuplicate = viewModel::duplicateSelectedClip,
                onCopyEffects = viewModel::copyEffects,
                onPasteEffects = viewModel::pasteEffects,
                hasCopiedEffects = state.copiedEffects.isNotEmpty(),
                canUndo = state.undoStack.isNotEmpty(),
                canRedo = state.redoStack.isNotEmpty(),
                modifier = Modifier.weight(0.25f)
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
                    val effect = Effect(type = effectType, params = getDefaultParams(effectType))
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
            TextEditorSheet(
                playheadMs = state.playheadMs,
                onSave = { overlay ->
                    viewModel.addTextOverlay(overlay)
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
                onStartVoiceover = { viewModel.showToast("Voiceover recording: Coming soon") },
                onClose = viewModel::hideAudioPanel
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
                onClose = viewModel::hideAiToolsPanel,
                processingTool = state.aiProcessingTool
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
                    onRemove = {
                        val clipId = state.selectedClipId ?: return@EffectAdjustmentPanel
                        viewModel.removeEffect(clipId, effect.id)
                        viewModel.clearSelectedEffect()
                    },
                    onClose = viewModel::clearSelectedEffect
                )
            }
        }

        // Toast messages
        state.toastMessage?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = Mocha.Surface0,
                contentColor = Mocha.Text,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(message, fontSize = 13.sp)
            }
        }
    }
}

private fun getDefaultParams(type: EffectType): Map<String, Float> {
    return when (type) {
        EffectType.BRIGHTNESS -> mapOf("value" to 0f)
        EffectType.CONTRAST -> mapOf("value" to 1f)
        EffectType.SATURATION -> mapOf("value" to 1f)
        EffectType.TEMPERATURE -> mapOf("value" to 0f)
        EffectType.TINT -> mapOf("value" to 0f)
        EffectType.EXPOSURE -> mapOf("value" to 0f)
        EffectType.GAMMA -> mapOf("value" to 1f)
        EffectType.HIGHLIGHTS -> mapOf("value" to 0f)
        EffectType.SHADOWS -> mapOf("value" to 0f)
        EffectType.VIBRANCE -> mapOf("value" to 0f)
        EffectType.VIGNETTE -> mapOf("intensity" to 0.5f, "radius" to 0.7f)
        EffectType.GAUSSIAN_BLUR -> mapOf("radius" to 5f)
        EffectType.SHARPEN -> mapOf("strength" to 0.5f)
        EffectType.FILM_GRAIN -> mapOf("intensity" to 0.1f)
        EffectType.GLITCH -> mapOf("intensity" to 0.5f)
        EffectType.PIXELATE -> mapOf("size" to 10f)
        EffectType.CHROMATIC_ABERRATION -> mapOf("intensity" to 0.5f)
        EffectType.CHROMA_KEY -> mapOf("similarity" to 0.4f, "smoothness" to 0.1f, "spill" to 0.1f)
        EffectType.TILT_SHIFT -> mapOf("blur" to 0.01f, "focusY" to 0.5f, "width" to 0.1f)
        EffectType.CYBERPUNK -> mapOf("intensity" to 0.7f)
        EffectType.NOIR -> mapOf("intensity" to 0.7f)
        EffectType.VINTAGE -> mapOf("intensity" to 0.7f)
        EffectType.COOL_TONE -> mapOf("intensity" to 0.5f)
        EffectType.WARM_TONE -> mapOf("intensity" to 0.5f)
        EffectType.SPEED -> mapOf("value" to 1f)
        EffectType.MOSAIC -> mapOf("size" to 15f)
        EffectType.RADIAL_BLUR -> mapOf("intensity" to 0.5f)
        EffectType.MOTION_BLUR -> mapOf("intensity" to 0.5f)
        EffectType.FISHEYE -> mapOf("intensity" to 0.5f)
        EffectType.MIRROR -> emptyMap()
        EffectType.WAVE -> mapOf("amplitude" to 0.02f, "frequency" to 10f)
        EffectType.POSTERIZE -> mapOf("levels" to 6f)
        else -> emptyMap()
    }
}
