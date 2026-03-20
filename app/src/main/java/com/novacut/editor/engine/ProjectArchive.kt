package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.novacut.editor.model.Track
import com.novacut.editor.model.TextOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Bundles a NovaCut project (state + media files) into a zip archive for backup/transfer.
 */
object ProjectArchive {

    /**
     * Export a project as a .novacut zip archive.
     * Includes the project JSON + all source media files.
     */
    suspend fun exportArchive(
        context: Context,
        projectId: String,
        tracks: List<Track>,
        textOverlays: List<TextOverlay>,
        playheadMs: Long,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Serialize project state
            val state = AutoSaveState(
                projectId = projectId,
                tracks = tracks,
                textOverlays = textOverlays,
                playheadMs = playheadMs
            )
            val projectJson = state.serialize()

            // Collect all unique media URIs
            val mediaUris = tracks.flatMap { track ->
                track.clips.map { it.sourceUri }
            }.distinct()

            val totalFiles = mediaUris.size + 1 // +1 for project.json
            var processedFiles = 0

            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zip ->
                // Write project JSON
                zip.putNextEntry(ZipEntry("project.json"))
                zip.write(projectJson.toByteArray())
                zip.closeEntry()
                processedFiles++
                onProgress(processedFiles.toFloat() / totalFiles)

                // Write media files
                mediaUris.forEachIndexed { index, uri ->
                    try {
                        val fileName = "media/${index}_${uri.lastPathSegment ?: "media_$index"}"
                        zip.putNextEntry(ZipEntry(fileName))

                        context.contentResolver.openInputStream(uri)?.use { input ->
                            input.copyTo(zip, bufferSize = 8192)
                        }

                        zip.closeEntry()
                    } catch (e: Exception) {
                        Log.w("ProjectArchive", "Skipping media file: $uri", e)
                    }
                    processedFiles++
                    onProgress(processedFiles.toFloat() / totalFiles)
                }
            }

            true
        } catch (e: Exception) {
            Log.e("ProjectArchive", "Archive export failed", e)
            outputFile.delete()
            false
        }
    }

    /**
     * Get the estimated archive size in bytes.
     */
    suspend fun estimateArchiveSize(
        context: Context,
        tracks: List<Track>
    ): Long = withContext(Dispatchers.IO) {
        var totalSize = 0L
        val mediaUris = tracks.flatMap { it.clips.map { c -> c.sourceUri } }.distinct()

        for (uri in mediaUris) {
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                    totalSize += fd.length
                }
            } catch (e: Exception) {
                // Skip unreadable files
            }
        }

        totalSize + 4096 // Add overhead for project JSON
    }
}
