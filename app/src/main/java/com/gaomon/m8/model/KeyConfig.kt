package com.gaomon.m8.model

import android.content.Context
import android.content.SharedPreferences

class KeyConfig(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gaomon_m8_config", Context.MODE_PRIVATE)

    // 手写笔双侧键：上侧键默认套索按住回弹，下侧键默认橡皮按住回弹
    var penLower: ActionType
        get() = ActionType.fromId(prefs.getString("pen_lower", ActionType.TOGGLE_ERASER_HOLD.id)!!)
        set(v) = prefs.edit().putString("pen_lower", v.id).apply()

    var penUpper: ActionType
        get() = ActionType.fromId(prefs.getString("pen_upper", ActionType.TOGGLE_LASSO_HOLD.id)!!)
        set(v) = prefs.edit().putString("pen_upper", v.id).apply()

    // 系统开关
    var autoShowIme: Boolean
        get() = prefs.getBoolean("auto_show_ime", true)
        set(v) = prefs.edit().putBoolean("auto_show_ime", v).apply()
}
