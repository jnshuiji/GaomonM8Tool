package com.gaomon.m8.xposed

import android.app.Activity
import android.util.Log
import android.view.KeyEvent
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Method

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

        log(Log.INFO, TAG, "StarNote package ready, installing Gaomon M8 targeted hooks...")

        try {
            val classLoader = param.classLoader
            val targetActivityClass = findClassOrNull(classLoader, TARGET_ACTIVITY)

            if (targetActivityClass != null) {
                installTargetedHooks(targetActivityClass)
                log(Log.INFO, TAG, "Gaomon M8 targeted hooks installed on $TARGET_ACTIVITY")
            } else {
                // Fallback: 若目标类尚未直接由当前 ClassLoader 加载，则使用基类安全挂钩
                installFallbackHooks()
                log(Log.WARN, TAG, "Target activity class not found directly, installed fallback Activity hooks")
            }
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook StarNote: ${t.message}", t)
        }
    }

    private fun findClassOrNull(classLoader: ClassLoader, className: String): Class<*>? {
        return try {
            classLoader.loadClass(className)
        } catch (_: Throwable) {
            null
        }
    }

    private fun findMethodInHierarchy(clazz: Class<*>, methodName: String, vararg paramTypes: Class<*>): Method? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            try {
                val m = current.getDeclaredMethod(methodName, *paramTypes)
                m.isAccessible = true
                return m
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun installTargetedHooks(targetClass: Class<*>) {
        val onResumeMethod = findMethodInHierarchy(targetClass, "onResume")
        if (onResumeMethod != null) {
            hook(onResumeMethod).intercept { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject as? Activity
                if (activity != null && activity.javaClass.name == TARGET_ACTIVITY) {
                    NoteScribbleHook.onActivityResumed(activity)
                }
                result
            }
        }

        val onPauseMethod = findMethodInHierarchy(targetClass, "onPause")
        if (onPauseMethod != null) {
            hook(onPauseMethod).intercept { chain ->
                val activity = chain.thisObject as? Activity
                if (activity != null && activity.javaClass.name == TARGET_ACTIVITY) {
                    NoteScribbleHook.onActivityPaused(activity)
                }
                chain.proceed()
            }
        }

        val dispatchKeyEventMethod = findMethodInHierarchy(targetClass, "dispatchKeyEvent", KeyEvent::class.java)
        if (dispatchKeyEventMethod != null) {
            hook(dispatchKeyEventMethod).intercept { chain ->
                val activity = chain.thisObject as? Activity
                if (activity != null && activity.javaClass.name == TARGET_ACTIVITY) {
                    val event = chain.args[0] as? KeyEvent
                    if (event != null && event.keyCode == KeyEvent.KEYCODE_E) {
                        val deviceName = event.device?.name?.lowercase() ?: ""
                        if (deviceName.contains("gaomon") || deviceName.contains("tablet") || deviceName.contains("m8")) {
                            return@intercept true
                        }
                    }
                }
                chain.proceed()
            }
        }
    }

    private fun installFallbackHooks() {
        val activityClass = Activity::class.java

        val onResumeMethod = activityClass.getDeclaredMethod("onResume").apply { isAccessible = true }
        hook(onResumeMethod).intercept { chain ->
            val result = chain.proceed()
            val activity = chain.thisObject as? Activity
            if (activity != null && activity.javaClass.name == TARGET_ACTIVITY) {
                NoteScribbleHook.onActivityResumed(activity)
            }
            result
        }

        val onPauseMethod = activityClass.getDeclaredMethod("onPause").apply { isAccessible = true }
        hook(onPauseMethod).intercept { chain ->
            val activity = chain.thisObject as? Activity
            if (activity != null && activity.javaClass.name == TARGET_ACTIVITY) {
                NoteScribbleHook.onActivityPaused(activity)
            }
            chain.proceed()
        }

        val dispatchKeyEventMethod = activityClass.getDeclaredMethod("dispatchKeyEvent", KeyEvent::class.java).apply { isAccessible = true }
        hook(dispatchKeyEventMethod).intercept { chain ->
            val activity = chain.thisObject as? Activity
            if (activity != null && activity.javaClass.name == TARGET_ACTIVITY) {
                val event = chain.args[0] as? KeyEvent
                if (event != null && event.keyCode == KeyEvent.KEYCODE_E) {
                    val deviceName = event.device?.name?.lowercase() ?: ""
                    if (deviceName.contains("gaomon") || deviceName.contains("tablet") || deviceName.contains("m8")) {
                        return@intercept true
                    }
                }
            }
            chain.proceed()
        }
    }
}
