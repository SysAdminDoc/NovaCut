package com.novacut.editor.engine

import android.content.Context
import com.novacut.editor.model.Track
import com.novacut.editor.model.TextOverlay
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Auto-saves project state to disk periodically and on significant changes.
 * Uses a simple JSON serialization approach for crash recovery.
 */
@Singleton
class ProjectAutoSave @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val autoSaveDir = File(context.filesDir, "autosave").apply { mkdirs() }
    private var autoSaveJob: Job? = null

    /**
     * Start periodic auto-save (every 30 seconds).
     */
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
                } catch (_: Exception) { }
            }
        }
    }

    /**
     * Save state immediately (call on significant changes).
     */
    fun saveNow(projectId: String, state: AutoSaveState) {
        scope.launch {
            try {
                saveState(projectId, state)
            } catch (_: Exception) { }
        }
    }

    /**
     * Check if a recovery file exists for a project.
     */
    fun hasRecoveryData(projectId: String): Boolean {
        return getAutoSaveFile(projectId).exists()
    }

    /**
     * Load recovery data.
     */
    fun loadRecoveryData(projectId: String): String? {
        val file = getAutoSaveFile(projectId)
        return if (file.exists()) file.readText() else null
    }

    /**
     * Delete recovery data after successful load or discard.
     */
    fun clearRecoveryData(projectId: String) {
        getAutoSaveFile(projectId).delete()
    }

    fun stop() {
        autoSaveJob?.cancel()
    }

    private fun saveState(projectId: String, state: AutoSaveState) {
        val file = getAutoSaveFile(projectId)
        val tempFile = File(autoSaveDir, "${projectId}.tmp")

        // Write to temp first for atomicity
        tempFile.writeText(state.serialize())
        tempFile.renameTo(file)
    }

    private fun getAutoSaveFile(projectId: String): File {
        return File(autoSaveDir, "${projectId}.json")
    }
}

data class AutoSaveState(
    val projectId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tracksJson: String,
    val textOverlaysJson: String,
    val playheadMs: Long
) {
    fun serialize(): String {
        return buildString {
            appendLine("{")
            appendLine("  \"projectId\": \"$projectId\",")
            appendLine("  \"timestamp\": $timestamp,")
            appendLine("  \"playheadMs\": $playheadMs,")
            appendLine("  \"tracks\": $tracksJson,")
            appendLine("  \"textOverlays\": $textOverlaysJson")
            appendLine("}")
        }
    }
}
