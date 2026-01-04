package dev.matsyshyn.lab4

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import dev.matsyshyn.lab4.db.AppDatabase
import dev.matsyshyn.lab4.db.StabilityRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class GraphActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase
    private lateinit var chartStability: LineChart
    private lateinit var chartDistribution: BarChart
    private lateinit var spinnerTimeRange: Spinner
    private lateinit var btnCustomRange: Button
    private lateinit var btnExportCSV: Button
    private lateinit var tvMinValue: TextView
    
    private var currentRecords: List<StabilityRecord> = emptyList()
    private lateinit var tvMaxValue: TextView
    private lateinit var tvAvgValue: TextView
    private lateinit var tvCountValue: TextView

    private val timeRangeOptions = arrayOf(
        "Last hour",
        "Last 6 hours",
        "Last 12 hours",
        "Last 24 hours",
        "Last 7 days",
        "All time"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph)

        database = AppDatabase.getDatabase(this)

        chartStability = findViewById(R.id.chartStability)
        chartDistribution = findViewById(R.id.chartDistribution)
        spinnerTimeRange = findViewById(R.id.spinnerTimeRange)
        btnCustomRange = findViewById(R.id.btnCustomRange)
        btnExportCSV = findViewById(R.id.btnExportCSV)
        tvMinValue = findViewById(R.id.tvMinValue)
        tvMaxValue = findViewById(R.id.tvMaxValue)
        tvAvgValue = findViewById(R.id.tvAvgValue)
        tvCountValue = findViewById(R.id.tvCountValue)

        // Налаштування Spinner
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, timeRangeOptions.toList()) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view as TextView
                textView.setTextColor(getColor(android.R.color.black))
                textView.setBackgroundColor(getColor(android.R.color.white))
                return view
            }
            
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                val textView = view as TextView
                textView.setTextColor(getColor(android.R.color.black))
                textView.setBackgroundColor(getColor(android.R.color.white))
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTimeRange.adapter = adapter
        spinnerTimeRange.setSelection(3) // За замовчуванням "Last 24 hours"

        // Налаштування графіків
        setupLineChart()
        setupBarChart()

        // Обробники подій
        spinnerTimeRange.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                loadDataForTimeRange(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnCustomRange.setOnClickListener {
            showCustomRangeDialog()
        }

        btnExportCSV.setOnClickListener {
            if (currentRecords.isNotEmpty()) {
                exportToCSV(currentRecords)
            } else {
                Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show()
            }
        }

        // Завантажити дані за замовчуванням
        loadDataForTimeRange(3) // Last 24 hours
    }

    private fun setupLineChart() {
        chartStability.description.isEnabled = false
        chartStability.setTouchEnabled(true)
        chartStability.setDragEnabled(true)
        chartStability.setScaleEnabled(true)
        chartStability.setPinchZoom(true)
        chartStability.setBackgroundColor(android.graphics.Color.WHITE)

        val xAxis = chartStability.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false)
        xAxis.labelRotationAngle = -45f

        val leftAxis = chartStability.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.axisMinimum = 0f

        chartStability.axisRight.isEnabled = false
        chartStability.legend.isEnabled = true
    }

    private fun setupBarChart() {
        chartDistribution.description.isEnabled = false
        chartDistribution.setTouchEnabled(true)
        chartDistribution.setDragEnabled(true)
        chartDistribution.setScaleEnabled(true)
        chartDistribution.setPinchZoom(true)
        chartDistribution.setBackgroundColor(android.graphics.Color.WHITE)

        val xAxis = chartDistribution.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f

        val leftAxis = chartDistribution.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.axisMinimum = 0f

        chartDistribution.axisRight.isEnabled = false
        chartDistribution.legend.isEnabled = false
    }

    private fun loadDataForTimeRange(position: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val startTime = when (position) {
                0 -> now - (1 * 60 * 60 * 1000L) // Last hour
                1 -> now - (6 * 60 * 60 * 1000L) // Last 6 hours
                2 -> now - (12 * 60 * 60 * 1000L) // Last 12 hours
                3 -> now - (24 * 60 * 60 * 1000L) // Last 24 hours
                4 -> now - (7 * 24 * 60 * 60 * 1000L) // Last 7 days
                else -> 0L // All time
            }

            val records = if (startTime == 0L) {
                database.stabilityDao().getAllRecords().first()
            } else {
                database.stabilityDao().getRecordsSince(startTime).first()
            }

            launch(Dispatchers.Main) {
                currentRecords = records
                updateCharts(records)
                updateStatistics(records)
            }
        }
    }

    private fun updateCharts(records: List<StabilityRecord>) {
        if (records.isEmpty()) {
            Toast.makeText(this, "No data available for selected time range", Toast.LENGTH_SHORT).show()
            // Очистити графіки
            chartStability.data = null
            chartDistribution.data = null
            chartStability.invalidate()
            chartDistribution.invalidate()
            return
        }

        // Основний графік - лінійний (використовуємо індекс як X для простоти)
        val lineEntries = records.mapIndexed { index, record ->
            Entry(index.toFloat(), record.stabilityScore)
        }
        
        // Зберігаємо timestamps для форматування
        val timestamps = records.map { it.timestamp }.toList()

        val lineDataSet = LineDataSet(lineEntries, "Stability Score")
        lineDataSet.color = android.graphics.Color.parseColor("#2196F3")
        lineDataSet.setCircleColor(android.graphics.Color.parseColor("#2196F3"))
        lineDataSet.lineWidth = 2f
        lineDataSet.circleRadius = 4f
        lineDataSet.setDrawCircleHole(false)
        lineDataSet.valueTextSize = 10f
        lineDataSet.mode = LineDataSet.Mode.CUBIC_BEZIER

        // Налаштування форматування часу для X-осі
        val stabilityXAxis = chartStability.xAxis
        val timestampsList = timestamps
        stabilityXAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt().coerceIn(0, timestampsList.size - 1)
                if (index < timestampsList.size) {
                    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampsList[index]))
                }
                return ""
            }
        }

        val lineData = LineData(lineDataSet)
        chartStability.data = lineData
        chartStability.invalidate()

        // Графік розподілу - стовпчастий
        val distribution = calculateDistribution(records)
        val barEntries = distribution.mapIndexed { index, count ->
            BarEntry(index.toFloat(), count.toFloat())
        }

        val barDataSet = BarDataSet(barEntries, "Count")
        barDataSet.color = android.graphics.Color.parseColor("#4CAF50")
        barDataSet.valueTextSize = 10f

        val barData = BarData(barDataSet)
        chartDistribution.data = barData

        // Налаштування підписів для графіка розподілу
        val distributionXAxis = chartDistribution.xAxis
        distributionXAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                if (index < distribution.size) {
                    val range = getRangeForIndex(index, records)
                    return "%.1f-%.1f".format(range.first, range.second)
                }
                return ""
            }
        }

        chartDistribution.invalidate()
    }

    private fun calculateDistribution(records: List<StabilityRecord>): List<Int> {
        if (records.isEmpty()) return emptyList()

        val min = records.minOf { it.stabilityScore }
        val max = records.maxOf { it.stabilityScore }
        val range = max - min
        val bucketCount = 10
        val bucketSize = range / bucketCount

        val buckets = IntArray(bucketCount)
        records.forEach { record ->
            val bucketIndex = ((record.stabilityScore - min) / bucketSize).toInt().coerceIn(0, bucketCount - 1)
            buckets[bucketIndex]++
        }

        return buckets.toList()
    }

    private fun getRangeForIndex(index: Int, records: List<StabilityRecord>): Pair<Float, Float> {
        val min = records.minOf { it.stabilityScore }
        val max = records.maxOf { it.stabilityScore }
        val range = max - min
        val bucketSize = range / 10

        val start = min + (index * bucketSize)
        val end = min + ((index + 1) * bucketSize)
        return Pair(start, end)
    }

    private fun updateStatistics(records: List<StabilityRecord>) {
        if (records.isEmpty()) {
            tvMinValue.text = "0.00"
            tvMaxValue.text = "0.00"
            tvAvgValue.text = "0.00"
            tvCountValue.text = "0"
            return
        }

        val min = records.minOf { it.stabilityScore }
        val max = records.maxOf { it.stabilityScore }
        val avg = records.map { it.stabilityScore }.average()
        val count = records.size

        tvMinValue.text = "%.2f".format(min)
        tvMaxValue.text = "%.2f".format(max)
        tvAvgValue.text = "%.2f".format(avg)
        tvCountValue.text = count.toString()
    }

    private fun showCustomRangeDialog() {
        val calendar = Calendar.getInstance()
        val startDate = calendar.clone() as Calendar
        startDate.add(Calendar.DAY_OF_MONTH, -1) // За замовчуванням вчора

        // Вибір початкової дати
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                startDate.set(year, month, dayOfMonth)
                
                // Вибір початкового часу
                TimePickerDialog(
                    this,
                    { _, hourOfDay, minute ->
                        startDate.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        startDate.set(Calendar.MINUTE, minute)
                        startDate.set(Calendar.SECOND, 0)
                        startDate.set(Calendar.MILLISECOND, 0)
                        
                        // Вибір кінцевої дати
                        val endDate = calendar.clone() as Calendar
                        DatePickerDialog(
                            this,
                            { _, year2, month2, dayOfMonth2 ->
                                endDate.set(year2, month2, dayOfMonth2)
                                
                                // Вибір кінцевого часу
                                TimePickerDialog(
                                    this,
                                    { _, hourOfDay2, minute2 ->
                                        endDate.set(Calendar.HOUR_OF_DAY, hourOfDay2)
                                        endDate.set(Calendar.MINUTE, minute2)
                                        endDate.set(Calendar.SECOND, 59)
                                        endDate.set(Calendar.MILLISECOND, 999)
                                        
                                        // Завантажити дані для вибраного діапазону
                                        loadDataForCustomRange(startDate.timeInMillis, endDate.timeInMillis)
                                    },
                                    calendar.get(Calendar.HOUR_OF_DAY),
                                    calendar.get(Calendar.MINUTE),
                                    true
                                ).show()
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            startDate.get(Calendar.YEAR),
            startDate.get(Calendar.MONTH),
            startDate.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadDataForCustomRange(startTime: Long, endTime: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            val records = database.stabilityDao().getRecordsByTimeRange(startTime, endTime).first()
            
            launch(Dispatchers.Main) {
                currentRecords = records
                updateCharts(records)
                updateStatistics(records)
                
                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                Toast.makeText(
                    this@GraphActivity,
                    "Loaded data from ${dateFormat.format(Date(startTime))} to ${dateFormat.format(Date(endTime))}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun exportToCSV(records: List<StabilityRecord>) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val csv = StringBuilder()
                // Заголовок
                csv.append("Timestamp,Stability Score,X,Y,Z\n")
                
                // Дані
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                records.forEach { record ->
                    csv.append("${dateFormat.format(Date(record.timestamp))},")
                    csv.append("${record.stabilityScore},")
                    csv.append("${record.x},")
                    csv.append("${record.y},")
                    csv.append("${record.z}\n")
                }
                
                // Збереження файлу
                val fileName = "stability_data_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ - використовуємо MediaStore
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    
                    val uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                    uri?.let {
                        contentResolver.openOutputStream(it)?.use { outputStream ->
                            outputStream.write(csv.toString().toByteArray())
                        }
                        launch(Dispatchers.Main) {
                            Toast.makeText(this@GraphActivity, "Exported to Downloads/$fileName", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    // Старі версії Android
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = java.io.File(downloadsDir, fileName)
                    file.writeText(csv.toString())
                    launch(Dispatchers.Main) {
                        Toast.makeText(this@GraphActivity, "Exported to Downloads/$fileName", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(this@GraphActivity, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

