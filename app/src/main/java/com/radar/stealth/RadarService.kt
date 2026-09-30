package com.radar.stealth

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class RadarService : Service() {
    private lateinit var wifiRadar: WifiRadar
    private lateinit var bleRadar: BleRadar

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel("radar", "Radar", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        val n = NotificationCompat.Builder(this, "radar")
            .setContentTitle("Radar passivo ativo")
            .setContentText("Escutando WiFi + BLE sem transmitir")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .build()
        startForeground(1, n)
        wifiRadar = WifiRadar(this)
        bleRadar = BleRadar(this)
        wifiRadar.start()
        bleRadar.start()
    }

    override fun onBind(i: Intent?): IBinder? = null
    override fun onDestroy() {
        try { wifiRadar.stop() } catch (_: Exception) {}
        try { bleRadar.stop() } catch (_: Exception) {}
        super.onDestroy()
    }
}
