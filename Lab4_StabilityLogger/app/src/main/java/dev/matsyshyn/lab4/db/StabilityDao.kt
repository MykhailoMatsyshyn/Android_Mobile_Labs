package dev.matsyshyn.lab4.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StabilityDao {
    @Insert
    suspend fun insert(record: StabilityRecord)

    // Отримати всі дані (для графіка потім)
    @Query("SELECT * FROM stability_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<StabilityRecord>>

    // Отримати дані за часовим діапазоном (для графіка)
    @Query("SELECT * FROM stability_records WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    fun getRecordsByTimeRange(startTime: Long, endTime: Long): Flow<List<StabilityRecord>>

    // Отримати дані за останні N мілісекунд
    @Query("SELECT * FROM stability_records WHERE timestamp >= :sinceTime ORDER BY timestamp ASC")
    fun getRecordsSince(sinceTime: Long): Flow<List<StabilityRecord>>

    // Видалити старі дані (старші за вказаний час)
    @Query("DELETE FROM stability_records WHERE timestamp < :beforeTime")
    suspend fun deleteOldRecords(beforeTime: Long)

    // Видалити всі дані
    @Query("DELETE FROM stability_records")
    suspend fun deleteAll()

    // Отримати кількість записів
    @Query("SELECT COUNT(*) FROM stability_records")
    suspend fun getRecordCount(): Int

    // Отримати мінімальне значення
    @Query("SELECT MIN(stabilityScore) FROM stability_records WHERE timestamp >= :sinceTime")
    suspend fun getMinStability(sinceTime: Long): Float?

    // Отримати максимальне значення
    @Query("SELECT MAX(stabilityScore) FROM stability_records WHERE timestamp >= :sinceTime")
    suspend fun getMaxStability(sinceTime: Long): Float?

    // Отримати середнє значення
    @Query("SELECT AVG(stabilityScore) FROM stability_records WHERE timestamp >= :sinceTime")
    suspend fun getAvgStability(sinceTime: Long): Double?
}


