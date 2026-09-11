package com.gaomon.m8.daemon

import java.util.regex.Pattern

object DeviceProcParser {

    private val HANDLER_PATTERN = Pattern.compile("""Handlers=.*?(event\d+)""")
    private val NAME_PATTERN = Pattern.compile("""Name="([^"]+)"""")

    /**
     * 解析 /proc/bus/input/devices 输出，查找属于 Gaomon M8 的 input event 设备节点路径。
     * 例如返回 ["/dev/input/event16", "/dev/input/event19"]
     */
    fun parseGaomonEventNodes(devicesProcOutput: String): List<String> {
        val result = mutableListOf<String>()
        if (devicesProcOutput.isBlank()) return result

        val blocks = devicesProcOutput.split("\n\n")
        for (block in blocks) {
            val isGaomon = block.contains("256c", ignoreCase = true) ||
                    block.contains("gaomon", ignoreCase = true)
            if (isGaomon) {
                val handlerMatcher = HANDLER_PATTERN.matcher(block)
                if (handlerMatcher.find()) {
                    val eventName = handlerMatcher.group(1)
                    if (!eventName.isNullOrBlank()) {
                        result.add("/dev/input/$eventName")
                    }
                }
            }
        }
        return result
    }

    /**
     * 检查当前事件行是否属于 Gaomon 设备。
     * 若已识别出 Gaomon 节点集合，行首的设备路径必须在集合中；
     * 若节点集合为空（例如未探测到或热插拔前），返回 true 允许按键 fallback 识别。
     */
    fun isEventFromGaomon(line: String, gaomonNodes: Set<String>): Boolean {
        if (gaomonNodes.isEmpty()) return true
        val colonIndex = line.indexOf(':')
        if (colonIndex <= 0) return false
        val devicePath = line.substring(0, colonIndex).trim()
        return gaomonNodes.contains(devicePath)
    }
}
