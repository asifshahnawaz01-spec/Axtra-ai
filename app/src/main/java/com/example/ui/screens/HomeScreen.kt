package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.AxtraConstants
import com.example.core.OrbState
import com.example.overlay.OverlayService
import com.example.ui.components.AiOrbCanvas
import com.example.ui.components.ConfirmationOverlayCard
import com.example.ui.components.GlassCard
import com.example.ui.components.OwnerLabelBadge
import com.example.ui.components.TaskProgressCard
import com.example.ui.theme.LocalAxtraPalette
import com.example.ui.viewmodel.MainViewModel

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenBriefing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAxtraPalette.current
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val orbState by viewModel.orbState.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val lastAnswer by viewModel.lastAnswer.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOfflineMode.collectAsStateWithLifecycle()
    val errorState by viewModel.errorState.collectAsStateWithLifecycle()
    val batteryPercent by viewModel.batteryPercent.collectAsStateWithLifecycle()
    val rmsLevel by viewModel.rmsLevel.collectAsStateWithLifecycle()
    val noisyWarning by viewModel.noisyWarning.collectAsStateWithLifecycle()
    val executionState by viewModel.executionState.collectAsStateWithLifecycle()
    val isUrduTtsMissing by viewModel.isUrduVoiceMissing.collectAsStateWithLifecycle()
    val isServiceRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()

    var typedCommand by remember { mutableStateOf("") }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.toggleMicListening()
    }

    val startVoiceOrRequestMic: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.toggleMicListening()
        else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("home_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header with Owner Label + Always On Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = AxtraConstants.APP_NAME,
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.primaryNeon
                )
                Spacer(Modifier.height(2.dp))
                OwnerLabelBadge(fontSizeSp = 12f)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Always On",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (settings.alwaysOnEnabled) palette.primaryNeon else palette.textSecondary
                )
                Spacer(Modifier.width(6.dp))
                Switch(
                    checked = settings.alwaysOnEnabled,
                    onCheckedChange = { viewModel.toggleAlwaysOn(it) },
                    modifier = Modifier.testTag("home_always_on_switch"),
                    colors = SwitchDefaults.colors(checkedThumbColor = palette.primaryNeon)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Battery, Service & Offline status row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = palette.surfaceGlass,
                border = BorderStroke(1.dp, palette.borderGlow)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.BatteryChargingFull, contentDescription = null, tint = palette.successGreen, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("$batteryPercent% • ${if (isServiceRunning) "Active" else "Standby"}", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                }
            }

            if (settings.wakeWordEnabled) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = palette.primaryNeon.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, palette.primaryNeon.copy(alpha = 0.55f))
                ) {
                    Text(
                        text = "Say \"Hey Astra\"",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.primaryNeon,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            if (isOffline) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = palette.errorRed.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, palette.errorRed.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CloudOff, contentDescription = null, tint = palette.errorRed, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Offline Mode", style = MaterialTheme.typography.labelMedium, color = palette.errorRed)
                    }
                }
            }
        }

        // Urdu Voice Pack missing warning
        AnimatedVisibility(visible = isUrduTtsMissing) {
            GlassCard(
                modifier = Modifier.padding(top = 10.dp),
                borderColor = palette.goldAccent
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اردو TTS آواز موجود نہیں ہے۔",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.goldAccent,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.installUrduTtsData() }) {
                        Text("Download Urdu Voice", color = palette.primaryNeon)
                    }
                }
            }
        }

        // Overlay permission prompt when Always-On is enabled without Draw Over Apps access
        val lifecycleOwner = LocalLifecycleOwner.current
        var canDrawOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
        DisposableEffect(lifecycleOwner, settings.alwaysOnEnabled) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    val granted = Settings.canDrawOverlays(context)
                    canDrawOverlay = granted
                    if (granted && settings.alwaysOnEnabled) {
                        runCatching { context.startService(Intent(context, OverlayService::class.java)) }
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        AnimatedVisibility(visible = settings.alwaysOnEnabled && !canDrawOverlay) {
            GlassCard(
                modifier = Modifier.padding(top = 10.dp),
                borderColor = palette.primaryNeon
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اسکرین بٹن (Overlay) کے لیے Display Over Apps اجازت دیں۔",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.primaryNeon,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        runCatching { context.startActivity(intent) }
                    }) {
                        Text("Allow Overlay", color = palette.goldAccent)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Center AI Orb
        AiOrbCanvas(
            orbState = orbState,
            rmsLevel = rmsLevel,
            lowEndMode = settings.lowEndPhoneMode,
            size = 204.dp,
            onClick = startVoiceOrRequestMic
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = when (orbState) {
                OrbState.LISTENING -> "سن رہی ہوں... (Listening)"
                OrbState.THINKING -> "سوچ رہی ہوں... (Thinking)"
                OrbState.SPEAKING -> "بول رہی ہوں... (Tap to interrupt)"
                OrbState.ERROR -> "مسئلہ پیش آیا (Tap to retry)"
                OrbState.IDLE -> "بات کرنے کے لیے آرب دبائیں"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = palette.primaryNeon
        )

        // Mic level meter & noise tip while listening
        AnimatedVisibility(visible = orbState == OrbState.LISTENING) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = { rmsLevel },
                    modifier = Modifier.fillMaxWidth(0.6f).height(5.dp),
                    color = palette.primaryNeon,
                    trackColor = palette.surfaceElevated
                )
                if (noisyWarning) {
                    Text(
                        text = "ماحول میں شور زیادہ ہے، فون کے قریب بولیں۔",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.goldAccent,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Live transcript
        AnimatedVisibility(visible = partialTranscript.isNotBlank()) {
            GlassCard(modifier = Modifier.padding(top = 10.dp)) {
                Text(
                    text = "You: $partialTranscript",
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.textPrimary
                )
            }
        }

        // Error card with Retry
        errorState?.let { err ->
            Spacer(Modifier.height(10.dp))
            GlassCard(borderColor = palette.errorRed) {
                Text(err.messageUr, style = MaterialTheme.typography.bodyLarge, color = palette.errorRed)
                Text(err.messageEn, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    err.retryCommand?.let { cmd ->
                        Button(
                            onClick = { viewModel.submitCommand(cmd) },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.primaryNeon)
                        ) {
                            Text("Retry", color = Color.Black)
                        }
                    }
                    TextButton(onClick = { viewModel.dismissError() }) {
                        Text("Dismiss", color = palette.textSecondary)
                    }
                }
            }
        }

        // Confirmation Card
        executionState.confirmation?.let { confirm ->
            Spacer(Modifier.height(10.dp))
            ConfirmationOverlayCard(
                confirmation = confirm,
                onConfirm = { viewModel.resolveConfirmation(true) },
                onCancel = { viewModel.resolveConfirmation(false) }
            )
        }

        // Multi-step Task Progress Card
        if (executionState.steps.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            TaskProgressCard(
                steps = executionState.steps,
                onCancel = { viewModel.stopEverything() }
            )
        }

        Spacer(Modifier.height(12.dp))

        // Last Answer Card + Kill Switch
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = palette.goldAccent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Axtra Reply", style = MaterialTheme.typography.labelMedium, color = palette.goldAccent)
                }
                if (orbState == OrbState.SPEAKING || orbState == OrbState.THINKING || executionState.isRunning) {
                    TextButton(
                        onClick = { viewModel.stopEverything() },
                        modifier = Modifier.testTag("home_kill_switch_btn")
                    ) {
                        Icon(Icons.Filled.StopCircle, contentDescription = "Stop Everything", tint = palette.errorRed, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Stop", color = palette.errorRed)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = lastAnswer,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.textPrimary
            )
        }

        Spacer(Modifier.height(12.dp))

        // Quick Action Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickChip("Call", Icons.Filled.Call) { typedCommand = "Call " }
            QuickChip("WhatsApp", Icons.AutoMirrored.Filled.Send) { typedCommand = "WhatsApp par message likho " }
            QuickChip("Alarm", Icons.Filled.Alarm) { viewModel.submitCommand("Subah 6 baje ka alarm laga do") }
            QuickChip("Briefing", Icons.Filled.WbSunny) { onOpenBriefing() }
            QuickChip("Notifications", Icons.Filled.NotificationsActive) { viewModel.submitCommand("Kisi ka message aya hai?") }
            QuickChip("Find Phone", Icons.Filled.PhoneAndroid) { viewModel.submitCommand("Axtra kahan ho") }
        }

        Spacer(Modifier.height(12.dp))

        // Type Command Bar (supports Urdu / Saraiki / English / Roman Urdu)
        OutlinedTextField(
            value = typedCommand,
            onValueChange = { typedCommand = it },
            placeholder = { Text("Urdu, Saraiki or English mein likhein...", color = palette.textSecondary) },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = palette.primaryNeon,
                unfocusedBorderColor = palette.borderGlow,
                focusedTextColor = palette.textPrimary,
                unfocusedTextColor = palette.textPrimary
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                if (typedCommand.isNotBlank()) {
                    viewModel.submitCommand(typedCommand)
                    typedCommand = ""
                }
            }),
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (typedCommand.isNotBlank()) {
                            viewModel.submitCommand(typedCommand)
                            typedCommand = ""
                        }
                    },
                    modifier = Modifier.testTag("home_send_command_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send command", tint = palette.primaryNeon)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_command_input")
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun QuickChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val palette = LocalAxtraPalette.current
    AssistChip(
        onClick = onClick,
        label = { Text(label, color = palette.textPrimary) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = palette.primaryNeon, modifier = Modifier.size(16.dp)) },
        colors = AssistChipDefaults.assistChipColors(containerColor = palette.surfaceGlass),
        border = BorderStroke(1.dp, palette.borderGlow),
        modifier = Modifier.testTag("quick_chip_${label.lowercase().replace(" ", "_")}")
    )
}
