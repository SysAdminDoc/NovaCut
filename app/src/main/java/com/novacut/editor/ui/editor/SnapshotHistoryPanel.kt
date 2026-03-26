package com.novacut.editor.ui.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.model.ProjectSnapshot
import java.text.SimpleDateFormat
import java.util.*

private val Surface0 = Color(0xFF313244)
private val TextColor = Color(0xFFCDD6F4)
private val Subtext = Color(0xFFA6ADC8)
private val Mauve = Color(0xFFCBA6F7)
private val Green = Color(0xFFA6E3A1)
private val Yellow = Color(0xFFF9E2AF)
private val Red = Color(0xFFF38BA8)
private val Crust = Color(0xFF11111B)

@Composable
fun SnapshotHistoryPanel(
    snapshots: List<ProjectSnapshot>,
    onCreateSnapshot: (String) -> Unit,
    onRestoreSnapshot: (String) -> Unit,
    onDeleteSnapshot: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showNameDialog by remember { mutableStateOf(false) }
    var snapshotName by remember { mutableStateOf("") }
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Save Snapshot", color = TextColor) },
            text = {
                OutlinedTextField(
                    value = snapshotName,
                    onValueChange = { snapshotName = it },
                    singleLine = true,
                    placeholder = { Text("Snapshot name...", color = Subtext.copy(alpha = 0.5f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Mauve,
                        unfocusedBorderColor = Subtext.copy(alpha = 0.3f),
                        focusedTextColor = TextColor,
                        unfocusedTextColor = TextColor,
                        cursorColor = Mauve
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val defaultName = "Snapshot ${java.text.SimpleDateFormat("MMM d HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"
                    onCreateSnapshot(snapshotName.ifBlank { defaultName })
                    snapshotName = ""
                    showNameDialog = false
                }) {
                    Text("Save", color = Mauve)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("Cancel", color = Subtext)
                }
            },
            containerColor = Color(0xFF1E1E2E)
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Crust, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Version History", color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row {
                IconButton(onClick = { showNameDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, "New Snapshot", tint = Green, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = Subtext, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (snapshots.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null, tint = Subtext.copy(alpha = 0.3f), modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("No snapshots yet", color = Subtext, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Save your project state to roll back later", color = Subtext.copy(alpha = 0.5f), fontSize = 10.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(snapshots.sortedByDescending { it.timestamp }, key = { it.id }) { snapshot ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface0)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.SaveAlt, null, tint = Yellow, modifier = Modifier.size(20.dp))
                            Column {
                                Text(
                                    snapshot.label.ifEmpty { "Untitled Snapshot" },
                                    color = TextColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    dateFormat.format(Date(snapshot.timestamp)),
                                    color = Subtext,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Restore
                            IconButton(
                                onClick = { onRestoreSnapshot(snapshot.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Restore, "Restore", tint = Green, modifier = Modifier.size(16.dp))
                            }
                            // Delete
                            IconButton(
                                onClick = { onDeleteSnapshot(snapshot.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Delete, "Delete", tint = Red.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
