package dev.matsyshyn.smartparkingsystem.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dev.matsyshyn.smartparkingsystem.data.api.ApiClient
import dev.matsyshyn.smartparkingsystem.data.api.DeviceApiService
import dev.matsyshyn.smartparkingsystem.data.api.DeviceUpdateRequest
import dev.matsyshyn.smartparkingsystem.data.api.SensorDataStream
import dev.matsyshyn.smartparkingsystem.data.database.AppDatabase
import dev.matsyshyn.smartparkingsystem.data.device.DeviceController
import dev.matsyshyn.smartparkingsystem.data.firebase.FirebaseService
import dev.matsyshyn.smartparkingsystem.data.model.AutomationRule
import dev.matsyshyn.smartparkingsystem.data.model.SensorType
import dev.matsyshyn.smartparkingsystem.data.model.DeviceState
import dev.matsyshyn.smartparkingsystem.data.model.DeviceType
import dev.matsyshyn.smartparkingsystem.data.model.DirectionPanelsState
import dev.matsyshyn.smartparkingsystem.data.model.HeatingState
import dev.matsyshyn.smartparkingsystem.data.model.SensorData
import dev.matsyshyn.smartparkingsystem.data.model.VentilationState
import dev.matsyshyn.smartparkingsystem.data.sensor.SensorDataGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

class ParkingRepository constructor(
    private val database: AppDatabase,
    private val firebaseService: FirebaseService,
    private val deviceController: DeviceController,
    private val sensorDataGenerator: SensorDataGenerator,
    private val context: Context,
    private val useApi: Boolean = false, // true = використовувати API, false = локальний генератор
    private val useStreaming: Boolean = false, // true = використовувати SSE streaming, false = polling
    private val useDeviceApi: Boolean = false // true = використовувати API для пристроїв, false = локальний контролер
) {
    private val sensorDataStream = SensorDataStream()
    private val sensorDataDao = database.sensorDataDao()
    private val deviceStateDao = database.deviceStateDao()
    private val automationRuleDao = database.automationRuleDao()
    private val directionPanelsDao = database.directionPanelsDao()
    private val ventilationDao = database.ventilationDao()
    private val heatingDao = database.heatingDao()
    
    // ========== Sensor Data ==========
    
    fun getLatestSensorData(limit: Int = 100): Flow<List<SensorData>> {
        return sensorDataDao.getLatestSensorData(limit)
    }
    
    fun getSensorDataByTimeRange(startTime: Long, endTime: Long): Flow<List<SensorData>> {
        return sensorDataDao.getSensorDataByTimeRange(startTime, endTime)
    }
    
    suspend fun getLatestSensorDataOnce(): SensorData? {
        return sensorDataDao.getLatestSensorDataOnce()
    }
    
    suspend fun insertSensorData(sensorData: SensorData): Long {
        val id = sensorDataDao.insertSensorData(sensorData)
        // Спроба синхронізації, якщо є інтернет
        if (isNetworkAvailable()) {
            syncSensorDataToCloud()
        }
        return id
    }
    
    suspend fun getSensorStatistics(startTime: Long, sensorType: SensorType = SensorType.FREE_SPOTS): SensorStatistics {
        val totalCount = sensorDataDao.getTotalCount()
        val recentData = sensorDataDao.getRecentDataForTrend(startTime)
        
        // Обчислюємо статистику для всіх сенсорів
        val avgFreeSpots = sensorDataDao.getAverageFreeSpots(startTime) ?: 0f
        val medFreeSpots = sensorDataDao.getMedianFreeSpots(startTime)?.toFloat() ?: 0f
        val trendFreeSpots = calculateTrend(recentData) { it.freeSpots.toFloat() }
        
        val avgCoLevel = sensorDataDao.getAverageCoLevel(startTime) ?: 0f
        val medCoLevel = sensorDataDao.getMedianCoLevel(startTime) ?: 0f
        val trendCoLevel = calculateTrend(recentData) { it.coLevel }
        
        val avgNoxLevel = sensorDataDao.getAverageNoxLevel(startTime) ?: 0f
        val medNoxLevel = sensorDataDao.getMedianNoxLevel(startTime) ?: 0f
        val trendNoxLevel = calculateTrend(recentData) { it.noxLevel }
        
        val avgTemperature = sensorDataDao.getAverageTemperature(startTime) ?: 0f
        val medTemperature = sensorDataDao.getMedianTemperature(startTime) ?: 0f
        val trendTemperature = calculateTrend(recentData) { it.temperature }
        
        return SensorStatistics(
            totalCount = totalCount,
            averageFreeSpots = avgFreeSpots,
            medianFreeSpots = medFreeSpots,
            trendFreeSpots = trendFreeSpots,
            averageCoLevel = avgCoLevel,
            medianCoLevel = medCoLevel,
            trendCoLevel = trendCoLevel,
            averageNoxLevel = avgNoxLevel,
            medianNoxLevel = medNoxLevel,
            trendNoxLevel = trendNoxLevel,
            averageTemperature = avgTemperature,
            medianTemperature = medTemperature,
            trendTemperature = trendTemperature
        )
    }
    
    private fun calculateTrend(data: List<SensorData>, valueExtractor: (SensorData) -> Float): Float {
        if (data.size < 2) return 0f
        val firstHalf = data.take(data.size / 2).map(valueExtractor).average().toFloat()
        val secondHalf = data.takeLast(data.size / 2).map(valueExtractor).average().toFloat()
        return secondHalf - firstHalf // Позитивне = зростання, негативне = зниження
    }
    
    // ========== Device States ==========
    
    fun getAllDeviceStates(): Flow<List<DeviceState>> {
        return deviceStateDao.getAllDeviceStates()
    }
    
    fun getDeviceState(deviceId: String): Flow<DeviceState?> {
        return deviceStateDao.getDeviceState(deviceId)
    }
    
    suspend fun updateDeviceState(deviceState: DeviceState) {
        val networkAvailable = isNetworkAvailable()
        android.util.Log.d("ParkingRepository", "updateDeviceState: useDeviceApi=$useDeviceApi, networkAvailable=$networkAvailable, deviceId=${deviceState.deviceId}")
        
        if (useDeviceApi && networkAvailable) {
            // Використовуємо API для оновлення
            try {
                android.util.Log.d("ParkingRepository", "🔄 Роблю PUT запит до /api/devices/${deviceState.deviceId}")
                val request = when (deviceState.deviceType) {
                    DeviceType.DIRECTION_PANELS -> {
                        DeviceUpdateRequest(
                            enabled = deviceState.enabled,
                            brightness = deviceState.brightness
                        )
                    }
                    DeviceType.VENTILATION -> {
                        DeviceUpdateRequest(
                            enabled = deviceState.enabled,
                            fan_speed = deviceState.fanSpeed
                        )
                    }
                    DeviceType.HEATING -> {
                        DeviceUpdateRequest(
                            enabled = deviceState.enabled,
                            heating_power = deviceState.heatingPower
                        )
                    }
                }
                
                val response = ApiClient.deviceApiService.updateDevice(deviceState.deviceId, request)
                android.util.Log.d("ParkingRepository", "✅ Отримано відповідь від API: enabled=${response.enabled}")
                val updatedState = response.toDeviceState()
                
                // Оновлюємо локальний контролер
                deviceController.updateDeviceState(updatedState.deviceId) { updatedState }
                
                // Зберігаємо поточний стан в БД
                deviceStateDao.insertDeviceState(updatedState)
                
                // Зберігаємо історію в відповідну таблицю
                saveDeviceHistory(updatedState)
            } catch (e: Exception) {
                android.util.Log.e("ParkingRepository", "❌ Помилка API запиту: ${e.message}", e)
                // Якщо API недоступний, використовуємо локальний контролер
                android.util.Log.w("ParkingRepository", "Використовую локальний контролер замість API")
                updateDeviceStateLocally(deviceState)
            }
        } else {
            android.util.Log.w("ParkingRepository", "API вимкнено або мережа недоступна. Використовую локальний контролер")
            // Використовуємо локальний контролер
            updateDeviceStateLocally(deviceState)
        }
    }
    
    private suspend fun updateDeviceStateLocally(deviceState: DeviceState) {
        // Оновлюємо в контролері
        deviceController.updateDeviceState(deviceState.deviceId) { deviceState }
        
        // Отримуємо оновлений стан з контролера
        val updatedState = deviceController.getDeviceState(deviceState.deviceId)
        updatedState?.let {
            // Зберігаємо поточний стан в БД
            deviceStateDao.insertDeviceState(it)
            
            // Зберігаємо історію в відповідну таблицю
            saveDeviceHistory(it)
            
            // Синхронізуємо з хмарою
            if (isNetworkAvailable()) {
                syncDeviceStatesToCloud()
            }
        }
    }
    
    /**
     * Зберігає історію зміни стану пристрою в відповідну таблицю
     */
    private suspend fun saveDeviceHistory(deviceState: DeviceState) {
        when (deviceState.deviceType) {
            DeviceType.DIRECTION_PANELS -> {
                // Зберігаємо тільки релевантні поля для панелей
                val history = DirectionPanelsState(
                    deviceId = deviceState.deviceId,
                    enabled = deviceState.enabled,
                    brightness = deviceState.brightness,
                    timestamp = System.currentTimeMillis(),
                    synced = false
                )
                directionPanelsDao.insertHistory(history)
            }
            DeviceType.VENTILATION -> {
                // Зберігаємо тільки релевантні поля для вентиляції
                val history = VentilationState(
                    deviceId = deviceState.deviceId,
                    enabled = deviceState.enabled,
                    fanSpeed = deviceState.fanSpeed,
                    timestamp = System.currentTimeMillis(),
                    synced = false
                )
                ventilationDao.insertHistory(history)
            }
            DeviceType.HEATING -> {
                // Зберігаємо тільки релевантні поля для обігріву
                val history = HeatingState(
                    deviceId = deviceState.deviceId,
                    enabled = deviceState.enabled,
                    heatingPower = deviceState.heatingPower,
                    timestamp = System.currentTimeMillis(),
                    synced = false
                )
                heatingDao.insertHistory(history)
            }
        }
    }
    
    /**
     * Завантажити стани пристроїв з API
     */
    suspend fun fetchDeviceStatesFromApi(): Result<List<DeviceState>> {
        val networkAvailable = isNetworkAvailable()
        android.util.Log.d("ParkingRepository", "fetchDeviceStatesFromApi: useDeviceApi=$useDeviceApi, networkAvailable=$networkAvailable")
        
        return if (useDeviceApi && networkAvailable) {
            try {
                android.util.Log.d("ParkingRepository", "📋 Роблю GET запит до /api/devices...")
                val response = ApiClient.deviceApiService.getAllDevices()
                android.util.Log.d("ParkingRepository", "✅ Отримано ${response.devices.size} пристроїв з API")
                val deviceStates = response.devices.map { it.toDeviceState() }
                
                // Оновлюємо локальний контролер
                deviceStates.forEach { state ->
                    android.util.Log.d("ParkingRepository", "   - ${state.deviceId}: enabled=${state.enabled}, type=${state.deviceType}")
                    deviceController.updateDeviceState(state.deviceId) { state }
                    deviceStateDao.insertDeviceState(state)
                }
                
                Result.success(deviceStates)
            } catch (e: Exception) {
                android.util.Log.e("ParkingRepository", "❌ Помилка API запиту: ${e.message}", e)
                Result.failure(e)
            }
        } else {
            android.util.Log.w("ParkingRepository", "API вимкнено або мережа недоступна. Пропускаю завантаження з API")
            Result.failure(Exception("API not enabled or no network"))
        }
    }
    
    fun getDeviceController(): DeviceController {
        return deviceController
    }
    
    // ========== Automation Rules ==========
    
    fun getAllRules(): Flow<List<AutomationRule>> {
        return automationRuleDao.getAllRules()
    }
    
    fun getEnabledRules(): Flow<List<AutomationRule>> {
        return automationRuleDao.getEnabledRules()
    }
    
    suspend fun insertRule(rule: AutomationRule) {
        automationRuleDao.insertRule(rule)
        if (isNetworkAvailable()) {
            syncRulesToCloud()
        }
    }
    
    suspend fun updateRule(rule: AutomationRule) {
        automationRuleDao.updateRule(rule)
        if (isNetworkAvailable()) {
            syncRulesToCloud()
        }
        // Оцінюємо правила з поточними даними після оновлення
        evaluateRulesWithCurrentData()
    }
    
    /**
     * Оцінює правила автоматизації з останніми даними сенсорів
     */
    suspend fun evaluateRulesWithCurrentData() {
        android.util.Log.d("ParkingRepository", "🔍 Оцінюю правила автоматизації з поточними даними...")
        val latestData = sensorDataDao.getLatestSensorDataOnce()
        if (latestData != null) {
            android.util.Log.d("ParkingRepository", "📊 Останні дані: temp=${latestData.temperature}°C, free_spots=${latestData.freeSpots}, co=${latestData.coLevel}")
            evaluateAutomationRules(latestData)
        } else {
            android.util.Log.w("ParkingRepository", "⚠️ Немає даних сенсорів для оцінки правил")
        }
    }
    
    suspend fun deleteRule(rule: AutomationRule) {
        automationRuleDao.deleteRule(rule)
        if (isNetworkAvailable()) {
            syncRulesToCloud()
        }
    }
    
    // ========== Automation Logic ==========
    
    suspend fun evaluateAutomationRules(sensorData: SensorData) {
        val enabledRules = automationRuleDao.getEnabledRules().first()
        android.util.Log.d("ParkingRepository", "📋 Знайдено ${enabledRules.size} увімкнених правил")
        
        enabledRules.forEach { rule ->
            val shouldTrigger = shouldTriggerRule(rule, sensorData)
            android.util.Log.d("ParkingRepository", "   Правило '${rule.ruleName}': ${if (shouldTrigger) "✅ спрацювало" else "❌ не спрацювало"}")
            if (shouldTrigger) {
                android.util.Log.d("ParkingRepository", "   ⚡ Виконую правило: ${rule.ruleName} → ${rule.deviceType}")
                executeRule(rule)
                automationRuleDao.updateRule(
                    rule.copy(lastTriggered = System.currentTimeMillis())
                )
            }
        }
    }
    
    private fun shouldTriggerRule(rule: AutomationRule, sensorData: SensorData): Boolean {
        val sensorValue = when (rule.sensorType) {
            dev.matsyshyn.smartparkingsystem.data.model.SensorType.FREE_SPOTS -> sensorData.freeSpots.toFloat()
            dev.matsyshyn.smartparkingsystem.data.model.SensorType.CO_LEVEL -> sensorData.coLevel
            dev.matsyshyn.smartparkingsystem.data.model.SensorType.NOX_LEVEL -> sensorData.noxLevel
            dev.matsyshyn.smartparkingsystem.data.model.SensorType.TEMPERATURE -> sensorData.temperature
        }
        
        return when (rule.operator) {
            dev.matsyshyn.smartparkingsystem.data.model.ComparisonOperator.LESS_THAN -> sensorValue < rule.threshold
            dev.matsyshyn.smartparkingsystem.data.model.ComparisonOperator.LESS_OR_EQUAL -> sensorValue <= rule.threshold
            dev.matsyshyn.smartparkingsystem.data.model.ComparisonOperator.GREATER_THAN -> sensorValue > rule.threshold
            dev.matsyshyn.smartparkingsystem.data.model.ComparisonOperator.GREATER_OR_EQUAL -> sensorValue >= rule.threshold
        }
    }
    
    private suspend fun executeRule(rule: AutomationRule) {
        when (rule.deviceType) {
            DeviceType.DIRECTION_PANELS -> {
                deviceController.setDirectionPanelsEnabled(
                    rule.actionEnabled,
                    rule.actionBrightness
                )
            }
            DeviceType.VENTILATION -> {
                deviceController.setVentilationSpeed(
                    rule.actionFanSpeed,
                    rule.actionEnabled
                )
            }
            DeviceType.HEATING -> {
                deviceController.setHeatingEnabled(
                    rule.actionEnabled,
                    rule.actionHeatingPower
                )
            }
        }
        
        // Оновлюємо стан пристрою в БД
        val deviceState = deviceController.getDeviceStateByType(rule.deviceType)
        deviceState?.let {
            // Зберігаємо поточний стан
            deviceStateDao.insertDeviceState(it)
            // Зберігаємо історію
            saveDeviceHistory(it)
        }
    }
    
    // ========== Synchronization ==========
    
    suspend fun syncSensorDataToCloud(): Result<Unit> {
        if (!isNetworkAvailable()) {
            return Result.failure(Exception("No network connection"))
        }
        
        return try {
            val unsyncedData = sensorDataDao.getUnsyncedSensorData()
            android.util.Log.d("ParkingRepository", "📤 Синхронізація: ${unsyncedData.size} несинхронізованих записів")
            
            if (unsyncedData.isNotEmpty()) {
                // Групуємо за timestamp для уникнення дублікатів
                val uniqueData = unsyncedData.distinctBy { it.timestamp }
                android.util.Log.d("ParkingRepository", "📤 Завантажую ${uniqueData.size} унікальних записів (з ${unsyncedData.size} несинхронізованих)")
                
                // Відвантажуємо з synced = true (бо ми саме зараз синхронізуємо)
                val dataToUpload = uniqueData.map { it.copy(synced = true) }
                firebaseService.uploadSensorDataList(dataToUpload).getOrThrow()
                
                uniqueData.forEach { data ->
                    sensorDataDao.markAsSynced(data.id)
                }
                
                android.util.Log.d("ParkingRepository", "✅ Синхронізовано ${uniqueData.size} записів з Firebase")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("ParkingRepository", "❌ Помилка синхронізації: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    suspend fun syncDeviceStatesToCloud(): Result<Unit> {
        if (!isNetworkAvailable()) {
            return Result.failure(Exception("No network connection"))
        }
        
        return try {
            val unsyncedStates = deviceStateDao.getUnsyncedDeviceStates()
            if (unsyncedStates.isNotEmpty()) {
                // Відвантажуємо з synced = true (бо ми саме зараз синхронізуємо)
                val statesToUpload = unsyncedStates.map { it.copy(synced = true) }
                firebaseService.uploadDeviceStateList(statesToUpload).getOrThrow()
                unsyncedStates.forEach { state ->
                    deviceStateDao.markAsSynced(state.deviceId)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun syncRulesToCloud(): Result<Unit> {
        if (!isNetworkAvailable()) {
            return Result.failure(Exception("No network connection"))
        }
        
        return try {
            val unsyncedRules = automationRuleDao.getUnsyncedRules()
            if (unsyncedRules.isNotEmpty()) {
                // Відвантажуємо з synced = true (бо ми саме зараз синхронізуємо)
                val rulesToUpload = unsyncedRules.map { it.copy(synced = true) }
                firebaseService.uploadAutomationRuleList(rulesToUpload).getOrThrow()
                unsyncedRules.forEach { rule ->
                    automationRuleDao.markAsSynced(rule.ruleId)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun downloadFromCloud(): Result<Unit> {
        if (!isNetworkAvailable()) {
            return Result.failure(Exception("No network connection"))
        }
        
        return try {
            android.util.Log.d("ParkingRepository", "📥 Початок завантаження даних з Firebase...")
            
            // Завантажуємо дані сенсорів
            // Отримуємо останній локальний timestamp для оптимізації
            val latestLocal = sensorDataDao.getLatestSensorDataOnce()
            val sinceTimestamp = latestLocal?.timestamp ?: 0L
            
            android.util.Log.d("ParkingRepository", "📊 Останній локальний timestamp: $sinceTimestamp")
            
            // Завантажуємо дані з Firebase (всі або новіші за останній локальний)
            val cloudSensorData = if (sinceTimestamp > 0) {
                firebaseService.downloadSensorData(sinceTimestamp).getOrThrow()
            } else {
                // Якщо немає локальних даних, завантажуємо всі
                firebaseService.downloadSensorData(0).getOrThrow()
            }
            
            android.util.Log.d("ParkingRepository", "📊 Завантажено ${cloudSensorData.size} записів з Firebase")
            
            if (cloudSensorData.isNotEmpty()) {
                // Отримуємо всі локальні timestamp для перевірки дублікатів
                val allLocalData = sensorDataDao.getLatestSensorData(10000).first() // Останні 10000 записів
                val localTimestamps = allLocalData.map { it.timestamp }.toSet()
                
                // Фільтруємо тільки нові дані (яких немає локально)
                val newData = cloudSensorData.filter { it.timestamp !in localTimestamps }
                android.util.Log.d("ParkingRepository", "✨ Знайдено ${newData.size} нових записів для вставки (з ${cloudSensorData.size} завантажених)")
                
                if (newData.isNotEmpty()) {
                    // Додаткова перевірка на дублікати перед вставкою
                    val dataToInsert = newData.filter { data ->
                        val existing = sensorDataDao.getSensorDataByTimestamp(data.timestamp)
                        existing == null
                    }
                    
                    if (dataToInsert.isNotEmpty()) {
                        // Вставляємо тільки нові дані
                        sensorDataDao.insertSensorDataList(dataToInsert.map { it.copy(synced = true) })
                        android.util.Log.d("ParkingRepository", "✅ Вставлено ${dataToInsert.size} нових записів (з ${newData.size} відфільтрованих)")
                    } else {
                        android.util.Log.d("ParkingRepository", "ℹ️ Всі дані вже є в локальній БД")
                    }
                } else {
                    android.util.Log.d("ParkingRepository", "ℹ️ Всі завантажені дані вже є локально")
                }
            }
            
            // Завантажуємо стани пристроїв
            val cloudDeviceStates = firebaseService.downloadDeviceStates().getOrThrow()
            android.util.Log.d("ParkingRepository", "📱 Завантажено ${cloudDeviceStates.size} станів пристроїв")
            cloudDeviceStates.forEach { state ->
                deviceStateDao.insertDeviceState(state.copy(synced = true))
                // Оновлюємо контролер
                deviceController.updateDeviceState(state.deviceId) { state }
            }
            
            // Завантажуємо правила
            val cloudRules = firebaseService.downloadAutomationRules().getOrThrow()
            android.util.Log.d("ParkingRepository", "📋 Завантажено ${cloudRules.size} правил")
            cloudRules.forEach { rule ->
                automationRuleDao.insertRule(rule.copy(synced = true))
            }
            
            android.util.Log.d("ParkingRepository", "✅ Завантаження з Firebase завершено")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("ParkingRepository", "❌ Помилка завантаження з Firebase: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    suspend fun syncAll(): Result<Unit> {
        if (!isNetworkAvailable()) {
            android.util.Log.w("ParkingRepository", "⚠️ Немає інтернету, синхронізація неможлива")
            return Result.failure(Exception("No network connection"))
        }
        
        return try {
            android.util.Log.d("ParkingRepository", "🔄 Початок повної синхронізації...")
            
            // Спочатку відвантажуємо несинхронізовані дані
            android.util.Log.d("ParkingRepository", "📤 Крок 1: Відвантаження несинхронізованих даних...")
            syncSensorDataToCloud()
            syncDeviceStatesToCloud()
            syncRulesToCloud()
            
            // Потім завантажуємо нові дані з хмари
            android.util.Log.d("ParkingRepository", "📥 Крок 2: Завантаження нових даних з хмари...")
            downloadFromCloud()
            
            android.util.Log.d("ParkingRepository", "✅ Повна синхронізація завершена")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("ParkingRepository", "❌ Помилка синхронізації: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    suspend fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    suspend fun checkCloudConnection(): Boolean {
        return try {
            firebaseService.checkConnection()
        } catch (e: Exception) {
            false
        }
    }
    
    // ========== Sensor Data Generator / API ==========
    
    fun getSensorDataGenerator(): SensorDataGenerator {
        return sensorDataGenerator
    }
    
    /**
     * Отримує дані сенсорів з API або локального генератора
     */
    suspend fun fetchSensorDataFromSource(): SensorData {
        val networkAvailable = isNetworkAvailable()
        android.util.Log.d("ParkingRepository", "fetchSensorDataFromSource: useApi=$useApi, networkAvailable=$networkAvailable")
        
        return if (useApi && networkAvailable) {
            try {
                android.util.Log.i("ParkingRepository", "📥 Роблю GET запит до /api/sensor-data...")
                val response = ApiClient.sensorApiService.getSensorData()
                android.util.Log.i("ParkingRepository", "📥 ОТРИМАНО ВІДПОВІДЬ ВІД СЕРВЕРА:")
                android.util.Log.i("ParkingRepository", "   - free_spots: ${response.free_spots}")
                android.util.Log.i("ParkingRepository", "   - co_level: ${response.co_level}")
                android.util.Log.i("ParkingRepository", "   - nox_level: ${response.nox_level}")
                android.util.Log.i("ParkingRepository", "   - temperature: ${response.temperature}")
                android.util.Log.i("ParkingRepository", "   - parking_occupied: ${response.parking_occupied}")
                android.util.Log.i("ParkingRepository", "   - timestamp: ${response.timestamp}")
                val sensorData = response.toSensorData()
                android.util.Log.i("ParkingRepository", "🔄 ПІСЛЯ КОНВЕРТАЦІЇ:")
                android.util.Log.i("ParkingRepository", "   - freeSpots: ${sensorData.freeSpots}")
                android.util.Log.i("ParkingRepository", "   - coLevel: ${sensorData.coLevel}")
                android.util.Log.i("ParkingRepository", "   - noxLevel: ${sensorData.noxLevel}")
                android.util.Log.i("ParkingRepository", "   - temperature: ${sensorData.temperature}")
                android.util.Log.i("ParkingRepository", "   - parkingOccupied: ${sensorData.parkingOccupied}")
                android.util.Log.i("ParkingRepository", "   - timestamp: ${sensorData.timestamp}")
                sensorData
            } catch (e: Exception) {
                android.util.Log.e("ParkingRepository", "Помилка API запиту: ${e.message}", e)
                // Якщо API недоступний, використовуємо локальний генератор
                android.util.Log.w("ParkingRepository", "Використовую локальний генератор замість API")
                sensorDataGenerator.generateNextSensorData()
            }
        } else {
            android.util.Log.w("ParkingRepository", "API вимкнено або мережа недоступна. Використовую локальний генератор")
            // Використовуємо локальний генератор
            sensorDataGenerator.generateNextSensorData()
        }
    }
    
    /**
     * Отримує поток даних сенсорів через SSE streaming
     */
    fun streamSensorDataFromApi(): Flow<SensorData> {
        android.util.Log.d("ParkingRepository", "streamSensorDataFromApi: useApi=$useApi, useStreaming=$useStreaming")
        
        return if (useApi && useStreaming) {
            android.util.Log.i("ParkingRepository", "🌊 Використовую SSE STREAMING для отримання даних")
            android.util.Log.d("ParkingRepository", "Спроба підключитися до SSE stream...")
            sensorDataStream.streamSensorData()
        } else {
            if (!useApi) {
                android.util.Log.w("ParkingRepository", "⚠️ API вимкнено (useApi=false). Використовую локальний генератор")
            } else if (!useStreaming) {
                android.util.Log.w("ParkingRepository", "⚠️ Streaming вимкнено (useStreaming=false). Використовую локальний генератор")
            }
            // Використовуємо локальний генератор
            sensorDataGenerator.generateSensorDataFlow()
        }
    }
    
    fun closeStream() {
        sensorDataStream.close()
    }
    
    // ========== Database Management ==========
    
    /**
     * Очистити всі дані з локальної БД
     */
    suspend fun clearAllLocalData() {
        sensorDataDao.deleteAll()
        deviceStateDao.deleteAll()
        automationRuleDao.deleteAll()
        directionPanelsDao.deleteOldHistory(Long.MAX_VALUE)
        ventilationDao.deleteOldHistory(Long.MAX_VALUE)
        heatingDao.deleteOldHistory(Long.MAX_VALUE)
    }
    
    /**
     * Видалити файл БД (повне очищення)
     */
    suspend fun deleteDatabase() {
        context.deleteDatabase("smart_parking_database")
    }
}

data class SensorStatistics(
    val totalCount: Int,
    // Статистика для вільних місць
    val averageFreeSpots: Float,
    val medianFreeSpots: Float,
    val trendFreeSpots: Float,
    // Статистика для CO
    val averageCoLevel: Float,
    val medianCoLevel: Float,
    val trendCoLevel: Float,
    // Статистика для NOx
    val averageNoxLevel: Float,
    val medianNoxLevel: Float,
    val trendNoxLevel: Float,
    // Статистика для температури
    val averageTemperature: Float,
    val medianTemperature: Float,
    val trendTemperature: Float
)

