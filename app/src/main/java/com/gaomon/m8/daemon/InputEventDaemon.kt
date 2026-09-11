package com.gaomon.m8.daemon

import android.content.Context
import android.content.Intent
import android.util.Log
import com.gaomon.m8.model.ActionType
import com.gaomon.m8.model.KeyConfig
import com.gaomon.m8.xposed.NoteScribbleHook
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArraySet

class InputEventDaemon(private val context: Context) {

    companion object {
        private const val TAG = "GaomonDaemon"
        private const val TARGET_PACKAGE = "com.onyx.galaxy.note"
    }

    @Volatile
    private var isRunning = false
    private var workerThread: Thread? = null
    private var suProcess: Process? = null
    private var currentReader: BufferedReader? = null
    private val keyConfig = KeyConfig(context)
    private val gaomonNodes = CopyOnWriteArraySet<String>()

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
            currentReader?.close()
        } catch (_: Throwable) {}
        try {
            suProcess?.destroy()
        } catch (_: Throwable) {}
        workerThread?.interrupt()
        workerThread = null
        currentReader = null
        suProcess = null
        Log.i(TAG, "Gaomon M8 stylus daemon stopped")
    }

    private fun refreshGaomonNodes() {
        try {
            val process = ProcessBuilder("su", "-c", "cat /proc/bus/input/devices").start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            val nodes = DeviceProcParser.parseGaomonEventNodes(output)
            if (nodes.isNotEmpty()) {
                gaomonNodes.clear()
                gaomonNodes.addAll(nodes)
                Log.i(TAG, "Detected Gaomon M8 input event nodes: $nodes")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to inspect /proc/bus/input/devices: ${t.message}")
        }
    }

    private fun runLoop() {
        refreshGaomonNodes()

        while (isRunning) {
            var reader: BufferedReader? = null
            var process: Process? = null
            try {
                val imeSetting = if (keyConfig.autoShowIme) "1" else "0"
                val cmd = arrayOf(
                    "su", "-c",
                    "settings put secure show_ime_with_hard_keyboard $imeSetting; getevent -l"
                )
                process = Runtime.getRuntime().exec(cmd)
                suProcess = process

                reader = BufferedReader(InputStreamReader(process.inputStream))
                currentReader = reader

                var ctrlHeld = false
                var currentLine = reader.readLine()

                while (isRunning && currentLine != null) {
                    // 1. 过滤：仅当事件属于 Gaomon 设备时才处理，彻底避免外接物理键盘（如打字输入 'e'）误触
                    if (currentLine.contains("EV_KEY") &&
                        DeviceProcParser.isEventFromGaomon(currentLine, gaomonNodes)
                    ) {
                        handleEventLine(currentLine, ctrlHeld) { newCtrl ->
                            ctrlHeld = newCtrl
                        }
                    }
                    currentLine = reader.readLine()
                }
                process.waitFor()
            } catch (_: InterruptedException) {
                break
            } catch (t: Throwable) {
                if (isRunning) {
                    Log.e(TAG, "Error in stylus daemon loop", t)
                    try { Thread.sleep(2000) } catch (_: InterruptedException) { break }
                }
            } finally {
                try { reader?.close() } catch (_: Throwable) {}
                try { process?.destroy() } catch (_: Throwable) {}
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
