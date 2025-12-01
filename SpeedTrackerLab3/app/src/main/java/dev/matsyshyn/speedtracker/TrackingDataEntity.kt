package dev.matsyshyn.speedtracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracking_data_cache")
data class TrackingDataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: String,
    val speedKmh: Float,
    val accelMagnitude: Float,
    val latitude: Double,
    val longitude: Double,
    val intervalSeconds: Int,
    val synced: Boolean = false // Чи синхронізовано з Firebase
)
