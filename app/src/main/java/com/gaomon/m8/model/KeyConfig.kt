package com.gaomon.m8.model

import android.content.Context
import android.content.SharedPreferences

class KeyConfig(context: Context) {
    companion object {
        const val PREFS_NAME = "gaomon_m8_config"
        const val KEY_PEN_LOWER = "pen_lower"
        const val KEY_PEN_UPPER = "pen_upper"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 主键（原下侧键，默认按住切橡皮）
    var penPrimary: ActionType
        get() = penLower
        set(v) { penLower = v }

    // 副键（原上侧键，默认套索）
    var penSecondary: ActionType
        get() = penUpper
        set(v) { penUpper = v }

    var penLower: ActionType
        get() = ActionType.fromId(prefs.getString(KEY_PEN_LOWER, ActionType.TOGGLE_ERASER_HOLD.id)!!)
        set(v) = prefs.edit().putString(KEY_PEN_LOWER, v.id).apply()

    var penUpper: ActionType
        get() = ActionType.fromId(prefs.getString(KEY_PEN_UPPER, ActionType.SELECT_LASSO.id)!!)
        set(v) = prefs.edit().putString(KEY_PEN_UPPER, v.id).apply()
}
