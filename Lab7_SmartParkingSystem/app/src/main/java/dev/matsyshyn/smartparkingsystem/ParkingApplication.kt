package dev.matsyshyn.smartparkingsystem

import android.app.Application
import dev.matsyshyn.smartparkingsystem.data.database.AppDatabase
import dev.matsyshyn.smartparkingsystem.data.device.DeviceController
import dev.matsyshyn.smartparkingsystem.data.firebase.FirebaseService
import dev.matsyshyn.smartparkingsystem.data.repository.ParkingRepository
import dev.matsyshyn.smartparkingsystem.data.sensor.SensorDataGenerator

class ParkingApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val firebaseService by lazy { FirebaseService() }
    val deviceController by lazy { DeviceController() }
    val sensorDataGenerator by lazy { SensorDataGenerator() }
    val repository by lazy {
        ParkingRepository(
            database = database,
            firebaseService = firebaseService,
            deviceController = deviceController,
            sensorDataGenerator = sensorDataGenerator,
            context = this,
            useApi = true, // true = використовувати REST API, false = локальний генератор
            useStreaming = false, // true = використовувати SSE streaming, false = polling (запити кожні 5 сек)
            useDeviceApi = true // true = використовувати API для пристроїв, false = локальний контролер
        )
    }
}

