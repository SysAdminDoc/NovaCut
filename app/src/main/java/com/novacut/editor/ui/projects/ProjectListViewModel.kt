package com.novacut.editor.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacut.editor.engine.AutoSaveState
import com.novacut.editor.engine.ProjectAutoSave
import com.novacut.editor.engine.TemplateManager
import com.novacut.editor.engine.UserTemplate
import com.novacut.editor.engine.db.ProjectDao
import com.novacut.editor.model.Project
import com.novacut.editor.model.SortMode
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ProjectListViewModel @Inject constructor(
    private val projectDao: ProjectDao,
    private val autoSave: ProjectAutoSave,
    private val templateManager: TemplateManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.DATE_DESC)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val allProjects: StateFlow<List<Project>> = projectDao.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projects: StateFlow<List<Project>> = combine(
        allProjects, _searchQuery, _sortMode
    ) { projects, query, sort ->
        val filtered = if (query.isBlank()) projects
        else projects.filter { it.name.contains(query, ignoreCase = true) }

        when (sort) {
            SortMode.DATE_DESC -> filtered.sortedByDescending { it.updatedAt }
            SortMode.DATE_ASC -> filtered.sortedBy { it.updatedAt }
            SortMode.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            SortMode.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            SortMode.DURATION_DESC -> filtered.sortedByDescending { it.durationMs }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    fun createProject(name: String = "Untitled", onCreated: (String) -> Unit = {}) {
        val project = Project(name = name)
        viewModelScope.launch {
            projectDao.insertProject(project)
            onCreated(project.id)
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            projectDao.deleteProject(project)
            autoSave.clearRecoveryData(project.id)
        }
    }

    fun renameProject(project: Project, newName: String) {
        viewModelScope.launch {
            projectDao.updateProject(project.copy(name = newName, updatedAt = System.currentTimeMillis()))
        }
    }

    fun getUserTemplates(): List<UserTemplate> = templateManager.listTemplates()

    fun deleteUserTemplate(id: String) {
        templateManager.deleteTemplate(id)
    }

    fun createFromTemplate(template: UserTemplate, onCreated: (String) -> Unit) {
        val state = templateManager.loadTemplateState(template) ?: return
        val (tracks, textOverlays) = state
        val project = Project(
            name = template.name,
            aspectRatio = template.aspectRatio,
            frameRate = template.frameRate,
            resolution = template.resolution
        )
        viewModelScope.launch {
            projectDao.insertProject(project)
            // Save the template's tracks/overlays as auto-save state for the new project
            val autoState = AutoSaveState(
                projectId = project.id,
                tracks = tracks.map { track ->
                    // Clear clips from media tracks, keep structure from non-media tracks
                    track.copy(clips = if (track.type == TrackType.VIDEO || track.type == TrackType.AUDIO) emptyList() else track.clips)
                },
                textOverlays = textOverlays
            )
            autoSave.saveNow(project.id, autoState)
            onCreated(project.id)
        }
    }

    fun duplicateProject(project: Project) {
        val newId = UUID.randomUUID().toString()
        val existingNames = allProjects.value.map { it.name }.toSet()
        val baseName = project.name.replace("""\s*\(Copy\s*\d*\)\s*$""".toRegex(), "").trim()
        var copyName = "$baseName (Copy)"
        var counter = 2
        while (copyName in existingNames) {
            copyName = "$baseName (Copy $counter)"
            counter++
        }
        val newProject = project.copy(
            id = newId,
            name = copyName,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            projectDao.insertProject(newProject)
            autoSave.copyAutoSave(project.id, newId)
        }
    }

    fun createProjectFromImport(videoUri: android.net.Uri, onCreated: (String) -> Unit) {
        val fileName = videoUri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Imported"
        val project = Project(
            name = fileName,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            projectDao.insertProject(project)
            // The editor will handle adding the clip when it opens
            onCreated(project.id)
        }
    }
}
