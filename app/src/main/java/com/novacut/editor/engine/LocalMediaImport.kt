package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException

private const val LOCAL_MEDIA_IMPORT_TAG = "LocalMediaImport"
private const val LOCAL_MEDIA_FALLBACK_STEM = "media"

internal fun managedMediaDir(context: Context): File = File(context.filesDir, "media/imports")

internal fun pendingCameraCaptureDir(context: Context): File = File(context.cacheDir, "camera-captures")

internal fun importUriToManagedMedia(
    context: Context,
    uri: Uri,
    mediaType: String
): Uri? {
    if (uri.scheme == "file") {
        val sourceFile = uri.path?.let(::File)
        if (sourceFile != null && sourceFile.exists()) {
            val appFilesRoot = context.filesDir.absoluteFile
            val sourceCanonical = runCatching { sourceFile.canonicalFile }.getOrNull()
            if (sourceCanonical != null &&
                sourceCanonical.path.startsWith(appFilesRoot.path + File.separator)
            ) {
                return Uri.fromFile(sourceCanonical)
            }
        }
    }

    val destinationDir = managedMediaDir(context)
    if (!destinationDir.exists() && !destinationDir.mkdirs() && !destinationDir.exists()) {
        Log.w(LOCAL_MEDIA_IMPORT_TAG, "Failed to create managed media directory: ${destinationDir.path}")
        return null
    }

    val destinationFile = createUniqueManagedMediaFile(
        directory = destinationDir,
        displayName = resolveMediaDisplayName(context, uri),
        extension = resolveManagedMediaExtension(context, uri, mediaType)
    )

    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            destinationFile.outputStream().use { output -> input.copyTo(output) }
        } ?: run {
            Log.w(LOCAL_MEDIA_IMPORT_TAG, "Cannot open input stream for $uri")
            return null
        }

        if (destinationFile.length() <= 0L) {
            destinationFile.delete()
            Log.w(LOCAL_MEDIA_IMPORT_TAG, "Imported file was empty for $uri")
            null
        } else {
            Uri.fromFile(destinationFile)
        }
    } catch (e: Exception) {
        destinationFile.delete()
        Log.w(LOCAL_MEDIA_IMPORT_TAG, "Failed to import media URI $uri", e)
        null
    }
}

internal fun finalizePendingCameraCapture(
    context: Context,
    pendingFile: File,
    mediaType: String
): Uri? {
    if (!pendingFile.exists() || pendingFile.length() <= 0L) {
        runCatching { pendingFile.delete() }
        Log.w(LOCAL_MEDIA_IMPORT_TAG, "Pending camera capture missing or empty: ${pendingFile.path}")
        return null
    }

    val destinationDir = managedMediaDir(context)
    if (!destinationDir.exists() && !destinationDir.mkdirs() && !destinationDir.exists()) {
        Log.w(LOCAL_MEDIA_IMPORT_TAG, "Failed to create managed media directory: ${destinationDir.path}")
        return null
    }

    val destinationFile = createUniqueManagedMediaFile(
        directory = destinationDir,
        displayName = pendingFile.name,
        extension = ".${pendingFile.extension}".takeIf { pendingFile.extension.isNotBlank() }
            ?: defaultManagedMediaExtension(mediaType)
    )

    return try {
        moveFileReplacing(pendingFile, destinationFile)
        Uri.fromFile(destinationFile)
    } catch (_: Exception) {
        try {
            pendingFile.inputStream().use { input ->
                destinationFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (destinationFile.length() <= 0L) {
                throw IOException("Finalized camera capture is empty")
            }
            pendingFile.delete()
            Uri.fromFile(destinationFile)
        } catch (copyError: Exception) {
            destinationFile.delete()
            Log.w(LOCAL_MEDIA_IMPORT_TAG, "Failed to finalize camera capture ${pendingFile.path}", copyError)
            null
        }
    }
}

internal fun resolveMediaDisplayName(context: Context, uri: Uri): String? {
    if (uri.scheme == "file") {
        return uri.lastPathSegment
    }

    return runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else {
                null
            }
        }
    }.getOrNull() ?: uri.lastPathSegment
}

internal fun resolveManagedMediaExtension(
    context: Context,
    uri: Uri,
    mediaType: String
): String {
    val mimeType = context.contentResolver.getType(uri)
    val mimeExtension = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
    if (!mimeExtension.isNullOrBlank()) {
        return ".${mimeExtension.lowercase()}"
    }

    val displayName = resolveMediaDisplayName(context, uri)
    val displayExtension = displayName?.substringAfterLast('.', "")
    if (!displayExtension.isNullOrBlank()) {
        return ".${displayExtension.lowercase()}"
    }

    return defaultManagedMediaExtension(mediaType)
}

private fun defaultManagedMediaExtension(mediaType: String): String {
    return when (mediaType) {
        "image" -> ".jpg"
        "audio" -> ".m4a"
        else -> ".mp4"
    }
}

private fun createUniqueManagedMediaFile(
    directory: File,
    displayName: String?,
    extension: String
): File {
    val safeExtension = extension
        .takeIf { it.startsWith('.') && it.length > 1 }
        ?.lowercase()
        ?: ".bin"
    val fallbackName = "$LOCAL_MEDIA_FALLBACK_STEM$safeExtension"
    val preferredName = displayName
        ?.takeIf { it.isNotBlank() }
        ?.let { sanitizeFileNamePreservingExtension(it, fallbackStem = LOCAL_MEDIA_FALLBACK_STEM, maxLength = 72) }
        ?: fallbackName
    val normalizedName = if (preferredName.endsWith(safeExtension)) {
        preferredName
    } else {
        sanitizeFileNamePreservingExtension(
            raw = preferredName.substringBeforeLast('.', preferredName) + safeExtension,
            fallbackStem = LOCAL_MEDIA_FALLBACK_STEM,
            maxLength = 72
        )
    }

    var candidate = File(directory, "${System.currentTimeMillis()}_$normalizedName")
    var index = 2
    while (candidate.exists()) {
        candidate = File(directory, "${System.currentTimeMillis()}_${index}_$normalizedName")
        index++
    }
    return candidate
}
