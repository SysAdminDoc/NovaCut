package com.novacut.editor.engine

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FontRegistry @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fontsDir = File(context.filesDir, "fonts")
    private val typefaceCache = mutableMapOf<String, Typeface>()

    data class ImportedFont(
        val fileName: String,
        val displayName: String,
        val file: File
    )

    fun listImportedFonts(): List<ImportedFont> {
        if (!fontsDir.exists()) return emptyList()
        return fontsDir.listFiles()
            ?.filter { it.extension.lowercase() in setOf("ttf", "otf") }
            ?.mapNotNull { file ->
                val typeface = loadTypeface(file) ?: return@mapNotNull null
                ImportedFont(
                    fileName = file.name,
                    displayName = file.nameWithoutExtension.replace(Regex("[_-]"), " "),
                    file = file
                )
            }
            ?.sortedBy { it.displayName }
            ?: emptyList()
    }

    fun importFont(uri: Uri): ImportedFont? {
        fontsDir.mkdirs()
        val inputStream = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot open font URI", e)
            return null
        } ?: return null

        val fileName = resolveFileName(uri)
        val targetFile = File(fontsDir, fileName)
        val partialFile = File(fontsDir, "$fileName.partial")

        return try {
            inputStream.use { input ->
                partialFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (loadTypeface(partialFile) == null) {
                partialFile.delete()
                Log.w(TAG, "Font file is not a valid typeface: $fileName")
                return null
            }
            partialFile.renameTo(targetFile)
            ImportedFont(
                fileName = targetFile.name,
                displayName = targetFile.nameWithoutExtension.replace(Regex("[_-]"), " "),
                file = targetFile
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to import font", e)
            partialFile.delete()
            null
        }
    }

    fun deleteFont(fileName: String): Boolean {
        typefaceCache.remove(fileName)
        return File(fontsDir, fileName).delete()
    }

    fun resolveTypeface(fontFamily: String): Typeface? {
        if (!fontFamily.startsWith(CUSTOM_PREFIX)) return null
        val fileName = fontFamily.removePrefix(CUSTOM_PREFIX)
        typefaceCache[fileName]?.let { return it }
        val file = File(fontsDir, fileName)
        return loadTypeface(file)?.also { typefaceCache[fileName] = it }
    }

    fun isCustomFont(fontFamily: String): Boolean = fontFamily.startsWith(CUSTOM_PREFIX)

    fun fontFamilyKey(fileName: String): String = "$CUSTOM_PREFIX$fileName"

    private fun loadTypeface(file: File): Typeface? = try {
        if (file.exists() && file.length() > 0) Typeface.createFromFile(file) else null
    } catch (e: Exception) {
        Log.w(TAG, "Invalid font file: ${file.name}", e)
        null
    }

    private fun resolveFileName(uri: Uri): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        val nameFromCursor = cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) it.getString(idx) else null
            } else null
        }
        val baseName = nameFromCursor ?: uri.lastPathSegment ?: "font_${System.currentTimeMillis()}"
        val ext = baseName.substringAfterLast('.', "ttf").lowercase()
        val stem = baseName.substringBeforeLast('.')
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(60)
        return "$stem.$ext"
    }

    companion object {
        private const val TAG = "FontRegistry"
        const val CUSTOM_PREFIX = "custom:"
    }
}
