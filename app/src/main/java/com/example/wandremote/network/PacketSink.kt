package com.example.wandremote.network

import com.example.wandremote.shared.Packet

/** Anything that can receive the app's mouse/command packets (UDP to the companion app, or Bluetooth HID). */
fun interface PacketSink {
    /** Must never block the caller (it may be a sensor callback). */
    fun send(packet: Packet)
}
