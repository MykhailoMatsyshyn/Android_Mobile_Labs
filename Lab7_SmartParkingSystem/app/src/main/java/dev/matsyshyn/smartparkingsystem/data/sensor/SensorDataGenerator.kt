package dev.matsyshyn.smartparkingsystem.data.sensor

import dev.matsyshyn.smartparkingsystem.data.model.SensorData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/**
 * Генератор емульованих даних сенсорів з реалістичною поведінкою
 */
class SensorDataGenerator {
    private val random = Random.Default
    
    // Стан системи для реалістичної поведінки
    private var currentFreeSpots = 50 // Початкова кількість вільних місць
    private var currentCoLevel = 50f // Початковий рівень CO
    private var currentNoxLevel = 30f // Початковий рівень NOx
    private var currentTemperature = 7.5f // Поточна температура (реалістична базова: 5-10°C)
    
    // Тренди та параметри
    private var freeSpotsTrend = 0 // -1 (зменшується), 0 (стабільно), 1 (збільшується)
    private var coTrend = 0f
    private var temperatureTrend = 0f
    
    // Час для циклічних змін
    private var timeCounter = 0L
    
    /**
     * Генерує потік даних сенсорів з інтервалом
     */
    fun generateSensorDataFlow(intervalMs: Long = 5000): Flow<SensorData> = flow {
        while (true) {
            emit(generateNextSensorData())
            kotlinx.coroutines.delay(intervalMs)
        }
    }
    
    /**
     * Генерує наступні дані сенсорів з реалістичною поведінкою
     */
    fun generateNextSensorData(): SensorData {
        timeCounter++
        
        // Оновлюємо тренди кожні 10 хвилин (120 ітерацій при 5 сек інтервалі)
        if (timeCounter % 120 == 0L) {
            updateTrends()
        }
        
        // Генеруємо масив датчиків для кожного місця парковки (100 місць)
        val parkingSensors = generateParkingSensors()
        
        // Обчислюємо похідні значення з масиву датчиків
        val occupiedCount = parkingSensors.sum()
        val freeSpots = 100 - occupiedCount
        val parkingOccupied = (occupiedCount / 100f).coerceIn(0f, 1f)
        
        // Генеруємо якість повітря (залежить від кількості машин)
        val coLevel = generateCoLevel(occupiedCount)
        val noxLevel = generateNoxLevel(occupiedCount)
        
        // Генеруємо температуру (залежить від часу та вентиляції)
        val temperature = generateTemperature(coLevel)
        
        return SensorData(
            timestamp = System.currentTimeMillis(),
            parkingSensors = parkingSensors,
            parkingOccupied = parkingOccupied,
            freeSpots = freeSpots,
            coLevel = coLevel,
            noxLevel = noxLevel,
            temperature = temperature,
            synced = false
        )
    }
    
    /**
     * Генерує масив датчиків для кожного місця парковки (100 місць)
     * Повертає List<Int> де 0 = вільне місце, 1 = зайняте місце
     */
    private fun generateParkingSensors(): List<Int> {
        // Оновлюємо кількість вільних місць з трендом
        val freeSpots = generateFreeSpots()
        val occupiedSpots = 100 - freeSpots
        
        // Створюємо масив з 100 елементів
        val sensors = MutableList(100) { 0 }
        
        // Випадково розподіляємо зайняті місця
        val occupiedIndices = (0..99).shuffled().take(occupiedSpots)
        occupiedIndices.forEach { index ->
            sensors[index] = 1
        }
        
        // Додаємо невеликий шум (можливість зміни стану окремих місць)
        sensors.forEachIndexed { index, value ->
            if (random.nextFloat() < 0.05f) { // 5% шанс зміни стану
                sensors[index] = if (value == 0) 1 else 0
            }
        }
        
        return sensors.toList()
    }
    
    /**
     * Генерує кількість вільних місць з трендом та шумом
     */
    private fun generateFreeSpots(): Int {
        // Змінюємо тренд залежно від поточного стану
        when {
            currentFreeSpots < 10 -> freeSpotsTrend = 1 // Мало місць - тренд на збільшення
            currentFreeSpots > 90 -> freeSpotsTrend = -1 // Багато місць - тренд на зменшення
            else -> {
                // Випадковий тренд
                if (random.nextFloat() < 0.1f) {
                    freeSpotsTrend = random.nextInt(-1, 2)
                }
            }
        }
        
        // Змінюємо значення з трендом
        val change = when (freeSpotsTrend) {
            -1 -> random.nextInt(-3, 1) // Зменшується
            1 -> random.nextInt(0, 4)   // Збільшується
            else -> random.nextInt(-2, 3) // Стабільно
        }
        
        // Додаємо шум
        val noise = random.nextInt(-2, 3)
        currentFreeSpots = (currentFreeSpots + change + noise).coerceIn(0, 100)
        
        return currentFreeSpots
    }
    
    /**
     * Генерує рівень CO залежно від кількості зайнятих місць
     */
    private fun generateCoLevel(occupiedSpots: Int): Float {
        // Базовий рівень залежить від кількості машин
        val baseLevel = (occupiedSpots / 100f) * 200f + 20f
        
        // Оновлюємо тренд
        coTrend += (random.nextFloat() - 0.5f) * 0.1f
        coTrend = coTrend.coerceIn(-2f, 2f)
        
        // Додаємо шум та тренд
        val noise = (random.nextFloat() - 0.5f) * 20f
        val anomaly = if (random.nextFloat() < 0.05f) {
            // 5% шанс аномалії (різкий стрибок)
            random.nextFloat() * 100f - 50f
        } else {
            0f
        }
        
        currentCoLevel = (baseLevel + coTrend + noise + anomaly).coerceIn(0f, 500f)
        return currentCoLevel
    }
    
    /**
     * Генерує рівень NOx залежно від кількості зайнятих місць
     */
    private fun generateNoxLevel(occupiedSpots: Int): Float {
        // NOx зазвичай нижчий за CO
        val baseLevel = (occupiedSpots / 100f) * 150f + 15f
        val noise = (random.nextFloat() - 0.5f) * 15f
        val anomaly = if (random.nextFloat() < 0.03f) {
            random.nextFloat() * 80f - 40f
        } else {
            0f
        }
        
        currentNoxLevel = (baseLevel + noise + anomaly).coerceIn(0f, 500f)
        return currentNoxLevel
    }
    
    /**
     * Генерує температуру з урахуванням часу та якості повітря
     * Реалістичний діапазон: 5-10°C з логічними зв'язками
     */
    private fun generateTemperature(coLevel: Float): Float {
        // Базова температура в реалістичному діапазоні (5-10°C)
        // Невеликі варіації залежно від часу доби
        val dayProgress = (timeCounter % 17280) / 17280f
        val baseTemperature = when {
            dayProgress < 0.25f -> {
                // Ніч (0-6 год): 5-7°C (трохи прохолодніше)
                5f + dayProgress * 8f
            }
            dayProgress < 0.5f -> {
                // Ранок (6-12 год): 7-9°C (поступове потепління)
                7f + (dayProgress - 0.25f) * 8f
            }
            dayProgress < 0.75f -> {
                // День (12-18 год): 9-10°C (найтепліше)
                9f + (dayProgress - 0.5f) * 4f
            }
            else -> {
                // Вечір (18-24 год): 10-6°C (поступове охолодження)
                10f - (dayProgress - 0.75f) * 16f
            }
        }
        
        // Логічний зв'язок: високий CO (багато машин) → трохи підвищує температуру
        // (відпрацьовані гази від двигунів)
        val coEffect = (coLevel / 500f) * 2f // Максимум +2°C при CO=500
        
        // Невеликий шум для реалістичності
        val noise = (random.nextFloat() - 0.5f) * 1.5f // ±0.75°C
        
        // Поступовий тренд (може бути пов'язаний з вентиляцією)
        temperatureTrend += (random.nextFloat() - 0.5f) * 0.02f
        temperatureTrend = temperatureTrend.coerceIn(-0.5f, 0.5f)
        
        // Фінальна температура в реалістичному діапазоні 5-10°C
        currentTemperature = (baseTemperature + coEffect + noise + temperatureTrend).coerceIn(5f, 10f)
        return currentTemperature
    }
    
    /**
     * Оновлює тренди для реалістичної поведінки
     */
    private fun updateTrends() {
        // Випадкові зміни трендів
        if (random.nextFloat() < 0.3f) {
            freeSpotsTrend = random.nextInt(-1, 2)
        }
    }
    
    /**
     * Скидає генератор до початкового стану
     */
    fun reset() {
        currentFreeSpots = 50
        currentCoLevel = 50f
        currentNoxLevel = 30f
        currentTemperature = 7.5f // Реалістична базова температура
        freeSpotsTrend = 0
        coTrend = 0f
        temperatureTrend = 0f
        timeCounter = 0L
    }
}

