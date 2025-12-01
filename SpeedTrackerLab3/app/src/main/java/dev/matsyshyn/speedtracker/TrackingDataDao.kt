package dev.matsyshyn.speedtracker

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackingDataDao {
    @Query("SELECT * FROM tracking_data_cache WHERE synced = 0 ORDER BY timestamp ASC")
    fun getUnsyncedData(): Flow<List<TrackingDataEntity>>
    
    @Query("SELECT * FROM tracking_data_cache WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedDataList(): List<TrackingDataEntity>
    
    @Insert
    suspend fun insert(data: TrackingDataEntity): Long
    
    @Update
    suspend fun update(data: TrackingDataEntity)
    
    @Query("UPDATE tracking_data_cache SET synced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)
    
    @Query("DELETE FROM tracking_data_cache WHERE synced = 1")
    suspend fun deleteSyncedData()
}
