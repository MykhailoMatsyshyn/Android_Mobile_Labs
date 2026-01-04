package dev.matsyshyn.smartparkingsystem.data.database

import androidx.room.*
import androidx.room.ColumnInfo
import dev.matsyshyn.smartparkingsystem.data.model.DirectionPanelsState
import kotlinx.coroutines.flow.Flow

@Dao
interface DirectionPanelsDao {
    /**
     * Отримати всю історію для конкретного пристрою
     */
    @Query("SELECT * FROM direction_panels_history WHERE device_id = :deviceId ORDER BY timestamp DESC")
    fun getDeviceHistory(deviceId: String): Flow<List<DirectionPanelsState>>
    
    /**
     * Отримати історію з певного часу
     */
    @Query("SELECT * FROM direction_panels_history WHERE device_id = :deviceId AND timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    fun getDeviceHistorySince(deviceId: String, sinceTimestamp: Long): Flow<List<DirectionPanelsState>>
    
    /**
     * Отримати поточний стан (останній запис)
     */
    @Query("SELECT * FROM direction_panels_history WHERE device_id = :deviceId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getCurrentState(deviceId: String): DirectionPanelsState?
    
    /**
     * Отримати несинхронізовані записи
     */
    @Query("SELECT * FROM direction_panels_history WHERE synced = 0")
    suspend fun getUnsyncedHistory(): List<DirectionPanelsState>
    
    /**
     * Вставити новий запис
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(state: DirectionPanelsState): Long
    
    /**
     * Вставити список записів
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryList(states: List<DirectionPanelsState>)
    
    /**
     * Позначити як синхронізовано
     */
    @Query("UPDATE direction_panels_history SET synced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)
    
    /**
     * Отримати статистику роботи
     */
    @Query("""
        SELECT 
            COUNT(*) as total_changes,
            SUM(CASE WHEN enabled = 1 THEN 1 ELSE 0 END) as enabled_count,
            AVG(brightness) as avg_brightness,
            MIN(timestamp) as first_change,
            MAX(timestamp) as last_change
        FROM direction_panels_history 
        WHERE device_id = :deviceId AND timestamp >= :sinceTimestamp
    """)
    suspend fun getStatistics(deviceId: String, sinceTimestamp: Long): DirectionPanelsStatistics?
    
    /**
     * Видалити старі записи
     */
    @Query("DELETE FROM direction_panels_history WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldHistory(beforeTimestamp: Long)
}

data class DirectionPanelsStatistics(
    @ColumnInfo(name = "total_changes")
    val totalChanges: Int,
    
    @ColumnInfo(name = "enabled_count")
    val enabledCount: Int,
    
    @ColumnInfo(name = "avg_brightness")
    val avgBrightness: Float?,
    
    @ColumnInfo(name = "first_change")
    val firstChange: Long?,
    
    @ColumnInfo(name = "last_change")
    val lastChange: Long?
)





