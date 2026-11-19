package com.example.wandremote

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.wandremote.sensors.VolumeKeys
import com.example.wandremote.shared.Protocol
import com.example.wandremote.ui.theme.WandRemoteTheme

class MainActivity : ComponentActivity() {
    private val vm: WandViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WandRemoteTheme { WandRemoteApp() } }
        if (savedInstanceState == null) handleLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    private fun handleLink(intent: Intent) {
        val link = intent.data?.takeIf { it.scheme == Protocol.PAIRING_SCHEME } ?: return
        intent.data = null // don't handle the same link again
        vm.handlePairingLink(link.toString())
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val h = VolumeKeys.handler
            if (h != null) {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) h(code, true)
                    KeyEvent.ACTION_UP -> h(code, false)
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
