package com.novacut.editor.ui.editor

import androidx.compose.ui.graphics.Color
import com.novacut.editor.model.TrackType
import com.novacut.editor.ui.theme.Mocha

internal fun trackAccentColor(trackType: TrackType): Color = when (trackType) {
    TrackType.VIDEO -> Mocha.Blue
    TrackType.AUDIO -> Mocha.Green
    TrackType.OVERLAY -> Mocha.Peach
    TrackType.TEXT -> Mocha.Mauve
    TrackType.ADJUSTMENT -> Mocha.Yellow
}
