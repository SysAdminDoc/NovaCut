package com.novacut.editor.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.novacut.editor.R
import com.novacut.editor.ui.theme.Mocha
import kotlinx.coroutines.delay

/**
 * Scratchpad for per-project free-form notes. Content is auto-saved ~750ms after the
 * last keystroke to avoid hammering Room on every character. Intentionally minimal —
 * no markdown, no voice input, no timestamp anchors (those can be layered on later).
 */
@Composable
fun ScratchpadSheet(
    initialNotes: String,
    projectName: String,
    onNotesChanged: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Key the remember to the project id via initialNotes so opening a different
    // project reloads its notes instead of sticking to the previous project's state.
    var text by remember(initialNotes) { mutableStateOf(initialNotes) }
    val committed = remember(initialNotes) { mutableStateOf(initialNotes) }

    // Debounced persist: flush after 750ms of quiet typing.
    LaunchedEffect(text) {
        if (text == committed.value) return@LaunchedEffect
        delay(750)
        if (text != committed.value) {
            committed.value = text
            onNotesChanged(text)
        }
    }

    PremiumEditorPanel(
        title = stringResource(R.string.scratchpad_title),
        subtitle = projectName.ifBlank { stringResource(R.string.scratchpad_subtitle_default) },
        icon = Icons.AutoMirrored.Filled.Notes,
        accent = Mocha.Yellow,
        onClose = onClose,
        modifier = modifier,
        closeContentDescription = stringResource(R.string.scratchpad_close_content_description)
    ) {
        Text(
            text = stringResource(R.string.scratchpad_hint),
            color = Mocha.Subtext0,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 360.dp),
            placeholder = {
                Text(
                    text = stringResource(R.string.scratchpad_placeholder),
                    color = Mocha.Subtext0.copy(alpha = 0.7f)
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Mocha.Text,
                unfocusedTextColor = Mocha.Text,
                focusedBorderColor = Mocha.Yellow,
                unfocusedBorderColor = Mocha.CardStroke,
                focusedContainerColor = Mocha.PanelRaised,
                unfocusedContainerColor = Mocha.PanelRaised,
                cursorColor = Mocha.Yellow
            )
        )

        Spacer(Modifier.height(8.dp))
        val status = if (text == committed.value) {
            stringResource(R.string.scratchpad_saved, text.length)
        } else {
            stringResource(R.string.scratchpad_saving)
        }
        Text(
            text = status,
            color = Mocha.Subtext0,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
