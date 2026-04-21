package com.novacut.editor.ui.editor

import android.content.Context
import android.net.Uri
import com.novacut.editor.model.ColorGrade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Delegate handling color grading, LUT import, and scope operations.
 * Extracted from EditorViewModel to reduce its size.
 */
class ColorGradingDelegate(
    private val stateFlow: MutableStateFlow<EditorState>,
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val saveUndoState: (String) -> Unit,
    private val showToast: (String) -> Unit,
    private val pauseIfPlaying: () -> Unit,
    private val dismissedPanelState: (EditorState) -> EditorState,
    private val getSelectedClip: () -> com.novacut.editor.model.Clip?,
    private val updatePreview: () -> Unit,
    private val saveProject: () -> Unit
) {
    private val _showLutPicker = MutableStateFlow(false)
    val showLutPicker: StateFlow<Boolean> = _showLutPicker.asStateFlow()

    fun showColorGrading() {
        pauseIfPlaying()
        stateFlow.update { dismissedPanelState(it).copy(panels = it.panels.closeAll().open(PanelId.COLOR_GRADING)) }
    }

    fun hideColorGrading() {
        stateFlow.update { it.copy(panels = it.panels.close(PanelId.COLOR_GRADING)) }
    }

    fun beginColorGradeAdjust() {
        saveUndoState("Color grade")
    }

    fun updateClipColorGrade(colorGrade: ColorGrade) {
        val clipId = stateFlow.value.selectedClipId ?: return
        stateFlow.update { s ->
            s.copy(tracks = s.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(colorGrade = colorGrade) else clip
                })
            })
        }
        updatePreview()
        saveProject()
    }

    fun importLut() {
        _showLutPicker.value = true
    }

    fun onLutPickerDismissed() {
        _showLutPicker.value = false
    }

    fun onLutFileSelected(uri: Uri) {
        _showLutPicker.value = false
        scope.launch(Dispatchers.IO) {
            try {
                val lutDir = File(appContext.filesDir, "luts").also { it.mkdirs() }
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "imported.cube"
                val destFile = File(lutDir, fileName)
                // Write to a sibling temp file first so a failed import never
                // leaves a partial/corrupt .cube at the live path that subsequent
                // imports would silently overwrite or use.
                val tempFile = File(lutDir, "$fileName.tmp")
                try {
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    // renameTo is atomic on the same filesystem (internal storage).
                    // Fall back to copy+delete if we cross a mount point (rare).
                    if (!tempFile.renameTo(destFile)) {
                        destFile.delete()
                        tempFile.copyTo(destFile, overwrite = true)
                        tempFile.delete()
                    }
                } catch (e: Exception) {
                    tempFile.delete()
                    throw e
                }
                withContext(Dispatchers.Main) {
                    setClipLut(destFile.absolutePath)
                    showToast("LUT applied: $fileName")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Failed to import LUT: ${e.message}")
                }
            }
        }
    }

    fun setClipLut(lutPath: String) {
        val clip = getSelectedClip() ?: return
        saveUndoState("Apply LUT")
        val currentGrade = clip.colorGrade ?: ColorGrade()
        updateClipColorGrade(currentGrade.copy(lutPath = lutPath))
    }

}
