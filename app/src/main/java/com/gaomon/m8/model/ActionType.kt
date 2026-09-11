package com.gaomon.m8.model

import androidx.annotation.StringRes
import com.gaomon.m8.R

enum class ActionType(val id: String, @StringRes val stringRes: Int, val displayName: String) {
    NONE("none", R.string.action_none, "无操作"),
    TOGGLE_ERASER_HOLD("eraser_hold", R.string.action_eraser_hold, "按住切橡皮松开回画笔"),
    SELECT_PEN("pen", R.string.action_pen, "画笔"),
    SELECT_ERASER("eraser", R.string.action_eraser, "橡皮擦"),
    SELECT_LASSO("lasso", R.string.action_lasso, "套索");

    companion object {
        fun fromId(id: String): ActionType {
            return entries.firstOrNull { it.id == id } ?: NONE
        }
    }
}
