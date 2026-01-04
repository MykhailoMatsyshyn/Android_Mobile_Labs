package dev.matsyshyn.smartparkingsystem.data.database

import androidx.room.*
import dev.matsyshyn.smartparkingsystem.data.model.DeviceState
import dev.matsyshyn.smartparkingsystem.data.model.DeviceType
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceStateDao {
    @Query("SELECT * FROM device_state")
    fun getAllDeviceStates(): Flow<List<DeviceState>>
    
    @Query("SELECT * FROM device_state WHERE device_id = :deviceId")
    fun getDeviceState(deviceId: String): Flow<DeviceState?>
    
    @Query("SELECT * FROM device_state WHERE device_type = :deviceType")
    fun getDeviceStateByType(deviceType: DeviceType): Flow<DeviceState?>
    
    @Query("SELECT * FROM device_state WHERE synced = 0")
    suspend fun getUnsyncedDeviceStates(): List<DeviceState>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeviceState(deviceState: DeviceState)
    
    @Update
    suspend fun updateDeviceState(deviceState: DeviceState)
    
    @Query("UPDATE device_state SET synced = 1 WHERE device_id = :deviceId")
    suspend fun markAsSynced(deviceId: String)
    
    @Query("DELETE FROM device_state")
    suspend fun deleteAll()
}





