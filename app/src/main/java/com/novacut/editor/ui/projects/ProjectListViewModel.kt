package com.novacut.editor.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacut.editor.engine.ProjectAutoSave
import com.novacut.editor.engine.db.ProjectDao
import com.novacut.editor.model.Project
import com.novacut.editor.model.SortMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ProjectListViewModel @Inject constructor(
    private val projectDao: ProjectDao,
    private val autoSave: ProjectAutoSave
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

    fun createProject(name: String = "Untitled"): String {
        val project = Project(name = name)
        viewModelScope.launch {
            projectDao.insertProject(project)
        }
        return project.id
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            projectDao.deleteProject(project)
        }
    }

    fun renameProject(project: Project, newName: String) {
        viewModelScope.launch {
            projectDao.updateProject(project.copy(name = newName, updatedAt = System.currentTimeMillis()))
        }
    }

    fun duplicateProject(project: Project) {
        val newId = UUID.randomUUID().toString()
        val newProject = project.copy(
            id = newId,
            name = "${project.name} (Copy)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            projectDao.insertProject(newProject)
            autoSave.copyAutoSave(project.id, newId)
        }
    }
}
