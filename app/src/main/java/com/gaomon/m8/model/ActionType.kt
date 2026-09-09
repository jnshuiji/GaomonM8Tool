package com.gaomon.m8.model

enum class ActionType(val id: String, val displayName: String) {
    NONE("none", "无操作"),
    TOGGLE_ERASER_HOLD("eraser_hold", "按住切橡皮松开回画笔"),
    TOGGLE_LASSO_HOLD("lasso_hold", "按住切套索松开回画笔"),
    SELECT_PEN("pen", "画笔"),
    SELECT_ERASER("eraser", "橡皮擦"),
    SELECT_LASSO("lasso", "套索");

    companion object {
        fun fromId(id: String): ActionType {
            return entries.firstOrNull { it.id == id } ?: NONE
        }
    }
}
