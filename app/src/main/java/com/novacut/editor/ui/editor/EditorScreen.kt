package com.novacut.editor.ui.editor

import android.os.Environment
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
import com.novacut.editor.engine.ExportState
import com.novacut.editor.model.*
import com.novacut.editor.ui.export.ExportSheet
import com.novacut.editor.ui.mediapicker.MediaPickerSheet
import com.novacut.editor.ui.theme.Mocha
import java.io.File

@Composable
fun EditorScreen(
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
                onClipSelected = viewModel::selectClip,
                onPlayheadMoved = viewModel::seekTo,
                onZoomChanged = viewModel::setZoomLevel,
                onScrollChanged = viewModel::setScrollOffset,
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
                        EditorTool.SPLIT -> {
                            viewModel.splitClipAtPlayhead()
                            viewModel.setTool(EditorTool.NONE)
                        }
                        else -> {}
                    }
                },
                onAddMedia = viewModel::showMediaPicker,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onDelete = viewModel::deleteSelectedClip,
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
                onMediaSelected = { uri -> viewModel.addClipToTrack(uri) },
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
                onConfigChanged = viewModel::updateExportConfig,
                onStartExport = {
                    val moviesDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_MOVIES
                    )
                    val outputDir = File(moviesDir, "NovaCut").apply { mkdirs() }
                    viewModel.startExport(outputDir)
                },
                onClose = viewModel::hideExportSheet
            )
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

private fun getDefaultParams(type: EffectType): MutableMap<String, Float> {
    return when (type) {
        EffectType.BRIGHTNESS -> mutableMapOf("value" to 0f)
        EffectType.CONTRAST -> mutableMapOf("value" to 1f)
        EffectType.SATURATION -> mutableMapOf("value" to 1f)
        EffectType.TEMPERATURE -> mutableMapOf("value" to 0f)
        EffectType.TINT -> mutableMapOf("value" to 0f)
        EffectType.EXPOSURE -> mutableMapOf("value" to 0f)
        EffectType.GAMMA -> mutableMapOf("value" to 1f)
        EffectType.HIGHLIGHTS -> mutableMapOf("value" to 0f)
        EffectType.SHADOWS -> mutableMapOf("value" to 0f)
        EffectType.VIBRANCE -> mutableMapOf("value" to 0f)
        EffectType.VIGNETTE -> mutableMapOf("intensity" to 0.5f, "radius" to 0.7f)
        EffectType.GAUSSIAN_BLUR -> mutableMapOf("radius" to 5f)
        EffectType.SHARPEN -> mutableMapOf("strength" to 0.5f)
        EffectType.FILM_GRAIN -> mutableMapOf("intensity" to 0.1f)
        EffectType.GLITCH -> mutableMapOf("intensity" to 0.5f)
        EffectType.PIXELATE -> mutableMapOf("size" to 10f)
        EffectType.CHROMATIC_ABERRATION -> mutableMapOf("intensity" to 0.5f)
        EffectType.CHROMA_KEY -> mutableMapOf("similarity" to 0.4f, "smoothness" to 0.1f, "spill" to 0.1f)
        EffectType.TILT_SHIFT -> mutableMapOf("blur" to 0.01f, "focusY" to 0.5f, "width" to 0.1f)
        EffectType.CYBERPUNK -> mutableMapOf("intensity" to 0.7f)
        EffectType.NOIR -> mutableMapOf("intensity" to 0.7f)
        EffectType.VINTAGE -> mutableMapOf("intensity" to 0.7f)
        EffectType.COOL_TONE -> mutableMapOf("intensity" to 0.5f)
        EffectType.WARM_TONE -> mutableMapOf("intensity" to 0.5f)
        EffectType.SPEED -> mutableMapOf("value" to 1f)
        EffectType.MOSAIC -> mutableMapOf("size" to 15f)
        EffectType.RADIAL_BLUR -> mutableMapOf("intensity" to 0.5f)
        EffectType.MOTION_BLUR -> mutableMapOf("intensity" to 0.5f)
        EffectType.FISHEYE -> mutableMapOf("intensity" to 0.5f)
        EffectType.MIRROR -> mutableMapOf()
        EffectType.WAVE -> mutableMapOf("amplitude" to 0.02f, "frequency" to 10f)
        EffectType.POSTERIZE -> mutableMapOf("levels" to 6f)
        else -> mutableMapOf()
    }
}
