package com.gaomon.m8.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gaomon.m8.data.DeviceRepository
import com.gaomon.m8.model.ActionType
import com.gaomon.m8.model.DeviceStatus
import com.gaomon.m8.model.KeyConfig
import com.gaomon.m8.model.LsposedStatus
import com.gaomon.m8.model.MainUiState
import com.gaomon.m8.model.RootStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DeviceRepository(application)
    val keyConfig = KeyConfig(application)

    private val _uiState = MutableStateFlow(
        MainUiState(autoShowIme = keyConfig.autoShowIme)
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun refreshStatus(isLsposedHooked: Boolean) {
        // 1. 同步更新 LSPosed 状态
        _uiState.update { current ->
            current.copy(
                lsposedStatus = if (isLsposedHooked) LsposedStatus.ACTIVE else LsposedStatus.INACTIVE
            )
        }

        // 2. 异步协程更新 Root 与硬件状态
        viewModelScope.launch {
            val hasRoot = repository.checkRootPermission()
            val (hasDevice, deviceInfo) = repository.checkHardwareConnected()

            _uiState.update { current ->
                current.copy(
                    rootStatus = if (hasRoot) RootStatus.GRANTED else RootStatus.DENIED,
                    deviceStatus = if (hasDevice) DeviceStatus.Connected(deviceInfo) else DeviceStatus.Disconnected
                )
            }
        }
    }

    fun setAutoShowIme(enabled: Boolean) {
        keyConfig.autoShowIme = enabled
        _uiState.update { it.copy(autoShowIme = enabled) }
        viewModelScope.launch {
            repository.setAutoShowIme(enabled)
        }
    }

    fun setPenLower(action: ActionType) {
        keyConfig.penLower = action
    }

    fun setPenUpper(action: ActionType) {
        keyConfig.penUpper = action
    }
}
