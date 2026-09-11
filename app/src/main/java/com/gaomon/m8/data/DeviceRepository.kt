package com.gaomon.m8.data

import android.content.Context
import android.hardware.input.InputManager
import android.hardware.usb.UsbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeviceRepository(private val context: Context) {

    suspend fun checkHardwareConnected(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        // 1. 优先通过 UsbManager 查询 (标准免 root 公开 API)
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

        // 2. 备选通过 InputManager 遍历当前接入的输入设备 (标准免 root 公开 API)
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
        val inputIds = inputManager?.inputDeviceIds ?: intArrayOf()
        for (id in inputIds) {
            val device = inputManager?.getInputDevice(id) ?: continue
            if (device.vendorId == 0x256c || device.name.contains("Gaomon", ignoreCase = true)) {
                return@withContext Pair(true, "${device.name} (HID Input)")
            }
        }

        return@withContext Pair(false, "")
    }
}
