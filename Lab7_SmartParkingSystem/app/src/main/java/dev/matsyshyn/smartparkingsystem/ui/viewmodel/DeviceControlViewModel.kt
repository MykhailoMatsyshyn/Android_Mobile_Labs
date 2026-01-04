package dev.matsyshyn.smartparkingsystem.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.matsyshyn.smartparkingsystem.ParkingApplication
import dev.matsyshyn.smartparkingsystem.data.model.DeviceState
import dev.matsyshyn.smartparkingsystem.data.model.DeviceType
import dev.matsyshyn.smartparkingsystem.data.repository.ParkingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceControlViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ParkingRepository = (application as ParkingApplication).repository
    
    private val _deviceStates = MutableStateFlow<List<DeviceState>>(emptyList())
    val deviceStates: StateFlow<List<DeviceState>> = _deviceStates.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    init {
        loadDeviceStates()
    }
    
    fun loadDeviceStates() {
        viewModelScope.launch {
            _isLoading.value = true
            android.util.Log.d("DeviceControlViewModel", "🚀 Завантаження станів пристроїв...")
            
            try {
                // Спочатку завантажуємо з API (якщо увімкнено)
                val result = repository.fetchDeviceStatesFromApi()
                if (result.isSuccess) {
                    android.util.Log.d("DeviceControlViewModel", "✅ Стани завантажено з API")
                } else {
                    android.util.Log.w("DeviceControlViewModel", "⚠️ Не вдалося завантажити з API: ${result.exceptionOrNull()?.message}")
                }
                
                // Чекаємо трохи, щоб API дані встигли зберегтися в БД
                kotlinx.coroutines.delay(100)
                
                // Потім підписуємося на зміни з локальної БД
                repository.getAllDeviceStates().collect { states ->
                    android.util.Log.d("DeviceControlViewModel", "📊 Оновлено стани з БД: ${states.size} пристроїв")
                    _deviceStates.value = states
                    // Після першого оновлення приховуємо завантаження
                    if (_isLoading.value) {
                        _isLoading.value = false
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DeviceControlViewModel", "❌ Помилка завантаження: ${e.message}", e)
                _isLoading.value = false
            }
        }
    }
    
    fun refreshDeviceStates() {
        loadDeviceStates()
    }
    
    fun evaluateAutomationRules() {
        viewModelScope.launch {
            android.util.Log.d("DeviceControlViewModel", "🔍 Оцінюю правила автоматизації...")
            // Чекаємо, поки дані завантажаться
            if (_isLoading.value) {
                kotlinx.coroutines.delay(200)
            }
            repository.evaluateRulesWithCurrentData()
            // Після оцінки правил стани пристроїв можуть змінитися,
            // тому оновлюємо їх (Flow автоматично оновить UI)
            android.util.Log.d("DeviceControlViewModel", "✅ Правила оцінено, стани пристроїв оновляться автоматично")
        }
    }
    
    fun setDirectionPanelsEnabled(enabled: Boolean, brightness: Int = 50) {
        viewModelScope.launch {
            android.util.Log.d("DeviceControlViewModel", "🔄 Зміна стану панелей: enabled=$enabled, brightness=$brightness")
            repository.getDeviceController().setDirectionPanelsEnabled(enabled, brightness)
            val device = repository.getDeviceController().getDeviceStateByType(DeviceType.DIRECTION_PANELS)
            device?.let {
                repository.updateDeviceState(it)
            }
        }
    }
    
    fun setVentilationSpeed(speed: Int, enabled: Boolean = true) {
        viewModelScope.launch {
            android.util.Log.d("DeviceControlViewModel", "🔄 Зміна стану вентиляції: enabled=$enabled, speed=$speed")
            repository.getDeviceController().setVentilationSpeed(speed, enabled)
            val device = repository.getDeviceController().getDeviceStateByType(DeviceType.VENTILATION)
            device?.let {
                repository.updateDeviceState(it)
            }
        }
    }
    
    fun setHeatingEnabled(enabled: Boolean, power: Int = 1) {
        viewModelScope.launch {
            android.util.Log.d("DeviceControlViewModel", "🔄 Зміна стану обігріву: enabled=$enabled, power=$power")
            repository.getDeviceController().setHeatingEnabled(enabled, power)
            val device = repository.getDeviceController().getDeviceStateByType(DeviceType.HEATING)
            device?.let {
                repository.updateDeviceState(it)
            }
        }
    }
}

