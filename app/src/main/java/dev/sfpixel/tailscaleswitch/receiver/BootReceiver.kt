package dev.sfpixel.tailscaleswitch.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.sfpixel.tailscaleswitch.data.SsidRepository
import dev.sfpixel.tailscaleswitch.util.startMonitoringService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val repository = SsidRepository(context)
            CoroutineScope(Dispatchers.IO).launch {
                if (repository.isServiceEnabled.first()) {
                    startMonitoringService(context)
                }
            }
        }
    }
}
