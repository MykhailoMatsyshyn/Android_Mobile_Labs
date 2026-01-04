package dev.matsyshyn.smartparkingsystem.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.matsyshyn.smartparkingsystem.ParkingApplication
import dev.matsyshyn.smartparkingsystem.data.model.SensorType
import dev.matsyshyn.smartparkingsystem.data.model.SensorData
import dev.matsyshyn.smartparkingsystem.data.repository.ParkingRepository
import dev.matsyshyn.smartparkingsystem.data.repository.SensorStatistics
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MonitoringViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ParkingRepository = (application as ParkingApplication).repository
    
    private val _sensorData = MutableStateFlow<List<SensorData>>(emptyList())
    val sensorData: StateFlow<List<SensorData>> = _sensorData.asStateFlow()
    
    private val _statistics = MutableStateFlow<SensorStatistics?>(null)
    val statistics: StateFlow<SensorStatistics?> = _statistics.asStateFlow()
    
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private var generationJob: Job? = null
    
    init {
        loadSensorData()
        checkConnection()
        // Автоматична синхронізація при старті
        autoSync()
    }
    
    private fun autoSync() {
        viewModelScope.launch {
            // Невелика затримка, щоб UI встиг завантажитися
            kotlinx.coroutines.delay(1000)
            if (repository.isNetworkAvailable()) {
                android.util.Log.d("MonitoringViewModel", "Автоматична синхронізація...")
                repository.syncAll()
            }
        }
    }
    
    fun loadSensorData(limit: Int = 100) {
        viewModelScope.launch {
            repository.getLatestSensorData(limit).collect { data ->
                _sensorData.value = data
                updateStatistics()
            }
        }
    }
    
    fun loadSensorDataByTimeRange(startTime: Long, endTime: Long) {
        viewModelScope.launch {
            repository.getSensorDataByTimeRange(startTime, endTime).collect { data ->
                _sensorData.value = data
                updateStatistics()
            }
        }
    }
    
    private fun updateStatistics() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000) // Останні 24 години
            // getSensorStatistics тепер обчислює статистику для всіх сенсорів
            val stats = repository.getSensorStatistics(startTime, SensorType.FREE_SPOTS)
            _statistics.value = stats
        }
    }
    
    fun updateStatisticsForSensor(sensorType: SensorType) {
        // Тепер статистика обчислюється для всіх сенсорів, тому просто оновлюємо
        updateStatistics()
    }
    
    fun startGenerating(intervalMs: Long = 5000) {
        if (_isGenerating.value) return
        
        android.util.Log.d("MonitoringViewModel", "🚀 Початок генерації даних...")
        _isGenerating.value = true
        generationJob = viewModelScope.launch {
            // Використовуємо polling (запити кожні 5 секунд)
            android.util.Log.i("MonitoringViewModel", "🔄 Використовую POLLING (запити кожні ${intervalMs}мс)")
            var syncCounter = 0
            while (true) {
                try {
                    android.util.Log.d("MonitoringViewModel", "📥 [POLLING] Роблю GET запит до /api/sensor-data...")
                    val data = repository.fetchSensorDataFromSource()
                    android.util.Log.i("MonitoringViewModel", "✅ [POLLING] Отримано дані: free_spots=${data.freeSpots}, co=${data.coLevel}, temp=${data.temperature}")
                    repository.insertSensorData(data)
                    // Оцінюємо правила автоматизації
                    repository.evaluateAutomationRules(data)
                    
                    // Періодична синхронізація (кожні 12 запитів = ~1 хвилина)
                    syncCounter++
                    if (syncCounter >= 12) {
                        syncCounter = 0
                        if (repository.isNetworkAvailable()) {
                            android.util.Log.d("MonitoringViewModel", "🔄 Періодична синхронізація...")
                            repository.syncSensorDataToCloud()
                        }
                    }
                    
                    kotlinx.coroutines.delay(intervalMs)
                } catch (ex: Exception) {
                    android.util.Log.e("MonitoringViewModel", "❌ Помилка polling: ${ex.message}", ex)
                    kotlinx.coroutines.delay(intervalMs)
                }
            }
        }
    }
    
    fun stopGenerating() {
        generationJob?.cancel()
        generationJob = null
        repository.closeStream()
        _isGenerating.value = false
    }
    
    fun syncWithCloud() {
        viewModelScope.launch {
            repository.syncAll()
            checkConnection()
        }
    }
    
    fun checkConnection() {
        viewModelScope.launch {
            val wasConnected = _isConnected.value
            val isNowConnected = repository.isNetworkAvailable() && repository.checkCloudConnection()
            _isConnected.value = isNowConnected
            
            // Якщо інтернет з'явився (був відсутній, тепер є) - синхронізуємо
            if (!wasConnected && isNowConnected) {
                android.util.Log.d("MonitoringViewModel", "🌐 Інтернет відновлено, синхронізую дані...")
                repository.syncAll()
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopGenerating()
    }
}

