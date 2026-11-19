package com.example.wandremote.desktop.input

import com.example.wandremote.shared.MouseButton
import java.awt.MouseInfo
import java.awt.Robot
import java.awt.event.InputEvent
import kotlin.math.roundToInt

/** Thin wrapper over [Robot]. Not wired to the network yet. */
class InputController {
    private val robot = Robot().apply { autoDelay = 0 }

    fun moveBy(dx: Int, dy: Int) {
        val p = MouseInfo.getPointerInfo().location
        robot.mouseMove(p.x + dx, p.y + dy)
    }

    fun moveTo(x: Int, y: Int) = robot.mouseMove(x, y)

    fun press(button: MouseButton) = robot.mousePress(mask(button))

    fun release(button: MouseButton) = robot.mouseRelease(mask(button))

    fun scroll(notches: Float) = robot.mouseWheel(notches.roundToInt())

    /** Presses all key codes in order, then releases them in reverse (e.g. VK_CONTROL, VK_Z). */
    fun keyCombo(vararg keyCodes: Int) {
        keyCodes.forEach { robot.keyPress(it) }
        keyCodes.reversed().forEach { robot.keyRelease(it) }
    }

    private fun mask(button: MouseButton): Int = when (button) {
        MouseButton.LEFT -> InputEvent.BUTTON1_DOWN_MASK
        MouseButton.RIGHT -> InputEvent.BUTTON3_DOWN_MASK
        MouseButton.MIDDLE -> InputEvent.BUTTON2_DOWN_MASK
    }
}
