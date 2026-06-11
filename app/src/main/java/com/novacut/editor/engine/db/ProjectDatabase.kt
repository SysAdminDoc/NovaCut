package com.novacut.editor.engine.db

import android.util.Log
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.novacut.editor.model.Project
import com.novacut.editor.model.AspectRatio
import com.novacut.editor.model.Resolution
import kotlinx.coroutines.flow.Flow

@Database(entities = [Project::class], version = 7, exportSchema = true)
@TypeConverters(Converters::class)
abstract class ProjectDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN templateId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE projects ADD COLUMN proxyEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {}
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_projects_updatedAt ON projects (updatedAt)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN deletedAtEpochMs INTEGER DEFAULT NULL")
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
    }
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE deletedAtEpochMs IS NULL ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE deletedAtEpochMs IS NULL ORDER BY updatedAt DESC")
    suspend fun getAllProjectsSnapshot(): List<Project>

    @Query("SELECT * FROM projects WHERE deletedAtEpochMs IS NOT NULL ORDER BY deletedAtEpochMs DESC")
    fun getTrashedProjects(): Flow<List<Project>>

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

    @Query("UPDATE projects SET deletedAtEpochMs = :epochMs WHERE id = :id")
    suspend fun softDelete(id: String, epochMs: Long)

    @Query("UPDATE projects SET deletedAtEpochMs = NULL WHERE id = :id")
    suspend fun restoreProject(id: String)

    @Query("DELETE FROM projects WHERE deletedAtEpochMs IS NOT NULL AND deletedAtEpochMs < :cutoffEpochMs")
    suspend fun purgeTrashedOlderThan(cutoffEpochMs: Long): Int
}

class Converters {
    @TypeConverter
    fun fromAspectRatio(value: AspectRatio): String = value.name

    @TypeConverter
    fun toAspectRatio(value: String): AspectRatio = try {
        AspectRatio.valueOf(value)
    } catch (_: IllegalArgumentException) {
        Log.w("Converters", "Unknown aspect ratio '$value', falling back to RATIO_16_9")
        AspectRatio.RATIO_16_9
    }

    @TypeConverter
    fun fromResolution(value: Resolution): String = value.name

    @TypeConverter
    fun toResolution(value: String): Resolution = try {
        Resolution.valueOf(value)
    } catch (_: IllegalArgumentException) {
        Log.w("Converters", "Unknown resolution '$value', falling back to FHD_1080P")
        Resolution.FHD_1080P
    }
}
