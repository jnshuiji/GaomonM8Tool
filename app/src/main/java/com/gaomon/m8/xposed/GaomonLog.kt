package com.gaomon.m8.xposed

import android.util.Log
import io.github.libxposed.api.XposedModule

/**
 * 统一双通道日志分发器（同时输出至 logcat 与 LSPosedFramework 物理日志文件）
 */
object GaomonLog {

    @Volatile
    var module: XposedModule? = null

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        try {
            module?.log(Log.INFO, tag, msg)
        } catch (_: Throwable) {}
    }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        try {
            module?.log(Log.DEBUG, tag, msg)
        } catch (_: Throwable) {}
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        try {
            module?.log(Log.WARN, tag, msg)
        } catch (_: Throwable) {}
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        Log.e(tag, msg, t)
        try {
            val fullMsg = if (t != null) "$msg: ${Log.getStackTraceString(t)}" else msg
            module?.log(Log.ERROR, tag, fullMsg)
        } catch (_: Throwable) {}
    }
}
