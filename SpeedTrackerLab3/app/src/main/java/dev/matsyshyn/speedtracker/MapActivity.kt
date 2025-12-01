package dev.matsyshyn.speedtracker

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.*

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var progressBar: ProgressBar
    private val dataList = mutableListOf<TrackingData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        progressBar = findViewById(R.id.progressBar)
        val btnBack = findViewById<Button>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)

        // Перевіряємо чи передані відфільтровані дані
        val filteredData = intent.getSerializableExtra("filtered_data") as? ArrayList<*>
        if (filteredData != null && filteredData.isNotEmpty()) {
            // Використовуємо передані відфільтровані дані
            dataList.clear()
            filteredData.forEach {
                if (it is TrackingData) {
                    dataList.add(it)
                }
            }
            // Оновлюємо маркери коли карта готова
            if (::map.isInitialized) {
                updateMapMarkers()
            }
        } else {
            // Якщо дані не передані, завантажуємо всі з Firebase
            loadDataFromFirebase()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        
        // Налаштування карти
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = true
        map.mapType = GoogleMap.MAP_TYPE_NORMAL
        
        // Оновити маркери якщо дані вже завантажені
        if (dataList.isNotEmpty()) {
            updateMapMarkers()
        }
    }

    private fun loadDataFromFirebase() {
        progressBar.visibility = View.VISIBLE
        val database = FirebaseDatabase.getInstance("https://speedtrackerlab3-default-rtdb.europe-west1.firebasedatabase.app")
            .getReference("speed_tracking")

        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                dataList.clear()
                for (dataSnapshot in snapshot.children) {
                    val data = dataSnapshot.getValue(TrackingData::class.java)
                    data?.let { dataList.add(it) }
                }
                
                progressBar.visibility = View.GONE
                
                if (dataList.isEmpty()) {
                    Toast.makeText(this@MapActivity, "Немає даних для відображення", Toast.LENGTH_SHORT).show()
                } else {
                    updateMapMarkers()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MapActivity, "Помилка: ${error.message}", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
            }
        })
    }

    private fun updateMapMarkers() {
        if (!::map.isInitialized) return
        
        map.clear()
        
        if (dataList.isEmpty()) return
        
        var firstLocation: LatLng? = null
        
        // Додаємо маркери для всіх записів
        dataList.forEach { data ->
            if (data.latitude != 0.0 && data.longitude != 0.0) {
                val location = LatLng(data.latitude, data.longitude)
                
                if (firstLocation == null) {
                    firstLocation = location
                }
                
                val marker = MarkerOptions()
                    .position(location)
                    .title("Швидкість: ${String.format(Locale.US, "%.1f", data.speedKmh)} км/год")
                    .snippet("Час: ${data.timestamp}")
                
                map.addMarker(marker)
            }
        }
        
        // Переміщуємо камеру до першої точки
        firstLocation?.let {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 12f))
        }
    }
}
