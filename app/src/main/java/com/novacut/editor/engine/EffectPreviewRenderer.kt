package com.novacut.editor.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.novacut.editor.model.EffectType

object EffectPreviewRenderer {

    fun renderPreview(source: Bitmap, effectType: EffectType): Bitmap? {
        val matrix = colorMatrixForEffect(effectType) ?: return null
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    fun colorMatrixForEffect(effectType: EffectType): ColorMatrix? = when (effectType) {
        EffectType.BRIGHTNESS -> ColorMatrix(floatArrayOf(
            1.2f, 0f, 0f, 0f, 20f,
            0f, 1.2f, 0f, 0f, 20f,
            0f, 0f, 1.2f, 0f, 20f,
            0f, 0f, 0f, 1f, 0f
        ))
        EffectType.CONTRAST -> ColorMatrix(floatArrayOf(
            1.5f, 0f, 0f, 0f, -64f,
            0f, 1.5f, 0f, 0f, -64f,
            0f, 0f, 1.5f, 0f, -64f,
            0f, 0f, 0f, 1f, 0f
        ))
        EffectType.SATURATION -> ColorMatrix().apply { setSaturation(1.5f) }
        EffectType.EXPOSURE -> ColorMatrix(floatArrayOf(
            1.3f, 0f, 0f, 0f, 10f,
            0f, 1.3f, 0f, 0f, 10f,
            0f, 0f, 1.3f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        ))
        EffectType.TINT -> ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, 20f,
            0f, 1f, 0f, 0f, -10f,
            0f, 0f, 1f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        ))
        EffectType.GAMMA -> ColorMatrix(floatArrayOf(
            0.8f, 0f, 0f, 0f, 30f,
            0f, 0.8f, 0f, 0f, 30f,
            0f, 0f, 0.8f, 0f, 30f,
            0f, 0f, 0f, 1f, 0f
        ))
        EffectType.VIBRANCE -> ColorMatrix().apply { setSaturation(1.8f) }
        EffectType.COOL_TONE -> ColorMatrix(floatArrayOf(
            0.9f, 0f, 0f, 0f, 0f,
            0f, 0.95f, 0f, 0f, 0f,
            0f, 0f, 1.1f, 0f, 15f,
            0f, 0f, 0f, 1f, 0f
        ))
        EffectType.WARM_TONE -> ColorMatrix(floatArrayOf(
            1.1f, 0f, 0f, 0f, 15f,
            0f, 1f, 0f, 0f, 5f,
            0f, 0f, 0.9f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        EffectType.NOIR -> ColorMatrix().apply { setSaturation(0f) }
        EffectType.VINTAGE -> ColorMatrix(floatArrayOf(
            0.9f, 0.1f, 0.05f, 0f, 10f,
            0.05f, 0.85f, 0.1f, 0f, 5f,
            0.05f, 0.1f, 0.7f, 0f, 15f,
            0f, 0f, 0f, 1f, 0f
        ))
        EffectType.CYBERPUNK -> ColorMatrix(floatArrayOf(
            0.8f, 0.2f, 0f, 0f, 20f,
            0f, 0.7f, 0.3f, 0f, -10f,
            0.1f, 0f, 1.2f, 0f, 30f,
            0f, 0f, 0f, 1f, 0f
        ))
        EffectType.POSTERIZE -> ColorMatrix(floatArrayOf(
            2f, 0f, 0f, 0f, -128f,
            0f, 2f, 0f, 0f, -128f,
            0f, 0f, 2f, 0f, -128f,
            0f, 0f, 0f, 1f, 0f
        ))
        EffectType.HIGHLIGHTS -> ColorMatrix(floatArrayOf(
            1.1f, 0f, 0f, 0f, 15f,
            0f, 1.1f, 0f, 0f, 15f,
            0f, 0f, 1.1f, 0f, 15f,
            0f, 0f, 0f, 1f, 0f
        ))
        EffectType.SHADOWS -> ColorMatrix(floatArrayOf(
            0.9f, 0f, 0f, 0f, -15f,
            0f, 0.9f, 0f, 0f, -15f,
            0f, 0f, 0.9f, 0f, -15f,
            0f, 0f, 0f, 1f, 0f
        ))
        else -> null
    }

    val PREVIEWABLE_EFFECTS: Set<EffectType> = setOf(
        EffectType.BRIGHTNESS, EffectType.CONTRAST, EffectType.SATURATION,
        EffectType.EXPOSURE, EffectType.TINT, EffectType.GAMMA,
        EffectType.VIBRANCE, EffectType.COOL_TONE, EffectType.WARM_TONE,
        EffectType.NOIR, EffectType.VINTAGE, EffectType.CYBERPUNK,
        EffectType.POSTERIZE, EffectType.HIGHLIGHTS, EffectType.SHADOWS
    )
}
