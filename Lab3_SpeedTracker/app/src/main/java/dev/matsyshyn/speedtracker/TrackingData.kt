package dev.matsyshyn.speedtracker

import java.io.Serializable

data class TrackingData(
    val timestamp: String = "",
    val speedKmh: Float = 0f,
    val accelMagnitude: Float = 0f,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val intervalSeconds: Int = 10 // Дефолтний інтервал 10 секунд
) : Serializable