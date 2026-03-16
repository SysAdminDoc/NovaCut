package com.novacut.editor.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacut.editor.engine.db.ProjectDao
import com.novacut.editor.model.Project
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectListViewModel @Inject constructor(
    private val projectDao: ProjectDao
) : ViewModel() {

    val projects: StateFlow<List<Project>> = projectDao.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
}
