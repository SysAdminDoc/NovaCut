package com.novacut.editor.ui.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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

private val Surface0 = Color(0xFF313244)
private val TextColor = Color(0xFFCDD6F4)
private val Subtext = Color(0xFFA6ADC8)
private val Mauve = Color(0xFFCBA6F7)
private val Green = Color(0xFFA6E3A1)
private val Yellow = Color(0xFFF9E2AF)
private val Red = Color(0xFFF38BA8)
private val Blue = Color(0xFF89B4FA)
private val Crust = Color(0xFF11111B)

@Composable
fun CloudBackupPanel(
    isSignedIn: Boolean,
    lastBackupTime: Long?,
    backupProgress: Float?,
    onSignIn: () -> Unit,
    onBackupNow: () -> Unit,
    onRestore: () -> Unit,
    onAutoBackupToggled: (Boolean) -> Unit,
    autoBackupEnabled: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Cloud, null, tint = Blue, modifier = Modifier.size(20.dp))
                Text("Cloud Backup", color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Subtext, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        if (!isSignedIn) {
            // Sign in prompt
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface0)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudOff, null, tint = Subtext.copy(alpha = 0.4f), modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Sign in with Google to back up your projects", color = Subtext, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onSignIn,
                        colors = ButtonDefaults.buttonColors(containerColor = Blue),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Login, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sign In")
                    }
                }
            }
        } else {
            // Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface0, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = Green, modifier = Modifier.size(14.dp))
                        Text("Connected", color = Green, fontSize = 12.sp)
                    }
                    if (lastBackupTime != null) {
                        Text(
                            "Last backup: ${java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(lastBackupTime))}",
                            color = Subtext,
                            fontSize = 10.sp
                        )
                    }
                }
                if (backupProgress != null) {
                    CircularProgressIndicator(
                        progress = { backupProgress },
                        modifier = Modifier.size(24.dp),
                        color = Blue,
                        strokeWidth = 2.dp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Auto-backup toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface0)
                    .clickable { onAutoBackupToggled(!autoBackupEnabled) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Auto-Backup", color = TextColor, fontSize = 13.sp)
                    Text("Back up after each save", color = Subtext, fontSize = 10.sp)
                }
                Switch(
                    checked = autoBackupEnabled,
                    onCheckedChange = onAutoBackupToggled,
                    colors = SwitchDefaults.colors(checkedTrackColor = Blue)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onBackupNow,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Backup Now", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Blue.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, null, tint = Blue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Restore", color = Blue, fontSize = 12.sp)
                }
            }
        }
    }
}
