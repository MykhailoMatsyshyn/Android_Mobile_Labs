package dev.matsyshyn.lab4.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stability_records")
data class StabilityRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val stabilityScore: Float, // Наше обчислене відхилення
    val x: Float,
    val y: Float,
    val z: Float
)








