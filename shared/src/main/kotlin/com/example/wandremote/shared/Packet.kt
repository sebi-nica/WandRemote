package com.example.wandremote.shared

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One-byte type id written right after [Protocol.MAGIC]. */
enum class PacketType(val id: Byte) {
    MOVE(1),
    BUTTON(2),
    SCROLL(3),
    ORIENTATION(4),
    ABSOLUTE(5),
    GESTURE(6),
    LASER(7),
    PING(8),
    PONG(9),
    HELLO(10),
    BYE(11),
    SET_MODE(12),
    ZOOM(13),
    KEY(14);

    companion object {
        private val byId = entries.associateBy { it.id }
        fun fromId(id: Byte): PacketType? = byId[id]
    }
}

enum class MouseButton(val id: Byte) {
    LEFT(0), RIGHT(1), MIDDLE(2);

    companion object {
        fun fromId(id: Byte): MouseButton = entries.firstOrNull { it.id == id } ?: LEFT
    }
}

enum class Gesture(val id: Byte) {
    BACK_TAP(0), DOUBLE_BACK_TAP(1), FLICK_LEFT(2), FLICK_RIGHT(3), SHAKE(4);

    companion object {
        fun fromId(id: Byte): Gesture? = entries.firstOrNull { it.id == id }
    }
}

/** Keyboard keys the phone can press on the PC. */
enum class RemoteKey(val id: Byte) {
    LEFT(0), RIGHT(1), UP(2), DOWN(3), PAGE_UP(4), PAGE_DOWN(5), ESCAPE(6), F5(7), ENTER(8), SPACE(9);

    companion object {
        fun fromId(id: Byte): RemoteKey? = entries.firstOrNull { it.id == id }
    }
}

/** Which control mode the phone is in; the PC adapts how it treats incoming packets. */
enum class RemoteMode(val id: Byte) {
    POINTER(0), PRESENTER(1), THREE_D(2);

    companion object {
        fun fromId(id: Byte): RemoteMode? = entries.firstOrNull { it.id == id }
    }
}

/** All packets exchanged over UDP. */
sealed class Packet(val type: PacketType) {
    /** Relative cursor movement in pixels. */
    data class Move(val dx: Float, val dy: Float) : Packet(PacketType.MOVE)

    /** Button press/release (a click is press + release). */
    data class Button(val button: MouseButton, val pressed: Boolean) : Packet(PacketType.BUTTON)

    /** Scroll amount in wheel notches (fractional allowed). */
    data class Scroll(val dx: Float, val dy: Float) : Packet(PacketType.SCROLL)

    /** Full device orientation as a unit quaternion (3D mode). */
    data class Orientation(val w: Float, val x: Float, val y: Float, val z: Float) : Packet(PacketType.ORIENTATION)

    /** Absolute cursor position, normalized 0..1 over the target screen (presenter mode). */
    data class Absolute(val x: Float, val y: Float) : Packet(PacketType.ABSOLUTE)

    /** A recognized gesture; the PC maps it to an action/shortcut. */
    data class GestureEvent(val gesture: Gesture) : Packet(PacketType.GESTURE)

    /** Laser-pointer overlay state, position normalized 0..1. */
    data class Laser(val on: Boolean, val x: Float, val y: Float) : Packet(PacketType.LASER)

    /** Latency probe; the PC echoes the timestamp back in a [Pong]. */
    data class Ping(val timestampNanos: Long) : Packet(PacketType.PING)
    data class Pong(val timestampNanos: Long) : Packet(PacketType.PONG)

    /** Sent by the phone when it connects (and periodically); [name] is truncated to fit. */
    data class Hello(val name: String) : Packet(PacketType.HELLO)

    /** Sent by the phone when it disconnects. */
    data object Bye : Packet(PacketType.BYE)

    /** Sent by the phone when the user switches mode (and with each Hello, so the PC stays in sync). */
    data class SetMode(val mode: RemoteMode) : Packet(PacketType.SET_MODE)

    /** 3D mode zoom: positive = zoom in, in arbitrary steps (fractional allowed). */
    data class Zoom(val amount: Float) : Packet(PacketType.ZOOM)

    /** Key press/release (a tap is press + release), e.g. arrow keys for slides. */
    data class Key(val key: RemoteKey, val pressed: Boolean) : Packet(PacketType.KEY)
}

/** Binary encoding: [MAGIC][type][payload...], little-endian. */
object PacketCodec {
    const val MAX_SIZE: Int = 64
    private const val MAX_NAME_BYTES: Int = 48

    fun encode(packet: Packet): ByteArray {
        val buf = ByteBuffer.allocate(MAX_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(Protocol.MAGIC).put(packet.type.id)
        when (packet) {
            is Packet.Move -> buf.putFloat(packet.dx).putFloat(packet.dy)
            is Packet.Button -> buf.put(packet.button.id).put(if (packet.pressed) 1 else 0)
            is Packet.Scroll -> buf.putFloat(packet.dx).putFloat(packet.dy)
            is Packet.Orientation -> buf.putFloat(packet.w).putFloat(packet.x).putFloat(packet.y).putFloat(packet.z)
            is Packet.Absolute -> buf.putFloat(packet.x).putFloat(packet.y)
            is Packet.GestureEvent -> buf.put(packet.gesture.id)
            is Packet.Laser -> buf.put(if (packet.on) 1 else 0).putFloat(packet.x).putFloat(packet.y)
            is Packet.Ping -> buf.putLong(packet.timestampNanos)
            is Packet.Pong -> buf.putLong(packet.timestampNanos)
            is Packet.Hello -> {
                val bytes = packet.name.toByteArray(Charsets.UTF_8).let { if (it.size > MAX_NAME_BYTES) it.copyOf(MAX_NAME_BYTES) else it }
                buf.put(bytes.size.toByte()).put(bytes)
            }
            Packet.Bye -> Unit
            is Packet.SetMode -> buf.put(packet.mode.id)
            is Packet.Zoom -> buf.putFloat(packet.amount)
            is Packet.Key -> buf.put(packet.key.id).put(if (packet.pressed) 1 else 0)
        }
        return buf.array().copyOf(buf.position())
    }

    /** Returns null for malformed or foreign data. */
    fun decode(data: ByteArray, length: Int = data.size): Packet? {
        if (length < 2) return null
        val buf = ByteBuffer.wrap(data, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        if (buf.get() != Protocol.MAGIC) return null
        val type = PacketType.fromId(buf.get()) ?: return null
        return try {
            when (type) {
                PacketType.MOVE -> Packet.Move(buf.float, buf.float)
                PacketType.BUTTON -> Packet.Button(MouseButton.fromId(buf.get()), buf.get() != 0.toByte())
                PacketType.SCROLL -> Packet.Scroll(buf.float, buf.float)
                PacketType.ORIENTATION -> Packet.Orientation(buf.float, buf.float, buf.float, buf.float)
                PacketType.ABSOLUTE -> Packet.Absolute(buf.float, buf.float)
                PacketType.GESTURE -> Packet.GestureEvent(Gesture.fromId(buf.get()) ?: return null)
                PacketType.LASER -> Packet.Laser(buf.get() != 0.toByte(), buf.float, buf.float)
                PacketType.PING -> Packet.Ping(buf.long)
                PacketType.PONG -> Packet.Pong(buf.long)
                PacketType.HELLO -> {
                    val len = buf.get().toInt() and 0xFF
                    if (len > MAX_NAME_BYTES) return null
                    val bytes = ByteArray(len).also { buf.get(it) }
                    Packet.Hello(String(bytes, Charsets.UTF_8))
                }
                PacketType.BYE -> Packet.Bye
                PacketType.SET_MODE -> Packet.SetMode(RemoteMode.fromId(buf.get()) ?: return null)
                PacketType.ZOOM -> Packet.Zoom(buf.float)
                PacketType.KEY -> Packet.Key(RemoteKey.fromId(buf.get()) ?: return null, buf.get() != 0.toByte())
            }
        } catch (e: java.nio.BufferUnderflowException) {
            null
        }
    }
}
