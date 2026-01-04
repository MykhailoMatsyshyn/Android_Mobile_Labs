package dev.matsyshyn.lab4.utils

import android.graphics.Color
import java.util.*

/**
 * Менеджер для призначення унікальних кольорів пристроям
 * 
 * Кожному пристрою (deviceId) призначається свій унікальний колір
 * для зручної ідентифікації на графіку
 */
object DeviceColorManager {
    
    // Палітра кольорів для пристроїв
    private val deviceColors = listOf(
        Color.parseColor("#2196F3"), // Синій
        Color.parseColor("#4CAF50"), // Зелений
        Color.parseColor("#FF9800"), // Помаранчевий
        Color.parseColor("#F44336"), // Червоний
        Color.parseColor("#9C27B0"), // Фіолетовий
        Color.parseColor("#00BCD4"), // Бірюзовий
        Color.parseColor("#FFC107"), // Жовтий
        Color.parseColor("#795548"), // Коричневий
        Color.parseColor("#E91E63"), // Рожевий
        Color.parseColor("#607D8B"), // Сіро-блакитний
        Color.parseColor("#3F51B5"), // Індиго
        Color.parseColor("#009688"), // Бірюзовий темний
        Color.parseColor("#CDDC39"), // Лайм
        Color.parseColor("#FF5722"), // Глибокий помаранчевий
        Color.parseColor("#673AB7")  // Глибокий фіолетовий
    )
    
    // Мапа для збереження призначених кольорів
    private val deviceColorMap = mutableMapOf<String, Int>()
    
    /**
     * Отримати колір для пристрою
     * Якщо пристрій ще не має кольору - призначається новий
     */
    fun getColorForDevice(deviceId: String?): Int {
        if (deviceId == null || deviceId.isEmpty()) {
            return Color.GRAY // Сірий для невідомих пристроїв
        }
        
        // Якщо колір вже призначений - повертаємо його
        if (deviceColorMap.containsKey(deviceId)) {
            return deviceColorMap[deviceId]!!
        }
        
        // Призначаємо новий колір
        val colorIndex = deviceColorMap.size % deviceColors.size
        val color = deviceColors[colorIndex]
        deviceColorMap[deviceId] = color
        
        return color
    }
    
    /**
     * Отримати назву пристрою для відображення
     */
    fun getDeviceDisplayName(deviceId: String?, deviceName: String?): String {
        if (deviceName != null && deviceName.isNotEmpty()) {
            return deviceName
        }
        if (deviceId != null && deviceId.isNotEmpty()) {
            return "Device ${deviceId.takeLast(8)}" // Останні 8 символів ID
        }
        return "Unknown Device"
    }
    
    /**
     * Очистити мапу кольорів (для оновлення)
     */
    fun clear() {
        deviceColorMap.clear()
    }
    
    /**
     * Отримати всі призначені кольори
     */
    fun getAllDeviceColors(): Map<String, Int> {
        return deviceColorMap.toMap()
    }
}

