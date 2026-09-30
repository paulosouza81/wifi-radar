package com.radar.stealth

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class WifiTarget(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val freq: Int,
    val caps: String,
    val distanceM: Double,
    val isHotspotPhone: Boolean,
    val clientsHint: String
)

class WifiRadar(private val ctx: Context) {
    private val wifi: WifiManager =
        ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _targets = MutableStateFlow<List<WifiTarget>>(emptyList())
    val targets: StateFlow<List<WifiTarget>> = _targets

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                @SuppressLint("MissingPermission")
                val res = try { wifi.scanResults } catch (_: Exception) { emptyList() }
                _targets.value = res.map { it.toTarget() }.sortedByDescending { it.rssi }
            }
        }
    }

    fun start() {
        ctx.registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        scan()
    }

    fun stop() { try { ctx.unregisterReceiver(receiver) } catch (_: Exception) {} }

    fun scan(): Boolean {
        return try { wifi.startScan() } catch (_: Exception) { false }
    }

    private fun ScanResult.toTarget(): WifiTarget {
        val d = rssiToDistance(level, frequency)
        // heurística hotspot de celular: SSID típico Android/iPhone ou OUI + sinal forte e móvel
        val ssidLow = (SSID ?: "").lowercase()
        val hotspotSig = ssidLow.contains("android") || ssidLow.contains("iphone") ||
                ssidLow.contains("moto") || ssidLow.contains("xiaomi") ||
                ssidLow.contains("galaxy") || capabilities.contains("[IBSS]")
        return WifiTarget(
            ssid = if (SSID.isNullOrEmpty()) "<oculta>" else SSID,
            bssid = BSSID ?: "??:??:??:??:??:??",
            rssi = level,
            freq = frequency,
            caps = capabilities ?: "",
            distanceM = d,
            isHotspotPhone = hotspotSig,
            clientsHint = capsToSec(capabilities ?: "")
        )
    }

    companion object {
        fun rssiToDistance(rssi: Int, freqMhz: Int): Double {
            // modelo log-distance, n=2.7 indoor/coletivo
            val n = 2.7
            val txPower = -45 // RSSI a 1m típico
            return Math.pow(10.0, (txPower - rssi) / (10 * n))
        }
        fun capsToSec(caps: String): String {
            return when {
                caps.contains("WPA3") -> "WPA3-SAE"
                caps.contains("WPA2") -> "WPA2-PSK"
                caps.contains("WPA") -> "WPA-PSK"
                caps.contains("WEP") -> "WEP"
                caps.contains("ESS") && !caps.contains("WPA") && !caps.contains("WEP") -> "ABERTA"
                else -> caps
            }
        }
        fun ouiVendor(bssid: String): String {
            val oui = bssid.take(8).uppercase()
            return when {
                oui.startsWith("8A:") -> "possível hotspot Android (MAC randômico)"
                else -> "OUI $oui — consulta em wireshark OUI db"
            }
        }
    }
}
