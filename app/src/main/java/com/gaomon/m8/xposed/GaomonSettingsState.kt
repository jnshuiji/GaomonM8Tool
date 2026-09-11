package com.gaomon.m8.xposed

import android.content.Context
import com.gaomon.m8.model.ActionType
import com.gaomon.m8.model.KeyConfig

/**
 * 进程内快速配置状态缓存（纳秒级热路径访问，避免频繁读写 SharedPreferences）
 */
object GaomonSettingsState {

    @Volatile
    var penUpper: ActionType = ActionType.SELECT_LASSO
        private set

    @Volatile
    var penLower: ActionType = ActionType.TOGGLE_ERASER_HOLD
        private set

    @Volatile
    private var isInitialized = false

    fun initIfNeeded(context: Context) {
        if (!isInitialized) {
            reload(context)
        }
    }

    fun reload(context: Context) {
        synchronized(this) {
            val config = KeyConfig(context)
            penUpper = config.penUpper
            penLower = config.penLower
            isInitialized = true
        }
    }

    fun updateUpperAction(action: ActionType) {
        penUpper = action
    }

    fun updateLowerAction(action: ActionType) {
        penLower = action
    }
}
