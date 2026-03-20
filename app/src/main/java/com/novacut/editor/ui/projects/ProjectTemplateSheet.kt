package com.novacut.editor.ui.projects

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novacut.editor.engine.UserTemplate
import com.novacut.editor.model.*

private val Surface0 = Color(0xFF313244)
private val TextColor = Color(0xFFCDD6F4)
private val Subtext = Color(0xFFA6ADC8)
private val Mauve = Color(0xFFCBA6F7)
private val Peach = Color(0xFFFAB387)
private val Green = Color(0xFFA6E3A1)
private val Blue = Color(0xFF89B4FA)
private val Yellow = Color(0xFFF9E2AF)
private val Red = Color(0xFFF38BA8)
private val Teal = Color(0xFF94E2D5)
private val Crust = Color(0xFF11111B)

data class ProjectTemplateUI(
    val id: String,
    val name: String,
    val description: String,
    val category: TemplateCategory,
    val icon: ImageVector,
    val accentColor: Color,
    val aspectRatio: AspectRatio,
    val tracks: List<TrackType>,
    val suggestedDuration: String
)

val projectTemplates = listOf(
    ProjectTemplateUI(
        id = "blank", name = "Blank Project", description = "Start from scratch",
        category = TemplateCategory.BLANK, icon = Icons.Default.Add, accentColor = Subtext,
        aspectRatio = AspectRatio.RATIO_16_9, tracks = listOf(TrackType.VIDEO, TrackType.AUDIO),
        suggestedDuration = "Any"
    ),
    ProjectTemplateUI(
        id = "vlog", name = "Vlog", description = "Talk to camera with B-roll cutaways",
        category = TemplateCategory.VLOG, icon = Icons.Default.Videocam, accentColor = Mauve,
        aspectRatio = AspectRatio.RATIO_16_9,
        tracks = listOf(TrackType.VIDEO, TrackType.VIDEO, TrackType.AUDIO, TrackType.TEXT),
        suggestedDuration = "5-15 min"
    ),
    ProjectTemplateUI(
        id = "tutorial", name = "Tutorial", description = "Screen recording with voiceover",
        category = TemplateCategory.TUTORIAL, icon = Icons.Default.School, accentColor = Blue,
        aspectRatio = AspectRatio.RATIO_16_9,
        tracks = listOf(TrackType.VIDEO, TrackType.OVERLAY, TrackType.AUDIO, TrackType.TEXT),
        suggestedDuration = "5-30 min"
    ),
    ProjectTemplateUI(
        id = "short_tiktok", name = "Short / TikTok", description = "Vertical short-form content",
        category = TemplateCategory.SHORT_FORM, icon = Icons.Default.PhoneAndroid, accentColor = Red,
        aspectRatio = AspectRatio.RATIO_9_16,
        tracks = listOf(TrackType.VIDEO, TrackType.AUDIO, TrackType.TEXT),
        suggestedDuration = "15-60s"
    ),
    ProjectTemplateUI(
        id = "short_reel", name = "Instagram Reel", description = "Vertical reel with music",
        category = TemplateCategory.SHORT_FORM, icon = Icons.Default.CameraRoll, accentColor = Peach,
        aspectRatio = AspectRatio.RATIO_9_16,
        tracks = listOf(TrackType.VIDEO, TrackType.AUDIO, TrackType.AUDIO, TrackType.TEXT),
        suggestedDuration = "15-90s"
    ),
    ProjectTemplateUI(
        id = "cinematic", name = "Cinematic", description = "Widescreen cinematic look",
        category = TemplateCategory.CINEMATIC, icon = Icons.Default.Movie, accentColor = Yellow,
        aspectRatio = AspectRatio.RATIO_21_9,
        tracks = listOf(TrackType.VIDEO, TrackType.VIDEO, TrackType.AUDIO, TrackType.AUDIO, TrackType.TEXT),
        suggestedDuration = "2-10 min"
    ),
    ProjectTemplateUI(
        id = "slideshow", name = "Slideshow", description = "Photo slideshow with music",
        category = TemplateCategory.SLIDESHOW, icon = Icons.Default.PhotoLibrary, accentColor = Green,
        aspectRatio = AspectRatio.RATIO_16_9,
        tracks = listOf(TrackType.VIDEO, TrackType.AUDIO, TrackType.TEXT),
        suggestedDuration = "1-5 min"
    ),
    ProjectTemplateUI(
        id = "promo", name = "Promo / Ad", description = "Product or service promotion",
        category = TemplateCategory.PROMO, icon = Icons.Default.Campaign, accentColor = Teal,
        aspectRatio = AspectRatio.RATIO_16_9,
        tracks = listOf(TrackType.VIDEO, TrackType.OVERLAY, TrackType.AUDIO, TrackType.TEXT),
        suggestedDuration = "15-60s"
    ),
    ProjectTemplateUI(
        id = "square_social", name = "Square (Social)", description = "1:1 for Instagram/Facebook",
        category = TemplateCategory.PROMO, icon = Icons.Default.CropSquare, accentColor = Blue,
        aspectRatio = AspectRatio.RATIO_1_1,
        tracks = listOf(TrackType.VIDEO, TrackType.AUDIO, TrackType.TEXT),
        suggestedDuration = "15-60s"
    )
)

@Composable
fun ProjectTemplateSheet(
    onTemplateSelected: (ProjectTemplateUI) -> Unit,
    onDismiss: () -> Unit,
    onUserTemplateSelected: (UserTemplate) -> Unit = {},
    onDeleteUserTemplate: (String) -> Unit = {},
    userTemplates: List<UserTemplate> = emptyList(),
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Crust, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("New Project", color = TextColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "Close", tint = Subtext)
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("Choose a template to get started", color = Subtext, fontSize = 13.sp)

        Spacer(Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = if (userTemplates.isEmpty()) 400.dp else 300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(projectTemplates, key = { it.id }) { template ->
                ProjectTemplateCard(
                    template = template,
                    onClick = { onTemplateSelected(template) }
                )
            }
        }

        // User templates section
        if (userTemplates.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("My Templates", color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(userTemplates, key = { it.id }) { ut ->
                    UserTemplateCard(
                        template = ut,
                        onClick = { onUserTemplateSelected(ut) },
                        onDelete = { onDeleteUserTemplate(ut.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun UserTemplateCard(
    template: UserTemplate,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Surface0)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Mauve.copy(alpha = 0.15f), Color.Transparent)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Bookmark,
                template.name,
                tint = Mauve,
                modifier = Modifier.size(24.dp)
            )
        }
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    template.name,
                    color = TextColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.Close,
                    "Delete",
                    tint = Subtext.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(onClick = onDelete)
                )
            }
            Text(
                "${template.trackTypes.size} tracks${if (template.textOverlayCount > 0) ", ${template.textOverlayCount} texts" else ""}",
                color = Subtext,
                fontSize = 9.sp,
                maxLines = 1
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    template.aspectRatio.label,
                    color = Mauve,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .background(Mauve.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun ProjectTemplateCard(
    template: ProjectTemplateUI,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Surface0)
            .clickable(onClick = onClick)
    ) {
        // Header with gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(template.accentColor.copy(alpha = 0.2f), Color.Transparent)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                template.icon,
                template.name,
                tint = template.accentColor,
                modifier = Modifier.size(28.dp)
            )
        }

        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(template.name, color = TextColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(template.description, color = Subtext, fontSize = 10.sp, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    template.aspectRatio.label,
                    color = template.accentColor,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .background(template.accentColor.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
                Text(
                    template.suggestedDuration,
                    color = Subtext,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .background(Surface0, RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
