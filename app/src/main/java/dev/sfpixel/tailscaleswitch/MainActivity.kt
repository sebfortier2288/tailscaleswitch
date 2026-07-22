package dev.sfpixel.tailscaleswitch

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.sfpixel.tailscaleswitch.data.SsidRepository
import dev.sfpixel.tailscaleswitch.ui.theme.TailscaleSwitchTheme
import dev.sfpixel.tailscaleswitch.util.startMonitoringService
import dev.sfpixel.tailscaleswitch.util.stopMonitoringService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = SsidRepository(this)
        setContent {
            TailscaleSwitchTheme {
                MainScreen(repository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(repository: SsidRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val trustedSsids by repository.trustedSsids.collectAsState(initial = emptySet())
    val isEnabled by repository.isServiceEnabled.collectAsState(initial = false)
    val isAutoDisconnectEnabled by repository.isAutoDisconnectEnabled.collectAsState(initial = false)
    
    var newSsid by remember { mutableStateOf(value = "") }
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(value = false) }
    var isBackgroundLocationGranted by remember { mutableStateOf(value = false) }
    var hibernationStatus by remember { mutableIntStateOf(UnusedAppRestrictionsConstants.DISABLED) }

    fun checkStatuses() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(context.packageName)

        isBackgroundLocationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Prior to Q, background location was included in FINE/COARSE
        }

        val future = PackageManagerCompat.getUnusedAppRestrictionsStatus(context)
        future.addListener(
            {
                try {
                    hibernationStatus = future.get()
                } catch (_: Exception) {
                    // Ignore
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkStatuses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionsToRequest = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(context, "Permissions accordées", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permissions manquantes pour le SSID", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            launcher.launch(missingPermissions.toTypedArray())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Tailscale Switch") })
        },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Daemon Status: ${if (isEnabled) "Active" else "Inactive"}", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        scope.launch {
                            repository.setServiceEnabled(checked)
                            if (checked) {
                                startMonitoringService(context)
                            } else {
                                stopMonitoringService(context)
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-disconnect", style = MaterialTheme.typography.titleMedium)
                    Text("Turn off VPN after 1 min with no network", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = isAutoDisconnectEnabled,
                    onCheckedChange = { checked ->
                        scope.launch {
                            repository.setAutoDisconnectEnabled(checked)
                        }
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // System Optimizations
            Text("System Optimizations", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            
            OptimizationItem(
                title = "Battery Optimization",
                status = if (isIgnoringBatteryOptimizations) "Disabled (Good)" else "Enabled (Might kill daemon)",
                isGood = isIgnoringBatteryOptimizations,
            ) {
                @Suppress("BatteryLife")
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:${context.packageName}".toUri()
                }
                context.startActivity(intent)
            }

            OptimizationItem(
                title = "Background Location",
                status = if (isBackgroundLocationGranted) "Always allowed (Good)" else "Only while in use (May fail in BG)",
                isGood = isBackgroundLocationGranted,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // On Android 11+ we must show a rationale first, but for simplicity we'll direct to settings
                    // or just try to request it if we have foreground first.
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                    context.startActivity(intent)
                }
            }

            OptimizationItem(
                title = "Auto-Reset Permissions",
                status = when (hibernationStatus) {
                    UnusedAppRestrictionsConstants.DISABLED -> "Disabled (Good)"
                    UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE -> "Not applicable"
                    else -> "Enabled (Might revoke permissions)"
                },
                isGood = (hibernationStatus == UnusedAppRestrictionsConstants.DISABLED) ||
                        (hibernationStatus == UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE),
            ) {
                val intent = IntentCompat.createManageUnusedAppRestrictionsIntent(context, context.packageName)
                context.startActivity(intent)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Add SSID
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newSsid,
                    onValueChange = { newSsid = it },
                    label = { Text("Wifi SSID") },
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        if (newSsid.isNotBlank()) {
                            scope.launch {
                                repository.addSsid(newSsid)
                                newSsid = ""
                            }
                        }
                    },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Ajouter")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Trusted Networks", style = MaterialTheme.typography.titleSmall)
            
            LazyColumn {
                items(trustedSsids.toList()) { ssid ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Text(ssid, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                scope.launch { repository.removeSsid(ssid) }
                            },
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OptimizationItem(title: String, status: String, isGood: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isGood) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.Info, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(title: @Composable () -> Unit) {
    CenterAlignedTopAppBar(title = title)
}
