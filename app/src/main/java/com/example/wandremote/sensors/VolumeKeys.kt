package com.example.wandremote.sensors

/**
 * Bridge between MainActivity's key events and the Pointer screen.
 * The Pointer screen sets [handler] while visible and connected; MainActivity then consumes volume keys.
 * Arguments: keyCode, pressed.
 */
object VolumeKeys {
    @Volatile var handler: ((keyCode: Int, pressed: Boolean) -> Unit)? = null
}
