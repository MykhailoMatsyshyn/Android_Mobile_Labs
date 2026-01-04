package dev.matsyshyn.smartparkingsystem.ui.devicecontrol

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dev.matsyshyn.smartparkingsystem.R
import dev.matsyshyn.smartparkingsystem.databinding.FragmentDeviceControlBinding
import dev.matsyshyn.smartparkingsystem.data.model.DeviceType
import dev.matsyshyn.smartparkingsystem.ui.viewmodel.DeviceControlViewModel
import kotlinx.coroutines.launch

class DeviceControlFragment : Fragment() {
    private var _binding: FragmentDeviceControlBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: DeviceControlViewModel by viewModels()
    private var isUpdatingFromViewModel = false // Прапорець для запобігання циклічним оновленням
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeviceControlBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    private var rulesEvaluated = false // Прапорець, щоб оцінити правила тільки один раз
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupControls()
        observeViewModel()
        observeLoadingState()
    }
    
    private fun observeLoadingState() {
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                // Показуємо/приховуємо скелетон
                binding.progressDeviceControl.visibility = if (isLoading) View.VISIBLE else View.GONE
                binding.contentDeviceControl.visibility = if (isLoading) View.GONE else View.VISIBLE
                
                // Оцінюємо правила автоматизації після завантаження даних (тільки один раз)
                if (!isLoading && !rulesEvaluated) {
                    rulesEvaluated = true
                    evaluateRules()
                }
            }
        }
    }
    
    private fun evaluateRules() {
        lifecycleScope.launch {
            // Невелика затримка, щоб UI встиг оновитися
            kotlinx.coroutines.delay(300)
            viewModel.evaluateAutomationRules()
        }
    }
    
    private fun setupControls() {
        // Direction Panels
        binding.switchDirectionPanels.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingFromViewModel) {
                val brightness = binding.sliderBrightness.value.toInt()
                viewModel.setDirectionPanelsEnabled(isChecked, brightness)
            }
        }
        
        binding.sliderBrightness.addOnChangeListener { _, value, _ ->
            binding.tvBrightnessValue.text = "${value.toInt()}%"
            if (!isUpdatingFromViewModel && binding.switchDirectionPanels.isChecked) {
                viewModel.setDirectionPanelsEnabled(true, value.toInt())
            }
        }
        
        // Ventilation
        binding.switchVentilation.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingFromViewModel) {
                val speed = binding.sliderFanSpeed.value.toInt()
                viewModel.setVentilationSpeed(speed, isChecked)
            }
        }
        
        binding.sliderFanSpeed.addOnChangeListener { _, value, _ ->
            binding.tvFanSpeedValue.text = value.toInt().toString()
            if (!isUpdatingFromViewModel && binding.switchVentilation.isChecked) {
                viewModel.setVentilationSpeed(value.toInt(), true)
            }
        }
        
        // Heating
        binding.switchHeating.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingFromViewModel) {
                val power = binding.sliderHeatingPower.value.toInt()
                viewModel.setHeatingEnabled(isChecked, power)
            }
        }
        
        binding.sliderHeatingPower.addOnChangeListener { _, value, _ ->
            binding.tvHeatingPowerValue.text = value.toInt().toString()
            if (!isUpdatingFromViewModel && binding.switchHeating.isChecked) {
                viewModel.setHeatingEnabled(true, value.toInt())
            }
        }
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.deviceStates.collect { states ->
                // Оновлюємо UI тільки якщо не завантажується
                if (!viewModel.isLoading.value) {
                    updateDeviceStates(states)
                }
            }
        }
    }
    
    private fun updateDeviceStates(states: List<dev.matsyshyn.smartparkingsystem.data.model.DeviceState>) {
        isUpdatingFromViewModel = true // Відключаємо слухачі під час оновлення
        
        states.forEach { state ->
            when (state.deviceType) {
                DeviceType.DIRECTION_PANELS -> {
                    binding.switchDirectionPanels.isChecked = state.enabled
                    binding.sliderBrightness.value = state.brightness.toFloat()
                    binding.tvBrightnessValue.text = "${state.brightness}%"
                }
                DeviceType.VENTILATION -> {
                    binding.switchVentilation.isChecked = state.enabled
                    binding.sliderFanSpeed.value = state.fanSpeed.toFloat()
                    binding.tvFanSpeedValue.text = state.fanSpeed.toString()
                }
                DeviceType.HEATING -> {
                    binding.switchHeating.isChecked = state.enabled
                    binding.sliderHeatingPower.value = state.heatingPower.toFloat()
                    binding.tvHeatingPowerValue.text = state.heatingPower.toString()
                }
            }
        }
        
        // Повертаємо слухачі після невеликої затримки
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            isUpdatingFromViewModel = false
        }, 100)
    }
    
    override fun onResume() {
        super.onResume()
        // Скидаємо прапорець при поверненні на екран
        rulesEvaluated = false
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

