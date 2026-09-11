package com.gaomon.m8.xposed

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

object NoteScribbleHook {
    private const val TAG = "GaomonM8Hook"
    const val ACTION_COMMAND = "com.gaomon.m8.ACTION_COMMAND"
    const val EXTRA_CMD = "cmd"

    private var activeActivityRef: WeakReference<Activity>? = null

    // 缓存工具名称（如 "ERASER", "INK_PEN", "LASSO" 等）到对应的 View
    private val toolViewCache = ConcurrentHashMap<String, WeakReference<View>>()
    private var toolContainerRef: WeakReference<ViewGroup>? = null

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_COMMAND) return
            val cmd = intent.getStringExtra(EXTRA_CMD) ?: return
            Log.d(TAG, "Received Gaomon stylus command: $cmd")

            val activity = activeActivityRef?.get()
            if (activity == null || activity.isFinishing || activity.isDestroyed) {
                Log.w(TAG, "No active NoteScribbleActivity to handle command: $cmd")
                return
            }

            activity.runOnUiThread {
                handleCommand(activity, cmd)
            }
        }
    }

    fun onActivityResumed(activity: Activity) {
        activeActivityRef = WeakReference(activity)
        clearCaches()
        try {
            val filter = IntentFilter(ACTION_COMMAND)
            ContextCompat.registerReceiver(
                activity,
                commandReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            Log.i(TAG, "Registered Gaomon command receiver in StarNote")
            try {
                val notifyIntent = Intent("com.gaomon.m8.ACTION_HOOK_ACTIVE")
                activity.sendBroadcast(notifyIntent)
            } catch (_: Throwable) {}
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register receiver in StarNote", t)
        }
    }

    fun onActivityPaused(activity: Activity) {
        if (activeActivityRef?.get() == activity) {
            activeActivityRef = null
        }
        clearCaches()
        try {
            activity.unregisterReceiver(commandReceiver)
            Log.i(TAG, "Unregistered Gaomon command receiver from StarNote")
        } catch (_: Throwable) {}
    }

    private fun clearCaches() {
        toolViewCache.clear()
        toolContainerRef = null
    }

    private fun handleCommand(activity: Activity, cmd: String) {
        when (cmd) {
            "pen" -> selectPen(activity)
            "eraser" -> selectEraser(activity)
            "lasso" -> selectLasso(activity)
            "eraser_hold_down" -> onEraserHoldDown(activity)
            "eraser_hold_up" -> onEraserHoldUp(activity)
            else -> Log.w(TAG, "Unknown stylus command: $cmd")
        }
    }

    private fun findViewByName(activity: Activity, name: String): View? {
        val resId = activity.resources.getIdentifier(name, "id", activity.packageName)
        if (resId != 0) {
            val view = activity.findViewById<View>(resId)
            if (view != null) return view
        }
        val decor = activity.window.decorView as? ViewGroup ?: return null
        return findViewRecursively(decor, name)
    }

    private fun findViewRecursively(parent: ViewGroup, targetName: String): View? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.id != View.NO_ID) {
                try {
                    val entry = child.resources.getResourceEntryName(child.id)
                    if (entry == targetName) return child
                } catch (_: Throwable) {}
            }
            if (child is ViewGroup) {
                val found = findViewRecursively(child, targetName)
                if (found != null) return found
            }
        }
        return null
    }

    private fun getToolContainer(activity: Activity): ViewGroup? {
        toolContainerRef?.get()?.let { return it }
        val container = findViewByName(activity, "ll_shape_tool_container") as? ViewGroup
        if (container != null) {
            toolContainerRef = WeakReference(container)
        }
        return container
    }

    /**
     * 工具选择核心逻辑：
     * 1. 快路径：检查是否已缓存该工具 View
     * 2. 慢路径：遍历容器中的每个 View，基于运行时对象的实际 Enum 类型进行鲁棒比对（兼容混淆）
     */
    private fun selectToolByEnumName(activity: Activity, vararg targetEnumNames: String): Boolean {
        // 1. 快路径：直接从缓存中获取 View
        for (target in targetEnumNames) {
            val upperTarget = target.uppercase()
            val cachedView = toolViewCache[upperTarget]?.get()
            if (cachedView != null && cachedView.isAttachedToWindow) {
                cachedView.performClick()
                Log.d(TAG, "Fast-path: selected tool $upperTarget")
                return true
            }
        }

        // 2. 慢路径：遍历容器子 View 并缓存所有识别到的工具
        val container = getToolContainer(activity) ?: return false
        var targetFoundView: View? = null
        var foundEnumName: String? = null

        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            try {
                var clazz: Class<*>? = child.javaClass
                while (clazz != null && clazz.name != "android.view.View") {
                    for (field in clazz.declaredFields) {
                        field.isAccessible = true
                        val value = field.get(child)
                        if (value != null && (value is Enum<*> || value.javaClass.isEnum)) {
                            val enumName = value.toString().uppercase()
                            toolViewCache[enumName] = WeakReference(child)
                            if (targetFoundView == null && targetEnumNames.any { it.equals(enumName, ignoreCase = true) }) {
                                targetFoundView = child
                                foundEnumName = enumName
                            }
                        }
                    }
                    clazz = clazz.superclass
                }
            } catch (_: Throwable) {}
        }

        if (targetFoundView != null) {
            targetFoundView.performClick()
            Log.d(TAG, "Cold-path: selected tool $foundEnumName and populated cache")
            return true
        }

        return false
    }

    private fun selectEraser(activity: Activity) {
        val success = selectToolByEnumName(activity, "ERASER")
        if (!success) {
            val container = getToolContainer(activity) ?: return
            if (container.childCount > 3) container.getChildAt(3).performClick()
        }
    }

    private fun selectPen(activity: Activity) {
        val success = selectToolByEnumName(activity, "INK_PEN", "MARKER_PEN", "BALL_PEN", "PENCIL", "BRUSH_PEN", "PEN")
        if (!success) {
            val container = getToolContainer(activity) ?: return
            if (container.childCount > 0) container.getChildAt(0).performClick()
        }
    }

    private fun selectLasso(activity: Activity) {
        val success = selectToolByEnumName(activity, "LASSO")
        if (!success) {
            val container = getToolContainer(activity) ?: return
            if (container.childCount > 4) container.getChildAt(4).performClick()
        }
    }

    private fun onEraserHoldDown(activity: Activity) {
        selectEraser(activity)
    }

    private fun onEraserHoldUp(activity: Activity) {
        selectPen(activity)
    }
}
