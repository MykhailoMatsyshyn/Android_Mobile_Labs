package dev.matsyshyn.smartparkingsystem.ui.rules

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dev.matsyshyn.smartparkingsystem.R
import dev.matsyshyn.smartparkingsystem.databinding.FragmentRulesBinding
import dev.matsyshyn.smartparkingsystem.data.model.AutomationRule
import dev.matsyshyn.smartparkingsystem.data.model.ComparisonOperator
import dev.matsyshyn.smartparkingsystem.data.model.DeviceType
import dev.matsyshyn.smartparkingsystem.data.model.SensorType
import dev.matsyshyn.smartparkingsystem.ui.viewmodel.RulesViewModel
import kotlinx.coroutines.launch
import java.util.UUID

class RulesFragment : Fragment() {
    private var _binding: FragmentRulesBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: RulesViewModel by viewModels()
    private lateinit var adapter: RulesAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRulesBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        adapter = RulesAdapter(
            onToggleEnabled = { rule ->
                viewModel.toggleRuleEnabled(rule)
            },
            onEdit = { rule ->
                showEditRuleDialog(rule)
            },
            onDelete = { rule ->
                showDeleteConfirmDialog(rule)
            }
        )
        
        binding.recyclerRules.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerRules.adapter = adapter
        
        binding.fabAddRule.setOnClickListener {
            showAddRuleDialog()
        }
        
        observeViewModel()
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.rules.collect { rules ->
                adapter.submitList(rules)
            }
        }
    }
    
    private fun showAddRuleDialog() {
        showEditRuleDialog(null)
    }
    
    private fun showEditRuleDialog(rule: AutomationRule?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_rule, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (rule == null) getString(R.string.add_rule) else getString(R.string.edit_rule))
            .setView(dialogView)
            .setPositiveButton(getString(android.R.string.ok)) { _, _ ->
                // Обробка буде в onShow
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        
        dialog.setOnShowListener {
            val nameInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_rule_name)
            val sensorTypeSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.spinner_sensor_type)
            val operatorSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.spinner_operator)
            val thresholdInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_threshold)
            val deviceTypeSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.spinner_device_type)
            val actionEnabledSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_action_enabled)
            val brightnessSlider = dialogView.findViewById<com.google.android.material.slider.Slider>(R.id.slider_brightness)
            val fanSpeedSlider = dialogView.findViewById<com.google.android.material.slider.Slider>(R.id.slider_fan_speed)
            val heatingPowerSlider = dialogView.findViewById<com.google.android.material.slider.Slider>(R.id.slider_heating_power)
            
            // Перевірка на null
            if (nameInput == null || sensorTypeSpinner == null || operatorSpinner == null || 
                thresholdInput == null || deviceTypeSpinner == null || actionEnabledSwitch == null ||
                brightnessSlider == null || fanSpeedSlider == null || heatingPowerSlider == null) {
                android.util.Log.e("RulesFragment", "❌ Не вдалося знайти всі елементи в діалозі")
                dialog.dismiss()
                return@setOnShowListener
            }
            
            // Заповнюємо значення якщо редагуємо
            rule?.let {
                nameInput.setText(it.ruleName)
                thresholdInput.setText(it.threshold.toString())
                actionEnabledSwitch.isChecked = it.actionEnabled
            }
            
            // Налаштування спінерів
            val sensorTypes = SensorType.values().map { it.name }
            val operators = ComparisonOperator.values().map { it.name }
            val deviceTypes = DeviceType.values().map { it.name }
            
            sensorTypeSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sensorTypes).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            operatorSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, operators).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            deviceTypeSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, deviceTypes).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            
            // Функція для оновлення видимості слайдерів
            fun updateSliderVisibility(deviceType: DeviceType) {
                when (deviceType) {
                    DeviceType.DIRECTION_PANELS -> {
                        brightnessSlider.visibility = View.VISIBLE
                        fanSpeedSlider.visibility = View.GONE
                        heatingPowerSlider.visibility = View.GONE
                    }
                    DeviceType.VENTILATION -> {
                        brightnessSlider.visibility = View.GONE
                        fanSpeedSlider.visibility = View.VISIBLE
                        heatingPowerSlider.visibility = View.GONE
                    }
                    DeviceType.HEATING -> {
                        brightnessSlider.visibility = View.GONE
                        fanSpeedSlider.visibility = View.GONE
                        heatingPowerSlider.visibility = View.VISIBLE
                    }
                }
            }
            
            // Обробник зміни типу пристрою
            deviceTypeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selectedDeviceType = DeviceType.valueOf(deviceTypes[position])
                    updateSliderVisibility(selectedDeviceType)
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
            
            // Встановлюємо вибір для спінерів з перевіркою
            rule?.let {
                val sensorIndex = sensorTypes.indexOf(it.sensorType.name)
                if (sensorIndex >= 0) {
                    sensorTypeSpinner.setSelection(sensorIndex)
                }
                
                val operatorIndex = operators.indexOf(it.operator.name)
                if (operatorIndex >= 0) {
                    operatorSpinner.setSelection(operatorIndex)
                }
                
                val deviceIndex = deviceTypes.indexOf(it.deviceType.name)
                if (deviceIndex >= 0) {
                    deviceTypeSpinner.setSelection(deviceIndex)
                    updateSliderVisibility(it.deviceType)
                }
                
                brightnessSlider.value = it.actionBrightness.toFloat().coerceIn(0f, 100f)
                fanSpeedSlider.value = it.actionFanSpeed.toFloat().coerceIn(1f, 3f)
                heatingPowerSlider.value = it.actionHeatingPower.toFloat().coerceIn(1f, 2f)
            } ?: run {
                // Для нового правила показуємо слайдер яскравості за замовчуванням
                updateSliderVisibility(DeviceType.DIRECTION_PANELS)
            }
            
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString()
                val threshold = thresholdInput.text.toString().toFloatOrNull()
                
                if (name.isBlank() || threshold == null) {
                    android.widget.Toast.makeText(requireContext(), "Заповніть всі поля", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                val newRule = AutomationRule(
                    ruleId = rule?.ruleId ?: UUID.randomUUID().toString(),
                    ruleName = name,
                    enabled = rule?.enabled ?: true,
                    sensorType = SensorType.valueOf(sensorTypes[sensorTypeSpinner.selectedItemPosition]),
                    operator = ComparisonOperator.valueOf(operators[operatorSpinner.selectedItemPosition]),
                    threshold = threshold,
                    deviceType = DeviceType.valueOf(deviceTypes[deviceTypeSpinner.selectedItemPosition]),
                    actionEnabled = actionEnabledSwitch.isChecked,
                    actionBrightness = brightnessSlider.value.toInt(),
                    actionFanSpeed = fanSpeedSlider.value.toInt(),
                    actionHeatingPower = heatingPowerSlider.value.toInt()
                )
                
                if (rule == null) {
                    viewModel.insertRule(newRule)
                } else {
                    viewModel.updateRule(newRule)
                }
                
                dialog.dismiss()
            }
        }
        
        dialog.show()
    }
    
    private fun showDeleteConfirmDialog(rule: AutomationRule) {
        AlertDialog.Builder(requireContext())
            .setTitle("Видалити правило?")
            .setMessage("Ви впевнені, що хочете видалити правило \"${rule.ruleName}\"?")
            .setPositiveButton(android.R.string.yes) { _, _ ->
                viewModel.deleteRule(rule)
            }
            .setNegativeButton(android.R.string.no, null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

