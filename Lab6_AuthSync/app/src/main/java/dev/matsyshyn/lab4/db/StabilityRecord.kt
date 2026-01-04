package dev.matsyshyn.lab4.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Модель запису стабільності з гіроскопа
 * 
 * Містить дані з гіроскопа та метаінформацію про пристрій для синхронізації
 */
@Entity(tableName = "stability_records")
data class StabilityRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val stabilityScore: Float, // Наше обчислене відхилення
    val x: Float,
    val y: Float,
    val z: Float,
    
    // Інформація про користувача
    val userId: String? = null, // ID користувача, який зібрав дані (для синхронізації)
    
    // Метаінформація про пристрій
    val deviceId: String? = null, // Унікальний ідентифікатор пристрою (Android ID)
    val deviceName: String? = null, // Назва/модель пристрою (наприклад, "Samsung Galaxy S21")
    val deviceModel: String? = null, // Модель пристрою (наприклад, "SM-G991B")
    val manufacturer: String? = null, // Виробник (наприклад, "Samsung")
    val osVersion: String? = null, // Версія Android (наприклад, "13")
    val osSdkInt: Int? = null, // SDK версія (наприклад, 33)
    
    // Синхронізація з хмарою
    val syncedToCloud: Boolean = false, // Чи завантажено на Firebase
    val cloudId: String? = null, // ID запису в Firebase (для синхронізації)
    val syncedAt: Long? = null, // Час синхронізації
    
    // Обробка дублікатів
    val isDuplicate: Boolean = false, // Чи це дублікат
    val duplicateGroupId: String? = null // ID групи дублікатів (для групування)
)



