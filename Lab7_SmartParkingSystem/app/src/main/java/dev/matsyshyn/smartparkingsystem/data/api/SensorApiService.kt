package dev.matsyshyn.smartparkingsystem.data.api

import dev.matsyshyn.smartparkingsystem.data.model.SensorData
import retrofit2.http.GET

/**
 * REST API інтерфейс для отримання даних сенсорів
 * 
 * Використання:
 * 1. Запустіть sensor_api_server.py на комп'ютері
 * 2. Змініть BASE_URL в ApiClient на IP адресу вашого комп'ютера
 * 3. Переконайтеся, що мобільний пристрій і комп'ютер в одній мережі
 */
interface SensorApiService {
    @GET("api/sensor-data")
    suspend fun getSensorData(): SensorDataResponse
}

data class SensorDataResponse(
    val timestamp: Long,
    val parking_sensors: List<Int>, // Масив з 100 елементів [0,1,0,1,1,...]
    val parking_occupied: Float,
    val free_spots: Int,
    val co_level: Float,
    val nox_level: Float,
    val temperature: Float
) {
    fun toSensorData(): dev.matsyshyn.smartparkingsystem.data.model.SensorData {
        // Використовуємо значення з сервера (не обчислюємо заново!)
        // Сервер вже обчислив free_spots та parking_occupied з parking_sensors
        return dev.matsyshyn.smartparkingsystem.data.model.SensorData(
            timestamp = timestamp,
            parkingSensors = parking_sensors,
            parkingOccupied = parking_occupied,  // Використовуємо з сервера!
            freeSpots = free_spots,  // Використовуємо з сервера!
            coLevel = co_level,
            noxLevel = nox_level,
            temperature = temperature,
            synced = false
        )
    }
}

