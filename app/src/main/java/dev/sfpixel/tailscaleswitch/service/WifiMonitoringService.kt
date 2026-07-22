package dev.sfpixel.tailscaleswitch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dev.sfpixel.tailscaleswitch.MainActivity
import dev.sfpixel.tailscaleswitch.data.SsidRepository
import dev.sfpixel.tailscaleswitch.util.SsidUtils
import dev.sfpixel.tailscaleswitch.util.TailscaleController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

class WifiMonitoringService : LifecycleService() {

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var ssidRepository: SsidRepository
    private var currentSsid: String? = null
    private val activeNetworks = mutableSetOf<Network>()
    private var disconnectJob: Job? = null

    companion object {
        private const val TAG = "WifiMonitoringService"
        private const val CHANNEL_ID = "wifi_monitoring_channel"
        private const val NOTIFICATION_ID = 1
        private val DISCONNECT_DELAY = 1.minutes
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            synchronized(activeNetworks) {
                activeNetworks.add(network)
                cancelDisconnectTimer()
            }
            checkWifiAndToggleTailscale()
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            synchronized(activeNetworks) {
                activeNetworks.remove(network)
                if (activeNetworks.isEmpty()) {
                    startDisconnectTimer()
                }
            }
            checkWifiAndToggleTailscale()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                checkWifiAndToggleTailscale()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        ssidRepository = SsidRepository(this)
        createNotificationChannel()
        val notification = createNotification()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        registerNetworkCallback()
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    private fun startDisconnectTimer() {
        lifecycleScope.launch {
            if (ssidRepository.isAutoDisconnectEnabled.first()) {
                Log.d(TAG, "No network available. Starting 1 minute timer before disconnecting VPN.")
                disconnectJob?.cancel()
                disconnectJob = lifecycleScope.launch {
                    delay(DISCONNECT_DELAY)
                    Log.d(TAG, "Network still unavailable after 1 minute. Disconnecting Tailscale.")
                    TailscaleController.disconnect(this@WifiMonitoringService)
                }
            }
        }
    }

    private fun cancelDisconnectTimer() {
        if (disconnectJob != null) {
            Log.d(TAG, "Network restored. Canceling disconnect timer.")
            disconnectJob?.cancel()
            disconnectJob = null
        }
    }

    private fun checkWifiAndToggleTailscale() {
        lifecycleScope.launch {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val wifiInfo: WifiInfo? = wifiManager.connectionInfo

            val ssid = SsidUtils.cleanSsid(wifiInfo?.ssid)
            if (ssid == null) {
                // Not connected to a specific Wi-Fi
                handleDisconnected()
            } else {
                handleConnected(ssid)
            }
        }
    }

    private suspend fun handleConnected(ssid: String) {
        if (currentSsid == ssid) return
        currentSsid = ssid
        Log.d(TAG, "Connected to Wi-Fi: $ssid")

        val trustedSsids = ssidRepository.trustedSsids.first()
        if (trustedSsids.contains(ssid)) {
            Log.d(TAG, "Trusted SSID detected, disconnecting Tailscale")
            TailscaleController.disconnect(this)
        } else {
            Log.d(TAG, "Untrusted SSID detected, connecting Tailscale")
            TailscaleController.connect(this)
        }
    }

    private fun handleDisconnected() {
        if (currentSsid == null) return
        currentSsid = null
        Log.d(TAG, "Disconnected from Wi-Fi, connecting Tailscale")
        TailscaleController.connect(this)
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Wi-Fi Monitoring Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tailscale Switch Active")
            .setContentText("Monitoring Wi-Fi networks...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
}
