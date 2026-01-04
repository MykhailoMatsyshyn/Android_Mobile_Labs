package dev.matsyshyn.lab5ml

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.util.*

class PredictionFragment : Fragment() {

    private lateinit var bluetoothManager: BluetoothDataManager
    private lateinit var classifier: ActivityClassifier
    
    private val dataWindow = mutableListOf<FloatArray>()
    private val WINDOW_SIZE = 20
    private val STEP_SIZE = 10

    // UI
    private lateinit var tvStatus: TextView
    private lateinit var tvPrediction: TextView
    private lateinit var tvConfidence: TextView
    private lateinit var tvAccel: TextView
    private lateinit var tvGyro: TextView
    private lateinit var tvWindowSize: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnRescan: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_prediction, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvStatus = view.findViewById(R.id.tvStatus)
        tvPrediction = view.findViewById(R.id.tvPrediction)
        tvConfidence = view.findViewById(R.id.tvConfidence)
        tvAccel = view.findViewById(R.id.tvAccel)
        tvGyro = view.findViewById(R.id.tvGyro)
        tvWindowSize = view.findViewById(R.id.tvWindowSize)
        progressBar = view.findViewById(R.id.progressBar)
        btnRescan = view.findViewById(R.id.btnRescan)

        try {
            classifier = ActivityClassifier(requireContext())
            tvStatus.text = "✅ Model loaded"
        } catch (e: Exception) {
            tvStatus.text = "❌ Model error: ${e.message}"
            Log.e("Lab5", "Model load error", e)
        }
        
        // Ініціалізація confidence
        tvPrediction.text = "---"
        tvConfidence.text = "Confidence: ---"

        bluetoothManager = BluetoothDataManager(requireContext())
        
        bluetoothManager.onStatusChanged = { status ->
            tvStatus.text = status
        }
        
        bluetoothManager.onDataReceived = { rawString ->
            processData(rawString)
        }

        btnRescan.setOnClickListener {
            if (checkPermissions()) {
                bluetoothManager.startScan()
            } else {
                requestPermissions()
            }
        }

        if (checkPermissions()) {
            bluetoothManager.startScan()
        } else {
            requestPermissions()
        }
    }

    private fun processData(rawString: String) {
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

                val sample = floatArrayOf(ax, ay, az, gx, gy, gz)
                dataWindow.add(sample)

                // Оновлюємо прогрес
                val progress = (dataWindow.size * 100 / WINDOW_SIZE).coerceAtMost(100)
                progressBar.progress = progress
                tvWindowSize.text = "Window: ${dataWindow.size}/$WINDOW_SIZE"

                // Якщо накопичили 20 замірів - робимо прогноз
                if (dataWindow.size == WINDOW_SIZE) {
                    val result = classifier.predict(dataWindow)
                    Log.d("Lab5", "Prediction result: $result")
                    val parts = result.split(" (")
                    if (parts.size == 2) {
                        val className = parts[0].trim()
                        val confidenceStr = parts[1].removeSuffix(")").trim()
                        tvPrediction.text = className
                        tvConfidence.text = "Confidence: $confidenceStr"
                        Log.d("Lab5", "Setting confidence: $confidenceStr")
                    } else {
                        tvPrediction.text = result.trim()
                        tvConfidence.text = "Confidence: ---"
                        Log.d("Lab5", "Could not parse result: $result")
                    }

                    // Очищаємо вікно наполовину (Step Size = 10)
                    repeat(STEP_SIZE) {
                        if (dataWindow.isNotEmpty()) {
                            dataWindow.removeAt(0)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Lab5", "Process error", e)
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
}

