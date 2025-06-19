package com.example.vacuumcontroller

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class DeviceAdapter(
    private val devices: MutableList<BluetoothDevice> = mutableListOf(),
    private val onDeviceClick: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    private var isConnecting = false

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val deviceName: TextView = view.findViewById(R.id.deviceName)
        val deviceAddress: TextView = view.findViewById(R.id.deviceAddress)
        val connectButton: MaterialButton = view.findViewById(R.id.connectButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        val context = holder.itemView.context
        
        // Check permissions before accessing device name
        val deviceName = if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED || 
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S
        ) {
            device.name ?: "GIGA-Chat"
        } else {
            "GIGA-Chat"
        }
        
        holder.deviceName.text = deviceName
        holder.deviceAddress.text = device.address
        
        holder.connectButton.isEnabled = !isConnecting
        holder.connectButton.text = if (isConnecting) "Connettendo..." else "Connetti"
        
        holder.connectButton.setOnClickListener {
            onDeviceClick(device)
        }
    }

    override fun getItemCount(): Int = devices.size

    fun updateDevices(newDevices: List<BluetoothDevice>) {
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }
    
    fun addDevice(device: BluetoothDevice) {
        if (!devices.any { it.address == device.address }) {
            devices.add(device)
            notifyItemInserted(devices.size - 1)
        }
    }
    
    fun setConnecting(connecting: Boolean) {
        isConnecting = connecting
        notifyDataSetChanged()
    }
    
    fun clear() {
        devices.clear()
        notifyDataSetChanged()
    }
}