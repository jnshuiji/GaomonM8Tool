package com.gaomon.m8

import com.gaomon.m8.daemon.DeviceProcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProcParserTest {

    private val sampleProcDevices = """
I: Bus=0006 Vendor=15d9 Product=00a3 Version=0000
N: Name="Xiaomi Keyboard"
P: Phys=
S: Sysfs=/devices/0006:15D9:00A3.0001/input/input6
U: Uniq=
H: Handlers=leds event6 cpufreq 
B: PROP=0
B: EV=120013

I: Bus=0000 Vendor=0000 Product=0000 Version=0000
N: Name="xiaomi-touch"
P: Phys=xiaomi-touch/input0
S: Sysfs=/devices/virtual/input/input5
U: Uniq=
H: Handlers=event4 cpufreq 
B: PROP=0

I: Bus=0003 Vendor=256c Product=0064 Version=0110
N: Name="GAOMON Gaomon Tablet_M8 Pen"
P: Phys=usb-xhci-hcd.2.auto-1/input1
S: Sysfs=/devices/platform/soc/a600000.ssusb/a600000.dwc3/xhci-hcd.2.auto/usb2/2-1/2-1:1.1/0003:256C:0064.0021/input/input56
U: Uniq=
H: Handlers=event16 cpufreq 
B: PROP=0

I: Bus=0003 Vendor=256c Product=0064 Version=0110
N: Name="GAOMON Gaomon Tablet_M8 Keyboard"
P: Phys=usb-xhci-hcd.2.auto-1/input2
S: Sysfs=/devices/platform/soc/a600000.ssusb/a600000.dwc3/xhci-hcd.2.auto/usb2/2-1/2-1:1.2/0003:256C:0064.0022/input/input59
U: Uniq=
H: Handlers=event19 cpufreq 
B: PROP=0
    """.trimIndent()

    @Test
    fun testParseGaomonEventNodes() {
        val nodes = DeviceProcParser.parseGaomonEventNodes(sampleProcDevices)
        assertEquals(2, nodes.size)
        assertTrue(nodes.contains("/dev/input/event16"))
        assertTrue(nodes.contains("/dev/input/event19"))
        // 确认不包含 Xiaomi Keyboard (event6) 或 Touch (event4)
        assertFalse(nodes.contains("/dev/input/event6"))
        assertFalse(nodes.contains("/dev/input/event4"))
    }

    @Test
    fun testIsEventFromGaomonFilter() {
        val gaomonNodes = setOf("/dev/input/event16", "/dev/input/event19")

        // 来自高漫压感笔的事件
        val stylusEvent = "/dev/input/event16: EV_KEY       BTN_STYLUS           DOWN"
        assertTrue(DeviceProcParser.isEventFromGaomon(stylusEvent, gaomonNodes))

        // 来自高漫数位板按键的事件
        val m8KeyEvent = "/dev/input/event19: EV_KEY       KEY_E                DOWN"
        assertTrue(DeviceProcParser.isEventFromGaomon(m8KeyEvent, gaomonNodes))

        // 来自小米外接物理键盘打字产生的事件（必须过滤掉）
        val keyboardEvent = "/dev/input/event6: EV_KEY       KEY_E                DOWN"
        assertFalse(DeviceProcParser.isEventFromGaomon(keyboardEvent, gaomonNodes))

        // 来自触摸屏的滑动事件（必须过滤掉）
        val touchEvent = "/dev/input/event4: EV_ABS       ABS_MT_POSITION_X    000005a2"
        assertFalse(DeviceProcParser.isEventFromGaomon(touchEvent, gaomonNodes))
    }

    @Test
    fun testEmptyProcReturnsEmptyList() {
        val emptyNodes = DeviceProcParser.parseGaomonEventNodes("")
        assertTrue(emptyNodes.isEmpty())
    }
}
