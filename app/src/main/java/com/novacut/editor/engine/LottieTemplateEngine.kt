package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.TextDelegate
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders Lottie animations as video overlay frames.
 *
 * Dependency (add to build.gradle.kts):
 *   implementation("com.airbnb.android:lottie-compose:6.+")
 *
 * Usage flow:
 * 1. Load template from assets or file
 * 2. Replace dynamic text via TextDelegate
 * 3. Render frame-by-frame for export overlay
 *
 * Templates stored in: assets/lottie_templates/
 * Format: .json (Lottie) or .lottie (dotLottie compressed)
 */
@Singleton
class LottieTemplateEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class TitleTemplate(
        val id: String,
        val name: String,
        val category: String,       // "lower_third", "full_screen", "subtitle", "logo_reveal"
        val durationMs: Long,
        val assetPath: String,      // path in assets folder
        val editableFields: List<String> = listOf("title", "subtitle"),
        val previewUrl: String? = null
    )

    /**
     * Built-in animated title templates.
     */
    fun getBuiltInTemplates(): List<TitleTemplate> = listOf(
        TitleTemplate("lower_third_slide", "Slide In Lower Third", "lower_third", 3000L, "lottie_templates/lower_third_slide.json", listOf("name", "title")),
        TitleTemplate("lower_third_modern", "Modern Lower Third", "lower_third", 4000L, "lottie_templates/lower_third_modern.json", listOf("name", "title")),
        TitleTemplate("title_bounce", "Bounce Title", "full_screen", 2500L, "lottie_templates/title_bounce.json", listOf("title")),
        TitleTemplate("title_typewriter", "Typewriter", "full_screen", 3000L, "lottie_templates/title_typewriter.json", listOf("title")),
        TitleTemplate("title_glitch", "Glitch Reveal", "full_screen", 2000L, "lottie_templates/title_glitch.json", listOf("title")),
        TitleTemplate("title_neon", "Neon Glow", "full_screen", 3500L, "lottie_templates/title_neon.json", listOf("title")),
        TitleTemplate("subtitle_fade", "Fade Subtitle", "subtitle", 2000L, "lottie_templates/subtitle_fade.json", listOf("text")),
        TitleTemplate("logo_reveal_circle", "Circle Logo Reveal", "logo_reveal", 2500L, "lottie_templates/logo_circle.json", listOf("title")),
        TitleTemplate("countdown_3", "3-2-1 Countdown", "full_screen", 3000L, "lottie_templates/countdown.json"),
        TitleTemplate("subscribe_button", "Subscribe Button", "lower_third", 4000L, "lottie_templates/subscribe.json")
    )

    /**
     * Render a single frame of a Lottie animation.
     * Used during export to burn titles into video.
     *
     * @param composition Pre-loaded Lottie composition
     * @param frameTimeMs Time position within the animation
     * @param width Output frame width
     * @param height Output frame height
     * @param textReplacements Map of text layer names to replacement values
     * @return ARGB bitmap of the rendered frame
     */
    fun renderFrame(
        composition: LottieComposition,
        frameTimeMs: Long,
        width: Int,
        height: Int,
        textReplacements: Map<String, String> = emptyMap()
    ): Bitmap {
        val drawable = LottieDrawable()
        drawable.composition = composition

        // Replace dynamic text
        if (textReplacements.isNotEmpty()) {
            val textDelegate = TextDelegate(drawable)
            textReplacements.forEach { (layerName, text) ->
                textDelegate.setText(layerName, text)
            }
            drawable.setTextDelegate(textDelegate)
        }

        // Set progress (0..1)
        val progress = (frameTimeMs.toFloat() / composition.duration).coerceIn(0f, 1f)
        drawable.progress = progress

        // Render to bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * Load a Lottie composition from assets.
     */
    suspend fun loadTemplate(assetPath: String): LottieComposition? {
        return try {
            val result = LottieCompositionFactory.fromAsset(context, assetPath)
            val task = result
            val latch = java.util.concurrent.CountDownLatch(1)
            var composition: LottieComposition? = null
            task.addListener { result ->
                composition = result
                latch.countDown()
            }.addFailureListener {
                latch.countDown()
            }
            latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
            composition
        } catch (e: Exception) {
            null
        }
    }
}
