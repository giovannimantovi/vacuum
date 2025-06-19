package com.example.vacuumcontroller

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Button;

class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothService: BluetoothService
    private lateinit var deviceAdapter: DeviceAdapter
    private val handler = Handler(Looper.getMainLooper())
    
    // Current state
    private var currentState = VacuumState()
    private var isConnected = false
    private var isScanning = false
    
    // UI Views
    private lateinit var connectionIndicator: CardView
    private lateinit var connectionStatus: TextView
    private lateinit var searchButton: Button
    private lateinit var deviceRecyclerView: RecyclerView
    private lateinit var errorCard: CardView
    private lateinit var errorText: TextView
    
    private lateinit var statusCard: CardView
    private lateinit var powerIndicator: CardView
    private lateinit var powerIcon: ImageView
    private lateinit var statusText: TextView
    private lateinit var infoContainer: View
    
    private lateinit var controlsCard: CardView
    private lateinit var powerOnButton: Button
    private lateinit var powerOffButton: Button
    private lateinit var levelControls: View
    private lateinit var decreaseButton: Button
    private lateinit var increaseButton: Button
    
    private lateinit var logCard: CardView
    private lateinit var logText: TextView
    private lateinit var stopButton: Button
    
    // Info tiles
    private lateinit var levelInfo: View
    private lateinit var powerInfo: View
    private lateinit var sensorInfo: View
    
    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            initializeBluetooth()
        } else {
            showError("Permessi Bluetooth necessari per il funzionamento dell'app")
        }
    }
    
    // Bluetooth enable launcher
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (bluetoothService.isBluetoothEnabled()) {
            initializeBluetooth()
        } else {
            showError("Bluetooth deve essere abilitato")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initializeViews()
        setupRecyclerView()
        setupClickListeners()
        
        bluetoothService = BluetoothService(this)
        setupBluetoothCallbacks()
        
        checkPermissionsAndInitialize()
    }
    
    private fun initializeViews() {
        // Connection views
        connectionIndicator = findViewById(R.id.connectionIndicator)
        connectionStatus = findViewById(R.id.connectionStatus)
        searchButton = findViewById(R.id.searchButton)
        deviceRecyclerView = findViewById(R.id.deviceRecyclerView)
        errorCard = findViewById(R.id.errorCard)
        errorText = findViewById(R.id.errorText)
        
        // Status views
        statusCard = findViewById(R.id.statusCard)
        powerIndicator = findViewById(R.id.powerIndicator)
        powerIcon = findViewById(R.id.powerIcon)
        statusText = findViewById(R.id.statusText)
        infoContainer = findViewById(R.id.infoContainer)
        
        // Control views
        controlsCard = findViewById(R.id.controlsCard)
        powerOnButton = findViewById(R.id.powerOnButton)
        powerOffButton = findViewById(R.id.powerOffButton)
        levelControls = findViewById(R.id.levelControls)
        decreaseButton = findViewById(R.id.decreaseButton)
        increaseButton = findViewById(R.id.increaseButton)
        
        // Log views
        logCard = findViewById(R.id.logCard)
        logText = findViewById(R.id.logText)
        stopButton = findViewById(R.id.stopButton)
        
        // Info tiles
        levelInfo = findViewById(R.id.levelInfo)
        powerInfo = findViewById(R.id.powerInfo)
        sensorInfo = findViewById(R.id.sensorInfo)
    }
    
    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter { device ->
            connectToDevice(device)
        }
        deviceRecyclerView.layoutManager = LinearLayoutManager(this)
        deviceRecyclerView.adapter = deviceAdapter
    }
    
    private fun setupClickListeners() {
        searchButton.setOnClickListener {
            if (isScanning) {
                stopScan()
            } else {
                startScan()
            }
        }
        
        powerOnButton.setOnClickListener {
            sendCommand("1")
        }
        
        powerOffButton.setOnClickListener {
            sendCommand("0")
        }
        
        increaseButton.setOnClickListener {
            sendCommand("3")
        }
        
        decreaseButton.setOnClickListener {
            sendCommand("4")
        }
        
        stopButton.setOnClickListener {
            bluetoothService.disconnect()
        }
    }
    
    private fun setupBluetoothCallbacks() {
        bluetoothService.setScanCallback(object : BluetoothService.ScanCallback {
            override fun onDeviceFound(device: BluetoothDevice) {
                runOnUiThread {
                    deviceAdapter.addDevice(device)
                    deviceRecyclerView.visibility = View.VISIBLE
                }
            }
            
            override fun onScanComplete() {
                runOnUiThread {
                    setScanningState(false)
                }
            }
            
            override fun onError(error: String) {
                runOnUiThread {
                    showError(error)
                    setScanningState(false)
                }
            }
        })
        
        bluetoothService.setConnectionCallback(object : BluetoothService.ConnectionCallback {
            override fun onConnected() {
                runOnUiThread {
                    setConnectionState(true)
                    deviceAdapter.setConnecting(false)
                    deviceRecyclerView.visibility = View.GONE
                    showConnectedUI()
                    
                    // Request initial status
                    handler.postDelayed({
                        sendCommand("STATUS")
                    }, 500)
                }
            }
            
            override fun onDisconnected() {
                runOnUiThread {
                    setConnectionState(false)
                    hideConnectedUI()
                    logText.text = "Connessione persa"
                }
            }
            
            override fun onDataReceived(data: String) {
                runOnUiThread {
                    updateLog(data)
                    parseAndUpdateState(data)
                }
            }
            
            override fun onError(error: String) {
                runOnUiThread {
                    showError(error)
                    deviceAdapter.setConnecting(false)
                }
            }
        })
    }
    
    private fun checkPermissionsAndInitialize() {
        val requiredPermissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
        
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            initializeBluetooth()
        }
    }
    
    private fun initializeBluetooth() {
        if (!bluetoothService.isBluetoothEnabled()) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        }
    }
    
    private fun startScan() {
        hideError()
        deviceAdapter.clear()
        setScanningState(true)
        bluetoothService.startScan()
    }
    
    private fun stopScan() {
        bluetoothService.stopScan()
        setScanningState(false)
    }
    
    private fun connectToDevice(device: BluetoothDevice) {
        deviceAdapter.setConnecting(true)
        bluetoothService.connectToDevice(device)
    }
    
    private fun sendCommand(command: String) {
        if (isConnected) {
            bluetoothService.sendCommand(command)
        }
    }
    
    private fun setScanningState(scanning: Boolean) {
        isScanning = scanning
        searchButton.text = if (scanning) "Interrompi ricerca" else "Cerca GIGA-Chat"
        searchButton.isEnabled = true
        
        if (scanning) {
            startConnectionAnimation()
        } else {
            stopConnectionAnimation()
        }
    }
    
    private fun setConnectionState(connected: Boolean) {
        isConnected = connected
        connectionStatus.text = if (connected) "Connesso" else "Disconnesso"
        
        // Update connection indicator
        val indicatorColor = if (connected) Color.parseColor("#4CAF50") else Color.parseColor("#2196F3")
        val backgroundColor = Color.argb(51, Color.red(indicatorColor), Color.green(indicatorColor), Color.blue(indicatorColor))
        
        connectionIndicator.setCardBackgroundColor(backgroundColor)
        connectionStatus.setTextColor(if (connected) Color.parseColor("#4CAF50") else Color.WHITE)
        
        searchButton.visibility = if (connected) View.GONE else View.VISIBLE
        
        if (connected) {
            stopConnectionAnimation()
        }
    }
    
    private fun showConnectedUI() {
        statusCard.visibility = View.VISIBLE
        controlsCard.visibility = View.VISIBLE
        logCard.visibility = View.VISIBLE
        
        // Animate cards appearing
        animateCardIn(statusCard)
        handler.postDelayed({ animateCardIn(controlsCard) }, 100)
        handler.postDelayed({ animateCardIn(logCard) }, 200)
    }
    
    private fun hideConnectedUI() {
        statusCard.visibility = View.GONE
        controlsCard.visibility = View.GONE
        logCard.visibility = View.GONE
    }
    
    private fun parseAndUpdateState(message: String) {
        val newState = VacuumState.parseFromMessage(message)
        if (newState != null) {
            val stateChanged = currentState.stato != newState.stato
            val levelChanged = currentState.livello != newState.livello
            
            currentState = newState
            updateStatusUI(stateChanged, levelChanged)
        }
    }
    
    private fun updateStatusUI(stateChanged: Boolean, levelChanged: Boolean) {
        // Update power indicator
        if (stateChanged) {
            updatePowerIndicator()
        }
        
        // Update status text
        statusText.text = currentState.getStatusText()
        statusText.setTextColor(currentState.getStatusColor())
        
        // Update controls
        updateControlButtons()
        
        // Update info tiles
        if (currentState.isOn()) {
            infoContainer.visibility = View.VISIBLE
            levelControls.visibility = View.VISIBLE
            updateInfoTiles(levelChanged)
        } else {
            infoContainer.visibility = View.GONE
            levelControls.visibility = View.GONE
        }
    }
    
    private fun updatePowerIndicator() {
        val isOn = currentState.isOn()
        val color = if (isOn) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
        val backgroundColor = Color.argb(76, Color.red(color), Color.green(color), Color.blue(color))
        
        powerIndicator.setCardBackgroundColor(backgroundColor)
        powerIcon.setImageResource(if (isOn) R.drawable.ic_power_on else R.drawable.ic_power_off)
        powerIcon.setColorFilter(color)
        
        if (isOn) {
            animatePowerOn()
        }
    }
    
    private fun updateControlButtons() {
        powerOnButton.isEnabled = !currentState.isOn()
        powerOffButton.isEnabled = currentState.isOn()
        decreaseButton.isEnabled = currentState.canDecrease()
        increaseButton.isEnabled = currentState.canIncrease()
    }
    
    private fun updateInfoTiles(levelChanged: Boolean) {
        // Level info
        updateInfoTile(levelInfo, "Livello", currentState.livello.toString(), 
            R.drawable.ic_tune, Color.parseColor("#2196F3"))
        
        // Power info  
        updateInfoTile(powerInfo, "Potenza", "${currentState.potenza} W",
            R.drawable.ic_power_on, Color.parseColor("#FF9800"))
        
        // Sensor info
        updateInfoTile(sensorInfo, "Sensore", String.format("%04d", currentState.sensore),
            R.drawable.ic_analytics, Color.parseColor("#9C27B0"))
        
        if (levelChanged) {
            animateLevelChange()
        }
    }
    
    private fun updateInfoTile(tileView: View, title: String, value: String, iconRes: Int, color: Int) {
        val titleView = tileView.findViewById<TextView>(R.id.infoTitle)
        val valueView = tileView.findViewById<TextView>(R.id.infoValue)
        val iconView = tileView.findViewById<ImageView>(R.id.infoIcon)
        val indicatorView = tileView.findViewById<View>(R.id.infoIndicator)
        val cardView = tileView as CardView
        
        titleView.text = title
        valueView.text = value
        iconView.setImageResource(iconRes)
        iconView.setColorFilter(color)
        indicatorView.setBackgroundColor(color)
        
        val backgroundColor = Color.argb(38, Color.red(color), Color.green(color), Color.blue(color))
        cardView.setCardBackgroundColor(backgroundColor)
    }
    
    private fun showError(error: String) {
        errorText.text = error
        errorCard.visibility = View.VISIBLE
        
        // Hide error after 5 seconds
        handler.postDelayed({
            hideError()
        }, 5000)
    }
    
    private fun hideError() {
        errorCard.visibility = View.GONE
    }
    
    private fun updateLog(message: String) {
        logText.text = message
    }
    
    // Animation methods
    private fun startConnectionAnimation() {
        val scaleAnimation = ObjectAnimator.ofFloat(connectionIndicator, "scaleX", 1.0f, 1.1f, 1.0f)
        scaleAnimation.duration = 1500
        scaleAnimation.repeatCount = ValueAnimator.INFINITE
        scaleAnimation.interpolator = AccelerateDecelerateInterpolator()
        
        val scaleAnimationY = ObjectAnimator.ofFloat(connectionIndicator, "scaleY", 1.0f, 1.1f, 1.0f)
        scaleAnimationY.duration = 1500
        scaleAnimationY.repeatCount = ValueAnimator.INFINITE
        scaleAnimationY.interpolator = AccelerateDecelerateInterpolator()
        
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleAnimation, scaleAnimationY)
        animatorSet.start()
        
        connectionIndicator.tag = animatorSet
    }
    
    private fun stopConnectionAnimation() {
        val animatorSet = connectionIndicator.tag as? AnimatorSet
        animatorSet?.cancel()
        connectionIndicator.scaleX = 1.0f
        connectionIndicator.scaleY = 1.0f
    }
    
    private fun animatePowerOn() {
        val rotationAnimation = ObjectAnimator.ofFloat(powerIcon, "rotation", 0f, 360f)
        rotationAnimation.duration = 800
        rotationAnimation.interpolator = AccelerateDecelerateInterpolator()
        rotationAnimation.start()
    }
    
    private fun animateLevelChange() {
        val scaleAnimation = ObjectAnimator.ofFloat(levelInfo, "scaleX", 1.0f, 1.03f, 1.0f)
        scaleAnimation.duration = 600
        scaleAnimation.interpolator = BounceInterpolator()
        
        val scaleAnimationY = ObjectAnimator.ofFloat(levelInfo, "scaleY", 1.0f, 1.03f, 1.0f)
        scaleAnimationY.duration = 600
        scaleAnimationY.interpolator = BounceInterpolator()
        
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleAnimation, scaleAnimationY)
        animatorSet.start()
    }
    
    private fun animateCardIn(card: View) {
        card.alpha = 0f
        card.translationY = 50f
        
        val fadeIn = ObjectAnimator.ofFloat(card, "alpha", 0f, 1f)
        val slideIn = ObjectAnimator.ofFloat(card, "translationY", 50f, 0f)
        
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(fadeIn, slideIn)
        animatorSet.duration = 300
        animatorSet.interpolator = AccelerateDecelerateInterpolator()
        animatorSet.start()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        bluetoothService.disconnect()
    }
}