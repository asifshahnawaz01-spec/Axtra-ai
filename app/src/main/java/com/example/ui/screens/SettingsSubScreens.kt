package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.AxtraConstants
import com.example.ui.components.GlassCard
import com.example.ui.components.OwnerLabelBadge
import com.example.ui.theme.LocalAxtraPalette
import com.example.utils.PermissionUtils

@Composable
fun KeepAliveGuideScreen(
    permissionUtils: PermissionUtils,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val palette = LocalAxtraPalette.current
    val context = LocalContext.current
    val isBatteryExempt = remember { permissionUtils.isGranted("battery_opt") }

    val oemGuides = remember {
        listOf(
            "Xiaomi / Redmi / POCO (MIUI / HyperOS)" to "1. Security App > Permissions > Autostart > Enable Axtra AI.\n2. App Info > Battery Saver > No Restrictions.\n3. Open Recents, long-press Axtra card > tap Lock icon.",
            "Oppo / Realme / OnePlus (ColorOS)" to "1. App Info > Battery Usage > Allow Background Activity & Allow Auto-Launch.\n2. Lock Axtra AI in Recent Apps tray.",
            "Vivo / iQOO (FuntouchOS)" to "1. iManager > App Manager > Autostart > Enable Axtra AI.\n2. Settings > Battery > High Background Power Consumption > Allow Axtra AI.",
            "Samsung (One UI)" to "1. Settings > Battery > Background usage limits > Never sleeping apps > Add Axtra AI.\n2. App Info > Battery > Unrestricted.",
            "Infinix / Tecno (XOS / HiOS)" to "1. Phone Master > Auto-start Management > Enable Axtra AI.\n2. Settings > Battery > App Power Consumption > Don't Optimize.",
            "Huawei / Honor (EMUI / MagicOS)" to "1. Settings > Battery > App Launch > Find Axtra AI > Switch to Manual and enable Auto-launch, Secondary launch, and Run in background."
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp).testTag("keep_alive_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = palette.primaryNeon)
                }
                Text("Keep Axtra Alive", style = MaterialTheme.typography.titleLarge, color = palette.primaryNeon)
            }
        }
        item {
            GlassCard(borderColor = if (isBatteryExempt) palette.successGreen else palette.goldAccent) {
                Text("Android Battery Optimization", style = MaterialTheme.typography.titleMedium, color = palette.goldAccent)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (isBatteryExempt) "Battery optimization exemption is already GRANTED."
                    else "Allow Axtra to stay active in the background without aggressive Android sleep.",
                    color = palette.textPrimary
                )
                if (!isBatteryExempt) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { runCatching { context.startActivity(permissionUtils.buildSpecialAccessIntent("battery_opt")) } },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primaryNeon)
                    ) { Text("Disable Battery Optimization", color = Color.Black) }
                }
            }
        }
        items(oemGuides) { (oem, steps) ->
            GlassCard {
                Text(oem, style = MaterialTheme.typography.titleMedium, color = palette.primaryNeon)
                Spacer(Modifier.height(6.dp))
                Text(steps, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
            }
        }
    }
}

@Composable
fun PermissionHealthScreen(
    permissionUtils: PermissionUtils,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val palette = LocalAxtraPalette.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { tick++ }

    val items = remember { permissionUtils.getAllPermissionItems() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp).testTag("permission_health_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = palette.primaryNeon)
                }
                Text("Permission Health (${items.count { permissionUtils.isGranted(it.id) }}/${items.size})", style = MaterialTheme.typography.titleLarge, color = palette.primaryNeon)
            }
        }
        items(items, key = { it.id }) { item ->
            val granted = remember(tick) { permissionUtils.isGranted(item.id) }
            GlassCard(borderColor = if (granted) palette.successGreen.copy(alpha = 0.5f) else palette.goldAccent.copy(alpha = 0.6f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${item.titleEn} • ${item.titleUr}", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                        Text(item.whyNeededEn, style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                    }
                    if (granted) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = "Granted", tint = palette.successGreen)
                    } else {
                        OutlinedButton(onClick = {
                            if (item.isSpecialAccess) {
                                runCatching { context.startActivity(permissionUtils.buildSpecialAccessIntent(item.id)) }
                            } else if (item.runtimePermissions.isNotEmpty()) {
                                permLauncher.launch(item.runtimePermissions.toTypedArray())
                            }
                        }) {
                            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = palette.goldAccent)
                            Text(" Fix", color = palette.goldAccent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyAboutScreen(
    onDeleteAllData: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val palette = LocalAxtraPalette.current
    var showConfirmDelete by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp).testTag("privacy_about_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = palette.primaryNeon)
                }
                Text("Privacy, Limitations & About", style = MaterialTheme.typography.titleLarge, color = palette.primaryNeon)
            }
        }
        item {
            GlassCard {
                Text("${AxtraConstants.APP_NAME} v${AxtraConstants.APP_VERSION}", style = MaterialTheme.typography.titleLarge, color = palette.primaryNeon)
                Text(AxtraConstants.APP_TAGLINE, color = palette.textSecondary)
                Spacer(Modifier.height(10.dp))
                OwnerLabelBadge(fontSizeSp = 16f)
            }
        }
        item {
            GlassCard {
                Text("Privacy Commitment / رازداری", style = MaterialTheme.typography.titleMedium, color = palette.goldAccent)
                Spacer(Modifier.height(6.dp))
                Text(
                    "• All memories, routines, notes, expenses, and logs stay strictly on your device in local Room & EncryptedSharedPreferences.\n" +
                        "• Only your explicit prompt text (and screen text ONLY when you ask 'Screen par kya hai?') is sent to Google Gemini using your own API key.\n" +
                        "• Banking, wallet, and password manager apps are blocked from screen reading by default.\n" +
                        "• آپ کا تمام ذاتی ڈیٹا صرف آپ کے فون پر محفوظ رہتا ہے۔",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textPrimary
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { showConfirmDelete = true },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.errorRed)
                ) {
                    Text("Delete All My Data / تمام ڈیٹا حذف کریں", color = Color.White)
                }
            }
        }
        item {
            GlassCard {
                Text("Known Android Limitations", style = MaterialTheme.typography.titleMedium, color = palette.primaryNeon)
                Spacer(Modifier.height(6.dp))
                Text(
                    "• Direct Wi-Fi / Mobile Data toggles are restricted by Android 10+; Axtra opens the official Settings Panel instead.\n" +
                        "• Call recording is blocked by Android and is not implemented.\n" +
                        "• Some banking apps set FLAG_SECURE which prevents Accessibility reading (Axtra also blocks them intentionally).\n" +
                        "• Android 14+ requires foreground microphone permission while the app is visible before starting Always-On.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
            }
        }
        item {
            GlassCard {
                Text("Open-Source Licenses", style = MaterialTheme.typography.titleMedium, color = palette.primaryNeon)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Built with Jetpack Compose, Material 3, AndroidX Room, Hilt, Retrofit, OkHttp, Moshi, Kotlinx Serialization, WorkManager, Biometric, CameraX, and Google ML Kit (Apache 2.0).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
            }
        }
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            containerColor = palette.surfaceElevated,
            title = { Text("Delete All Data?", color = palette.errorRed) },
            text = { Text("This clears all memories, routines, notes, expenses, logs, and your encrypted API key.", color = palette.textPrimary) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAllData()
                        showConfirmDelete = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.errorRed)
                ) { Text("Delete Everything", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}
