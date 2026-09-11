package com.gaomon.m8.model

enum class RootStatus {
    CHECKING,
    GRANTED,
    DENIED
}

sealed class DeviceStatus {
    data object Checking : DeviceStatus()
    data class Connected(val info: String) : DeviceStatus()
    data object Disconnected : DeviceStatus()
}

enum class LsposedStatus {
    CHECKING,
    ACTIVE,
    INACTIVE
}

data class MainUiState(
    val rootStatus: RootStatus = RootStatus.CHECKING,
    val deviceStatus: DeviceStatus = DeviceStatus.Checking,
    val lsposedStatus: LsposedStatus = LsposedStatus.CHECKING,
    val autoShowIme: Boolean = true
)
