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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import dev.sfpixel.tailscaleswitch.ui.icon.AppIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import dev.sfpixel.tailscaleswitch.data.ActionLogEntry
import dev.sfpixel.tailscaleswitch.data.ActionType
import dev.sfpixel.tailscaleswitch.data.HistoryRepository
import dev.sfpixel.tailscaleswitch.data.SsidRepository
import dev.sfpixel.tailscaleswitch.ui.theme.TailscaleSwitchTheme
import dev.sfpixel.tailscaleswitch.util.startMonitoringService
import dev.sfpixel.tailscaleswitch.util.stopMonitoringService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = SsidRepository(this)
        val historyRepository = HistoryRepository(this)
        setContent {
            TailscaleSwitchTheme {
                MainScreen(repository, historyRepository)
            }
        }
    }
}

@Composable
fun MainScreen(repository: SsidRepository, historyRepository: HistoryRepository) {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var showHistory by remember { mutableStateOf(value = false) }

    BackHandler(enabled = showHistory) {
        showHistory = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showHistory) "Historique (24h)" else "Tailscale Switch") },
                navigationIcon = {
                    if (showHistory) {
                        IconButton(onClick = { showHistory = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                        }
                    }
                },
                actions = {
                    if (showHistory) {
                        IconButton(onClick = {
                            scope.launch { historyRepository.clearHistory() }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Effacer l'historique")
                        }
                    } else {
                        IconButton(onClick = { showHistory = true }) {
                            Icon(AppIcons.History, contentDescription = "Historique")
                        }
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (showHistory) {
                HistoryView(historyRepository)
            } else {
                MainContent(
                    repository = repository,
                    historyRepository = historyRepository,
                    lifecycleOwner = lifecycleOwner
                )
            }
        }
    }
}

@Composable
fun MainContent(
    repository: SsidRepository,
    historyRepository: HistoryRepository,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
        val allGranted = permissions.values.all { it }
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

    Column(
        modifier = Modifier
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
                            historyRepository.logAction(ActionType.SERVICE, "Service activé manuellement")
                            startMonitoringService(context)
                        } else {
                            historyRepository.logAction(ActionType.SERVICE, "Service désactivé manuellement")
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
                        historyRepository.logAction(
                            ActionType.SERVICE,
                            "Option auto-déconnexion ${if (checked) "activée" else "désactivée"}"
                        )
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
                            historyRepository.logAction(ActionType.SERVICE, "SSID approuvé ajouté : $newSsid")
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
                            scope.launch {
                                repository.removeSsid(ssid)
                                historyRepository.logAction(ActionType.SERVICE, "SSID approuvé supprimé : $ssid")
                            }
                        },
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryView(historyRepository: HistoryRepository) {
    val logs by historyRepository.recentLogs.collectAsState(initial = emptyList())
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val dayFormat = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()) }

    if (logs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = AppIcons.History,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Aucune action enregistrée au cours des 24 dernières heures.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(logs, key = { it.id }) { log ->
                HistoryItemCard(log = log, dateFormat = dateFormat, dayFormat = dayFormat)
            }
        }
    }
}

@Composable
fun HistoryItemCard(log: ActionLogEntry, dateFormat: SimpleDateFormat, dayFormat: SimpleDateFormat) {
    val (icon: ImageVector, color) = when (log.type) {
        ActionType.CONNECT -> Pair(Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary)
        ActionType.DISCONNECT -> Pair(AppIcons.PowerSettingsNew, MaterialTheme.colorScheme.error)
        ActionType.NETWORK_CHANGE -> Pair(AppIcons.Wifi, MaterialTheme.colorScheme.secondary)
        ActionType.TIMER -> Pair(AppIcons.Timer, MaterialTheme.colorScheme.tertiary)
        ActionType.SERVICE -> Pair(Icons.Default.Info, MaterialTheme.colorScheme.outline)
    }

    val timeString = remember(log.timestamp) {
        val now = System.currentTimeMillis()
        val calNow = Calendar.getInstance().apply { timeInMillis = now }
        val calLog = Calendar.getInstance().apply { timeInMillis = log.timestamp }
        val isToday = (calNow[Calendar.YEAR] == calLog[Calendar.YEAR]) &&
                (calNow[Calendar.DAY_OF_YEAR] == calLog[Calendar.DAY_OF_YEAR])
        if (isToday) dateFormat.format(log.timestamp) else dayFormat.format(log.timestamp)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!log.details.isNullOrBlank()) {
                    Text(
                        text = log.details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = timeString,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
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
fun TopAppBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    CenterAlignedTopAppBar(
        title = title,
        navigationIcon = navigationIcon,
        actions = actions
    )
}
