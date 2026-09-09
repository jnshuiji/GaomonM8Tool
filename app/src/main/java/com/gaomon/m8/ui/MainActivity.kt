package com.gaomon.m8.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.gaomon.m8.App
import com.gaomon.m8.R
import com.gaomon.m8.databinding.ActivityMainBinding
import com.gaomon.m8.model.ActionType
import com.gaomon.m8.model.KeyConfig
import com.gaomon.m8.service.GaomonDaemonService
import io.github.libxposed.service.XposedService

class MainActivity : AppCompatActivity(), App.ServiceStateListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var keyConfig: KeyConfig
    private var isStarNoteHookActive = false
    private var xposedService: XposedService? = null

    private val actionList = ActionType.entries.map { it.displayName }

    private val hookActiveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.gaomon.m8.ACTION_HOOK_ACTIVE") {
                isStarNoteHookActive = true
                runOnUiThread {
                    binding.tvLsposedStatus.text = "LSPosed 模块: 已激活"
                    binding.tvLsposedStatus.setTextColor(getColor(R.color.status_green))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        keyConfig = KeyConfig(this)

        initStylusSpinners()
        initSystemSwitches()

        val filter = IntentFilter("com.gaomon.m8.ACTION_HOOK_ACTIVE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(hookActiveReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(hookActiveReceiver, filter)
        }

        // 打开应用时确保守护进程处于启动状态
        GaomonDaemonService.start(this)
    }

    override fun onStart() {
        super.onStart()
        App.addServiceStateListener(this, true)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onStop() {
        App.removeServiceStateListener(this)
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(hookActiveReceiver)
        } catch (_: Throwable) {}
    }

    override fun onServiceStateChanged(service: XposedService?) {
        xposedService = service
        runOnUiThread {
            refreshStatus()
        }
    }

    // 由 Xposed 模块挂钩，若模块已被 LSPosed 成功注入则返回 true
    fun isModuleActive(): Boolean {
        return false
    }

    private fun refreshStatus() {
        // 1. 检测 LSPosed 激活状态
        val service = xposedService ?: App.mService
        val boundStarNote = service?.scope?.contains("com.onyx.galaxy.note") == true
        val isHooked = boundStarNote || isModuleActive() || isStarNoteHookActive

        if (isHooked) {
            binding.tvLsposedStatus.text = "LSPosed 模块: 已激活"
            binding.tvLsposedStatus.setTextColor(getColor(R.color.status_green))
        } else {
            binding.tvLsposedStatus.text = "LSPosed 模块: 未激活"
            binding.tvLsposedStatus.setTextColor(getColor(R.color.status_orange))
        }

        // 2. 异步检测 Root 权限与硬件连接状态
        Thread {
            val hasRoot = checkRootPermission()
            val (hasDevice, _) = checkHardwareConnected()

            runOnUiThread {
                if (hasRoot) {
                    binding.tvRootStatus.text = "Root 权限: 已获取"
                    binding.tvRootStatus.setTextColor(getColor(R.color.status_green))
                } else {
                    binding.tvRootStatus.text = "Root 权限: 未获取"
                    binding.tvRootStatus.setTextColor(getColor(R.color.status_red))
                }

                if (hasDevice) {
                    binding.tvDeviceStatus.text = "硬件状态: 已连接"
                    binding.tvDeviceStatus.setTextColor(getColor(R.color.status_green))
                } else {
                    binding.tvDeviceStatus.text = "硬件状态: 未连接"
                    binding.tvDeviceStatus.setTextColor(getColor(R.color.status_red))
                }
            }
        }.start()
    }

    private fun checkRootPermission(): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", "id").start()
            process.waitFor() == 0
        } catch (t: Throwable) {
            false
        }
    }

    private fun checkHardwareConnected(): Pair<Boolean, String> {
        val usbManager = getSystemService(Context.USB_SERVICE) as? UsbManager
        val usbDevices = usbManager?.deviceList?.values ?: emptyList()
        val gaomonDevice = usbDevices.firstOrNull {
            it.vendorId == 0x256c || (it.productName?.contains("Gaomon", ignoreCase = true) == true)
        }
        if (gaomonDevice != null) {
            val name = gaomonDevice.productName ?: "Gaomon M8"
            val vidPid = String.format("%04X:%04X", gaomonDevice.vendorId, gaomonDevice.productId)
            return Pair(true, "$name ($vidPid)")
        }
        try {
            val process = ProcessBuilder("su", "-c", "cat /proc/bus/input/devices").start()
            val text = process.inputStream.bufferedReader().readText()
            if (text.contains("256c", ignoreCase = true) || text.contains("Gaomon", ignoreCase = true)) {
                return Pair(true, "Gaomon Tablet_M8 (256C:0064)")
            }
        } catch (_: Throwable) {}
        return Pair(false, "")
    }

    private fun initStylusSpinners() {
        val adapter = ArrayAdapter(this, R.layout.item_spinner, actionList).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }

        // 笔下侧键
        binding.spinnerPenLower.adapter = adapter
        binding.spinnerPenLower.setSelection(ActionType.entries.indexOf(keyConfig.penLower))
        binding.spinnerPenLower.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                keyConfig.penLower = ActionType.entries[position]
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        // 笔上侧键
        binding.spinnerPenUpper.adapter = adapter
        binding.spinnerPenUpper.setSelection(ActionType.entries.indexOf(keyConfig.penUpper))
        binding.spinnerPenUpper.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                keyConfig.penUpper = ActionType.entries[position]
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun initSystemSwitches() {
        binding.switchKeepIme.isChecked = keyConfig.autoShowIme
        binding.switchKeepIme.setOnCheckedChangeListener { _, isChecked ->
            keyConfig.autoShowIme = isChecked
            val cmd = if (isChecked) "settings put secure show_ime_with_hard_keyboard 1"
                      else "settings put secure show_ime_with_hard_keyboard 0"
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            } catch (ignored: Throwable) {}
        }
    }
}
