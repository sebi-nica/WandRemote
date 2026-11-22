package com.example.wandremote.desktop.input

import com.example.wandremote.shared.MouseButton
import java.awt.MouseInfo
import java.awt.Robot
import java.awt.event.InputEvent
import kotlin.math.roundToInt

/** Thin wrapper over [Robot]; open so tests can substitute a fake. */
open class InputController {
    private val robot by lazy { Robot().apply { autoDelay = 0 } }

    open fun moveBy(dx: Int, dy: Int) {
        val p = MouseInfo.getPointerInfo().location
        robot.mouseMove(p.x + dx, p.y + dy)
    }

    open fun press(button: MouseButton) = robot.mousePress(mask(button))

    open fun release(button: MouseButton) = robot.mouseRelease(mask(button))

    open fun scroll(notches: Float) = robot.mouseWheel(notches.roundToInt())

    open fun keyPress(keyCode: Int) = robot.keyPress(keyCode)

    open fun keyRelease(keyCode: Int) = robot.keyRelease(keyCode)

    private fun mask(button: MouseButton): Int = when (button) {
        MouseButton.LEFT -> InputEvent.BUTTON1_DOWN_MASK
        MouseButton.RIGHT -> InputEvent.BUTTON3_DOWN_MASK
        MouseButton.MIDDLE -> InputEvent.BUTTON2_DOWN_MASK
    }
}
