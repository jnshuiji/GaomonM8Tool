package com.gaomon.m8.xposed

import android.app.Activity
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class GaomonXposedModule : XposedModule() {

    companion object {
        private const val TAG = "GaomonM8Xposed"
        private const val TARGET_PACKAGE = "com.onyx.galaxy.note"
        private const val TARGET_ACTIVITY = "com.onyx.galaxy.note.editor.ui.NoteScribbleActivity"
        private const val SELF_PACKAGE = "com.gaomon.m8"
        private const val SELF_ACTIVITY = "com.gaomon.m8.ui.MainActivity"
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName == SELF_PACKAGE) {
            try {
                val classLoader = param.classLoader
                val mainActivityClass = classLoader.loadClass(SELF_ACTIVITY)
                val isActiveMethod = mainActivityClass.getMethod("isModuleActive")
                hook(isActiveMethod).intercept {
                    true
                }
                log(Log.INFO, TAG, "Self hook installed: isModuleActive -> true")
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "Failed to hook self: ${t.message}", t)
            }
            return
        }

        if (param.packageName != TARGET_PACKAGE) return

        log(Log.INFO, TAG, "StarNote package ready, installing Gaomon M8 hooks...")

        try {
            val activityClass = Activity::class.java

            // 1. 在画板页面激活/挂起时绑定/解绑手写笔工具切换接收器
            val onResumeMethod = activityClass.getDeclaredMethod("onResume")
            onResumeMethod.isAccessible = true

            hook(onResumeMethod).intercept { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject as? Activity
                if (activity != null && activity.javaClass.name == TARGET_ACTIVITY) {
                    log(Log.DEBUG, TAG, "NoteScribbleActivity onResume: binding hook")
                    NoteScribbleHook.onActivityResumed(activity)
                }
                result
            }

            val onPauseMethod = activityClass.getDeclaredMethod("onPause")
            onPauseMethod.isAccessible = true

            hook(onPauseMethod).intercept { chain ->
                val activity = chain.thisObject as? Activity
                if (activity != null && activity.javaClass.name == TARGET_ACTIVITY) {
                    log(Log.DEBUG, TAG, "NoteScribbleActivity onPause: unbinding hook")
                    NoteScribbleHook.onActivityPaused(activity)
                }
                chain.proceed()
            }

            // 2. 拦截并丢弃笔上侧键硬件映射的 KEYCODE_E，防止其在笔记文本框中误打出字母 'e'
            val dispatchKeyEventMethod = activityClass.getDeclaredMethod("dispatchKeyEvent", android.view.KeyEvent::class.java)
            dispatchKeyEventMethod.isAccessible = true

            hook(dispatchKeyEventMethod).intercept { chain ->
                val activity = chain.thisObject as? Activity
                if (activity != null && activity.javaClass.name == TARGET_ACTIVITY) {
                    val event = chain.args[0] as? android.view.KeyEvent
                    if (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_E) {
                        val deviceName = event.device?.name?.lowercase() ?: ""
                        if (deviceName.contains("gaomon") || deviceName.contains("tablet") || deviceName.contains("m8")) {
                            return@intercept true
                        }
                    }
                }
                chain.proceed()
            }

            log(Log.INFO, TAG, "Gaomon M8 hooks installed successfully into StarNote!")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook StarNote: ${t.message}", t)
        }
    }
}
