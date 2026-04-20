package com.novacut.editor.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub engine -- requires app.rive:rive-android. See ROADMAP.md Tier A.13.
 *
 * Rive brings interactive animations with state-machine inputs, 120 fps playback,
 * and tiny vector-based asset sizes vs Lottie's JSON. Pairs with existing
 * [LottieTemplateEngine] as a parallel rendering path.
 *
 * Rive dependency (add to app/build.gradle.kts when ready):
 *   implementation("app.rive:rive-android:9.0.0")
 */
@Singleton
class RiveTemplateEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class RiveTemplate(
        val id: String,
        val displayName: String,
        val assetPath: String,
        val artboardName: String? = null,
        val stateMachineName: String? = null,
        val inputs: List<StateMachineInput> = emptyList()
    )

    data class StateMachineInput(
        val name: String,
        val type: InputType,
        val defaultValue: Any? = null
    ) {
        enum class InputType { BOOLEAN, NUMBER, TRIGGER }
    }

    /** Returns list of built-in Rive templates bundled with the app. */
    fun getBuiltInTemplates(): List<RiveTemplate> = BUILT_IN_TEMPLATES

    /** Whether Rive runtime is available on this device. */
    fun isAvailable(): Boolean = false

    /**
     * Render one frame of a Rive template to a bitmap for export-pipeline compositing.
     * Returns null when Rive runtime is unavailable.
     */
    suspend fun renderFrame(
        template: RiveTemplate,
        timeSec: Float,
        widthPx: Int,
        heightPx: Int,
        inputValues: Map<String, Any> = emptyMap()
    ): Bitmap? = withContext(Dispatchers.Default) {
        Log.d(TAG, "renderFrame: stub -- requires Rive runtime (${template.id} @ ${timeSec}s)")
        null
    }

    companion object {
        private const val TAG = "RiveTemplateEngine"

        private val BUILT_IN_TEMPLATES = listOf(
            RiveTemplate("rive_lower_third", "Interactive Lower Third", "rive/lower_third.riv"),
            RiveTemplate("rive_subscribe_btn", "Animated Subscribe", "rive/subscribe.riv"),
            RiveTemplate("rive_progress_bar", "Progress Bar", "rive/progress.riv"),
            RiveTemplate("rive_logo_reveal", "Morphing Logo Reveal", "rive/logo_reveal.riv"),
            RiveTemplate("rive_reaction_meter", "Reaction Meter", "rive/reaction_meter.riv")
        )
    }
}
