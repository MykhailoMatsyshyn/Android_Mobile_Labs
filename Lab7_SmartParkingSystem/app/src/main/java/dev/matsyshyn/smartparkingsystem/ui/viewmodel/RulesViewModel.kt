package dev.matsyshyn.smartparkingsystem.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.matsyshyn.smartparkingsystem.ParkingApplication
import dev.matsyshyn.smartparkingsystem.data.model.AutomationRule
import dev.matsyshyn.smartparkingsystem.data.model.ComparisonOperator
import dev.matsyshyn.smartparkingsystem.data.model.DeviceType
import dev.matsyshyn.smartparkingsystem.data.model.SensorType
import dev.matsyshyn.smartparkingsystem.data.repository.ParkingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class RulesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ParkingRepository = (application as ParkingApplication).repository
    
    private val _rules = MutableStateFlow<List<AutomationRule>>(emptyList())
    val rules: StateFlow<List<AutomationRule>> = _rules.asStateFlow()
    
    init {
        loadRules()
        initializeDefaultRules()
    }
    
    fun loadRules() {
        viewModelScope.launch {
            repository.getAllRules().collect { rulesList ->
                _rules.value = rulesList
            }
        }
    }
    
    private fun initializeDefaultRules() {
        viewModelScope.launch {
            val existingRules = repository.getAllRules().first()
            
            // Фіксовані ID для базових правил (щоб уникнути дублювання)
            val defaultRuleIds = listOf(
                "default_rule_free_spots",
                "default_rule_co_level",
                "default_rule_temperature"
            )
            
            // Перевіряємо чи всі базові правила вже існують
            val existingRuleIds = existingRules.map { it.ruleId }.toSet()
            val missingRuleIds = defaultRuleIds.filter { it !in existingRuleIds }
            
            if (missingRuleIds.isNotEmpty()) {
                // Правило 1: Вільних місць < 10 → включити панелі
                if ("default_rule_free_spots" !in existingRuleIds) {
                    val rule1 = AutomationRule(
                        ruleId = "default_rule_free_spots",
                        ruleName = "Мало вільних місць",
                        enabled = true,
                        sensorType = SensorType.FREE_SPOTS,
                        operator = ComparisonOperator.LESS_THAN,
                        threshold = 10f,
                        deviceType = DeviceType.DIRECTION_PANELS,
                        actionEnabled = true,
                        actionBrightness = 80
                    )
                    repository.insertRule(rule1)
                }
                
                // Правило 2: CO > 300 → максимальна вентиляція
                if ("default_rule_co_level" !in existingRuleIds) {
                    val rule2 = AutomationRule(
                        ruleId = "default_rule_co_level",
                        ruleName = "Високий рівень CO",
                        enabled = true,
                        sensorType = SensorType.CO_LEVEL,
                        operator = ComparisonOperator.GREATER_THAN,
                        threshold = 300f,
                        deviceType = DeviceType.VENTILATION,
                        actionEnabled = true,
                        actionFanSpeed = 3
                    )
                    repository.insertRule(rule2)
                }
                
                // Правило 3: T < -5 → включити обігрів
                if ("default_rule_temperature" !in existingRuleIds) {
                    val rule3 = AutomationRule(
                        ruleId = "default_rule_temperature",
                        ruleName = "Низька температура",
                        enabled = true,
                        sensorType = SensorType.TEMPERATURE,
                        operator = ComparisonOperator.LESS_THAN,
                        threshold = -5f,
                        deviceType = DeviceType.HEATING,
                        actionEnabled = true,
                        actionHeatingPower = 2
                    )
                    repository.insertRule(rule3)
                }
            }
        }
    }
    
    fun insertRule(rule: AutomationRule) {
        viewModelScope.launch {
            repository.insertRule(rule)
        }
    }
    
    fun updateRule(rule: AutomationRule) {
        viewModelScope.launch {
            repository.updateRule(rule)
            // Правила оцінюються автоматично в updateRule
        }
    }
    
    fun deleteRule(rule: AutomationRule) {
        viewModelScope.launch {
            repository.deleteRule(rule)
        }
    }
    
    fun toggleRuleEnabled(rule: AutomationRule) {
        viewModelScope.launch {
            repository.updateRule(rule.copy(enabled = !rule.enabled))
        }
    }
}

