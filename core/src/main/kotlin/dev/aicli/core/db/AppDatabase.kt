package dev.aicli.core.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Recent/known projects. This is genuinely relational (queried by recency, joined against
 * session history in the UI) so Room earns its place here rather than DataStore.
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val rootKind: String, // "app_workspace" | "external"
    val rootLocator: String, // absolute path, or a content:// tree URI
    val createdAtEpochMillis: Long,
    val lastOpenedAtEpochMillis: Long?,
)

@Dao
interface ProjectDao {
    @Upsert
    suspend fun upsert(project: ProjectEntity)

    // SQLite sorts NULL as the smallest value, so DESC already puts NULLs last without an
    // explicit NULLS LAST clause — which Room 2.6.1's compile-time query validator can't parse.
    @Query("SELECT * FROM projects ORDER BY lastOpenedAtEpochMillis DESC, createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: String): ProjectEntity?
}

/**
 * Persisted terminal session metadata — NOT the live PTY/process (that only exists while the
 * foreground service is alive). On app restart, [lastKnownPid] lets [SessionDao] entries be
 * reconciled against reality: if that pid is no longer alive, the session is marked
 * "ended: runtime terminated by the OS" rather than shown as still running. See ARCHITECTURE.md §8.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val providerId: String?, // null = plain shell session
    val projectId: String?,
    val workingDirectory: String,
    val createdAtEpochMillis: Long,
    val lastKnownPid: Int?,
    val state: String, // "running" | "exited" | "killed_by_os" | "error"
    val exitCode: Int?,
)

@Dao
interface SessionDao {
    @Upsert
    suspend fun upsert(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE state = 'running'")
    suspend fun getRunning(): List<SessionEntity>

    @Query("UPDATE sessions SET state = :state, exitCode = :exitCode WHERE id = :id")
    suspend fun updateState(id: String, state: String, exitCode: Int?)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [ProjectEntity::class, SessionEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "aicli.db",
            ).build().also { instance = it }
        }
    }
}
