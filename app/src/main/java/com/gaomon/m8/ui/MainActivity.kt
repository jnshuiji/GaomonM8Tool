package com.gaomon.m8.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gaomon.m8.App
import com.gaomon.m8.R
import com.gaomon.m8.databinding.ActivityMainBinding
import com.gaomon.m8.model.ActionType
import com.gaomon.m8.model.DeviceStatus
import com.gaomon.m8.model.LsposedStatus
import com.gaomon.m8.model.MainUiState
import com.gaomon.m8.model.RootStatus
import com.gaomon.m8.service.GaomonDaemonService
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), App.ServiceStateListener {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private var isStarNoteHookActive = false
    private var xposedService: XposedService? = null

    private val actionList by lazy { ActionType.entries.map { getString(it.stringRes) } }

    private val hookActiveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.gaomon.m8.ACTION_HOOK_ACTIVE") {
                isStarNoteHookActive = true
                viewModel.refreshStatus(checkLsposedHooked())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        observeUiState()

        val filter = IntentFilter("com.gaomon.m8.ACTION_HOOK_ACTIVE")
        ContextCompat.registerReceiver(
            this,
            hookActiveReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        // 确保守护进程处于启动状态
        GaomonDaemonService.start(this)
    }

    override fun onStart() {
        super.onStart()
        App.addServiceStateListener(this, true)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStatus(checkLsposedHooked())
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
        viewModel.refreshStatus(checkLsposedHooked())
    }

    // 由 Xposed 模块挂钩，若模块已被 LSPosed 成功注入则返回 true
    fun isModuleActive(): Boolean = false

    private fun checkLsposedHooked(): Boolean {
        val service = xposedService ?: App.mService
        val boundStarNote = service?.scope?.contains("com.onyx.galaxy.note") == true
        return boundStarNote || isModuleActive() || isStarNoteHookActive
    }

    private fun initViews() {
        initStylusSpinners()
        initSystemSwitches()
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUi(state)
                }
            }
        }
    }

    private fun renderUi(state: MainUiState) {
        // 1. Root 状态渲染
        when (state.rootStatus) {
            RootStatus.CHECKING -> {
                binding.tvRootStatus.setText(R.string.status_root_checking)
                binding.tvRootStatus.setTextColor(getColor(R.color.on_primary_container))
            }
            RootStatus.GRANTED -> {
                binding.tvRootStatus.setText(R.string.status_root_granted)
                binding.tvRootStatus.setTextColor(getColor(R.color.status_green))
            }
            RootStatus.DENIED -> {
                binding.tvRootStatus.setText(R.string.status_root_denied)
                binding.tvRootStatus.setTextColor(getColor(R.color.status_red))
            }
        }

        // 2. 硬件设备状态渲染
        when (val device = state.deviceStatus) {
            is DeviceStatus.Checking -> {
                binding.tvDeviceStatus.setText(R.string.status_device_checking)
                binding.tvDeviceStatus.setTextColor(getColor(R.color.on_primary_container))
            }
            is DeviceStatus.Connected -> {
                binding.tvDeviceStatus.text = getString(R.string.status_device_connected_format, device.info)
                binding.tvDeviceStatus.setTextColor(getColor(R.color.status_green))
            }
            is DeviceStatus.Disconnected -> {
                binding.tvDeviceStatus.setText(R.string.status_device_disconnected)
                binding.tvDeviceStatus.setTextColor(getColor(R.color.status_red))
            }
        }

        // 3. LSPosed 状态渲染
        when (state.lsposedStatus) {
            LsposedStatus.CHECKING -> {
                binding.tvLsposedStatus.setText(R.string.status_lsposed_checking)
                binding.tvLsposedStatus.setTextColor(getColor(R.color.on_primary_container))
            }
            LsposedStatus.ACTIVE -> {
                binding.tvLsposedStatus.setText(R.string.status_lsposed_active)
                binding.tvLsposedStatus.setTextColor(getColor(R.color.status_green))
            }
            LsposedStatus.INACTIVE -> {
                binding.tvLsposedStatus.setText(R.string.status_lsposed_inactive)
                binding.tvLsposedStatus.setTextColor(getColor(R.color.status_orange))
            }
        }

        // 4. 系统开关状态渲染
        if (binding.switchKeepIme.isChecked != state.autoShowIme) {
            binding.switchKeepIme.isChecked = state.autoShowIme
        }
    }

    private fun initStylusSpinners() {
        val adapter = ArrayAdapter(this, R.layout.item_spinner, actionList).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }

        // 笔下侧键
        binding.spinnerPenLower.adapter = adapter
        binding.spinnerPenLower.setSelection(ActionType.entries.indexOf(viewModel.keyConfig.penLower))
        binding.spinnerPenLower.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                viewModel.setPenLower(ActionType.entries[position])
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        // 笔上侧键
        binding.spinnerPenUpper.adapter = adapter
        binding.spinnerPenUpper.setSelection(ActionType.entries.indexOf(viewModel.keyConfig.penUpper))
        binding.spinnerPenUpper.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                viewModel.setPenUpper(ActionType.entries[position])
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun initSystemSwitches() {
        binding.switchKeepIme.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != viewModel.uiState.value.autoShowIme) {
                viewModel.setAutoShowIme(isChecked)
            }
        }
    }
}
