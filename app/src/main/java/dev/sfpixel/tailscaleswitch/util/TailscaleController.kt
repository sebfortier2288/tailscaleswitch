package dev.sfpixel.tailscaleswitch.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

object TailscaleController {
    private const val TAG = "TailscaleController"
    private const val TAILSCALE_PACKAGE = "com.tailscale.ipn"
    private const val TAILSCALE_RECEIVER = "com.tailscale.ipn.IPNReceiver"
    private const val ACTION_CONNECT = "com.tailscale.ipn.CONNECT_VPN"
    private const val ACTION_DISCONNECT = "com.tailscale.ipn.DISCONNECT_VPN"

    fun connect(context: Context) {
        sendIntent(context, ACTION_CONNECT)
    }

    fun disconnect(context: Context) {
        sendIntent(context, ACTION_DISCONNECT)
    }

    private fun sendIntent(context: Context, action: String) {
        Log.d(TAG, "Sending intent: $action")
        val intent = Intent(action).apply {
            component = ComponentName(TAILSCALE_PACKAGE, TAILSCALE_RECEIVER)
            setPackage(TAILSCALE_PACKAGE)
        }
        context.sendBroadcast(intent)
    }
}
