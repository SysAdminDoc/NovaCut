package com.novacut.editor.engine

import android.content.Context
import android.util.Log
import com.novacut.editor.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EdlExporter"

/**
 * Exports NovaCut project timeline as industry-standard EDL (CMX 3600)
 * or FCPXML for import into desktop editors (Premiere, Resolve, FCPX).
 */
@Singleton
class EdlExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    enum class ExportFormat(val extension: String, val displayName: String) {
        EDL("edl", "EDL (CMX 3600)"),
        FCPXML("fcpxml", "Final Cut Pro XML"),
        OTIO("otio", "OpenTimelineIO JSON")
    }

    /**
     * Export timeline as EDL file.
     */
    suspend fun exportEdl(
        projectName: String,
        tracks: List<Track>,
        frameRate: Int = 30
    ): File? = withContext(Dispatchers.IO) {
        try {
            val sb = StringBuilder()
            sb.appendLine("TITLE: $projectName")
            sb.appendLine("FCM: NON-DROP FRAME")
            sb.appendLine()

            val videoClips = tracks
                .filter { it.type == TrackType.VIDEO && it.isVisible }
                .flatMap { it.clips }
                .sortedBy { it.timelineStartMs }

            for ((index, clip) in videoClips.withIndex()) {
                val editNum = String.format("%03d", index + 1)
                val reelName = clip.sourceUri.lastPathSegment
                    ?.take(8)?.uppercase() ?: "AX"

                val srcIn = msToTimecode(clip.trimStartMs, frameRate)
                val srcOut = msToTimecode(clip.trimEndMs, frameRate)
                val recIn = msToTimecode(clip.timelineStartMs, frameRate)
                val recOut = msToTimecode(clip.timelineEndMs, frameRate)

                // Transition
                val transition = if (clip.transition != null) {
                    val frames = (clip.transition.durationMs * frameRate / 1000).toInt()
                    "D ${String.format("%03d", frames)}"
                } else {
                    "C"
                }

                sb.appendLine(
                    "$editNum  $reelName  V  $transition  $srcIn $srcOut $recIn $recOut"
                )

                // Speed effect
                if (clip.speed != 1.0f) {
                    val effectiveFps = frameRate * clip.speed
                    sb.appendLine(
                        "M2   $reelName  ${String.format("%.1f", effectiveFps)}  $srcIn"
                    )
                }

                // Source file comment
                sb.appendLine(
                    "* FROM CLIP NAME: ${clip.sourceUri.lastPathSegment ?: "unknown"}"
                )

                // Effects as comments
                for (effect in clip.effects.filter { it.enabled }) {
                    sb.appendLine("* EFFECT NAME: ${effect.type.displayName}")
                }

                sb.appendLine()
            }

            val outputDir = File(context.getExternalFilesDir(null), "exports")
            outputDir.mkdirs()
            val sanitized = projectName.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(50)
            val file = File(outputDir, "${sanitized}.edl")
            file.writeText(sb.toString())
            Log.d(TAG, "EDL exported: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "EDL export failed", e)
            null
        }
    }

    /**
     * Export timeline as FCPXML for Final Cut Pro / DaVinci Resolve.
     */
    suspend fun exportFcpxml(
        projectName: String,
        tracks: List<Track>,
        frameRate: Int = 30,
        resolution: Resolution = Resolution.FHD_1080P,
        aspectRatio: AspectRatio = AspectRatio.RATIO_16_9
    ): File? = withContext(Dispatchers.IO) {
        try {
            val (w, h) = resolution.forAspect(aspectRatio)
            val sb = StringBuilder()

            sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            sb.appendLine("""<!DOCTYPE fcpxml>""")
            sb.appendLine("""<fcpxml version="1.10">""")
            sb.appendLine("""  <resources>""")
            sb.appendLine(
                """    <format id="r1" name="FFVideoFormat${h}p${frameRate}" """ +
                    """frameDuration="100/${frameRate * 100}s" width="$w" height="$h"/>"""
            )

            // Add media resources
            val allClips = tracks.flatMap { it.clips }
                .distinctBy { it.sourceUri.toString() }
            for ((idx, clip) in allClips.withIndex()) {
                val mediaId = "r${idx + 10}"
                val fileName = clip.sourceUri.lastPathSegment ?: "media_$idx"
                val duration = "${clip.sourceDurationMs}/1000s"
                sb.appendLine(
                    """    <asset id="$mediaId" name="$fileName" start="0s" """ +
                        """duration="$duration" hasVideo="1" hasAudio="1">"""
                )
                sb.appendLine(
                    """      <media-rep kind="original-media" """ +
                        """src="file://${clip.sourceUri}"/>"""
                )
                sb.appendLine("""    </asset>""")
            }

            sb.appendLine("""  </resources>""")
            sb.appendLine("""  <library>""")
            sb.appendLine("""    <event name="$projectName">""")
            sb.appendLine("""      <project name="$projectName">""")

            val totalDuration = tracks.flatMap { it.clips }
                .maxOfOrNull { it.timelineEndMs } ?: 0
            sb.appendLine(
                """        <sequence format="r1" """ +
                    """duration="${totalDuration}/1000s">"""
            )
            sb.appendLine("""          <spine>""")

            val videoClips = tracks
                .filter {
                    (it.type == TrackType.VIDEO || it.type == TrackType.OVERLAY) &&
                        it.isVisible
                }
                .flatMap { it.clips }
                .sortedBy { it.timelineStartMs }

            for (clip in videoClips) {
                val mediaIdx = allClips.indexOfFirst {
                    it.sourceUri == clip.sourceUri
                }
                val mediaId = "r${mediaIdx + 10}"
                val start = "${clip.trimStartMs}/1000s"
                val duration = "${clip.durationMs}/1000s"
                val offset = "${clip.timelineStartMs}/1000s"
                val clipName = clip.sourceUri.lastPathSegment ?: "clip"

                sb.appendLine(
                    """            <asset-clip ref="$mediaId" offset="$offset" """ +
                        """name="$clipName" start="$start" duration="$duration">"""
                )

                // Speed
                if (clip.speed != 1.0f) {
                    val scaledMs = (clip.durationMs * clip.speed).toLong()
                    sb.appendLine(
                        """              <timeMap>""" +
                            """<timept time="0s" value="0s" interp="smooth"/>""" +
                            """<timept time="$duration" """ +
                            """value="${scaledMs}/1000s" """ +
                            """interp="smooth"/></timeMap>"""
                    )
                }

                sb.appendLine("""            </asset-clip>""")
            }

            sb.appendLine("""          </spine>""")
            sb.appendLine("""        </sequence>""")
            sb.appendLine("""      </project>""")
            sb.appendLine("""    </event>""")
            sb.appendLine("""  </library>""")
            sb.appendLine("""</fcpxml>""")

            val outputDir = File(context.getExternalFilesDir(null), "exports")
            outputDir.mkdirs()
            val sanitized = projectName.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(50)
            val file = File(outputDir, "${sanitized}.fcpxml")
            file.writeText(sb.toString())
            Log.d(TAG, "FCPXML exported: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "FCPXML export failed", e)
            null
        }
    }

    private fun msToTimecode(ms: Long, fps: Int): String {
        val totalFrames = (ms * fps / 1000)
        val frames = (totalFrames % fps).toInt()
        val totalSeconds = totalFrames / fps
        val seconds = (totalSeconds % 60).toInt()
        val minutes = ((totalSeconds / 60) % 60).toInt()
        val hours = (totalSeconds / 3600).toInt()
        return String.format("%02d:%02d:%02d:%02d", hours, minutes, seconds, frames)
    }
}
