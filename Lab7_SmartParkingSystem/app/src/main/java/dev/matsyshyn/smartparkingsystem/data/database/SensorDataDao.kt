package dev.matsyshyn.smartparkingsystem.data.database

import androidx.room.*
import dev.matsyshyn.smartparkingsystem.data.model.SensorData
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorDataDao {
    @Query("SELECT * FROM sensor_data ORDER BY timestamp DESC LIMIT :limit")
    fun getLatestSensorData(limit: Int = 100): Flow<List<SensorData>>
    
    @Query("SELECT * FROM sensor_data WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    fun getSensorDataByTimeRange(startTime: Long, endTime: Long): Flow<List<SensorData>>
    
    @Query("SELECT * FROM sensor_data WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedSensorData(): List<SensorData>
    
    @Query("SELECT COUNT(*) FROM sensor_data")
    suspend fun getTotalCount(): Int
    
    @Query("SELECT AVG(free_spots) FROM sensor_data WHERE timestamp >= :startTime")
    suspend fun getAverageFreeSpots(startTime: Long): Float?
    
    @Query("SELECT AVG(co_level) FROM sensor_data WHERE timestamp >= :startTime")
    suspend fun getAverageCoLevel(startTime: Long): Float?
    
    @Query("SELECT AVG(temperature) FROM sensor_data WHERE timestamp >= :startTime")
    suspend fun getAverageTemperature(startTime: Long): Float?
    
    @Query("SELECT AVG(nox_level) FROM sensor_data WHERE timestamp >= :startTime")
    suspend fun getAverageNoxLevel(startTime: Long): Float?
    
    // Медіана для вільних місць
    @Query("""
        SELECT free_spots FROM sensor_data 
        WHERE timestamp >= :startTime 
        ORDER BY free_spots 
        LIMIT 1 OFFSET (SELECT COUNT(*) / 2 FROM sensor_data WHERE timestamp >= :startTime)
    """)
    suspend fun getMedianFreeSpots(startTime: Long): Int?
    
    // Медіана для CO
    @Query("""
        SELECT co_level FROM sensor_data 
        WHERE timestamp >= :startTime 
        ORDER BY co_level 
        LIMIT 1 OFFSET (SELECT COUNT(*) / 2 FROM sensor_data WHERE timestamp >= :startTime)
    """)
    suspend fun getMedianCoLevel(startTime: Long): Float?
    
    // Медіана для температури
    @Query("""
        SELECT temperature FROM sensor_data 
        WHERE timestamp >= :startTime 
        ORDER BY temperature 
        LIMIT 1 OFFSET (SELECT COUNT(*) / 2 FROM sensor_data WHERE timestamp >= :startTime)
    """)
    suspend fun getMedianTemperature(startTime: Long): Float?
    
    // Медіана для NOx
    @Query("""
        SELECT nox_level FROM sensor_data 
        WHERE timestamp >= :startTime 
        ORDER BY nox_level 
        LIMIT 1 OFFSET (SELECT COUNT(*) / 2 FROM sensor_data WHERE timestamp >= :startTime)
    """)
    suspend fun getMedianNoxLevel(startTime: Long): Float?
    
    // Останні значення для тренду
    @Query("SELECT * FROM sensor_data WHERE timestamp >= :startTime ORDER BY timestamp ASC LIMIT 10")
    suspend fun getRecentDataForTrend(startTime: Long): List<SensorData>
    
    @Query("SELECT * FROM sensor_data ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSensorDataOnce(): SensorData?
    
    @Query("SELECT * FROM sensor_data WHERE timestamp = :timestamp LIMIT 1")
    suspend fun getSensorDataByTimestamp(timestamp: Long): SensorData?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSensorData(sensorData: SensorData): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSensorDataList(sensorDataList: List<SensorData>)
    
    @Update
    suspend fun updateSensorData(sensorData: SensorData)
    
    @Query("UPDATE sensor_data SET synced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)
    
    @Query("DELETE FROM sensor_data WHERE timestamp < :timestamp")
    suspend fun deleteOldData(timestamp: Long)
    
    @Query("DELETE FROM sensor_data")
    suspend fun deleteAll()
}

