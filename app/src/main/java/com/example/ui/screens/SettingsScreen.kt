package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.accessibility.AxtraAccessibilityService
import com.example.core.AxtraConstants
import com.example.core.AxtraThemeStyle
import com.example.core.ConfirmationLevel
import com.example.core.LanguageMode
import com.example.core.OverlayAnimationStyle
import com.example.core.PrayerCalculationMethod
import com.example.core.UiLanguage
import com.example.service.AxtraNotificationListenerService
import com.example.ui.components.GlassCard
import com.example.ui.components.OwnerLabelBadge
import com.example.ui.theme.LocalAxtraPalette
import com.example.ui.viewmodel.SettingsViewModel
import com.example.voice.VoiceAssistantHelper
import com.example.voice.WakeWordDetector
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenPermissionHealth: () -> Unit,
    onOpenKeepAliveGuide: () -> Unit,
    onOpenPrivacyAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAxtraPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val aiTestStatus by viewModel.aiTestStatus.collectAsStateWithLifecycle()
    val isTestingAi by viewModel.isTestingAi.collectAsStateWithLifecycle()
    val isSyncingCloud by viewModel.isSyncingCloud.collectAsStateWithLifecycle()
    val cloudSyncStatus by viewModel.cloudSyncStatus.collectAsStateWithLifecycle()
    val blockedApps by viewModel.blockedApps.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val voices by viewModel.availableVoices.collectAsStateWithLifecycle()
    val hotwordStatus by viewModel.hotwordStatus.collectAsStateWithLifecycle()
    val lastDetectedHotword by viewModel.lastDetectedHotword.collectAsStateWithLifecycle()
    val isVoiceServiceReady by viewModel.isVoiceInteractionReady.collectAsStateWithLifecycle()
    val isAccConnected by AxtraAccessibilityService.isServiceConnected.collectAsStateWithLifecycle()
    val isNotifConnected by AxtraNotificationListenerService.isConnected.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    var isDefaultAssistant by remember { mutableStateOf(VoiceAssistantHelper.isAxtraDefaultAssistant(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefaultAssistant = VoiceAssistantHelper.isAxtraDefaultAssistant(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val assistantRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isDefaultAssistant = VoiceAssistantHelper.isAxtraDefaultAssistant(context)
    }

    var pinDraft by remember { mutableStateOf("") }
    var newBlockedPkg by remember { mutableStateOf("") }
    var wakeWordsDraft by remember { mutableStateOf(settings.customWakeWordsCsv) }
    LaunchedEffect(settings.customWakeWordsCsv) {
        wakeWordsDraft = settings.customWakeWordsCsv
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val content = viewModel.getBackupJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (!raw.isNullOrBlank()) viewModel.restoreFromJson(raw)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        statusBanner?.let { msg ->
            item {
                GlassCard(borderColor = palette.goldAccent) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(msg, color = palette.goldAccent, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.clearStatusBanner() }) {
                            Text("OK", color = palette.primaryNeon)
                        }
                    }
                }
            }
        }

        // 0. CLOUD ACCOUNT & FIRESTORE SYNC
        item {
            GlassCard(borderColor = palette.primaryNeon.copy(alpha = 0.55f)) {
                SectionHeader("Cloud Account & Firestore Sync", "Signed in via Google Credential Manager • Real-time cloud backup")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CloudDone, contentDescription = null, tint = palette.successGreen)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(viewModel.getSignedInDisplayName(), style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                        Text(viewModel.getSignedInEmail(), style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.syncLocalDataToCloud() },
                        enabled = !isSyncingCloud,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primaryNeon),
                        modifier = Modifier.testTag("settings_sync_cloud_btn")
                    ) { Text(if (isSyncingCloud) "Syncing..." else "Sync to Cloud", color = Color.Black) }
                    OutlinedButton(
                        onClick = { viewModel.signOut() },
                        modifier = Modifier.testTag("settings_sign_out_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = palette.errorRed)
                        Spacer(Modifier.width(4.dp))
                        Text("Sign Out", color = palette.errorRed)
                    }
                }
                cloudSyncStatus?.let { msg ->
                    Spacer(Modifier.height(6.dp))
                    Text(msg, style = MaterialTheme.typography.bodyMedium, color = palette.successGreen)
                }
            }
        }

        // 1. AI BRAIN (Firebase AI Logic)
        item {
            GlassCard {
                SectionHeader("AI Brain (Firebase Gemini AI)", "Powered by Firebase AI Logic & App Check — no manual API key needed")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.testGeminiAi() },
                        enabled = !isTestingAi,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primaryNeon),
                        modifier = Modifier.testTag("settings_test_ai_btn")
                    ) { Text(if (isTestingAi) "Testing Gemini..." else "Test Gemini AI", color = Color.Black) }
                }
                aiTestStatus?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = if (it.startsWith("Success")) palette.successGreen else palette.errorRed)
                }

                Spacer(Modifier.height(10.dp))
                Text("Model Selector", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                Text("Flash-Lite uses less quota; Flash is smarter for Urdu/Saraiki.", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                AxtraConstants.AVAILABLE_GEMINI_MODELS.forEach { (id, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.updateGeminiModel(id) }.padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, color = if (settings.geminiModel == id) palette.primaryNeon else palette.textSecondary)
                        if (settings.geminiModel == id) Text("✓", color = palette.primaryNeon)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Temperature: ${"%.2f".format(settings.temperature)}", color = palette.textPrimary)
                Text("Lower (0.1–0.3) gives more accurate JSON phone actions.", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                Slider(
                    value = settings.temperature,
                    onValueChange = { viewModel.updateTemperature(it) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = palette.primaryNeon, activeTrackColor = palette.primaryNeon)
                )

                Text("Max Output Tokens: ${settings.maxTokens}", color = palette.textPrimary)
                Text("Limits AI response length to save free tier tokens.", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                Slider(
                    value = settings.maxTokens.toFloat(),
                    onValueChange = { viewModel.updateMaxTokens(it.toInt()) },
                    valueRange = 256f..4096f,
                    colors = SliderDefaults.colors(thumbColor = palette.primaryNeon, activeTrackColor = palette.primaryNeon)
                )

                Text("Reply Language Mode", color = palette.textPrimary)
                Text("Controls whether Axtra replies in Urdu, English, or auto-detects.", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                LanguageMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.updateLanguageMode(mode) }.padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(mode.titleEn, color = if (settings.languageMode == mode) palette.primaryNeon else palette.textSecondary)
                        if (settings.languageMode == mode) Text("✓", color = palette.primaryNeon)
                    }
                }

                SettingSwitchRow(
                    title = "Long-Term Memory",
                    subtitle = "Save facts you ask Axtra to remember in local Room database",
                    checked = settings.longTermMemoryEnabled,
                    onCheckedChange = { viewModel.updateMemoryEnabled(it) }
                )

                SettingSwitchRow(
                    title = "Advanced Mode: Edit System Prompt",
                    subtitle = "Unlock custom system instructions sent to Gemini",
                    checked = settings.advancedPromptMode,
                    onCheckedChange = { viewModel.updateAdvancedPrompt(it) }
                )
                if (settings.advancedPromptMode) {
                    OutlinedTextField(
                        value = settings.customSystemPrompt,
                        onValueChange = { viewModel.updateCustomPrompt(it) },
                        label = { Text("System Prompt") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 2. VOICE
        item {
            GlassCard {
                SectionHeader("Voice (Urdu & English)", "Speech recognition, Urdu TTS voice, wake word, and barge-in")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.updateSttLanguage("ur-PK") }) {
                        Text("Listen: Urdu (ur-PK)", color = if (settings.sttLanguagePrimary == "ur-PK") palette.primaryNeon else palette.textSecondary)
                    }
                    OutlinedButton(onClick = { viewModel.updateSttLanguage("en-US") }) {
                        Text("Listen: English", color = if (settings.sttLanguagePrimary == "en-US") palette.primaryNeon else palette.textSecondary)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Silence Timeout: ${settings.silenceTimeoutMs} ms", color = palette.textPrimary)
                Text("How long Axtra waits after you pause speaking before stopping mic.", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                Slider(
                    value = settings.silenceTimeoutMs.toFloat(),
                    onValueChange = { viewModel.updateSilenceTimeout(it.toInt()) },
                    valueRange = 800f..3500f,
                    colors = SliderDefaults.colors(thumbColor = palette.primaryNeon, activeTrackColor = palette.primaryNeon)
                )

                Text("Speech Speed: ${"%.1f".format(settings.ttsSpeed)}x", color = palette.textPrimary)
                Text("Adjust voice speed from 0.5x to 1.5x.", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                Slider(
                    value = settings.ttsSpeed,
                    onValueChange = { viewModel.updateTtsSpeed(it) },
                    valueRange = 0.5f..1.5f
                )

                Text("Pitch: ${"%.1f".format(settings.ttsPitch)} • Volume: ${(settings.ttsVolume * 100).toInt()}%", color = palette.textPrimary)
                Slider(value = settings.ttsPitch, onValueChange = { viewModel.updateTtsPitch(it) }, valueRange = 0.6f..1.4f)
                Slider(value = settings.ttsVolume, onValueChange = { viewModel.updateTtsVolume(it) }, valueRange = 0.1f..1f)

                if (voices.isNotEmpty()) {
                    Text("Voice Selector (${voices.size} installed)", color = palette.textPrimary)
                    voices.take(4).forEach { v ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.updateTtsVoice(v.name) }.padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${v.localeDisplay} • ${v.name.takeLast(14)}", style = MaterialTheme.typography.labelMedium, color = if (settings.ttsVoiceName == v.name) palette.primaryNeon else palette.textSecondary)
                            if (settings.ttsVoiceName == v.name) Text("✓", color = palette.primaryNeon)
                        }
                    }
                }

                Button(
                    onClick = { viewModel.testVoice() },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primaryNeon)
                ) { Text("Test Voice", color = Color.Black) }

                Spacer(Modifier.height(6.dp))
                SettingSwitchRow(
                    title = "Hotword Detection ('Hey Astra' / 'Astra')",
                    subtitle = "Status: ${hotwordStatus.labelEn} • Est. ~${WakeWordDetector.estimatedBatteryImpactPercentPerHour(settings.wakeWordSensitivity)}%/hr",
                    checked = settings.wakeWordEnabled,
                    onCheckedChange = { viewModel.updateWakeWord(it) }
                )
                Text(
                    text = if (isDefaultAssistant || isVoiceServiceReady) {
                        "VoiceInteractionService: Active (Default Digital Assistant)"
                    } else {
                        "VoiceInteractionService: Not set as Default Assistant yet"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isDefaultAssistant || isVoiceServiceReady) palette.successGreen else palette.goldAccent
                )
                lastDetectedHotword?.let { phrase ->
                    Text(
                        text = "Last matched hotword: \"$phrase\"",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.primaryNeon
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isDefaultAssistant) {
                        Button(
                            onClick = {
                                val intent = VoiceAssistantHelper.buildSetDefaultAssistantIntent(context)
                                runCatching { assistantRoleLauncher.launch(intent) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.goldAccent),
                            modifier = Modifier.testTag("settings_default_assistant_btn")
                        ) {
                            Text("Set Default Assistant", color = Color.Black)
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.testWakeWord() },
                        modifier = Modifier.testTag("settings_test_hotword_btn")
                    ) {
                        Text("Test Hotword Session", color = palette.primaryNeon)
                    }
                }
                if (settings.wakeWordEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Text("Wake Sensitivity: ${(settings.wakeWordSensitivity * 100).toInt()}%", color = palette.textPrimary)
                    Slider(value = settings.wakeWordSensitivity, onValueChange = { viewModel.updateWakeSensitivity(it) }, valueRange = 0.1f..1f)

                    OutlinedTextField(
                        value = wakeWordsDraft,
                        onValueChange = { wakeWordsDraft = it },
                        label = { Text("Custom Wake Phrases (comma separated)") },
                        placeholder = { Text(AxtraConstants.DEFAULT_WAKE_WORDS_CSV) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("settings_custom_wake_words_input")
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.updateCustomWakeWords(wakeWordsDraft) },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.primaryNeon),
                            modifier = Modifier.testTag("settings_save_wake_words_btn")
                        ) { Text("Save Phrases", color = Color.Black) }
                        OutlinedButton(onClick = { viewModel.resetDefaultWakeWords() }) {
                            Text("Reset Defaults", color = palette.textSecondary)
                        }
                    }
                }
                SettingSwitchRow("Conversation Follow-up (8s)", "Automatically listen for follow-up after Axtra finishes replying", settings.conversationModeEnabled) {
                    viewModel.updateConversationMode(it)
                }
                SettingSwitchRow("Silent Mode (Text Only)", "Mute spoken replies and display text answers only", settings.silentModeEnabled) {
                    viewModel.updateSilentMode(it)
                }
                SettingSwitchRow("Volume-Down Hold Shortcut", "Long-press Volume Down anywhere to talk (requires Accessibility)", settings.volumeDownShortcutEnabled) {
                    viewModel.updateVolumeDownShortcut(it)
                }
                SettingSwitchRow("Call & SMS Voice Announcer", "Speak incoming caller number and SMS aloud", settings.callSmsAnnounceEnabled) {
                    viewModel.updateCallSmsAnnounce(it)
                }
            }
        }

        // 3. ALWAYS ON
        item {
            GlassCard {
                val minsToday = settings.listeningTimeTodayMs / 60_000L
                SectionHeader("Always-On & Battery", "Background microphone service status • Today: $minsToday min active")
                SettingSwitchRow("Always On Mode", "Keep Axtra running in notification bar even when closed", settings.alwaysOnEnabled) {
                    viewModel.updateAlwaysOn(it)
                }
                SettingSwitchRow("Start on Boot", "Automatically launch Always On after phone restart", settings.startOnBoot) {
                    viewModel.updateStartOnBoot(it)
                }
                SettingSwitchRow("Pause Below 10% Battery", "Automatically pause background listening on low battery", settings.pauseOnLowBattery) {
                    viewModel.updatePauseLowBattery(it)
                }
                SettingSwitchRow("Screen-On Only", "Pause wake-word listening whenever the screen is turned off", settings.screenOnOnly) {
                    viewModel.updateScreenOnOnly(it)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenKeepAliveGuide() }.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.BatteryAlert, contentDescription = null, tint = palette.goldAccent)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Keep Axtra Alive Guide", color = palette.textPrimary)
                            Text("Battery exemption & Xiaomi/Samsung/Oppo/Vivo steps", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = palette.textSecondary)
                }
            }
        }

        // 4. CONTROL & BLOCKED APPS
        item {
            GlassCard {
                SectionHeader("Phone Control & Safety", "Accessibility: ${if (isAccConnected) "ON" else "OFF"} • Notifications: ${if (isNotifConnected) "ON" else "OFF"}")
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenPermissionHealth() }.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.HealthAndSafety, contentDescription = null, tint = palette.primaryNeon)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Permission Health Dashboard", color = palette.textPrimary)
                            Text("Inspect all 18 permissions & fix any missing access", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = palette.textSecondary)
                }

                SettingSwitchRow("Screen Assistant", "Allow 'Screen par kya hai?' to read active non-blocked screen", settings.screenAssistantEnabled) {
                    viewModel.updateScreenAssistant(it)
                }

                Spacer(Modifier.height(6.dp))
                Text("Confirmation Level", color = palette.textPrimary)
                Text("Controls which actions ask 'Haan / Nahi' before executing.", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                ConfirmationLevel.entries.forEach { lvl ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.updateConfirmationLevel(lvl) }.padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(lvl.titleEn, color = if (settings.confirmationLevel == lvl) palette.primaryNeon else palette.textSecondary)
                        if (settings.confirmationLevel == lvl) Text("✓", color = palette.primaryNeon)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Blocked Zones (${blockedApps.size} apps protected)", color = palette.goldAccent)
                Text("Screen reading is strictly disabled inside these apps.", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newBlockedPkg,
                        onValueChange = { newBlockedPkg = it },
                        label = { Text("Add package (e.g. com.bank.app)") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Button(onClick = {
                        if (newBlockedPkg.isNotBlank()) {
                            viewModel.addBlockedApp(newBlockedPkg.trim(), newBlockedPkg.substringAfterLast('.'))
                            newBlockedPkg = ""
                        }
                    }) { Text("Add") }
                }
                blockedApps.take(6).forEach { app ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${app.appLabel} (${app.packageName})", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.removeBlockedApp(app) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Unblock", tint = palette.errorRed)
                        }
                    }
                }
            }
        }

        // 5. APPEARANCE & OVERLAY
        item {
            GlassCard {
                SectionHeader("Appearance & Overlay", "Themes, overlay orb style, font scaling, and UI language")
                Text("Theme", color = palette.textPrimary)
                AxtraThemeStyle.entries.forEach { style ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.updateThemeStyle(style) }.padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(style.titleEn, color = if (settings.themeStyle == style) palette.primaryNeon else palette.textSecondary)
                        if (settings.themeStyle == style) Text("✓", color = palette.primaryNeon)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Floating Overlay Style", color = palette.textPrimary)
                Text("Shown over other apps while Always On is active.", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                OverlayAnimationStyle.entries.forEach { style ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.updateOverlayStyle(style) }.padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(style.titleEn, color = if (settings.overlayAnimationStyle == style) palette.primaryNeon else palette.textSecondary)
                        if (settings.overlayAnimationStyle == style) Text("✓", color = palette.primaryNeon)
                    }
                }

                Text("Overlay Glow Intensity: ${(settings.overlayIntensity * 100).toInt()}%", color = palette.textPrimary)
                Slider(value = settings.overlayIntensity, onValueChange = { viewModel.updateOverlayIntensity(it) }, valueRange = 0.2f..1f)

                SettingSwitchRow("Low-End Phone Mode", "Reduces orb particles and animation frame cost", settings.lowEndPhoneMode) {
                    viewModel.updateLowEndMode(it)
                }
                SettingSwitchRow("Hide Overlay in Fullscreen", "Suppresses floating orb during fullscreen games/videos", settings.hideOverlayInFullscreen) {
                    viewModel.updateHideFullscreen(it)
                }

                Text("UI Language & RTL", color = palette.textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UiLanguage.entries.forEach { lang ->
                        OutlinedButton(onClick = { viewModel.updateUiLanguage(lang) }) {
                            Text(lang.title, color = if (settings.uiLanguage == lang) palette.primaryNeon else palette.textSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Font Size Scale: ${"%.2f".format(settings.fontScaleMultiplier)}x", color = palette.textPrimary)
                Slider(value = settings.fontScaleMultiplier, onValueChange = { viewModel.updateFontScale(it) }, valueRange = 0.85f..1.3f)

                Spacer(Modifier.height(6.dp))
                Text("Prayer Calculation Method", color = palette.textPrimary)
                PrayerCalculationMethod.entries.forEach { m ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.updatePrayerMethod(m) }.padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(m.titleEn, style = MaterialTheme.typography.labelMedium, color = if (settings.prayerMethod == m) palette.primaryNeon else palette.textSecondary)
                        if (settings.prayerMethod == m) Text("✓", color = palette.primaryNeon)
                    }
                }
            }
        }

        // 6. SECURITY, BACKUP & ABOUT
        item {
            GlassCard {
                SectionHeader("Security, Backup & About", "App lock PIN/Biometric, log retention, backup restore, and owner info")
                SettingSwitchRow("App Lock (Biometric / PIN)", "Require owner authentication when opening Axtra", settings.appLockEnabled) {
                    viewModel.updateAppLock(it)
                }
                SettingSwitchRow("Sensitive Actions Lock", "Extra lock check before changing safety or backup settings", settings.sensitiveActionsLockEnabled) {
                    viewModel.updateSensitiveLock(it)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = pinDraft,
                        onValueChange = { pinDraft = it.take(8) },
                        label = { Text("Set 4–8 digit PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Button(onClick = {
                        if (pinDraft.length >= 4) {
                            viewModel.saveAppPin(pinDraft)
                            pinDraft = ""
                        }
                    }) { Text("Set PIN") }
                }

                Spacer(Modifier.height(8.dp))
                Text("Action Log Retention", color = palette.textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(7 to "7d", 30 to "30d", 90 to "90d", 0 to "Never").forEach { (days, lbl) ->
                        OutlinedButton(onClick = { viewModel.updateLogRetention(days) }) {
                            Text(lbl, color = if (settings.logRetentionDays == days) palette.primaryNeon else palette.textSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { exportBackupLauncher.launch("axtra_backup.json") },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primaryNeon)
                    ) { Text("Export Backup", color = Color.Black) }
                    OutlinedButton(onClick = { importBackupLauncher.launch(arrayOf("application/json")) }) {
                        Text("Import Backup", color = palette.goldAccent)
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenPrivacyAbout() }.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = palette.goldAccent)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Privacy, Licenses & About", color = palette.textPrimary)
                            Text("Version ${AxtraConstants.APP_VERSION} • ${AxtraConstants.OWNER_LABEL}", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = palette.textSecondary)
                }
                OwnerLabelBadge(fontSizeSp = 13f)
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    val palette = LocalAxtraPalette.current
    Text(title, style = MaterialTheme.typography.titleMedium, color = palette.primaryNeon)
    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = LocalAxtraPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = palette.primaryNeon)
        )
    }
}
