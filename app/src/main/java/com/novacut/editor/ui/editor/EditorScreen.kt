package com.novacut.editor.ui.editor

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.util.Log
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
            // Top bar (Home / Undo / Redo / Delete / More / Export)
            EditorTopBar(
                onBack = onBack,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                canUndo = state.undoStack.isNotEmpty(),
                canRedo = state.redoStack.isNotEmpty(),
                selectedClipId = state.selectedClipId,
                onDelete = viewModel::deleteSelectedClip,
                onAddMedia = viewModel::showMediaPicker,
                onExport = viewModel::showExportSheet
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
                        "speed" -> { viewModel.setTool(EditorTool.SPEED); viewModel.dismissAllPanels() }
                        "transform" -> viewModel.showTransformPanel()
                        "effects" -> viewModel.showEffectsPanel()
                        "effects_disabled" -> viewModel.showToast("Select a clip to use Effects")
                        "transition" -> viewModel.showTransitionPicker()
                        "aspect" -> viewModel.showCropPanel()
                        "back" -> viewModel.selectClip(null)
                        "add_text" -> viewModel.showTextEditor()
                        "split" -> { viewModel.splitClipAtPlayhead(); viewModel.setTool(EditorTool.NONE) }
                        "trim" -> { viewModel.setTool(EditorTool.TRIM); viewModel.dismissAllPanels() }
                        "merge" -> viewModel.mergeWithNextClip()
                        "duplicate" -> viewModel.duplicateSelectedClip()
                        "freeze" -> { viewModel.insertFreezeFrame(); viewModel.setTool(EditorTool.NONE) }
                        "copy_fx" -> viewModel.copyEffects()
                        "paste_fx" -> viewModel.pasteEffects()
                        "auto_captions" -> viewModel.runAiTool("auto_captions")
                        "scene_detect" -> viewModel.runAiTool("scene_detect")
                        "smart_crop" -> viewModel.runAiTool("smart_crop")
                        "auto_color" -> viewModel.runAiTool("auto_color")
                        "stabilize" -> viewModel.runAiTool("stabilize")
                        "denoise" -> viewModel.runAiTool("denoise")
                        "remove_bg" -> viewModel.runAiTool("remove_bg")
                        "track_motion" -> viewModel.runAiTool("track_motion")
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
                    onEffectDragStarted = viewModel::beginEffectAdjust,
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
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    selectedClipId: String?,
    onDelete: () -> Unit,
    onAddMedia: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showOverflow by remember { mutableStateOf(false) }

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

            Spacer(modifier = Modifier.weight(1f))

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
                            Icon(Icons.Default.Add, contentDescription = "Add media")
                        }
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

