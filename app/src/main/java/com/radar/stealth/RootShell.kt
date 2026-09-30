package com.radar.stealth

import java.io.BufferedReader
import java.io.InputStreamReader

object RootShell {
    fun isRooted(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val out = BufferedReader(InputStreamReader(p.inputStream)).readText()
            p.waitFor()
            out.contains("uid=0")
        } catch (_: Exception) { false }
    }

    fun run(cmd: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val out = BufferedReader(InputStreamReader(p.inputStream)).readText()
            val err = BufferedReader(InputStreamReader(p.errorStream)).readText()
            p.waitFor()
            out + err
        } catch (e: Exception) { "ERR: ${e.message}" }
    }

    fun runSh(cmd: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val out = BufferedReader(InputStreamReader(p.inputStream)).readText()
            p.waitFor()
            out
        } catch (e: Exception) { "ERR: ${e.message}" }
    }
}
