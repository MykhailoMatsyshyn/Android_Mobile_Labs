package dev.matsyshyn.smartparkingsystem.data.database

import androidx.room.*
import androidx.room.ColumnInfo
import dev.matsyshyn.smartparkingsystem.data.model.HeatingState
import kotlinx.coroutines.flow.Flow

@Dao
interface HeatingDao {
    /**
     * Отримати всю історію для конкретного пристрою
     */
    @Query("SELECT * FROM heating_history WHERE device_id = :deviceId ORDER BY timestamp DESC")
    fun getDeviceHistory(deviceId: String): Flow<List<HeatingState>>
    
    /**
     * Отримати історію з певного часу
     */
    @Query("SELECT * FROM heating_history WHERE device_id = :deviceId AND timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    fun getDeviceHistorySince(deviceId: String, sinceTimestamp: Long): Flow<List<HeatingState>>
    
    /**
     * Отримати поточний стан (останній запис)
     */
    @Query("SELECT * FROM heating_history WHERE device_id = :deviceId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getCurrentState(deviceId: String): HeatingState?
    
    /**
     * Отримати несинхронізовані записи
     */
    @Query("SELECT * FROM heating_history WHERE synced = 0")
    suspend fun getUnsyncedHistory(): List<HeatingState>
    
    /**
     * Вставити новий запис
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(state: HeatingState): Long
    
    /**
     * Вставити список записів
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryList(states: List<HeatingState>)
    
    /**
     * Позначити як синхронізовано
     */
    @Query("UPDATE heating_history SET synced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)
    
    /**
     * Отримати статистику роботи
     */
    @Query("""
        SELECT 
            COUNT(*) as total_changes,
            SUM(CASE WHEN enabled = 1 THEN 1 ELSE 0 END) as enabled_count,
            AVG(heating_power) as avg_heating_power,
            MIN(timestamp) as first_change,
            MAX(timestamp) as last_change
        FROM heating_history 
        WHERE device_id = :deviceId AND timestamp >= :sinceTimestamp
    """)
    suspend fun getStatistics(deviceId: String, sinceTimestamp: Long): HeatingStatistics?
    
    /**
     * Видалити старі записи
     */
    @Query("DELETE FROM heating_history WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldHistory(beforeTimestamp: Long)
}

data class HeatingStatistics(
    @ColumnInfo(name = "total_changes")
    val totalChanges: Int,
    
    @ColumnInfo(name = "enabled_count")
    val enabledCount: Int,
    
    @ColumnInfo(name = "avg_heating_power")
    val avgHeatingPower: Float?,
    
    @ColumnInfo(name = "first_change")
    val firstChange: Long?,
    
    @ColumnInfo(name = "last_change")
    val lastChange: Long?
)





