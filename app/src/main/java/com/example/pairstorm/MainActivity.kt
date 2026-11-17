package com.example.pairstorm

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val requestCode = 41
    private lateinit var adapter: BluetoothAdapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var scanCallback: ScanCallback? = null
    private var originalAdapterName: String? = null
    private lateinit var nameInput: EditText
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var scanButton: Button
    private lateinit var stopScanButton: Button
    private val logLines = ArrayDeque<String>()

    // A dedicated custom service UUID identifies this lab beacon.
    // This is NOT a Google Fast Pair service UUID or Fast Pair payload.
    private val labServiceUuid = UUID.fromString("7d2a1000-7a6b-4c9e-9f31-2d7a6a110001")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        adapter = manager.adapter
        advertiser = adapter.bluetoothLeAdvertiser
        scanner = adapter.bluetoothLeScanner
        buildUi()
        checkAndRequestPermissions()
        refreshBluetoothState()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 18)
            setBackgroundColor(0xFF101820.toInt())
        }
        val title = TextView(this).apply {
            text = "PAIRSTORM"
            textSize = 28f
            setTextColor(0xFF64D8CB.toInt())
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "BLE Security Lab • Android 15"
            textSize = 13f
            setTextColor(0xFFB8C7CC.toInt())
            gravity = Gravity.CENTER
        }
        nameInput = EditText(this).apply {
            hint = "Назва тестового маяка"
            setSingleLine(true)
            setText("PairStorm Lab")
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF8A9AA0.toInt())
            setPadding(14, 8, 14, 8)
            setBackgroundColor(0xFF263640.toInt())
        }
        status = TextView(this).apply {
            text = "Статус: перевірка Bluetooth…"
            textSize = 14f
            setTextColor(0xFFFFD166.toInt())
            setPadding(0, 12, 0, 12)
        }
        startButton = makeButton("START BLE ADVERTISING")
        stopButton = makeButton("STOP ADVERTISING").apply { isEnabled = false }
        scanButton = makeButton("START SCANNER")
        stopScanButton = makeButton("STOP SCANNER").apply { isEnabled = false }
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        buttons.addView(startButton)
        buttons.addView(stopButton)
        buttons.addView(scanButton)
        buttons.addView(stopScanButton)

        val logTitle = TextView(this).apply {
            text = "EVENT LOG"
            textSize = 14f
            setTextColor(0xFF64D8CB.toInt())
            setPadding(0, 16, 0, 6)
        }
        logView = TextView(this).apply {
            text = "Очікування подій…"
            textSize = 12f
            setTextColor(0xFFE6EEF0.toInt())
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFF1B2A33.toInt())
        }
        val scroll = ScrollView(this).apply { addView(logView) }

        root.addView(title)
        root.addView(subtitle)
        root.addView(space(14))
        root.addView(nameInput)
        root.addView(status)
        root.addView(buttons)
        root.addView(logTitle)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)

        startButton.setOnClickListener { startAdvertising() }
        stopButton.setOnClickListener { stopAdvertising() }
        scanButton.setOnClickListener { startScanning() }
        stopScanButton.setOnClickListener { stopScanning() }
    }

    private fun makeButton(label: String) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(0xFF101820.toInt())
        setBackgroundColor(0xFF64D8CB.toInt())
        val p = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        p.bottomMargin = 7
        layoutParams = p
    }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, height)
    }

    private fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= 31) arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        ) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun checkAndRequestPermissions() {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), requestCode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == this.requestCode) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                refreshBluetoothState()
                log("Дозволи надано.")
            } else {
                status.text = "Статус: потрібні дозволи Bluetooth."
                log("Немає необхідних дозволів.")
            }
        }
    }

    private fun hasPermissions() = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun refreshBluetoothState() {
        if (!adapter.isEnabled) {
            status.text = "Статус: увімкни Bluetooth."
        } else if (!adapter.isMultipleAdvertisementSupported) {
            status.text = "Статус: BLE advertising не підтримується цим пристроєм."
        } else {
            status.text = "Статус: Bluetooth готовий."
            log("Bluetooth увімкнений; BLE advertising доступний.")
        }
    }

    private fun startAdvertising() {
        if (!hasPermissions()) { checkAndRequestPermissions(); return }
        if (!adapter.isEnabled) { status.text = "Увімкни Bluetooth."; return }
        val adv = adapter.bluetoothLeAdvertiser
        if (adv == null || !adapter.isMultipleAdvertisementSupported) {
            status.text = "Цей телефон не підтримує BLE advertising."
            log("Помилка: BLE advertiser недоступний.")
            return
        }
        val label = nameInput.text.toString().trim().take(18)
        if (label.isBlank()) { nameInput.error = "Введи назву"; return }

        try {
            // Android's legacy advertiser exposes the adapter's local name in the
            // advertising data. Restore the previous Bluetooth name when stopped.
            originalAdapterName = adapter.name
            adapter.name = label

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(false)
                .setTimeout(0)
                .build()

            // Keep the 128-bit service UUID in the primary packet and put the
            // local name in the scan response to stay within legacy 31-byte limits.
            val data = AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid(labServiceUuid))
                .build()
            val scanResponse = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build()

            advertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    runOnUiThread {
                        status.text = "Статус: BLE-реклама активна • $label"
                        startButton.isEnabled = false
                        stopButton.isEnabled = true
                        log("Advertising START: \"$label\" (custom lab beacon).")
                    }
                }
                override fun onStartFailure(errorCode: Int) {
                    runOnUiThread {
                        status.text = "Помилка запуску BLE: $errorCode"
                        log("Advertising FAILED: код $errorCode.")
                        restoreAdapterName()
                    }
                }
            }
            adv.startAdvertising(settings, data, scanResponse, advertiseCallback)
        } catch (e: SecurityException) {
            status.text = "Немає дозволу Bluetooth Connect/Advertise."
            log("SecurityException: ${e.message}")
        } catch (e: Exception) {
            status.text = "Помилка: ${e.message}"
            log("Помилка запуску: ${e.message}")
        }
    }

    private fun stopAdvertising() {
        try {
            advertiseCallback?.let { adapter.bluetoothLeAdvertiser?.stopAdvertising(it) }
        } catch (_: Exception) {}
        advertiseCallback = null
        restoreAdapterName()
        startButton.isEnabled = true
        stopButton.isEnabled = false
        status.text = "Статус: BLE-реклама зупинена."
        log("Advertising STOP.")
    }

    private fun restoreAdapterName() {
        try {
            originalAdapterName?.let { adapter.name = it }
        } catch (_: Exception) {}
        originalAdapterName = null
    }

    private fun startScanning() {
        if (!hasPermissions()) { checkAndRequestPermissions(); return }
        val bleScanner = adapter.bluetoothLeScanner
        if (bleScanner == null) { log("Scanner недоступний."); return }
        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(labServiceUuid)).build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val deviceName = try { result.device.name ?: result.scanRecord?.deviceName ?: "без назви" }
                    catch (_: SecurityException) { "назва недоступна" }
                val line = "Знайдено: $deviceName | RSSI ${result.rssi} dBm"
                runOnUiThread { log(line) }
            }
            override fun onScanFailed(errorCode: Int) {
                runOnUiThread { log("Scan FAILED: код $errorCode") }
            }
        }
        try {
            bleScanner.startScan(filters, settings, scanCallback)
            scanButton.isEnabled = false
            stopScanButton.isEnabled = true
            log("Scanner START: пошук маяків PairStorm.")
        } catch (e: Exception) {
            log("Не вдалося запустити scanner: ${e.message}")
        }
    }

    private fun stopScanning() {
        try { scanCallback?.let { adapter.bluetoothLeScanner?.stopScan(it) } }
        catch (_: Exception) {}
        scanCallback = null
        scanButton.isEnabled = true
        stopScanButton.isEnabled = false
        log("Scanner STOP.")
    }

    private fun log(message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logLines.addFirst("[$stamp] $message")
        while (logLines.size > 80) logLines.removeLast()
        if (::logView.isInitialized) logView.text = logLines.joinToString("\n")
    }

    override fun onDestroy() {
        stopScanning()
        stopAdvertising()
        super.onDestroy()
    }
}
