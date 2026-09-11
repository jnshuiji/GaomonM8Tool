package com.gaomon.m8.xposed

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Method

class GaomonXposedModule : XposedModule() {

    companion object {
        private const val TAG = "GaomonM8Xposed"
        private const val TARGET_PACKAGE = "com.onyx.galaxy.note"
        private const val TARGET_CANVAS_ACTIVITY = "com.onyx.galaxy.note.editor.ui.NoteScribbleActivity"
        private const val TARGET_SETTINGS_ACTIVITY = "com.onyx.galaxy.note.settings.ui.SettingsActivity"
        private const val SELF_PACKAGE = "com.gaomon.m8"

        const val ACTION_OPEN_SETTINGS = "com.gaomon.m8.ACTION_OPEN_SETTINGS"
        const val ACTION_CONFIG_CHANGED = "com.gaomon.m8.ACTION_CONFIG_CHANGED"
    }

    // 笔下侧键状态追踪器
    private var isLowerButtonHeld = false
    private var isCanvasHooked = false
    private var isSettingsHooked = false
    private var isFragmentHooked = false
    private var isInternalReceiverRegistered = false

    override fun onPackageReady(param: PackageReadyParam) {
        GaomonLog.module = this

        if (param.packageName == SELF_PACKAGE) {
            return
        }

        if (param.packageName != TARGET_PACKAGE) return

        GaomonLog.i(TAG, "StarNote package ready, initializing rootless Gaomon hooks...")

        try {
            // 全局 Activity 兜底保护 (Activity 基类挂钩，确保 100% 截获所有事件)
            installFallbackActivityHooks()
            GaomonLog.i(TAG, "Installed Activity fallback hooks successfully.")

            // 尝试在初始 ClassLoader 中注入目标特定类
            ensureTargetedHooks(param.classLoader)

        } catch (t: Throwable) {
            GaomonLog.e(TAG, "Failed to initialize StarNote hooks", t)
        }
    }

    private fun registerInternalReceiver(context: android.content.Context) {
        if (isInternalReceiverRegistered) return
        try {
            val filter = android.content.IntentFilter().apply {
                addAction(ACTION_OPEN_SETTINGS)
                addAction(ACTION_CONFIG_CHANGED)
            }
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                    when (intent?.action) {
                        ACTION_OPEN_SETTINGS -> {
                            GaomonLog.i(TAG, "Received ACTION_OPEN_SETTINGS in StarNote, opening SettingsActivity")
                            try {
                                val openIntent = android.content.Intent().apply {
                                    component = android.content.ComponentName(TARGET_PACKAGE, TARGET_SETTINGS_ACTIVITY)
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                ctx?.startActivity(openIntent)
                            } catch (e: Throwable) {
                                GaomonLog.e(TAG, "Failed to launch SettingsActivity internally", e)
                            }
                        }
                        ACTION_CONFIG_CHANGED -> {
                            ctx?.let { GaomonSettingsState.initIfNeeded(it) }
                        }
                    }
                }
            }
            androidx.core.content.ContextCompat.registerReceiver(
                context.applicationContext ?: context,
                receiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
            isInternalReceiverRegistered = true
            GaomonLog.i(TAG, "Registered StarNote internal broadcast receiver successfully.")
        } catch (t: Throwable) {
            GaomonLog.e(TAG, "Failed to register internal receiver: ${t.message}", t)
        }
    }

    private fun ensureTargetedHooks(classLoader: ClassLoader) {
        if (!isCanvasHooked) {
            val canvasClass = findClassOrNull(classLoader, TARGET_CANVAS_ACTIVITY)
            if (canvasClass != null) {
                installCanvasHooks(canvasClass)
                isCanvasHooked = true
                GaomonLog.i(TAG, "Installed targeted canvas hooks on $TARGET_CANVAS_ACTIVITY")
            }
        }

        if (!isSettingsHooked) {
            val settingsClass = findClassOrNull(classLoader, TARGET_SETTINGS_ACTIVITY)
            if (settingsClass != null) {
                installSettingsHooks(settingsClass)
                isSettingsHooked = true
                GaomonLog.i(TAG, "Installed targeted settings hooks on $TARGET_SETTINGS_ACTIVITY")
            }
        }

        if (!isFragmentHooked) {
            val fragmentClass = findClassOrNull(classLoader, "androidx.fragment.app.Fragment")
            if (fragmentClass != null) {
                installFragmentHooks(fragmentClass)
                isFragmentHooked = true
                GaomonLog.i(TAG, "Installed targeted fragment onViewCreated hook for stylus settings")
            }
        }
    }

    private fun findClassOrNull(classLoader: ClassLoader, className: String): Class<*>? {
        return try {
            classLoader.loadClass(className)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * 查找子类中声明的方法（不在 stopAtClass 及其超类中），避免与基类钩子冲突
     */
    private fun findDeclaredMethodInHierarchy(clazz: Class<*>, stopAtClass: Class<*>, methodName: String, vararg paramTypes: Class<*>): Method? {
        var current: Class<*>? = clazz
        while (current != null && current != stopAtClass && current != Any::class.java) {
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

    /**
     * 针对 StarNote 画板 (NoteScribbleActivity) 的全量输入与生命周期接管
     */
    private fun installCanvasHooks(targetClass: Class<*>) {
        // 若 NoteScribbleActivity 本身重写了 dispatchKeyEvent，则直接在其子类上优先拦截
        val dispatchKeyEventMethod = findDeclaredMethodInHierarchy(targetClass, Activity::class.java, "dispatchKeyEvent", KeyEvent::class.java)
        if (dispatchKeyEventMethod != null) {
            hook(dispatchKeyEventMethod).intercept { chain ->
                val activity = chain.thisObject as? Activity
                val event = chain.args[0] as? KeyEvent

                if (activity != null && event != null && activity.javaClass.name == TARGET_CANVAS_ACTIVITY) {
                    if (event.keyCode == KeyEvent.KEYCODE_E && isGaomonEvent(event)) {
                        val action = GaomonSettingsState.penUpper
                        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                            activity.runOnUiThread {
                                NoteScribbleHook.executeAction(activity, action, isDown = true)
                            }
                        } else if (event.action == KeyEvent.ACTION_UP) {
                            activity.runOnUiThread {
                                NoteScribbleHook.executeAction(activity, action, isDown = false)
                            }
                        }
                        // 消费事件并静默丢弃
                        return@intercept true
                    }
                }
                chain.proceed()
            }
        }

        // 若 NoteScribbleActivity 本身重写了 dispatchGenericMotionEvent
        val genericMotionMethod = findDeclaredMethodInHierarchy(targetClass, Activity::class.java, "dispatchGenericMotionEvent", MotionEvent::class.java)
        if (genericMotionMethod != null) {
            hook(genericMotionMethod).intercept { chain ->
                val activity = chain.thisObject as? Activity
                val event = chain.args[0] as? MotionEvent
                if (activity != null && event != null && activity.javaClass.name == TARGET_CANVAS_ACTIVITY) {
                    handleStylusMotionEvent(activity, event)
                }
                chain.proceed()
            }
        }

        // 若 NoteScribbleActivity 本身重写了 dispatchTouchEvent
        val touchMethod = findDeclaredMethodInHierarchy(targetClass, Activity::class.java, "dispatchTouchEvent", MotionEvent::class.java)
        if (touchMethod != null) {
            hook(touchMethod).intercept { chain ->
                val activity = chain.thisObject as? Activity
                val event = chain.args[0] as? MotionEvent
                if (activity != null && event != null && activity.javaClass.name == TARGET_CANVAS_ACTIVITY) {
                    handleStylusMotionEvent(activity, event)
                }
                chain.proceed()
            }
        }
    }

    /**
     * 针对 StarNote 设置界面 (SettingsActivity) 的注入
     */
    private fun installSettingsHooks(targetClass: Class<*>) {
        val onResumeMethod = findMethodInHierarchy(targetClass, "onResume")
        if (onResumeMethod != null) {
            hook(onResumeMethod).intercept { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject as? Activity
                if (activity != null) {
                    StylusSettingsInjector.injectIfPresent(activity)
                }
                result
            }
        }
    }

    /**
     * 针对 AndroidX Fragment 的挂钩：在 SettingsStylusFragment 视图初始化完成时注入
     */
    private fun installFragmentHooks(fragmentClass: Class<*>) {
        val onViewCreatedMethod = findMethodInHierarchy(
            fragmentClass,
            "onViewCreated",
            View::class.java,
            Bundle::class.java
        )
        if (onViewCreatedMethod != null) {
            hook(onViewCreatedMethod).intercept { chain ->
                val result = chain.proceed()
                val fragment = chain.thisObject
                if (fragment != null && fragment.javaClass.simpleName == "SettingsStylusFragment") {
                    try {
                        val activityMethod = fragment.javaClass.getMethod("getActivity")
                        val activity = activityMethod.invoke(fragment) as? Activity
                        if (activity != null) {
                            StylusSettingsInjector.injectIfPresent(activity)
                        }
                    } catch (_: Throwable) {}
                }
                result
            }
        }
    }

    /**
     * 全局 Activity 兜底挂钩 (在 Activity 基类挂钩，确保无论类何时加载均 100% 截获事件)
     */
    private fun installFallbackActivityHooks() {
        val activityClass = Activity::class.java

        val onResumeMethod = activityClass.getDeclaredMethod("onResume").apply { isAccessible = true }
        hook(onResumeMethod).intercept { chain ->
            val result = chain.proceed()
            val activity = chain.thisObject as? Activity
            val cl = activity?.javaClass?.classLoader
            if (activity != null && cl != null) {
                registerInternalReceiver(activity)
                ensureTargetedHooks(cl)
                val className = activity.javaClass.name
                GaomonLog.i(TAG, "Fallback onResume observed: $className")
                if (className == TARGET_CANVAS_ACTIVITY) {
                    GaomonSettingsState.initIfNeeded(activity)
                    NoteScribbleHook.onActivityResumed(activity)
                } else if (className.contains("SettingsActivity") || className.contains("Settings")) {
                    StylusSettingsInjector.attachToActivity(activity)
                }
            }
            result
        }

        val onPauseMethod = activityClass.getDeclaredMethod("onPause").apply { isAccessible = true }
        hook(onPauseMethod).intercept { chain ->
            val activity = chain.thisObject as? Activity
            if (activity != null && activity.javaClass.name == TARGET_CANVAS_ACTIVITY) {
                NoteScribbleHook.onActivityPaused(activity)
            }
            chain.proceed()
        }

        // 兜底 dispatchKeyEvent: 笔上侧键拦截与设置次级页返回键处理
        val dispatchKeyEventMethod = activityClass.getDeclaredMethod("dispatchKeyEvent", KeyEvent::class.java).apply { isAccessible = true }
        hook(dispatchKeyEventMethod).intercept { chain ->
            val activity = chain.thisObject as? Activity
            val event = chain.args[0] as? KeyEvent

            if (event != null && event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (StylusSettingsInjector.handleBackPress()) {
                    return@intercept true
                }
            }

            if (activity != null && event != null && activity.javaClass.name == TARGET_CANVAS_ACTIVITY) {
                if (event.keyCode == KeyEvent.KEYCODE_E && isGaomonEvent(event)) {
                    val action = GaomonSettingsState.penUpper
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        activity.runOnUiThread {
                            NoteScribbleHook.executeAction(activity, action, isDown = true)
                        }
                    } else if (event.action == KeyEvent.ACTION_UP) {
                        activity.runOnUiThread {
                            NoteScribbleHook.executeAction(activity, action, isDown = false)
                        }
                    }
                    return@intercept true
                }
            }
            chain.proceed()
        }

        // 兜底 onBackPressed 拦截次级设置页
        try {
            val onBackPressedMethod = activityClass.getDeclaredMethod("onBackPressed").apply { isAccessible = true }
            hook(onBackPressedMethod).intercept { chain ->
                if (StylusSettingsInjector.handleBackPress()) {
                    return@intercept null
                }
                chain.proceed()
            }
        } catch (_: Throwable) {}

        // 兜底 dispatchGenericMotionEvent: 笔下侧键悬停检测
        val genericMotionMethod = activityClass.getDeclaredMethod("dispatchGenericMotionEvent", MotionEvent::class.java).apply { isAccessible = true }
        hook(genericMotionMethod).intercept { chain ->
            val activity = chain.thisObject as? Activity
            val event = chain.args[0] as? MotionEvent
            if (activity != null && event != null && activity.javaClass.name == TARGET_CANVAS_ACTIVITY) {
                handleStylusMotionEvent(activity, event)
            }
            chain.proceed()
        }

        // 兜底 dispatchTouchEvent: 笔下侧键落笔检测
        val touchMethod = activityClass.getDeclaredMethod("dispatchTouchEvent", MotionEvent::class.java).apply { isAccessible = true }
        hook(touchMethod).intercept { chain ->
            val activity = chain.thisObject as? Activity
            val event = chain.args[0] as? MotionEvent
            if (activity != null && event != null && activity.javaClass.name == TARGET_CANVAS_ACTIVITY) {
                handleStylusMotionEvent(activity, event)
            }
            chain.proceed()
        }
    }

    private fun handleStylusMotionEvent(activity: Activity, event: MotionEvent) {
        var isStylus = false
        var isEraserTool = false

        for (i in 0 until event.pointerCount) {
            val toolType = event.getToolType(i)
            if (toolType == MotionEvent.TOOL_TYPE_STYLUS) {
                isStylus = true
            } else if (toolType == MotionEvent.TOOL_TYPE_ERASER) {
                isStylus = true
                isEraserTool = true
            }
        }

        if (!isStylus && !event.isFromSource(InputDevice.SOURCE_STYLUS)) {
            return
        }

        val buttonState = event.buttonState
        val isPrimaryPressed = (buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 || isEraserTool

        val action = GaomonSettingsState.penLower

        if (isPrimaryPressed && !isLowerButtonHeld) {
            isLowerButtonHeld = true
            activity.runOnUiThread {
                NoteScribbleHook.executeAction(activity, action, isDown = true)
            }
        } else if (!isPrimaryPressed && isLowerButtonHeld) {
            isLowerButtonHeld = false
            activity.runOnUiThread {
                NoteScribbleHook.executeAction(activity, action, isDown = false)
            }
        }
    }

    private fun isGaomonEvent(event: KeyEvent): Boolean {
        val device = event.device ?: return false
        if (device.vendorId == 0x256C) return true
        val name = device.name?.lowercase() ?: ""
        return name.contains("gaomon") || name.contains("tablet") || name.contains("m8")
    }
}
