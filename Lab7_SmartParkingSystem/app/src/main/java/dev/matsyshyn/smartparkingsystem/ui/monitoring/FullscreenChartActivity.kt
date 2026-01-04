package dev.matsyshyn.smartparkingsystem.ui.monitoring

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import dev.matsyshyn.smartparkingsystem.R
import dev.matsyshyn.smartparkingsystem.databinding.ActivityFullscreenChartBinding
import dev.matsyshyn.smartparkingsystem.data.model.SensorData
import dev.matsyshyn.smartparkingsystem.data.model.SensorType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FullscreenChartActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFullscreenChartBinding
    
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullscreenChartBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        
        // Отримуємо дані з Intent
        val sensorTypeName = intent.getStringExtra("sensor_type") ?: "FREE_SPOTS"
        val sensorType = try {
            SensorType.valueOf(sensorTypeName)
        } catch (e: IllegalArgumentException) {
            SensorType.FREE_SPOTS
        }
        @Suppress("UNCHECKED_CAST")
        val sensorDataList = (intent.getSerializableExtra("sensor_data") as? ArrayList<SensorData>) ?: arrayListOf()
        val label = intent.getStringExtra("label") ?: "Графік"
        
        setupChart()
        updateChart(sensorDataList, sensorType, label)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun setupChart() {
        binding.chartFullscreen.description.isEnabled = false
        binding.chartFullscreen.setTouchEnabled(true)
        binding.chartFullscreen.setDragEnabled(true)
        binding.chartFullscreen.setScaleEnabled(true)
        binding.chartFullscreen.setPinchZoom(true)
        
        // Темна тема для графіка
        binding.chartFullscreen.setBackgroundColor(Color.parseColor("#1E1E1E")) // Темний фон
        binding.chartFullscreen.setDrawGridBackground(false)
        
        // Налаштування легенди (світлий текст)
        val legend = binding.chartFullscreen.legend
        legend.isEnabled = true
        legend.textColor = Color.parseColor("#FFFFFF") // Білий текст
        legend.textSize = 14f
        legend.formSize = 14f
        legend.form = com.github.mikephil.charting.components.Legend.LegendForm.LINE
        
        binding.chartFullscreen.setNoDataText("Немає даних")
        binding.chartFullscreen.setNoDataTextColor(Color.parseColor("#CCCCCC")) // Світло-сірий
        
        // X ось (світлий текст)
        val xAxis = binding.chartFullscreen.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(true)
        xAxis.gridColor = Color.parseColor("#424242") // Темно-сіра сітка
        xAxis.textColor = Color.parseColor("#CCCCCC") // Світло-сірий текст
        xAxis.textSize = 12f
        xAxis.axisLineColor = Color.parseColor("#666666") // Сіра лінія осі
        
        // Y ось (світлий текст)
        val leftAxis = binding.chartFullscreen.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.parseColor("#424242") // Темно-сіра сітка
        leftAxis.textColor = Color.parseColor("#CCCCCC") // Світло-сірий текст
        leftAxis.textSize = 12f
        leftAxis.axisLineColor = Color.parseColor("#666666") // Сіра лінія осі
        
        val rightAxis = binding.chartFullscreen.axisRight
        rightAxis.isEnabled = false
    }
    
    private fun updateChart(data: List<SensorData>, sensorType: SensorType, label: String) {
        if (data.isEmpty()) {
            binding.chartFullscreen.clear()
            binding.chartFullscreen.invalidate()
            return
        }
        
        // Сортуємо дані за часом
        val sortedData = data.sortedBy { it.timestamp }
        
        val entries = mutableListOf<Entry>()
        val color = when (sensorType) {
            SensorType.FREE_SPOTS -> Color.parseColor("#2196F3")
            SensorType.CO_LEVEL -> Color.parseColor("#FF5722")
            SensorType.NOX_LEVEL -> Color.parseColor("#9C27B0")
            SensorType.TEMPERATURE -> Color.parseColor("#4CAF50")
            else -> Color.parseColor("#2196F3")
        }
        
        // Використовуємо індекс як X координату
        sortedData.forEachIndexed { index, sensorData ->
            val x = index.toFloat()
            val y = when (sensorType) {
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
        
        val lineData = LineData(dataSet)
        binding.chartFullscreen.data = lineData
        
        // Оновлюємо легенду (світлий текст)
        val legend = binding.chartFullscreen.legend
        legend.textColor = Color.parseColor("#FFFFFF")
        
        // Налаштування осей (темна тема)
        val leftAxis = binding.chartFullscreen.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.parseColor("#424242")
        leftAxis.textColor = Color.parseColor("#CCCCCC")
        
        // Автоматичне масштабування для Y осі
        binding.chartFullscreen.axisLeft.resetAxisMinimum()
        binding.chartFullscreen.axisLeft.resetAxisMaximum()
        
        // Налаштування X осі для відображення часу
        val xAxis = binding.chartFullscreen.xAxis
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                if (index >= 0 && index < sortedData.size) {
                    return dateFormat.format(Date(sortedData[index].timestamp))
                }
                return ""
            }
        }
        xAxis.setLabelCount(minOf(8, sortedData.size), true)
        xAxis.textColor = Color.parseColor("#CCCCCC")
        
        binding.chartFullscreen.invalidate()
        binding.chartFullscreen.notifyDataSetChanged()
        binding.chartFullscreen.animateX(500)
    }
}

