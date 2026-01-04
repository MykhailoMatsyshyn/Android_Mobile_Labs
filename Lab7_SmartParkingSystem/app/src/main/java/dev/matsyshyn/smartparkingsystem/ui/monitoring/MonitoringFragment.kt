package dev.matsyshyn.smartparkingsystem.ui.monitoring

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import dev.matsyshyn.smartparkingsystem.R
import dev.matsyshyn.smartparkingsystem.databinding.FragmentMonitoringBinding
import dev.matsyshyn.smartparkingsystem.data.model.SensorType
import dev.matsyshyn.smartparkingsystem.ui.viewmodel.MonitoringViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MonitoringFragment : Fragment() {
    private var _binding: FragmentMonitoringBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: MonitoringViewModel by viewModels()
    
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var selectedSensorType = SensorType.FREE_SPOTS
    private var selectedTimeRangeMinutes = 60 // 60 = 1 година (в хвилинах)
    
    private lateinit var parkingAdapter: ParkingSpotAdapter
    private lateinit var sensorDataListAdapter: SensorDataListAdapter
    private var isDataListExpanded = false
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMonitoringBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupParkingRecycler()
        setupChart()
        setupChips()
        setupButtons()
        setupDataList()
        observeViewModel()
        
        // Автоматична синхронізація при відкритті екрану
        lifecycleScope.launch {
            kotlinx.coroutines.delay(500) // Невелика затримка
            viewModel.syncWithCloud()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Синхронізація при поверненні на екран
        lifecycleScope.launch {
            viewModel.checkConnection()
            if (viewModel.isConnected.value) {
                viewModel.syncWithCloud()
            }
        }
    }
    
    private fun setupParkingRecycler() {
        parkingAdapter = ParkingSpotAdapter()
        binding.recyclerParking.layoutManager = GridLayoutManager(context, 10) // 10 колонок
        binding.recyclerParking.adapter = parkingAdapter
    }
    
    private fun setupChart() {
        binding.chart.description.isEnabled = false
        binding.chart.setTouchEnabled(true)
        binding.chart.setDragEnabled(true)
        binding.chart.setScaleEnabled(true)
        binding.chart.setPinchZoom(true)
        
        // Налаштування легенди
        val legend = binding.chart.legend
        legend.isEnabled = true
        legend.textColor = Color.parseColor("#212121") // Темний колір для легенди
        legend.textSize = 12f
        legend.formSize = 12f
        legend.form = com.github.mikephil.charting.components.Legend.LegendForm.LINE
        
        binding.chart.setNoDataText("Немає даних")
        binding.chart.setNoDataTextColor(Color.GRAY)
        
        val xAxis = binding.chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.textColor = Color.parseColor("#757575")
        xAxis.textSize = 10f
        
        val leftAxis = binding.chart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.parseColor("#E0E0E0")
        leftAxis.textColor = Color.parseColor("#757575")
        leftAxis.textSize = 10f
        
        val rightAxis = binding.chart.axisRight
        rightAxis.isEnabled = false
    }
    
    private fun setupChips() {
        // Sensor type chips - single selection mode
        binding.chipGroupSensors.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) {
                // Не дозволяємо зняти вибір - повертаємо попередній вибір
                binding.chipGroupSensors.check(
                    when (selectedSensorType) {
                        SensorType.FREE_SPOTS -> R.id.chip_free_spots
                        SensorType.CO_LEVEL -> R.id.chip_co
                        SensorType.NOX_LEVEL -> R.id.chip_nox
                        SensorType.TEMPERATURE -> R.id.chip_temperature
                        else -> R.id.chip_free_spots
                    }
                )
                return@setOnCheckedStateChangeListener
            }
            
            when {
                checkedIds.contains(R.id.chip_free_spots) -> {
                    selectedSensorType = SensorType.FREE_SPOTS
                    updateChartAndStatistics()
                }
                checkedIds.contains(R.id.chip_co) -> {
                    selectedSensorType = SensorType.CO_LEVEL
                    updateChartAndStatistics()
                }
                checkedIds.contains(R.id.chip_nox) -> {
                    selectedSensorType = SensorType.NOX_LEVEL
                    updateChartAndStatistics()
                }
                checkedIds.contains(R.id.chip_temperature) -> {
                    selectedSensorType = SensorType.TEMPERATURE
                    updateChartAndStatistics()
                }
            }
        }
        
        // Time range chips - single selection mode
        binding.chipGroupTime.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) {
                // Не дозволяємо зняти вибір - повертаємо попередній вибір
                binding.chipGroupTime.check(
                    when (selectedTimeRangeMinutes) {
                        5 -> R.id.chip_5m
                        30 -> R.id.chip_30m
                        60 -> R.id.chip_1h
                        360 -> R.id.chip_6h
                        1440 -> R.id.chip_24h
                        10080 -> R.id.chip_week
                        else -> R.id.chip_1h
                    }
                )
                return@setOnCheckedStateChangeListener
            }
            
            when {
                checkedIds.contains(R.id.chip_5m) -> {
                    selectedTimeRangeMinutes = 5
                    loadDataForTimeRange()
                }
                checkedIds.contains(R.id.chip_30m) -> {
                    selectedTimeRangeMinutes = 30
                    loadDataForTimeRange()
                }
                checkedIds.contains(R.id.chip_1h) -> {
                    selectedTimeRangeMinutes = 60
                    loadDataForTimeRange()
                }
                checkedIds.contains(R.id.chip_6h) -> {
                    selectedTimeRangeMinutes = 360 // 6 * 60
                    loadDataForTimeRange()
                }
                checkedIds.contains(R.id.chip_24h) -> {
                    selectedTimeRangeMinutes = 1440 // 24 * 60
                    loadDataForTimeRange()
                }
                checkedIds.contains(R.id.chip_week) -> {
                    selectedTimeRangeMinutes = 10080 // 7 * 24 * 60
                    loadDataForTimeRange()
                }
            }
        }
    }
    
    private fun setupButtons() {
        binding.btnStartGeneration.setOnClickListener {
            viewModel.startGenerating()
        }
        
        binding.btnStopGeneration.setOnClickListener {
            viewModel.stopGenerating()
        }
        
        binding.btnSync.setOnClickListener {
            viewModel.syncWithCloud()
        }
        
        binding.btnFullscreenChart.setOnClickListener {
            openFullscreenChart()
        }
    }
    
    private fun setupDataList() {
        sensorDataListAdapter = SensorDataListAdapter()
        binding.recyclerSensorData.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        binding.recyclerSensorData.adapter = sensorDataListAdapter
        
        // Спочатку список прихований
        binding.recyclerSensorData.visibility = View.GONE
        
        binding.headerDataList.setOnClickListener {
            android.util.Log.d("MonitoringFragment", "Клік по header_data_list, isDataListExpanded: $isDataListExpanded")
            isDataListExpanded = !isDataListExpanded
            if (isDataListExpanded) {
                android.util.Log.d("MonitoringFragment", "Розгортаю список даних")
                binding.recyclerSensorData.visibility = View.VISIBLE
                binding.iconExpandDataList.animate().rotation(180f).setDuration(200).start()
            } else {
                android.util.Log.d("MonitoringFragment", "Згортаю список даних")
                binding.recyclerSensorData.visibility = View.GONE
                binding.iconExpandDataList.animate().rotation(0f).setDuration(200).start()
            }
        }
        
        android.util.Log.d("MonitoringFragment", "setupDataList завершено, headerDataList: ${binding.headerDataList != null}, recyclerSensorData: ${binding.recyclerSensorData != null}")
    }
    
    private fun openFullscreenChart() {
        val currentData = viewModel.sensorData.value
        if (currentData.isEmpty()) {
            return
        }
        
        val label = when (selectedSensorType) {
            SensorType.FREE_SPOTS -> getString(R.string.free_spots)
            SensorType.CO_LEVEL -> getString(R.string.co_level)
            SensorType.NOX_LEVEL -> getString(R.string.nox_level)
            SensorType.TEMPERATURE -> getString(R.string.temperature)
            else -> getString(R.string.free_spots)
        }
        
        val intent = android.content.Intent(context, FullscreenChartActivity::class.java).apply {
            // Конвертуємо List в ArrayList для Serializable
            putExtra("sensor_data", ArrayList(currentData.map { it.toSerializable() }))
            putExtra("sensor_type", selectedSensorType.name)
            putExtra("label", label)
        }
        startActivity(intent)
    }
    
    private fun loadDataForTimeRange() {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (selectedTimeRangeMinutes * 60 * 1000L) // Конвертуємо хвилини в мілісекунди
        viewModel.loadSensorDataByTimeRange(startTime, endTime)
        viewModel.updateStatisticsForSensor(selectedSensorType)
    }
    
    private fun updateChartAndStatistics() {
        viewModel.updateStatisticsForSensor(selectedSensorType)
        // Графік оновлюється автоматично через observeViewModel
        // Але ми можемо примусово оновити його з поточними даними
        lifecycleScope.launch {
            val currentData = viewModel.sensorData.value
            if (currentData.isNotEmpty()) {
                updateChart(currentData)
            }
        }
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.sensorData.collect { data ->
                updateChart(data)
                updateCurrentValues(data.firstOrNull())
                // Оновлюємо список даних (нові зверху)
                // Сортуємо за timestamp DESC (новіші зверху)
                val sortedData = data.sortedByDescending { it.timestamp }
                sensorDataListAdapter.submitList(sortedData)
            }
        }
        
        lifecycleScope.launch {
            viewModel.statistics.collect { stats ->
                stats?.let { updateStatistics(it) }
            }
        }
        
        // Update statistics when sensor type changes
        updateChartAndStatistics()
        
        lifecycleScope.launch {
            viewModel.isGenerating.collect { isGenerating ->
                binding.btnStartGeneration.isEnabled = !isGenerating
                binding.btnStopGeneration.isEnabled = isGenerating
            }
        }
        
        lifecycleScope.launch {
            viewModel.isConnected.collect { isConnected ->
                binding.connectionStatus.text = if (isConnected) {
                    getString(R.string.connected)
                } else {
                    getString(R.string.disconnected)
                }
                binding.connectionStatus.setTextColor(
                    if (isConnected) Color.GREEN else Color.RED
                )
            }
        }
    }
    
    private fun updateChart(data: List<dev.matsyshyn.smartparkingsystem.data.model.SensorData>) {
        if (data.isEmpty()) {
            binding.chart.clear()
            binding.chart.invalidate()
            return
        }
        
        // Сортуємо дані за часом
        val sortedData = data.sortedBy { it.timestamp }
        
        // Знаходимо мінімальний timestamp для нормалізації
        val minTimestamp = sortedData.first().timestamp
        val maxTimestamp = sortedData.last().timestamp
        val timeRange = maxTimestamp - minTimestamp
        
        val entries = mutableListOf<Entry>()
        val (label, color) = when (selectedSensorType) {
            SensorType.FREE_SPOTS -> Pair(
                getString(R.string.free_spots),
                Color.parseColor("#2196F3")
            )
            SensorType.CO_LEVEL -> Pair(
                getString(R.string.co_level),
                Color.parseColor("#FF5722")
            )
            SensorType.NOX_LEVEL -> Pair(
                getString(R.string.nox_level),
                Color.parseColor("#9C27B0")
            )
            SensorType.TEMPERATURE -> Pair(
                getString(R.string.temperature),
                Color.parseColor("#4CAF50")
            )
            else -> Pair(
                getString(R.string.free_spots),
                Color.parseColor("#2196F3")
            )
        }
        
        // Використовуємо індекс як X координату для кращого відображення
        sortedData.forEachIndexed { index, sensorData ->
            val x = index.toFloat()
            val y = when (selectedSensorType) {
                SensorType.FREE_SPOTS -> sensorData.freeSpots.toFloat()
                SensorType.CO_LEVEL -> sensorData.coLevel
                SensorType.NOX_LEVEL -> sensorData.noxLevel
                SensorType.TEMPERATURE -> sensorData.temperature
                else -> sensorData.freeSpots.toFloat()
            }
            entries.add(Entry(x, y))
        }
        
        val dataSet = LineDataSet(entries, label).apply {
            this.color = color
            setCircleColor(color)
            lineWidth = 3f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = color
            fillAlpha = 50
        }
        
        // Створюємо LineData з ОДНИМ dataset (тільки для вибраного датчика)
        val lineData = LineData(dataSet)
        binding.chart.data = lineData
        
        // Оновлюємо легенду з правильним кольором
        val legend = binding.chart.legend
        legend.textColor = Color.parseColor("#212121")
        
        // Налаштування осей
        val leftAxis = binding.chart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.parseColor("#E0E0E0")
        leftAxis.textColor = Color.parseColor("#757575")
        
        // Автоматичне масштабування для Y осі
        binding.chart.axisLeft.resetAxisMinimum()
        binding.chart.axisLeft.resetAxisMaximum()
        
        // Налаштування X осі для відображення часу
        val xAxis = binding.chart.xAxis
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                if (index >= 0 && index < sortedData.size) {
                    return dateFormat.format(Date(sortedData[index].timestamp))
                }
                return ""
            }
        }
        xAxis.setLabelCount(minOf(5, sortedData.size), true)
        xAxis.textColor = Color.parseColor("#757575")
        
        // Очищаємо всі попередні дані та встановлюємо тільки один dataset
        binding.chart.clear()
        binding.chart.data = lineData
        binding.chart.invalidate()
        binding.chart.notifyDataSetChanged()
        binding.chart.animateX(500)
    }
    
    private fun updateCurrentValues(data: dev.matsyshyn.smartparkingsystem.data.model.SensorData?) {
        data?.let {
            // Parking visualization
            try {
                binding.tvCurrentFreeSpots?.text = "Вільні місця: ${it.freeSpots}/100"
                binding.recyclerParking?.let { recycler ->
                    parkingAdapter.updateSpots(it.parkingSensors)
                }
            } catch (e: Exception) {
                // Old layout
                binding.tvCurrentFreeSpots?.text = getString(R.string.free_spots) + ": ${it.freeSpots}"
            }
            
            // Temperature
            try {
                binding.thermometerView?.setTemperature(it.temperature)
            } catch (e: Exception) {
                // Old layout
            }
            binding.tvCurrentTemperature?.text = "${String.format("%.1f", it.temperature)}°C"
            
            // Air quality
            binding.tvCurrentCo?.text = "${String.format("%.1f", it.coLevel)} ppm"
            try {
                binding.progressCo?.progress = it.coLevel.toInt().coerceIn(0, 500)
                val coColor = when {
                    it.coLevel > 300 -> Color.parseColor("#D32F2F")
                    it.coLevel > 150 -> Color.parseColor("#FF9800")
                    else -> Color.parseColor("#4CAF50")
                }
                binding.progressCo?.progressTintList = android.content.res.ColorStateList.valueOf(coColor)
            } catch (e: Exception) {
                // Old layout
            }
            
            binding.tvCurrentNox?.text = "${String.format("%.1f", it.noxLevel)} ppm"
            try {
                binding.progressNox?.progress = it.noxLevel.toInt().coerceIn(0, 500)
                val noxColor = when {
                    it.noxLevel > 300 -> Color.parseColor("#D32F2F")
                    it.noxLevel > 150 -> Color.parseColor("#FF9800")
                    else -> Color.parseColor("#9C27B0")
                }
                binding.progressNox?.progressTintList = android.content.res.ColorStateList.valueOf(noxColor)
            } catch (e: Exception) {
                // Old layout
            }
        }
    }
    
    private fun updateStatistics(stats: dev.matsyshyn.smartparkingsystem.data.repository.SensorStatistics) {
        binding.tvTotalRecords?.text = stats.totalCount.toString()
        
        // Вільні місця
        binding.tvAvgFreeSpots?.text = String.format("%.0f", stats.averageFreeSpots)
        binding.tvMedianFreeSpots?.text = String.format("%.0f", stats.medianFreeSpots)
        val trendFreeSpotsText = when {
            stats.trendFreeSpots > 0.1f -> "↑ Зростання"
            stats.trendFreeSpots < -0.1f -> "↓ Зниження"
            else -> "→ Стабільно"
        }
        binding.tvTrendFreeSpots?.text = trendFreeSpotsText
        binding.tvTrendFreeSpots?.setTextColor(
            when {
                stats.trendFreeSpots > 0.1f -> Color.parseColor("#4CAF50")
                stats.trendFreeSpots < -0.1f -> Color.parseColor("#FF5722")
                else -> Color.GRAY
            }
        )
        
        // CO
        binding.tvAvgCo?.text = "${String.format("%.1f", stats.averageCoLevel)} ppm"
        binding.tvMedianCo?.text = "${String.format("%.1f", stats.medianCoLevel)} ppm"
        val trendCoText = when {
            stats.trendCoLevel > 0.1f -> "↑ Зростання"
            stats.trendCoLevel < -0.1f -> "↓ Зниження"
            else -> "→ Стабільно"
        }
        binding.tvTrendCo?.text = trendCoText
        binding.tvTrendCo?.setTextColor(
            when {
                stats.trendCoLevel > 0.1f -> Color.parseColor("#FF5722")
                stats.trendCoLevel < -0.1f -> Color.parseColor("#4CAF50")
                else -> Color.GRAY
            }
        )
        
        // NOx
        binding.tvAvgNox?.text = "${String.format("%.1f", stats.averageNoxLevel)} ppm"
        binding.tvMedianNox?.text = "${String.format("%.1f", stats.medianNoxLevel)} ppm"
        val trendNoxText = when {
            stats.trendNoxLevel > 0.1f -> "↑ Зростання"
            stats.trendNoxLevel < -0.1f -> "↓ Зниження"
            else -> "→ Стабільно"
        }
        binding.tvTrendNox?.text = trendNoxText
        binding.tvTrendNox?.setTextColor(
            when {
                stats.trendNoxLevel > 0.1f -> Color.parseColor("#9C27B0")
                stats.trendNoxLevel < -0.1f -> Color.parseColor("#4CAF50")
                else -> Color.GRAY
            }
        )
        
        // Температура
        binding.tvAvgTemperature?.text = "${String.format("%.1f", stats.averageTemperature)}°C"
        binding.tvMedianTemperature?.text = "${String.format("%.1f", stats.medianTemperature)}°C"
        val trendTempText = when {
            stats.trendTemperature > 0.1f -> "↑ Зростання"
            stats.trendTemperature < -0.1f -> "↓ Зниження"
            else -> "→ Стабільно"
        }
        binding.tvTrendTemperature?.text = trendTempText
        binding.tvTrendTemperature?.setTextColor(
            when {
                stats.trendTemperature > 0.1f -> Color.parseColor("#FF5722")
                stats.trendTemperature < -0.1f -> Color.parseColor("#2196F3")
                else -> Color.GRAY
            }
        )
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

