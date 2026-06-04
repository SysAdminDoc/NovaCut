package com.novacut.editor.ui.editor

import com.novacut.editor.model.Clip
import kotlin.math.abs

internal const val ACCESSIBILITY_NUDGE_MS = 100L

private const val KEYBOARD_FINE_NUDGE_MS = 100L
private const val KEYBOARD_COARSE_NUDGE_MS = 1000L

internal enum class TimelineClipLongPressResult { OPENED_COMPOUND, TOGGLED_MULTI_SELECT }

internal fun dispatchTimelineClipLongPress(
    clipId: String,
    isCompound: Boolean,
    onOpenCompoundClip: (String) -> Boolean,
    onToggleMultiSelect: (String) -> Unit,
): TimelineClipLongPressResult {
    if (isCompound && onOpenCompoundClip(clipId)) {
        return TimelineClipLongPressResult.OPENED_COMPOUND
    }
    onToggleMultiSelect(clipId)
    return TimelineClipLongPressResult.TOGGLED_MULTI_SELECT
}

internal fun findSnapTarget(positionMs: Long, targets: List<Long>, thresholdMs: Long): Long? {
    return targets.minByOrNull { abs(it - positionMs) }
        ?.takeIf { abs(it - positionMs) <= thresholdMs }
}

internal fun Clip.containsTimelinePosition(positionMs: Long): Boolean {
    return positionMs >= timelineStartMs && positionMs < timelineEndMs
}

internal fun Clip.accessibleSplitPointMs(playheadMs: Long): Long? {
    val earliestSplitMs = timelineStartMs + MIN_TIMELINE_CLIP_DURATION_MS
    val latestSplitMs = timelineEndMs - MIN_TIMELINE_CLIP_DURATION_MS
    if (latestSplitMs < earliestSplitMs) return null
    val preferredSplitMs = if (playheadMs in earliestSplitMs..latestSplitMs) {
        playheadMs
    } else {
        timelineStartMs + durationMs / 2
    }
    return preferredSplitMs.coerceIn(earliestSplitMs, latestSplitMs)
}

internal fun keyboardNudgeAmountMs(isShiftPressed: Boolean): Long =
    if (isShiftPressed) KEYBOARD_COARSE_NUDGE_MS else KEYBOARD_FINE_NUDGE_MS
