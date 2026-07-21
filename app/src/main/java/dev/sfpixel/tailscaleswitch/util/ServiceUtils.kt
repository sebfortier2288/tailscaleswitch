package dev.sfpixel.tailscaleswitch.util

import android.content.Context
import android.content.Intent
import dev.sfpixel.tailscaleswitch.service.WifiMonitoringService

fun startMonitoringService(context: Context) {
    val intent = Intent(context, WifiMonitoringService::class.java)
    context.startForegroundService(intent)
}

fun stopMonitoringService(context: Context) {
    val intent = Intent(context, WifiMonitoringService::class.java)
    context.stopService(intent)
}
