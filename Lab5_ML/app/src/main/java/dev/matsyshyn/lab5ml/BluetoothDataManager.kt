package dev.matsyshyn.lab5ml

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import java.util.*

class BluetoothDataManager(private val context: Context) {
    
    private val SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
    private val CHAR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef1")
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null
    private val scanHandler = Handler(Looper.getMainLooper())
    
    var onDataReceived: ((String) -> Unit)? = null // Callback для отриманих даних
    var onStatusChanged: ((String) -> Unit)? = null // Callback для зміни статусу
    
    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
    }
    
    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            onStatusChanged?.invoke("❌ Bluetooth вимкнено")
            return
        }
        
        onStatusChanged?.invoke("🔍 Scanning for Server...")
        
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            onStatusChanged?.invoke("❌ BLE Scanner недоступний")
            return
        }
        
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        scanCallback = object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                Log.d("Lab5", "Device found: ${device.name ?: device.address}")
                
                scanner.stopScan(this)
                scanCallback = null
                
                onStatusChanged?.invoke("🔗 Connecting to ${device.name ?: device.address}...")
                
                @SuppressLint("MissingPermission")
                bluetoothGatt = device.connectGatt(context, false, gattCallback)
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                scanCallback = null
                onStatusChanged?.invoke("❌ Scan failed: $errorCode")
            }
        }
        
        @SuppressLint("MissingPermission")
        scanner.startScan(listOf(filter), settings, scanCallback)
        
        scanHandler.postDelayed({
            @SuppressLint("MissingPermission")
            if (scanCallback != null) {
                scanner.stopScan(scanCallback)
                scanCallback = null
                onStatusChanged?.invoke("⏱️ Scan timeout")
            }
        }, 15000)
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                onStatusChanged?.invoke("🔗 Connected! Discovering services...")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                onStatusChanged?.invoke("❌ Disconnected")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val charac = gatt.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID)
                if (charac != null) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        gatt.requestMtu(512)
                    } else {
                        subscribeToNotifications(gatt, charac)
                    }
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val charac = gatt.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID)
                if (charac != null) {
                    subscribeToNotifications(gatt, charac)
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        private fun subscribeToNotifications(gatt: BluetoothGatt, charac: BluetoothGattCharacteristic) {
            gatt.setCharacteristicNotification(charac, true)
            val desc = charac.getDescriptor(CCCD_UUID)
            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(desc)
            onStatusChanged?.invoke("✅ Receiving Data!")
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val rawString = String(characteristic.value, Charsets.UTF_8)
            onDataReceived?.invoke(rawString)
        }
    }
    
    fun stop() {
        scanCallback?.let {
            @SuppressLint("MissingPermission")
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(it)
        }
        scanHandler.removeCallbacksAndMessages(null)
        @SuppressLint("MissingPermission")
        bluetoothGatt?.close()
    }
}

