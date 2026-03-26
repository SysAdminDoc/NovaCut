package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import dagger.hilt.android.qualifiers.ApplicationContext
import com.novacut.editor.model.Caption
import com.novacut.editor.model.CaptionStyle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Subtitle rendering engine for burned-in captions during export.
 *
 * Primary: libass via JNI (when integrated)
 *   URL: github.com/libass/libass
 *   Supports: Full ASS/SSA styling, RTL (FriBidi), CJK vertical (HarfBuzz), emoji
 *   NDK: Pure C, cross-compiles with FreeType + FriBidi + HarfBuzz
 *
 * Fallback: Android Canvas-based rendering (current implementation)
 *   Limitations: No ASS animation, limited styling, no RTL auto-detection
 *
 * Dependency (add to build.gradle.kts when ready):
 *   implementation("com.github.nicholasryan:libass-android:0.17.+")
 */
@Singleton
class SubtitleRenderEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Render a caption onto a transparent ARGB bitmap.
     * Used as an overlay during video export.
     *
     * @param caption The caption to render
     * @param width Video frame width
     * @param height Video frame height
     * @param timeMs Current time position (for word-level highlighting)
     * @return ARGB_8888 bitmap with rendered caption (transparent background)
     */
    fun renderCaption(
        caption: Caption,
        width: Int,
        height: Int,
        timeMs: Long
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val style = caption.style

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.color.toInt()
            textSize = style.fontSize * (height / 1080f)  // Scale relative to 1080p
            typeface = resolveTypeface(style.fontFamily)
            isFakeBoldText = true
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }

        // Outline/stroke
        if (style.outline) {
            val outlinePaint = TextPaint(textPaint).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = 3f * (height / 1080f)
                color = Color.BLACK
            }
            drawCaptionText(canvas, caption.text, outlinePaint, width, height, style, timeMs, caption)
        }

        // Fill text
        drawCaptionText(canvas, caption.text, textPaint, width, height, style, timeMs, caption)

        return bitmap
    }

    private fun drawCaptionText(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        width: Int,
        height: Int,
        style: CaptionStyle,
        timeMs: Long,
        caption: Caption
    ) {
        val maxWidth = (width * 0.85f).toInt()
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.2f)
            .build()

        val textHeight = layout.height.toFloat()
        val x = (width - maxWidth) / 2f
        val y = height * style.positionY - textHeight / 2f

        // Background box
        if (style.backgroundColor != 0L) {
            val bgPaint = Paint().apply {
                color = style.backgroundColor.toInt()
                this.style = Paint.Style.FILL
            }
            val padding = 12f * (height / 1080f)
            canvas.drawRoundRect(
                x - padding, y - padding,
                x + maxWidth + padding, y + textHeight + padding,
                8f, 8f, bgPaint
            )
        }

        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun resolveTypeface(fontFamily: String): Typeface {
        return try {
            Typeface.create(fontFamily, Typeface.NORMAL)
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
    }

    /**
     * Generate an ASS subtitle file from captions.
     * Can be used with libass for high-quality rendering or with FFmpeg for burning in.
     */
    fun generateAssFile(captions: List<Caption>, width: Int, height: Int): String {
        return buildString {
            appendLine("[Script Info]")
            appendLine("Title: NovaCut Export")
            appendLine("ScriptType: v4.00+")
            appendLine("PlayResX: $width")
            appendLine("PlayResY: $height")
            appendLine()
            appendLine("[V4+ Styles]")
            appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")

            // Generate styles per unique caption style
            val styles = captions.map { it.style }.distinct()
            styles.forEachIndexed { idx, style ->
                val fontSize = (style.fontSize * height / 1080f).toInt()
                val primaryColor = assColor(style.color)
                val outlineColor = "&H000000FF&"
                val bgColor = assColor(style.backgroundColor)
                appendLine("Style: Style$idx,${style.fontFamily},$fontSize,$primaryColor,&H000000FF&,$outlineColor,$bgColor,-1,0,0,0,100,100,0,0,1,2,1,2,10,10,10,1")
            }

            appendLine()
            appendLine("[Events]")
            appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")

            captions.forEachIndexed { idx, caption ->
                val styleIdx = styles.indexOf(caption.style)
                val start = formatAssTime(caption.startTimeMs)
                val end = formatAssTime(caption.endTimeMs)
                val text = caption.text.replace("\n", "\\N")
                appendLine("Dialogue: 0,$start,$end,Style$styleIdx,,0,0,0,,$text")
            }
        }
    }

    private fun assColor(color: Long): String {
        val a = ((color shr 24) and 0xFF).toInt()
        val r = ((color shr 16) and 0xFF).toInt()
        val g = ((color shr 8) and 0xFF).toInt()
        val b = (color and 0xFF).toInt()
        // ASS color format: &HAABBGGRR& (note: BGR order, alpha inverted)
        return "&H%02X%02X%02X%02X&".format(255 - a, b, g, r)
    }

    private fun formatAssTime(ms: Long): String {
        val h = (ms / 3600000).toInt()
        val m = ((ms % 3600000) / 60000).toInt()
        val s = ((ms % 60000) / 1000).toInt()
        val cs = ((ms % 1000) / 10).toInt()
        return "%d:%02d:%02d.%02d".format(h, m, s, cs)
    }
}
