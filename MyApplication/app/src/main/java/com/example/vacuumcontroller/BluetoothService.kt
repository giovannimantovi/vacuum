package com.example.vacuumcontroller

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.app.ActivityCompat
import java.util.*

class BluetoothService(private val context: Context) {
    
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    
    private val bluetoothLeScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner
    
    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false
    
    // UUID Arduino GIGA (stessi del Flutter)
    companion object {
        private val SERVICE_UUID = UUID.fromString("19B10000-E8F2-537E-4F6C-D104768A1214")
        private val RX_CHAR_UUID = UUID.fromString("19B10001-E8F2-537E-4F6C-D104768A1214")
        private val TX_CHAR_UUID = UUID.fromString("19B10002-E8F2-537E-4F6C-D104768A1214")
        private const val SCAN_PERIOD: Long = 10000
    }
    
    // Callback interfaces
    interface ScanCallback {
        fun onDeviceFound(device: BluetoothDevice)
        fun onScanComplete()
        fun onError(error: String)
    }
    
    interface ConnectionCallback {
        fun onConnected()
        fun onDisconnected()
        fun onDataReceived(data: String)
        fun onError(error: String)
    }
    
    private var scanCallback: ScanCallback? = null
    private var connectionCallback: ConnectionCallback? = null
    
    fun setScanCallback(callback: ScanCallback) {
        this.scanCallback = callback
    }
    
    fun setConnectionCallback(callback: ConnectionCallback) {
        this.connectionCallback = callback
    }
    
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    fun hasPermissions(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun startScan() {
        if (!hasPermissions()) {
            scanCallback?.onError("Permessi Bluetooth mancanti")
            return
        }
        
        if (!isBluetoothEnabled()) {
            scanCallback?.onError("Bluetooth non abilitato")
            return
        }
        
        if (isScanning) return
        
        val scanner = bluetoothLeScanner ?: run {
            scanCallback?.onError("Scanner Bluetooth non disponibile")
            return
        }
        
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        isScanning = true
        
        try {
            scanner.startScan(listOf(scanFilter), scanSettings, leScanCallback)
            
            // Stop scan after SCAN_PERIOD
            handler.postDelayed({
                stopScan()
            }, SCAN_PERIOD)
        } catch (e: SecurityException) {
            scanCallback?.onError("Errore sicurezza: ${e.message}")
            isScanning = false
        }
    }
    
    fun stopScan() {
        if (!isScanning) return
        
        if (hasPermissions()) {
            try {
                bluetoothLeScanner?.stopScan(leScanCallback)
            } catch (e: SecurityException) {
                // Ignora errori di sicurezza durante lo stop
            }
        }
        
        isScanning = false
        scanCallback?.onScanComplete()
    }
    
    private val leScanCallback = object : android.bluetooth.le.ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            scanCallback?.onDeviceFound(result.device)
        }
        
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            isScanning = false
            scanCallback?.onError("Scan fallito: $errorCode")
        }
    }
    
    fun connectToDevice(device: BluetoothDevice) {
        if (!hasPermissions()) {
            connectionCallback?.onError("Permessi Bluetooth mancanti")
            return
        }
        
        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        } catch (e: SecurityException) {
            connectionCallback?.onError("Errore sicurezza: ${e.message}")
        }
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (hasPermissions()) {
                        try {
                            gatt.discoverServices()
                        } catch (e: SecurityException) {
                            connectionCallback?.onError("Errore sicurezza: ${e.message}")
                        }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    handler.post {
                        connectionCallback?.onDisconnected()
                    }
                    cleanup()
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    rxCharacteristic = service.getCharacteristic(RX_CHAR_UUID)
                    txCharacteristic = service.getCharacteristic(TX_CHAR_UUID)
                    
                    if (rxCharacteristic != null && txCharacteristic != null) {
                        // Abilita notifiche per TX characteristic
                        enableNotifications(txCharacteristic!!)
                        
                        handler.post {
                            connectionCallback?.onConnected()
                        }
                    } else {
                        handler.post {
                            connectionCallback?.onError("Caratteristiche non trovate")
                        }
                    }
                } else {
                    handler.post {
                        connectionCallback?.onError("Servizio non trovato")
                    }
                }
            } else {
                handler.post {
                    connectionCallback?.onError("Errore scoperta servizi")
                }
            }
        }
        
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicChanged(gatt, characteristic)
            
            if (characteristic.uuid == TX_CHAR_UUID) {
                val data = String(characteristic.value)
                handler.post {
                    connectionCallback?.onDataReceived(data)
                }
            }
        }
        
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)
            // Notifiche abilitate con successo
        }
    }
    
    private fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
        if (!hasPermissions()) return
        
        try {
            bluetoothGatt?.setCharacteristicNotification(characteristic, true)
            
            val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            descriptor?.let {
                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                bluetoothGatt?.writeDescriptor(it)
            }
        } catch (e: SecurityException) {
            connectionCallback?.onError("Errore sicurezza: ${e.message}")
        }
    }
    
    fun sendCommand(command: String) {
        if (!hasPermissions()) {
            connectionCallback?.onError("Permessi Bluetooth mancanti")
            return
        }
        
        val characteristic = rxCharacteristic ?: run {
            connectionCallback?.onError("Caratteristica RX non disponibile")
            return
        }
        
        try {
            characteristic.value = command.toByteArray()
            val success = bluetoothGatt?.writeCharacteristic(characteristic) ?: false
            
            if (!success) {
                connectionCallback?.onError("Errore invio comando")
            }
        } catch (e: SecurityException) {
            connectionCallback?.onError("Errore sicurezza: ${e.message}")
        }
    }
    
    fun disconnect() {
        if (hasPermissions()) {
            try {
                bluetoothGatt?.disconnect()
            } catch (e: SecurityException) {
                // Ignora errori di sicurezza durante la disconnessione
            }
        }
        cleanup()
    }
    
    private fun cleanup() {
        if (hasPermissions()) {
            try {
                bluetoothGatt?.close()
            } catch (e: SecurityException) {
                // Ignora errori di sicurezza durante la pulizia
            }
        }
        bluetoothGatt = null
        rxCharacteristic = null
        txCharacteristic = null
    }
    
    fun isConnected(): Boolean {
        return bluetoothGatt != null && rxCharacteristic != null && txCharacteristic != null
    }
}