package com.novacut.editor.ui.editor

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.model.SaveIndicatorState
import com.novacut.editor.ui.theme.Mocha
import kotlinx.coroutines.delay

@Composable
fun AutoSaveIndicator(
    state: SaveIndicatorState,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        visible = state != SaveIndicatorState.HIDDEN
        if (state == SaveIndicatorState.SAVED) {
            delay(2000L)
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .background(
                    color = Mocha.Surface0.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            when (state) {
                SaveIndicatorState.SAVING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = Mocha.Subtext0
                    )
                    Text(
                        text = "Saving...",
                        fontSize = 10.sp,
                        color = Mocha.Subtext0
                    )
                }

                SaveIndicatorState.SAVED -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Saved",
                        tint = Mocha.Green,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Saved",
                        fontSize = 10.sp,
                        color = Mocha.Green
                    )
                }

                SaveIndicatorState.ERROR -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Save failed",
                        tint = Mocha.Red,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Save failed",
                        fontSize = 10.sp,
                        color = Mocha.Red
                    )
                }

                SaveIndicatorState.HIDDEN -> { /* Nothing */ }
            }
        }
    }
}
