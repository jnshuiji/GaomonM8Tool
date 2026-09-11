package com.gaomon.m8.data

import android.content.Context
import android.hardware.usb.UsbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class DeviceRepository(private val context: Context) {

    suspend fun checkRootPermission(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", "id").start()
            val finished = process.waitFor(2000, TimeUnit.MILLISECONDS)
            if (finished) {
                process.exitValue() == 0
            } else {
                process.destroy()
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun checkHardwareConnected(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        // 1. 优先通过 UsbManager 查询
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        val usbDevices = usbManager?.deviceList?.values ?: emptyList()
        val gaomonUsb = usbDevices.firstOrNull {
            it.vendorId == 0x256c || (it.productName?.contains("Gaomon", ignoreCase = true) == true)
        }
        if (gaomonUsb != null) {
            val name = gaomonUsb.productName ?: "Gaomon M8"
            val vidPid = String.format("%04X:%04X", gaomonUsb.vendorId, gaomonUsb.productId)
            return@withContext Pair(true, "$name ($vidPid)")
        }

        // 2. 通过 /proc/bus/input/devices 备选查询
        try {
            val process = ProcessBuilder("su", "-c", "cat /proc/bus/input/devices").start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(2000, TimeUnit.MILLISECONDS)
            if (!finished) process.destroy()

            if (text.contains("256c", ignoreCase = true) || text.contains("Gaomon", ignoreCase = true)) {
                return@withContext Pair(true, "Gaomon Tablet_M8 (256C:0064)")
            }
        } catch (_: Throwable) {}

        return@withContext Pair(false, "")
    }

    suspend fun setAutoShowIme(enabled: Boolean) = withContext(Dispatchers.IO) {
        val cmd = if (enabled) "settings put secure show_ime_with_hard_keyboard 1"
                  else "settings put secure show_ime_with_hard_keyboard 0"
        try {
            val process = ProcessBuilder("su", "-c", cmd).start()
            process.waitFor(2000, TimeUnit.MILLISECONDS)
        } catch (_: Throwable) {}
    }
}
