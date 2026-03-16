package com.novacut.editor.engine.db

import androidx.room.*
import com.novacut.editor.model.Project
import com.novacut.editor.model.AspectRatio
import com.novacut.editor.model.Resolution
import kotlinx.coroutines.flow.Flow

@Database(entities = [Project::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class ProjectDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProject(id: String): Project?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: Project)

    @Update
    suspend fun updateProject(project: Project)

    @Delete
    suspend fun deleteProject(project: Project)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String)
}

class Converters {
    @TypeConverter
    fun fromAspectRatio(value: AspectRatio): String = value.name

    @TypeConverter
    fun toAspectRatio(value: String): AspectRatio = try {
        AspectRatio.valueOf(value)
    } catch (_: IllegalArgumentException) {
        AspectRatio.RATIO_16_9
    }

    @TypeConverter
    fun fromResolution(value: Resolution): String = value.name

    @TypeConverter
    fun toResolution(value: String): Resolution = try {
        Resolution.valueOf(value)
    } catch (_: IllegalArgumentException) {
        Resolution.FHD_1080P
    }
}
