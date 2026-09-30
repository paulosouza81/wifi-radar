package com.radar.stealth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var wifiRadar: WifiRadar
    private lateinit var bleRadar: BleRadar
    private val scope = MainScope()
    private lateinit var log: TextView
    private lateinit var listWifi: TextView
    private lateinit var listBle: TextView

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val btnScan = Button(this).apply { text = "ESCANEAR (WiFi + BLE)" }
        val btnSvc = Button(this).apply { text = "MODO FANTASMA (serviço passivo)" }
        val btnRoot = Button(this).apply { text = "CAPTURAR PMKID (root+OTG)" }
        val inputBssid = EditText(this).apply { hint = "BSSID alvo ex: AA:BB:CC:DD:EE:FF" }
        log = TextView(this).apply { text = "root: ${RootShell.isRooted()}\n" }
        listWifi = TextView(this).apply { text = "wifi: --" }
        listBle = TextView(this).apply { text = "ble: --" }
        val scroll = ScrollView(this)
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        inner.addView(btnScan); inner.addView(btnSvc); inner.addView(inputBssid)
        inner.addView(btnRoot); inner.addView(log); inner.addView(listWifi); inner.addView(listBle)
        scroll.addView(inner); root.addView(scroll)
        setContentView(root)

        wifiRadar = WifiRadar(this)
        bleRadar = BleRadar(this)
        wifiRadar.start()
        bleRadar.start()

        // permissões em runtime: pedir LOCATION + BT_SCAN + BT_CONNECT antes em produção
        scope.launch {
            wifiRadar.targets.collect { list ->
                val txt = list.take(20).joinToString("\n") {
                    "${it.ssid} | ${it.bssid} | ${it.rssi}dBm ~${"%.1f".format(it.distanceM)}m | ${it.clientsHint} ${if (it.isHotspotPhone) "[HOTSPOT-CEL]" else ""}"
                }
                listWifi.text = "WIFI AO REDOR (${list.size}):\n$txt"
            }
        }
        scope.launch {
            bleRadar.targets.collect { map ->
                val txt = map.values.sortedByDescending { it.rssi }.take(20).joinToString("\n") {
                    "${it.name} | ${it.mac} | ${it.rssi}dBm ~${"%.1f".format(it.distanceM)}m | ${it.type}"
                }
                listBle.text = "CELULARES/BLE (${map.size}):\n$txt"
            }
        }

        btnScan.setOnClickListener {
            wifiRadar.scan()
            log.append("scan disparado\n")
        }
        btnSvc.setOnClickListener {
            startForegroundService(Intent(this, RadarService::class.java))
            log.append("serviço passivo ligado — sem probe ativo, só escuta\n")
        }
        btnRoot.setOnClickListener {
            val bssid = inputBssid.text.toString().ifBlank { "FF:FF:FF:FF:FF:FF" }
            scope.launch(Dispatchers.IO) {
                RootShell.run("echo $bssid > /sdcard/target_bssid.txt")
                val out = RootShell.run(StealthConnect.pmkidCaptureCmd("wlan1"))
                withContext(Dispatchers.Main) { log.append(out + "\n") }
            }
        }

        // toque longo em rede aberta = conectar stealth
        listWifi.setOnLongClickListener {
            val first = wifiRadar.targets.value.firstOrNull { it.clientsHint == "ABERTA" }
            if (first != null) {
                StealthConnect.connectOpen(this, first.ssid)
                log.append("conectando stealth em ${first.ssid}\n")
            } else log.append("nenhuma aberta no momento\n")
            true
        }
    }

    override fun onDestroy() {
        scope.cancel()
        try { wifiRadar.stop() } catch (_: Exception) {}
        try { bleRadar.stop() } catch (_: Exception) {}
        super.onDestroy()
    }
}
