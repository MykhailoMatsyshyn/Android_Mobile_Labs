package dev.matsyshyn.speedtracker

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.animation.RotateAnimation
import android.widget.ImageView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var chartSpeed: LineChart
    private lateinit var btnBack: Button
    private lateinit var btnStartDate: Button
    private lateinit var btnStartTime: Button
    private lateinit var btnEndDate: Button
    private lateinit var btnEndTime: Button
    private lateinit var btnApplyFilter: Button
    private lateinit var btnResetFilter: Button
    private lateinit var btnExportCsv: Button
    private lateinit var btnExportTxt: Button
    private lateinit var btnOpenMap: Button
    private lateinit var spinnerIntervalFilter: Spinner
    
    private lateinit var tvMaxSpeedStat: TextView
    private lateinit var tvAvgSpeedStat: TextView
    private lateinit var tvTotalRecords: TextView
    private lateinit var tvTotalDistance: TextView
    
    private lateinit var recyclerViewRecords: RecyclerView
    private lateinit var tvRecordsCount: TextView
    private lateinit var ivExpandArrow: ImageView
    private lateinit var dividerRecords: View
    private lateinit var recordsHeaderLayout: View
    private var isRecordsExpanded = false

    private val allDataList = mutableListOf<TrackingData>()
    private val filteredDataList = mutableListOf<TrackingData>()
    private var startDateTime: Calendar? = null
    private var endDateTime: Calendar? = null
    private var selectedIntervalFilter: Int? = null // null = всі інтервали

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val displayDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.US)
    private val displayTimeFormat = SimpleDateFormat("HH:mm", Locale.US)
    private val displayDateTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US)
    private val calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        initViews()
        setupChart()
        setupIntervalFilterSpinner()
        loadDataFromFirebase()
        setupClickListeners()
    }

    private fun initViews() {
        progressBar = findViewById(R.id.progressBar)
        chartSpeed = findViewById(R.id.chartSpeed)
        btnBack = findViewById(R.id.btnBack)
        btnStartDate = findViewById(R.id.btnStartDate)
        btnStartTime = findViewById(R.id.btnStartTime)
        btnEndDate = findViewById(R.id.btnEndDate)
        btnEndTime = findViewById(R.id.btnEndTime)
        btnApplyFilter = findViewById(R.id.btnApplyFilter)
        btnResetFilter = findViewById(R.id.btnResetFilter)
        btnExportCsv = findViewById(R.id.btnExportCsv)
        btnExportTxt = findViewById(R.id.btnExportTxt)
        btnOpenMap = findViewById(R.id.btnOpenMap)
        spinnerIntervalFilter = findViewById(R.id.spinnerIntervalFilter)
        
        tvMaxSpeedStat = findViewById(R.id.tvMaxSpeedStat)
        tvAvgSpeedStat = findViewById(R.id.tvAvgSpeedStat)
        tvTotalRecords = findViewById(R.id.tvTotalRecords)
        tvTotalDistance = findViewById(R.id.tvTotalDistance)
        
        recyclerViewRecords = findViewById(R.id.recyclerViewRecords)
        tvRecordsCount = findViewById(R.id.tvRecordsCount)
        ivExpandArrow = findViewById(R.id.ivExpandArrow)
        dividerRecords = findViewById(R.id.dividerRecords)
        recordsHeaderLayout = findViewById(R.id.recordsHeaderLayout)
        
        recyclerViewRecords.layoutManager = LinearLayoutManager(this)
    }

    private fun setupChart() {
        chartSpeed.description.isEnabled = false
        chartSpeed.setTouchEnabled(true)
        chartSpeed.setDragEnabled(true)
        chartSpeed.setScaleEnabled(true)
        chartSpeed.setPinchZoom(true)
        chartSpeed.legend.isEnabled = false
        
        val xAxis = chartSpeed.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textSize = 10f
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                if (index >= 0 && index < filteredDataList.size) {
                    val timestamp = filteredDataList[index].timestamp
                    return if (timestamp.isNotEmpty()) {
                        try {
                            val date = dateFormat.parse(timestamp)
                            SimpleDateFormat("HH:mm", Locale.US).format(date ?: Date())
                        } catch (e: Exception) {
                            ""
                        }
                    } else {
                        ""
                    }
                }
                return ""
            }
        }
        
        val leftAxis = chartSpeed.axisLeft
        leftAxis.textSize = 10f
        leftAxis.axisMinimum = 0f
        
        chartSpeed.axisRight.isEnabled = false
    }

    private fun setupIntervalFilterSpinner() {
        val intervals = arrayOf(
            "Всі інтервали",
            "5 сек",
            "10 сек",
            "15 сек",
            "30 сек",
            "60 сек",
            "120 сек"
        )
        
        val intervalValues = arrayOf(null, 5, 10, 15, 30, 60, 120)
        
        // Кастомний адаптер з правильними кольорами
        val adapter = object : ArrayAdapter<String>(this, R.layout.spinner_item_interval, intervals) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                if (position == 0) {
                    // "Всі інтервали" - чорний колір
                    view.setTextColor(resources.getColor(android.R.color.black, null))
                } else {
                    // Секунди - темний текст
                    view.setTextColor(resources.getColor(R.color.text_primary, null))
                }
                return view
            }
            
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                // Встановлюємо білий фон для кращої видимості
                view.setBackgroundColor(resources.getColor(android.R.color.white, null))
                if (position == 0) {
                    // "Всі інтервали" - чорний колір
                    view.setTextColor(resources.getColor(android.R.color.black, null))
                } else {
                    // Секунди - чорний текст для кращої видимості
                    view.setTextColor(resources.getColor(android.R.color.black, null))
                }
                return view
            }
        }
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_interval)
        spinnerIntervalFilter.adapter = adapter
        spinnerIntervalFilter.setSelection(0) // "Всі інтервали" за замовчуванням
        
        spinnerIntervalFilter.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedIntervalFilter = intervalValues[position]
                applyFilter()
            }
            
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnStartDate.setOnClickListener {
            val currentCalendar = startDateTime ?: Calendar.getInstance()
            val datePicker = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val newCalendar = Calendar.getInstance()
                    newCalendar.set(year, month, dayOfMonth)
                    // Зберігаємо час, якщо він був встановлений раніше
                    if (startDateTime != null) {
                        newCalendar.set(Calendar.HOUR_OF_DAY, startDateTime!!.get(Calendar.HOUR_OF_DAY))
                        newCalendar.set(Calendar.MINUTE, startDateTime!!.get(Calendar.MINUTE))
                    } else {
                        newCalendar.set(Calendar.HOUR_OF_DAY, 0)
                        newCalendar.set(Calendar.MINUTE, 0)
                    }
                    newCalendar.set(Calendar.SECOND, 0)
                    newCalendar.set(Calendar.MILLISECOND, 0)
                    startDateTime = newCalendar
                    btnStartDate.text = displayDateFormat.format(startDateTime!!.time)
                    updateStartTimeButton()
                },
                currentCalendar.get(Calendar.YEAR),
                currentCalendar.get(Calendar.MONTH),
                currentCalendar.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.show()
        }

        btnStartTime.setOnClickListener {
            val currentCalendar = startDateTime ?: Calendar.getInstance()
            val timePicker = TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    val newCalendar = startDateTime ?: Calendar.getInstance()
                    newCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    newCalendar.set(Calendar.MINUTE, minute)
                    newCalendar.set(Calendar.SECOND, 0)
                    newCalendar.set(Calendar.MILLISECOND, 0)
                    startDateTime = newCalendar
                    updateStartTimeButton()
                    if (btnStartDate.text == "Початкова дата") {
                        btnStartDate.text = displayDateFormat.format(startDateTime!!.time)
                    }
                },
                currentCalendar.get(Calendar.HOUR_OF_DAY),
                currentCalendar.get(Calendar.MINUTE),
                true
            )
            timePicker.show()
        }

        btnEndDate.setOnClickListener {
            val currentCalendar = endDateTime ?: Calendar.getInstance()
            val datePicker = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val newCalendar = Calendar.getInstance()
                    newCalendar.set(year, month, dayOfMonth)
                    // Зберігаємо час, якщо він був встановлений раніше
                    if (endDateTime != null) {
                        newCalendar.set(Calendar.HOUR_OF_DAY, endDateTime!!.get(Calendar.HOUR_OF_DAY))
                        newCalendar.set(Calendar.MINUTE, endDateTime!!.get(Calendar.MINUTE))
                    } else {
                        newCalendar.set(Calendar.HOUR_OF_DAY, 23)
                        newCalendar.set(Calendar.MINUTE, 59)
                    }
                    newCalendar.set(Calendar.SECOND, 59)
                    newCalendar.set(Calendar.MILLISECOND, 999)
                    endDateTime = newCalendar
                    btnEndDate.text = displayDateFormat.format(endDateTime!!.time)
                    updateEndTimeButton()
                },
                currentCalendar.get(Calendar.YEAR),
                currentCalendar.get(Calendar.MONTH),
                currentCalendar.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.show()
        }

        btnEndTime.setOnClickListener {
            val currentCalendar = endDateTime ?: Calendar.getInstance()
            val timePicker = TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    val newCalendar = endDateTime ?: Calendar.getInstance()
                    newCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    newCalendar.set(Calendar.MINUTE, minute)
                    newCalendar.set(Calendar.SECOND, 59)
                    newCalendar.set(Calendar.MILLISECOND, 999)
                    endDateTime = newCalendar
                    updateEndTimeButton()
                    if (btnEndDate.text == "Кінцева дата") {
                        btnEndDate.text = displayDateFormat.format(endDateTime!!.time)
                    }
                },
                currentCalendar.get(Calendar.HOUR_OF_DAY),
                currentCalendar.get(Calendar.MINUTE),
                true
            )
            timePicker.show()
        }

        btnApplyFilter.setOnClickListener {
            applyFilter()
        }

        btnResetFilter.setOnClickListener {
            startDateTime = null
            endDateTime = null
            selectedIntervalFilter = null
            btnStartDate.text = "Початкова дата"
            btnStartTime.text = "Початковий час"
            btnEndDate.text = "Кінцева дата"
            btnEndTime.text = "Кінцевий час"
            spinnerIntervalFilter.setSelection(0) // "Всі інтервали"
            applyFilter()
        }

        btnExportCsv.setOnClickListener { exportCsv() }
        btnExportTxt.setOnClickListener { exportTxt() }
        
        btnOpenMap.setOnClickListener {
            // Передаємо відфільтровані дані на карту
            val intent = android.content.Intent(this, MapActivity::class.java)
            // Передаємо дані через Intent як Serializable
            intent.putExtra("filtered_data", ArrayList(filteredDataList))
            startActivity(intent)
        }
        
        recordsHeaderLayout.setOnClickListener {
            toggleRecordsList()
        }
    }
    
    private fun toggleRecordsList() {
        isRecordsExpanded = !isRecordsExpanded
        
        if (isRecordsExpanded) {
            recyclerViewRecords.visibility = View.VISIBLE
            dividerRecords.visibility = View.VISIBLE
            ivExpandArrow.rotation = 180f
        } else {
            recyclerViewRecords.visibility = View.GONE
            dividerRecords.visibility = View.GONE
            ivExpandArrow.rotation = 0f
        }
        
        // Анімація обертання стрілки
        val rotateAnimation = RotateAnimation(
            if (isRecordsExpanded) 0f else 180f,
            if (isRecordsExpanded) 180f else 0f,
            RotateAnimation.RELATIVE_TO_SELF, 0.5f,
            RotateAnimation.RELATIVE_TO_SELF, 0.5f
        )
        rotateAnimation.duration = 200
        rotateAnimation.fillAfter = true
        ivExpandArrow.startAnimation(rotateAnimation)
    }

    private fun loadDataFromFirebase() {
        progressBar.visibility = View.VISIBLE
        val database = FirebaseDatabase.getInstance("https://speedtrackerlab3-default-rtdb.europe-west1.firebasedatabase.app")
            .getReference("speed_tracking")

        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allDataList.clear()
                for (dataSnapshot in snapshot.children) {
                    val data = dataSnapshot.getValue(TrackingData::class.java)
                    data?.let { allDataList.add(it) }
                }
                
                // Сортуємо за часом (нові зверху для списку)
                allDataList.sortByDescending { it.timestamp }
                
                filteredDataList.clear()
                filteredDataList.addAll(allDataList)
                
                // Для списку - нові зверху (вже відсортовано)
                // Для графіка - потрібно сортувати по зростанню
                val sortedForChart = filteredDataList.sortedBy { it.timestamp }
                
                updateStatistics()
                updateChart(sortedForChart)
                updateRecordsList()
                progressBar.visibility = View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@HistoryActivity, "Помилка: ${error.message}", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
            }
        })
    }

    private fun updateStartTimeButton() {
        startDateTime?.let {
            btnStartTime.text = displayTimeFormat.format(it.time)
        }
    }

    private fun updateEndTimeButton() {
        endDateTime?.let {
            btnEndTime.text = displayTimeFormat.format(it.time)
        }
    }

    private fun applyFilter() {
        filteredDataList.clear()
        
        // Фільтруємо дані
        for (data in allDataList) {
            // Фільтр по інтервалу
            val interval = if (data.intervalSeconds > 0) data.intervalSeconds else 10
            val matchesInterval = selectedIntervalFilter == null || interval == selectedIntervalFilter
            
            if (!matchesInterval) continue
            
            // Фільтр по даті та часу
            try {
                val dataDate = dateFormat.parse(data.timestamp) ?: continue
                val dataCalendar = Calendar.getInstance().apply {
                    time = dataDate
                }
                
                val matchesStart = startDateTime == null || 
                    dataCalendar.timeInMillis >= startDateTime!!.timeInMillis
                val matchesEnd = endDateTime == null || 
                    dataCalendar.timeInMillis <= endDateTime!!.timeInMillis
                
                if (matchesStart && matchesEnd) {
                    filteredDataList.add(data)
                }
            } catch (e: Exception) {
                // Skip invalid dates
            }
        }
        
        // Для списку - нові зверху (вже відсортовано в loadDataFromFirebase)
        // Для графіка - потрібно сортувати по зростанню
        val sortedForChart = filteredDataList.sortedBy { it.timestamp }
        
        updateStatistics()
        updateChart(sortedForChart)
        updateRecordsList()
    }
    
    private fun updateRecordsList() {
        tvRecordsCount.text = "(${filteredDataList.size})"
        recyclerViewRecords.adapter = HistoryAdapter(filteredDataList)
    }

    private fun updateStatistics() {
        if (filteredDataList.isEmpty()) {
            tvMaxSpeedStat.text = "0 км/год"
            tvAvgSpeedStat.text = "0 км/год"
            tvTotalRecords.text = "0"
            tvTotalDistance.text = "0 км"
            return
        }

        // Максимальна швидкість
        val maxSpeed = filteredDataList.maxOfOrNull { it.speedKmh } ?: 0f
        tvMaxSpeedStat.text = String.format(Locale.US, "%.1f км/год", maxSpeed)

        // Середня швидкість
        val avgSpeed = filteredDataList.map { it.speedKmh }.average().toFloat()
        tvAvgSpeedStat.text = String.format(Locale.US, "%.1f км/год", avgSpeed)

        // Загальна кількість записів
        tvTotalRecords.text = filteredDataList.size.toString()

        // Загальна відстань (розрахунок на основі швидкості та інтервалу відправки)
        var totalDistance = 0.0
        for (i in 0 until filteredDataList.size) {
            val data = filteredDataList[i]
            // Використовуємо інтервал з запису, або дефолт 10 сек для старих записів
            val interval = if (data.intervalSeconds > 0) data.intervalSeconds else 10
            val timeDiffHours = interval / 3600.0 // Конвертуємо секунди в години
            totalDistance += data.speedKmh * timeDiffHours
        }
        tvTotalDistance.text = String.format(Locale.US, "%.2f км", totalDistance)
    }

    private fun updateChart(dataForChart: List<TrackingData> = filteredDataList) {
        if (dataForChart.isEmpty()) {
            chartSpeed.clear()
            chartSpeed.invalidate()
            return
        }

        val entries = mutableListOf<Entry>()
        dataForChart.forEachIndexed { index, data ->
            entries.add(Entry(index.toFloat(), data.speedKmh))
        }

        val dataSet = LineDataSet(entries, "Швидкість")
        dataSet.color = resources.getColor(android.R.color.holo_blue_dark, null)
        dataSet.lineWidth = 2f
        dataSet.setCircleColor(resources.getColor(android.R.color.holo_blue_dark, null))
        dataSet.circleRadius = 4f
        dataSet.setDrawCircleHole(false)
        dataSet.valueTextSize = 9f
        dataSet.setDrawFilled(true)
        dataSet.fillColor = resources.getColor(android.R.color.holo_blue_light, null)
        dataSet.fillAlpha = 100

        val lineData = LineData(dataSet)
        chartSpeed.data = lineData
        chartSpeed.invalidate()
    }

    private fun exportCsv() {
        if (filteredDataList.isEmpty()) {
            Toast.makeText(this, "Немає даних для експорту", Toast.LENGTH_SHORT).show()
            return
        }
        
        val content = StringBuilder("Timestamp,Speed (km/h),Acceleration,Latitude,Longitude\n")
        filteredDataList.forEach {
            content.append("${it.timestamp},${it.speedKmh},${it.accelMagnitude},${it.latitude},${it.longitude}\n")
        }
        
        val fileName = if (startDateTime != null || endDateTime != null) {
            "history_filtered_${System.currentTimeMillis()}.csv"
        } else {
            "history_export_${System.currentTimeMillis()}.csv"
        }
        
        saveFile(fileName, content.toString())
    }

    private fun exportTxt() {
        if (filteredDataList.isEmpty()) {
            Toast.makeText(this, "Немає даних для експорту", Toast.LENGTH_SHORT).show()
            return
        }
        
        val content = StringBuilder("=== ЗВІТ ШВИДКОСТІ ===\n\n")
        val periodStart = if (startDateTime != null) {
            displayDateTimeFormat.format(startDateTime!!.time)
        } else {
            "з початку"
        }
        val periodEnd = if (endDateTime != null) {
            displayDateTimeFormat.format(endDateTime!!.time)
        } else {
            "до кінця"
        }
        content.append("Період: $periodStart - $periodEnd\n")
        content.append("Загальна кількість записів: ${filteredDataList.size}\n")
        content.append("Максимальна швидкість: ${tvMaxSpeedStat.text}\n")
        content.append("Середня швидкість: ${tvAvgSpeedStat.text}\n")
        content.append("Загальна відстань: ${tvTotalDistance.text}\n\n")
        content.append("Детальна інформація:\n")
        content.append("=".repeat(50) + "\n\n")
        
        filteredDataList.forEach {
            content.append("Час: ${it.timestamp}\n")
            content.append("Швидкість: ${it.speedKmh} км/год\n")
            content.append("Акселерометр: ${it.accelMagnitude}\n")
            content.append("Координати: ${it.latitude}, ${it.longitude}\n")
            content.append("-".repeat(50) + "\n")
        }
        
        val fileName = if (startDateTime != null || endDateTime != null) {
            "history_filtered_${System.currentTimeMillis()}.txt"
        } else {
            "history_export_${System.currentTimeMillis()}.txt"
        }
        
        saveFile(fileName, content.toString())
    }

    private fun saveFile(fileName: String, data: String) {
        try {
            val file = File(getExternalFilesDir(null), fileName)
            FileOutputStream(file).use { it.write(data.toByteArray()) }
            Toast.makeText(this, "Файл збережено:\n${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Помилка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}