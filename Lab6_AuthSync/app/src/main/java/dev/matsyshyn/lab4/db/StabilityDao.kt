package dev.matsyshyn.lab4.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StabilityDao {
    @Insert
    suspend fun insert(record: StabilityRecord)
    
    @Update
    suspend fun update(record: StabilityRecord)
    
    @Delete
    suspend fun delete(record: StabilityRecord)

    // Отримати всі дані (для графіка потім)
    @Query("SELECT * FROM stability_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<StabilityRecord>>

    // Отримати дані за останні N мілісекунд
    @Query("SELECT * FROM stability_records WHERE timestamp >= :sinceTime ORDER BY timestamp ASC")
    fun getRecordsSince(sinceTime: Long): Flow<List<StabilityRecord>>

    // Видалити старі дані (старші за вказаний час)
    @Query("DELETE FROM stability_records WHERE timestamp < :beforeTime")
    suspend fun deleteOldRecords(beforeTime: Long)
    
    // Видалити всі дані
    @Query("DELETE FROM stability_records")
    suspend fun deleteAll()
    
    // Отримати несинхронізовані записи
    @Query("SELECT * FROM stability_records WHERE syncedToCloud = 0")
    suspend fun getUnsyncedRecords(): List<StabilityRecord>
}



