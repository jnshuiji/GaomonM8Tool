package com.gaomon.m8.daemon

import android.content.Context
import android.content.Intent
import android.util.Log
import com.gaomon.m8.model.ActionType
import com.gaomon.m8.model.KeyConfig
import com.gaomon.m8.xposed.NoteScribbleHook
import java.io.BufferedReader
import java.io.InputStreamReader

class InputEventDaemon(private val context: Context) {

    companion object {
        private const val TAG = "GaomonDaemon"
        private const val TARGET_PACKAGE = "com.onyx.galaxy.note"
    }

    @Volatile
    private var isRunning = false
    private var workerThread: Thread? = null
    private var suProcess: Process? = null
    private val keyConfig = KeyConfig(context)

    fun start() {
        if (isRunning) return
        isRunning = true
        workerThread = Thread({ runLoop() }, "GaomonPenDaemon").apply {
            isDaemon = true
            start()
        }
        Log.i(TAG, "Gaomon M8 stylus daemon started")
    }

    fun stop() {
        isRunning = false
        try {
            suProcess?.destroy()
        } catch (ignored: Throwable) {}
        workerThread?.interrupt()
        workerThread = null
        Log.i(TAG, "Gaomon M8 stylus daemon stopped")
    }

    private fun runLoop() {
        while (isRunning) {
            try {
                val cmd = arrayOf(
                    "su", "-c",
                    "settings put secure show_ime_with_hard_keyboard 1; getevent -l"
                )
                val process = Runtime.getRuntime().exec(cmd)
                suProcess = process

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var ctrlHeld = false
                var currentLine = reader.readLine()

                while (isRunning && currentLine != null) {
                    // 仅监听按键相关的输入事件
                    if (currentLine.contains("EV_KEY")) {
                        handleEventLine(currentLine, ctrlHeld) { newCtrl ->
                            ctrlHeld = newCtrl
                        }
                    }
                    currentLine = reader.readLine()
                }
                process.waitFor()
            } catch (e: InterruptedException) {
                break
            } catch (t: Throwable) {
                Log.e(TAG, "Error in stylus daemon loop", t)
                try { Thread.sleep(2000) } catch (ignored: InterruptedException) {}
            }
        }
    }

    private fun handleEventLine(line: String, ctrlHeld: Boolean, onCtrlChanged: (Boolean) -> Unit) {
        val parts = line.split(":", limit = 2)
        if (parts.size < 2) return
        val eventStr = parts[1].trim()

        if (eventStr.contains("KEY_LEFTCTRL")) {
            val isDown = eventStr.contains("DOWN")
            onCtrlChanged(isDown)
            return
        }

        val isDown = eventStr.contains("DOWN")
        val isUp = eventStr.contains("UP")

        // 1. 笔上侧键: Gaomon 固件上报为无 Ctrl 的单独 KEY_E，或 BTN_STYLUS2 / BTN_RIGHT
        if ((eventStr.contains("KEY_E") && !ctrlHeld) ||
            eventStr.contains("BTN_STYLUS2") ||
            eventStr.contains("BTN_RIGHT")
        ) {
            if (isDown || isUp) {
                handleButtonAction(keyConfig.penUpper, isDown)
            }
            return
        }

        // 2. 笔下侧键: Gaomon 固件上报为 BTN_STYLUS 或 BTN_TOOL_RUBBER
        if (eventStr.contains("BTN_STYLUS") || eventStr.contains("BTN_TOOL_RUBBER")) {
            if (isDown || isUp) {
                handleButtonAction(keyConfig.penLower, isDown)
            }
            return
        }
    }

    private fun handleButtonAction(action: ActionType, isDown: Boolean) {
        when (action) {
            ActionType.TOGGLE_ERASER_HOLD -> {
                if (isDown) {
                    sendCommandToStarNote("eraser_hold_down")
                } else {
                    sendCommandToStarNote("eraser_hold_up")
                }
            }
            ActionType.SELECT_PEN -> {
                if (isDown) sendCommandToStarNote("pen")
            }
            ActionType.SELECT_ERASER -> {
                if (isDown) sendCommandToStarNote("eraser")
            }
            ActionType.SELECT_LASSO -> {
                if (isDown) sendCommandToStarNote("lasso")
            }
            ActionType.NONE -> {}
        }
    }

    private fun sendCommandToStarNote(cmd: String) {
        val intent = Intent(NoteScribbleHook.ACTION_COMMAND).apply {
            putExtra(NoteScribbleHook.EXTRA_CMD, cmd)
            setPackage(TARGET_PACKAGE)
        }
        context.sendBroadcast(intent)
        Log.d(TAG, "Sent stylus command to StarNote: $cmd")
    }
}
