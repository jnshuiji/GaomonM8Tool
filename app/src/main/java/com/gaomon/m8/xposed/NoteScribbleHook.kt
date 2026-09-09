package com.gaomon.m8.xposed

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import java.lang.ref.WeakReference

object NoteScribbleHook {
    private const val TAG = "GaomonM8Hook"
    const val ACTION_COMMAND = "com.gaomon.m8.ACTION_COMMAND"
    const val EXTRA_CMD = "cmd"

    private var activeActivityRef: WeakReference<Activity>? = null

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
        try {
            val filter = IntentFilter(ACTION_COMMAND)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.registerReceiver(commandReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                activity.registerReceiver(commandReceiver, filter)
            }
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
        try {
            activity.unregisterReceiver(commandReceiver)
            Log.i(TAG, "Unregistered Gaomon command receiver from StarNote")
        } catch (ignored: Throwable) {}
    }

    private fun handleCommand(activity: Activity, cmd: String) {
        when (cmd) {
            "pen" -> selectPen(activity)
            "eraser" -> selectEraser(activity)
            "lasso" -> selectLasso(activity)
            "eraser_hold_down" -> onEraserHoldDown(activity)
            "eraser_hold_up" -> onEraserHoldUp(activity)
            "lasso_hold_down" -> onLassoHoldDown(activity)
            "lasso_hold_up" -> onLassoHoldUp(activity)
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
                } catch (ignored: Throwable) {}
            }
            if (child is ViewGroup) {
                val found = findViewRecursively(child, targetName)
                if (found != null) return found
            }
        }
        return null
    }

    private fun getToolContainer(activity: Activity): ViewGroup? {
        return findViewByName(activity, "ll_shape_tool_container") as? ViewGroup
    }

    private fun selectToolByEnumName(activity: Activity, vararg targetEnumNames: String): Boolean {
        val container = getToolContainer(activity) ?: return false
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            try {
                var clazz: Class<*>? = child.javaClass
                while (clazz != null && clazz.name != "android.view.View") {
                    for (field in clazz.declaredFields) {
                        field.isAccessible = true
                        val value = field.get(child)
                        if (value != null && (value is Enum<*> || value.javaClass.isEnum)) {
                            val enumName = value.toString()
                            if (targetEnumNames.any { enumName.equals(it, ignoreCase = true) }) {
                                child.performClick()
                                Log.d(TAG, "Selected tool $enumName (child $i)")
                                return true
                            }
                        }
                    }
                    clazz = clazz.superclass
                }
            } catch (ignored: Throwable) {}
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

    private fun isToolSelected(child: View): Boolean {
        if (child.isSelected) return true
        try {
            var clazz: Class<*>? = child.javaClass
            while (clazz != null && clazz.name != "android.view.View") {
                for (field in clazz.declaredFields) {
                    if (field.type == Boolean::class.javaPrimitiveType) {
                        field.isAccessible = true
                        val value = field.getBoolean(child)
                        // StarNote ShapeIconView uses obfuscated field 't' for selection state
                        if (field.name == "t" && value) return true
                    }
                }
                clazz = clazz.superclass
            }
        } catch (ignored: Throwable) {}
        return false
    }

    private fun onEraserHoldDown(activity: Activity) {
        selectEraser(activity)
    }

    private fun onEraserHoldUp(activity: Activity) {
        selectPen(activity)
    }

    private fun onLassoHoldDown(activity: Activity) {
        selectLasso(activity)
    }

    private fun onLassoHoldUp(activity: Activity) {
        selectPen(activity)
    }
}
