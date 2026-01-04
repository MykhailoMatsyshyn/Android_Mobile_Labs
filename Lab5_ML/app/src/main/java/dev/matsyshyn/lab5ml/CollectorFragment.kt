package dev.matsyshyn.lab5ml

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.io.File
import java.io.FileWriter
import java.util.*

class CollectorFragment : Fragment() {

    private lateinit var bluetoothManager: BluetoothDataManager
    
    // Збір даних
    private var isRecording = false
    private val dataBuffer = StringBuilder()
    private var recordCount = 0
    private var currentClass = 4

    private val classNames = mapOf(
        0 to "Згинання рук",
        1 to "Підняття рук",
        2 to "Розведення рук",
        3 to "Обертання рук",
        4 to "Спокій"
    )

    // UI
    private lateinit var tvStatus: TextView
    private lateinit var tvData: TextView
    private lateinit var tvAccel: TextView
    private lateinit var tvGyro: TextView
    private lateinit var tvSelectedClass: TextView
    private lateinit var tvRecordCount: TextView
    private lateinit var btnRecord: Button
    private lateinit var btnSave: Button
    private lateinit var btnClass0: Button
    private lateinit var btnClass1: Button
    private lateinit var btnClass2: Button
    private lateinit var btnClass3: Button
    private lateinit var btnClass4: Button
    private lateinit var btnRescan: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_collector, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvStatus = view.findViewById(R.id.tvStatus)
        tvData = view.findViewById(R.id.tvData)
        tvAccel = view.findViewById(R.id.tvAccel)
        tvGyro = view.findViewById(R.id.tvGyro)
        tvSelectedClass = view.findViewById(R.id.tvSelectedClass)
        tvRecordCount = view.findViewById(R.id.tvRecordCount)
        btnRecord = view.findViewById(R.id.btnRecord)
        btnSave = view.findViewById(R.id.btnSave)
        btnClass0 = view.findViewById(R.id.btnClass0)
        btnClass1 = view.findViewById(R.id.btnClass1)
        btnClass2 = view.findViewById(R.id.btnClass2)
        btnClass3 = view.findViewById(R.id.btnClass3)
        btnClass4 = view.findViewById(R.id.btnClass4)
        btnRescan = view.findViewById(R.id.btnRescan)

        bluetoothManager = BluetoothDataManager(requireContext())
        
        bluetoothManager.onStatusChanged = { status ->
            tvStatus.text = status
        }
        
        bluetoothManager.onDataReceived = { rawString ->
            updateData(rawString)
        }

        btnRescan.setOnClickListener {
            if (checkPermissions()) {
                bluetoothManager.startScan()
            } else {
                requestPermissions()
            }
        }

        btnClass0.setOnClickListener { selectClass(0) }
        btnClass1.setOnClickListener { selectClass(1) }
        btnClass2.setOnClickListener { selectClass(2) }
        btnClass3.setOnClickListener { selectClass(3) }
        btnClass4.setOnClickListener { selectClass(4) }

        updateClassDisplay()

        btnRecord.setOnClickListener {
            isRecording = !isRecording
            if (isRecording) {
                btnRecord.text = "⏸ STOP Recording"
                Toast.makeText(context, "Started recording: ${classNames[currentClass]}", Toast.LENGTH_SHORT).show()
            } else {
                btnRecord.text = "▶ START Recording"
            }
        }

        btnSave.setOnClickListener {
            saveToCsv()
        }

        if (checkPermissions()) {
            bluetoothManager.startScan()
        } else {
            requestPermissions()
        }
    }

    private fun updateData(rawString: String) {
        try {
            val values = rawString.split(",")
            if (values.size == 6) {
                val ax = values[0].toFloatOrNull() ?: 0f
                val ay = values[1].toFloatOrNull() ?: 0f
                val az = values[2].toFloatOrNull() ?: 0f
                val gx = values[3].toFloatOrNull() ?: 0f
                val gy = values[4].toFloatOrNull() ?: 0f
                val gz = values[5].toFloatOrNull() ?: 0f

                tvAccel.text = String.format(Locale.US, "ax: %.2f\nay: %.2f\naz: %.2f", ax, ay, az)
                tvGyro.text = String.format(Locale.US, "gx: %.2f\ngy: %.2f\ngz: %.2f", gx, gy, gz)
                tvData.text = "📡 Receiving data..."

                if (isRecording) {
                    dataBuffer.append("$rawString,$currentClass\n")
                    recordCount++
                    tvRecordCount.text = "Samples: $recordCount"
                    btnRecord.text = "⏸ STOP ($recordCount)"
                    tvRecordCount.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                } else {
                    tvRecordCount.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
                }
            }
        } catch (e: Exception) {
            tvData.text = "Error parsing: $rawString"
            Log.e("Lab5", "Parse error", e)
        }
    }

    private fun saveToCsv() {
        try {
            val fileName = "training_data_${System.currentTimeMillis()}.csv"
            val file = File(requireContext().getExternalFilesDir(null), fileName)
            val header = "ax,ay,az,gx,gy,gz,label\n"
            
            FileWriter(file).use { 
                it.write(header + dataBuffer.toString()) 
            }
            
            Toast.makeText(context, "✅ Saved $fileName\n(${recordCount} rows)", Toast.LENGTH_LONG).show()
            dataBuffer.clear()
            recordCount = 0
            tvRecordCount.text = "Samples: 0"
            tvRecordCount.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
            btnRecord.text = "▶ START Recording"
            isRecording = false
        } catch (e: Exception) {
            Log.e("Lab5", "Error saving", e)
        }
    }

    private fun checkPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                   ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ), 1)
        } else {
            requestPermissions(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            ), 1)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            bluetoothManager.startScan()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bluetoothManager.stop()
    }

    private fun selectClass(classNum: Int) {
        currentClass = classNum
        updateClassDisplay()
        Toast.makeText(context, "Selected: ${classNames[classNum]}", Toast.LENGTH_SHORT).show()
    }

    private fun updateClassDisplay() {
        tvSelectedClass.text = "${classNames[currentClass]} ($currentClass)"
        
        val buttons = listOf(btnClass0, btnClass1, btnClass2, btnClass3, btnClass4)
        buttons.forEach { it.alpha = 0.6f }
        
        when (currentClass) {
            0 -> btnClass0.alpha = 1.0f
            1 -> btnClass1.alpha = 1.0f
            2 -> btnClass2.alpha = 1.0f
            3 -> btnClass3.alpha = 1.0f
            4 -> btnClass4.alpha = 1.0f
        }
    }
}

