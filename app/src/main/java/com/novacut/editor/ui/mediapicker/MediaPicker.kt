package com.novacut.editor.ui.mediapicker

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.novacut.editor.ui.theme.Mocha

@Composable
fun MediaPickerSheet(
    onMediaSelected: (Uri) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            // Take persistent permission
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { }
            onMediaSelected(uri)
        }
    }

    val singlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { }
            onMediaSelected(uri)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 400.dp)
            .background(Mocha.Mantle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Add Media", color = Mocha.Text, fontSize = 18.sp)
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Mocha.Subtext0, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Media type buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MediaTypeButton(
                icon = Icons.Default.Videocam,
                label = "Video",
                color = Mocha.Blue,
                modifier = Modifier.weight(1f)
            ) {
                singlePickerLauncher.launch(arrayOf("video/*"))
            }

            MediaTypeButton(
                icon = Icons.Default.Image,
                label = "Image",
                color = Mocha.Green,
                modifier = Modifier.weight(1f)
            ) {
                singlePickerLauncher.launch(arrayOf("image/*"))
            }

            MediaTypeButton(
                icon = Icons.Default.MusicNote,
                label = "Audio",
                color = Mocha.Peach,
                modifier = Modifier.weight(1f)
            ) {
                singlePickerLauncher.launch(arrayOf("audio/*"))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Multi-select button
        OutlinedButton(
            onClick = {
                videoPickerLauncher.launch(arrayOf("video/*", "image/*"))
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Mocha.Mauve
            ),
            border = BorderStroke(1.dp, Mocha.Surface1)
        ) {
            Icon(Icons.Default.LibraryAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select Multiple Files")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Record option
        OutlinedButton(
            onClick = { /* Camera capture - future */ },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Mocha.Red
            ),
            border = BorderStroke(1.dp, Mocha.Surface1)
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Record Video")
        }
    }
}

@Composable
private fun MediaTypeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, color = color, fontSize = 12.sp)
        }
    }
}
