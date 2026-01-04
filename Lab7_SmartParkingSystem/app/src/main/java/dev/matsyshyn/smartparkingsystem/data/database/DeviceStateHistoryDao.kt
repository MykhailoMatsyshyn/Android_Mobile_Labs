package dev.matsyshyn.smartparkingsystem.data.database

import androidx.room.*
import androidx.room.ColumnInfo
import dev.matsyshyn.smartparkingsystem.data.model.DeviceStateHistory
import dev.matsyshyn.smartparkingsystem.data.model.DeviceType
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceStateHistoryDao {
    /**
     * Отримати всю історію для конкретного пристрою
     */
    @Query("SELECT * FROM device_state_history WHERE device_id = :deviceId ORDER BY timestamp DESC")
    fun getDeviceHistory(deviceId: String): Flow<List<DeviceStateHistory>>
    
    /**
     * Отримати історію для конкретного пристрою з певного часу
     */
    @Query("SELECT * FROM device_state_history WHERE device_id = :deviceId AND timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    fun getDeviceHistorySince(deviceId: String, sinceTimestamp: Long): Flow<List<DeviceStateHistory>>
    
    /**
     * Отримати поточний стан пристрою (останній запис)
     */
    @Query("SELECT * FROM device_state_history WHERE device_id = :deviceId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getCurrentDeviceState(deviceId: String): DeviceStateHistory?
    
    /**
     * Отримати поточні стани всіх пристроїв
     */
    @Query("""
        SELECT * FROM device_state_history h1
        WHERE h1.timestamp = (
            SELECT MAX(h2.timestamp) 
            FROM device_state_history h2 
            WHERE h2.device_id = h1.device_id
        )
    """)
    fun getCurrentStates(): Flow<List<DeviceStateHistory>>
    
    /**
     * Отримати поточний стан пристрою за типом
     */
    @Query("SELECT * FROM device_state_history WHERE device_type = :deviceType ORDER BY timestamp DESC LIMIT 1")
    suspend fun getCurrentStateByType(deviceType: DeviceType): DeviceStateHistory?
    
    /**
     * Отримати несинхронізовані записи
     */
    @Query("SELECT * FROM device_state_history WHERE synced = 0")
    suspend fun getUnsyncedHistory(): List<DeviceStateHistory>
    
    /**
     * Вставити новий запис в історію
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: DeviceStateHistory): Long
    
    /**
     * Вставити список записів
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryList(historyList: List<DeviceStateHistory>)
    
    /**
     * Позначити як синхронізовано
     */
    @Query("UPDATE device_state_history SET synced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)
    
    /**
     * Позначити кілька записів як синхронізовані
     */
    @Query("UPDATE device_state_history SET synced = 1 WHERE id IN (:ids)")
    suspend fun markAsSyncedList(ids: List<Long>)
    
    /**
     * Отримати статистику роботи пристрою
     */
    @Query("""
        SELECT 
            COUNT(*) as total_changes,
            SUM(CASE WHEN enabled = 1 THEN 1 ELSE 0 END) as enabled_count,
            MIN(timestamp) as first_change,
            MAX(timestamp) as last_change
        FROM device_state_history 
        WHERE device_id = :deviceId AND timestamp >= :sinceTimestamp
    """)
    suspend fun getDeviceStatistics(deviceId: String, sinceTimestamp: Long): DeviceStatistics?
    
    /**
     * Видалити старі записи (старіше ніж вказаний timestamp)
     */
    @Query("DELETE FROM device_state_history WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldHistory(beforeTimestamp: Long)
}

data class DeviceStatistics(
    @ColumnInfo(name = "total_changes")
    val totalChanges: Int,
    
    @ColumnInfo(name = "enabled_count")
    val enabledCount: Int,
    
    @ColumnInfo(name = "first_change")
    val firstChange: Long?,
    
    @ColumnInfo(name = "last_change")
    val lastChange: Long?
)

