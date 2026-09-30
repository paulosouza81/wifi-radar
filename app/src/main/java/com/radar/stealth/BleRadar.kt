package com.radar.stealth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class BleTarget(
    val name: String,
    val mac: String,
    val rssi: Int,
    val distanceM: Double,
    val type: String
)

class BleRadar(private val ctx: Context) {
    private val _targets = MutableStateFlow<Map<String, BleTarget>>(emptyMap())
    val targets: StateFlow<Map<String, BleTarget>> = _targets

    private val bt by lazy {
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val scanner get() = bt?.bluetoothLeScanner

    private val cb = object : ScanCallback() {
        override fun onScanResult(t: Int, r: ScanResult) {
            @SuppressLint("MissingPermission")
            val dev = try { r.device } catch (_: Exception) { return }
            val mac = try { dev.address } catch (_: Exception) { return }
            val name = try { dev.name ?: r.scanRecord?.deviceName ?: "desconhecido" } catch (_: Exception) { "desconhecido" }
            val entry = BleTarget(
                name = name,
                mac = mac,
                rssi = r.rssi,
                distanceM = WifiRadar.rssiToDistance(r.rssi, 2400),
                type = guessType(name)
            )
            _targets.value = _targets.value + (mac to entry)
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        try {
            val s = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            // scan passivo: não pede scan response ativa extra, só escuta beacons
            scanner?.startScan(null, s, cb)
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    fun stop() { try { scanner?.stopScan(cb) } catch (_: Exception) {} }

    private fun guessType(name: String): String {
        val n = name.lowercase()
        return when {
            "iphone" in n || "ipad" in n -> "iPhone (BLE continuity)"
            "galaxy" in n || "pixel" in n || "moto" in n || "xiaomi" in n || "redmi" in n -> "Android phone"
            "watch" in n || "mi band" in n || "amazfit" in n -> "wearable"
            "airpods" in n || "buds" in n -> "fone"
            else -> "BLE genérico"
        }
    }
}
