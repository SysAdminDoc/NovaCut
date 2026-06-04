package com.novacut.editor.engine

import android.content.ComponentCallbacks2
import javax.inject.Inject
import javax.inject.Singleton

enum class MemoryTrimAction {
    CLEAR_THUMBNAILS,
    CLEAR_WAVEFORMS,
    CLEAR_PROXY_SCRATCH,
}

enum class MemoryTrimReason {
    UI_HIDDEN,
    VISIBLE_MEMORY_PRESSURE,
    BACKGROUND_MEMORY_PRESSURE,
    UNKNOWN,
}

data class MemoryTrimDecision(
    val level: Int,
    val levelName: String,
    val reason: MemoryTrimReason,
    val actions: List<MemoryTrimAction>,
    val logBreadcrumb: Boolean,
)

@Singleton
@Suppress("DEPRECATION")
class MemoryTrimPolicy @Inject constructor() {

    fun decisionFor(level: Int): MemoryTrimDecision {
        val allEditorCaches = listOf(
            MemoryTrimAction.CLEAR_THUMBNAILS,
            MemoryTrimAction.CLEAR_WAVEFORMS,
            MemoryTrimAction.CLEAR_PROXY_SCRATCH,
        )
        val visibleCacheTrim = listOf(
            MemoryTrimAction.CLEAR_THUMBNAILS,
            MemoryTrimAction.CLEAR_WAVEFORMS,
        )

        val reason: MemoryTrimReason
        val actions: List<MemoryTrimAction>
        val logBreadcrumb: Boolean

        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                reason = MemoryTrimReason.UI_HIDDEN
                actions = visibleCacheTrim
                logBreadcrumb = true
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                reason = MemoryTrimReason.VISIBLE_MEMORY_PRESSURE
                actions = listOf(MemoryTrimAction.CLEAR_WAVEFORMS)
                logBreadcrumb = false
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                reason = MemoryTrimReason.VISIBLE_MEMORY_PRESSURE
                actions = visibleCacheTrim
                logBreadcrumb = true
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                reason = MemoryTrimReason.BACKGROUND_MEMORY_PRESSURE
                actions = allEditorCaches
                logBreadcrumb = true
            }
            else -> {
                reason = MemoryTrimReason.UNKNOWN
                actions = emptyList()
                logBreadcrumb = false
            }
        }

        return MemoryTrimDecision(
            level = level,
            levelName = levelName(level),
            reason = reason,
            actions = actions,
            logBreadcrumb = logBreadcrumb,
        )
    }

    companion object {
        fun levelName(level: Int): String = when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "TRIM_MEMORY_UI_HIDDEN"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "TRIM_MEMORY_RUNNING_MODERATE"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "TRIM_MEMORY_RUNNING_LOW"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "TRIM_MEMORY_RUNNING_CRITICAL"
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "TRIM_MEMORY_BACKGROUND"
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "TRIM_MEMORY_MODERATE"
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "TRIM_MEMORY_COMPLETE"
            else -> "TRIM_MEMORY_UNKNOWN_$level"
        }
    }
}
