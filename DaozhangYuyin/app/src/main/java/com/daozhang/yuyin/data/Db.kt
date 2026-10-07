package com.daozhang.yuyin.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

object Mode {
    const val AT = "AT"             // 定点播报
    const val INTERVAL = "INTERVAL" // 间隔循环
}

object Status {
    const val RUNNING = "RUNNING"
    const val PAUSED = "PAUSED"
    const val DONE = "DONE"
    const val MISSED = "MISSED"     // 关机等原因错过的定点任务，不补播
}

@Entity(tableName = "task")
data class BroadcastTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** 金额，单位：分 */
    val amountFen: Long,
    /** 创建时生成的完整话术，如“收款十九块九” */
    val speechText: String,
    val mode: String,
    /** 定点任务的触发时间 / 循环任务的首次触发时间（毫秒） */
    val startAt: Long,
    /** 循环间隔（秒），最短 30 */
    val intervalSec: Int = 0,
    /** 循环总次数，0 = 无限 */
    val repeatTotal: Int = 0,
    val repeatDone: Int = 0,
    /** 每次触发连播几遍 1..3 */
    val playTimes: Int = 1,
    /** 静默时段（一天中的分钟数），-1 = 不设 */
    val quietStartMin: Int = -1,
    val quietEndMin: Int = -1,
    val status: String = Status.RUNNING,
    val nextFireAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "play_log")
data class PlayLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 0 = 立即播报（无任务） */
    val taskId: Long,
    val speechText: String,
    val playedAt: Long = System.currentTimeMillis(),
    /** OK / SKIP_QUIET / SKIP_OFF / ERROR */
    val result: String,
    val note: String = "",
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM task ORDER BY CASE status WHEN 'RUNNING' THEN 0 WHEN 'PAUSED' THEN 1 ELSE 2 END, nextFireAt ASC")
    fun observeAll(): Flow<List<BroadcastTask>>

    @Query("SELECT * FROM task WHERE id = :id")
    suspend fun get(id: Long): BroadcastTask?

    @Query("SELECT * FROM task WHERE status = 'RUNNING' ORDER BY nextFireAt ASC")
    suspend fun running(): List<BroadcastTask>

    @Insert
    suspend fun insert(t: BroadcastTask): Long

    @Update
    suspend fun update(t: BroadcastTask)

    @Query("DELETE FROM task WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT id FROM task")
    suspend fun allIds(): List<Long>

    @Query("DELETE FROM task")
    suspend fun deleteAll()

    @Insert
    suspend fun insertLog(l: PlayLog)

    @Query("SELECT COUNT(*) FROM play_log WHERE result = 'OK' AND playedAt >= :since")
    fun observePlayCountSince(since: Long): Flow<Int>

    @Query("SELECT * FROM play_log WHERE taskId = :taskId ORDER BY playedAt DESC LIMIT 50")
    fun observeLogs(taskId: Long): Flow<List<PlayLog>>

    @Query("DELETE FROM play_log WHERE playedAt < :before")
    suspend fun pruneLogs(before: Long)

    @Query("DELETE FROM play_log")
    suspend fun clearLogs()
}

@Database(entities = [BroadcastTask::class, PlayLog::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): TaskDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "daozhang.db")
                .build().also { inst = it }
        }
    }
}
