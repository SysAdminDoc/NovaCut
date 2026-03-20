package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Transformer
import com.novacut.editor.model.ProxyResolution
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Generates low-resolution proxy files for smooth timeline editing.
 * Proxies are stored in app cache and swapped with originals during export.
 */
@Singleton
class ProxyEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val proxyDir = File(context.cacheDir, "proxies").also { it.mkdirs() }
    private val proxyMap = mutableMapOf<String, Uri>() // sourceUri hash -> proxy Uri

    fun getProxyUri(sourceUri: Uri): Uri? {
        return proxyMap[sourceUri.toString().hashCode().toString()]
    }

    fun hasProxy(sourceUri: Uri): Boolean {
        val key = sourceUri.toString().hashCode().toString()
        val file = File(proxyDir, "proxy_$key.mp4")
        return file.exists().also { if (it) proxyMap[key] = Uri.fromFile(file) }
    }

    @OptIn(UnstableApi::class)
    suspend fun generateProxy(
        sourceUri: Uri,
        resolution: ProxyResolution,
        onProgress: (Float) -> Unit = {}
    ): Uri? = withContext(Dispatchers.Main) {
        val key = sourceUri.toString().hashCode().toString()
        val outFile = File(proxyDir, "proxy_$key.mp4")

        if (outFile.exists()) {
            proxyMap[key] = Uri.fromFile(outFile)
            return@withContext Uri.fromFile(outFile)
        }

        try {
            suspendCancellableCoroutine { cont ->
                val mediaItem = MediaItem.fromUri(sourceUri)
                val editedItem = EditedMediaItem.Builder(mediaItem)
                    .setEffects(Effects.EMPTY)
                    .build()

                val transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: androidx.media3.transformer.ExportResult) {
                            proxyMap[key] = Uri.fromFile(outFile)
                            cont.resume(Uri.fromFile(outFile))
                        }
                        override fun onError(composition: Composition, exportResult: androidx.media3.transformer.ExportResult, exportException: androidx.media3.transformer.ExportException) {
                            Log.e("ProxyEngine", "Proxy generation failed", exportException)
                            outFile.delete()
                            cont.resume(null)
                        }
                    })
                    .build()

                transformer.start(
                    Composition.Builder(
                        EditedMediaItemSequence.Builder(editedItem).build()
                    ).build(),
                    outFile.absolutePath
                )

                cont.invokeOnCancellation {
                    transformer.cancel()
                    outFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("ProxyEngine", "Proxy generation error", e)
            null
        }
    }

    fun clearProxies() {
        proxyDir.listFiles()?.forEach { it.delete() }
        proxyMap.clear()
    }

    fun getCacheSize(): Long {
        return proxyDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
}
