package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.GlassCard
import com.example.ui.theme.LocalAxtraPalette
import com.example.ui.viewmodel.ToolsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommandsToolsScreen(
    viewModel: ToolsViewModel,
    onRunVoiceCommand: (String) -> Unit,
    initialTab: Int = 0,
    modifier: Modifier = Modifier
) {
    val palette = LocalAxtraPalette.current
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var activeTool by remember { mutableStateOf("BRIEFING") }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val briefingText by viewModel.briefingResult.collectAsStateWithLifecycle()
    val prayerTimes by viewModel.prayerTimes.collectAsStateWithLifecycle()
    val translationText by viewModel.translationResult.collectAsStateWithLifecycle()
    val studyText by viewModel.studyResult.collectAsStateWithLifecycle()
    val ocrText by viewModel.ocrResult.collectAsStateWithLifecycle()
    val qrText by viewModel.qrResult.collectAsStateWithLifecycle()
    val notifSummary by viewModel.notificationSummary.collectAsStateWithLifecycle()
    val smartReplies by viewModel.smartReplies.collectAsStateWithLifecycle()
    val activeNotifs by viewModel.activeNotifications.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val monthlyTotal by viewModel.monthlyExpenseTotal.collectAsStateWithLifecycle()
    val todos by viewModel.todos.collectAsStateWithLifecycle()
    val phoneHealth by viewModel.phoneHealth.collectAsStateWithLifecycle()
    val screenTime by viewModel.screenTime.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshPrayerAndHealth()
    }

    val ocrPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.runOcrOnImageUri(it) }
    }

    val qrPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.scanQrFromImageUri(it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("commands_tools_screen")
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = palette.surfaceGlass,
            contentColor = palette.primaryNeon
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Commands & Guide") },
                modifier = Modifier.testTag("tab_commands")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Smart Tools") },
                modifier = Modifier.testTag("tab_tools")
            )
        }

        Spacer(Modifier.height(10.dp))

        if (selectedTab == 0) {
            val commandCategories = remember {
                listOf(
                    "Apps & Navigation" to listOf(
                        "YouTube kholo" to "Open YouTube",
                        "WhatsApp kholo" to "Open WhatsApp",
                        "Back jao" to "Go Back",
                        "Home par jao" to "Go Home"
                    ),
                    "Calls, SMS & WhatsApp" to listOf(
                        "Ali ko call karo" to "Call contact Ali",
                        "Ahmed ko WhatsApp par likho ke main late hun" to "Multi-step WhatsApp message",
                        "Kisi ka message aya?" to "Summarize notifications"
                    ),
                    "System & Media" to listOf(
                        "Torch on karo" to "Turn flashlight on",
                        "Volume up" to "Increase volume",
                        "WiFi settings kholo" to "Open Wi-Fi panel",
                        "Subah 6 baje ka alarm laga do" to "Set 6:00 AM alarm",
                        "5 minute ka timer lagao" to "Set 5 min timer"
                    ),
                    "Memory, Notes & Expenses" to listOf(
                        "Yaad rakho meri gaari ka number LEA-4521" to "Save to long-term memory",
                        "500 rupay khane par kharch hue" to "Log PKR 500 expense",
                        "Screen par kya hai?" to "Read & explain active screen"
                    )
                )
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(commandCategories) { (category, examples) ->
                    GlassCard {
                        Text(category, style = MaterialTheme.typography.titleMedium, color = palette.primaryNeon)
                        Spacer(Modifier.height(8.dp))
                        examples.forEach { (phrase, desc) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onRunVoiceCommand(phrase) }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("\"$phrase\"", style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
                                    Text(desc, style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                                }
                                Text("Try →", color = palette.goldAccent, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        } else {
            val toolTabs = remember {
                listOf(
                    "BRIEFING" to ("Briefing" to Icons.Filled.WbSunny),
                    "PRAYER" to ("Prayers" to Icons.Filled.Mosque),
                    "TRANSLATE" to ("Translate" to Icons.Filled.Translate),
                    "NOTES" to ("Notes" to Icons.Filled.NoteAdd),
                    "NOTIFS" to ("Notifs" to Icons.Filled.Notifications),
                    "OCR" to ("Doc OCR" to Icons.Filled.DocumentScanner),
                    "QR" to ("QR Scan" to Icons.Filled.QrCodeScanner),
                    "STUDY" to ("Study" to Icons.Filled.School),
                    "EXPENSE" to ("Expenses" to Icons.Filled.AttachMoney),
                    "TODO" to ("To-Do" to Icons.Filled.Checklist),
                    "HEALTH" to ("Health" to Icons.Filled.HealthAndSafety),
                    "FOCUS" to ("Focus" to Icons.Filled.Timer),
                    "FIND" to ("Find Phone" to Icons.Filled.PhoneAndroid)
                )
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        toolTabs.forEach { (key, labelIcon) ->
                            val selected = activeTool == key
                            AssistChip(
                                onClick = { activeTool = key },
                                label = { Text(labelIcon.first, color = if (selected) Color.Black else palette.textPrimary) },
                                leadingIcon = {
                                    Icon(
                                        labelIcon.second,
                                        contentDescription = null,
                                        tint = if (selected) Color.Black else palette.primaryNeon,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (selected) palette.primaryNeon else palette.surfaceGlass
                                ),
                                border = BorderStroke(1.dp, palette.borderGlow)
                            )
                        }
                    }
                }

                if (isBusy) {
                    item {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = palette.primaryNeon
                        )
                    }
                }

                item {
                    when (activeTool) {
                        "BRIEFING" -> GlassCard {
                            Text("Daily Briefing (Weather + Prayers + Calendar)", style = MaterialTheme.typography.titleMedium, color = palette.primaryNeon)
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.generateDailyBriefing(speakAloud = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.primaryNeon)
                            ) {
                                Text("Generate & Speak Briefing", color = Color.Black)
                            }
                            if (briefingText.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(briefingText, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
                            }
                        }

                        "PRAYER" -> GlassCard {
                            Text("Prayer Times (${settings.prayerMethod.titleEn})", style = MaterialTheme.typography.titleMedium, color = palette.primaryNeon)
                            Spacer(Modifier.height(8.dp))
                            listOf(
                                "Fajr / فجر" to prayerTimes.fajr,
                                "Sunrise / طلوع" to prayerTimes.sunrise,
                                "Dhuhr / ظہر" to prayerTimes.dhuhr,
                                "Asr / عصر" to prayerTimes.asr,
                                "Maghrib / مغرب" to prayerTimes.maghrib,
                                "Isha / عشاء" to prayerTimes.isha
                            ).forEach { (name, time) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(name, color = palette.textPrimary)
                                    Text(time, color = palette.goldAccent)
                                }
                            }
                        }

                        "TRANSLATE" -> {
                            var input by remember { mutableStateOf("") }
                            var targetLang by remember { mutableStateOf("Urdu") }
                            GlassCard {
                                Text("Smart Translator (Urdu / Saraiki / English / Arabic)", style = MaterialTheme.typography.titleMedium, color = palette.primaryNeon)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = input,
                                    onValueChange = { input = it },
                                    label = { Text("Enter text to translate") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("Urdu", "English", "Arabic").forEach { lang ->
                                        OutlinedButton(onClick = { targetLang = lang }) {
                                            Text(lang, color = if (targetLang == lang) palette.primaryNeon else palette.textSecondary)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.translateText(input, targetLang) },
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.primaryNeon)
                                ) {
                                    Text("Translate & Speak", color = Color.Black)
                                }
                                if (translationText.isNotBlank()) {
                                    Spacer(Modifier.height(10.dp))
                                    Text(translationText, color = palette.textPrimary)
                                }
                            }
                        }

                        "NOTES" -> {
                            var noteTitle by remember { mutableStateOf("") }
                            var noteBody by remember { mutableStateOf("") }
                            GlassCard {
                                Text("Dictation & Quick Notes", style = MaterialTheme.typography.titleMedium, color = palette.primaryNeon)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(value = noteTitle, onValueChange = { noteTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(value = noteBody, onValueChange = { noteBody = it }, label = { Text("Note content") }, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.saveNote(noteTitle, noteBody)
                                        noteTitle = ""
                                        noteBody = ""
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.primaryNeon)
                                ) {
                                    Text("Save Note", color = Color.Black)
                                }
                                Spacer(Modifier.height(10.dp))
                                notes.forEach { n ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(n.title, color = palette.goldAccent, style = MaterialTheme.typography.titleMedium)
                                            Text(n.content, color = palette.textPrimary, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        IconButton(onClick = { viewModel.deleteNote(n.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete Note", tint = palette.errorRed)
                                        }
                                    }
                                }
                            }
                        }

                        "NOTIFS" -> GlassCard {
                            Text("Notification Summarizer & Smart Replies", style = MaterialTheme.typography.titleMedium, color = palette.primaryNeon)
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.summarizeNotifications() },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.primaryNeon)
                            ) {
                                Text("Summarize Unread (${activeNotifs.size})", color = Color.Black)
                            }
                            if (notifSummary.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(notifSummary, color = palette.textPrimary)
                            }
                            activeNotifs.take(4).forEach { n ->
                                Spacer(Modifier.height(8.dp))
                                Text("${n.appName}: ${n.title} — ${n.text}", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                                if (n.replyAction != null) {
                                    TextButton(onClick = { viewModel.generateSmartReplies(n) }) {
                                        Text("Suggest 3 Smart Replies", color = palette.goldAccent)
                                    }
                                    smartReplies.forEach { reply ->
                                        OutlinedButton(
                                            onClick = { viewModel.sendQuickReply(n, reply) },
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                        ) {
                                            Text("Reply: $reply", color = palette.primaryNeon)
                                        }
                                    }
                                }
                            }
                        }

                        "OCR" -> GlassCard {
                            Text("Photo & Document Reader (ML Kit OCR)", style = MaterialTheme.typography.titleMedium, color = palette.primaryNeon)
                            Spacer(Modifier.height(6.dp))
                            Text("Select any document photo using the zero-permission Photo Picker.", color = palette.textSecondary, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    ocrPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.primaryNeon)
                            ) {
                                Text("Pick Photo & Read Text", color = Color.Black)
                            }
                            if (ocrText.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(ocrText, color = palette.textPrimary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        "QR" -> GlassCard {
                            Text("QR & Barcode Scanner (ML Kit)", style = MaterialTheme.typography.titleMedium, color = palette.primaryNeon)
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    qrPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.primaryNeon)
                            ) {
                                Text("Scan QR / Barcode Image", color = Color.Black)
                            }
                            if (qrText.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text("Result: $qrText", color = palette.goldAccent)
                                if (qrText.startsWith("http")) {
                                    TextButton(onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(qrText)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        }
                                    }) {
                                        Text("Open URL in Browser", color = palette.primaryNeon)
                                    }
                                }
                            }
                        }

                        "STUDY" -> {
                            var topic by remember { mutableStateOf("") }
                            GlassCard {
                                Text("Study Helper & Quiz Generator", style = MaterialTheme.typography.titleMedium, color = palette.primaryNeon)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(value = topic, onValueChange = { topic = it }, label = { Text("Enter topic or question") }, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { viewModel.askStudyHelper(topic, makeQuiz = false) },
                                        colors = ButtonDefaults.buttonColors(containerColor = palette.primaryNeon)
                                    ) { Text("Explain / Solve", color = Color.Black) }
                                    OutlinedButton(onClick = { viewModel.askStudyHelper(topic, makeQuiz = true) }) {
                                        Text("Make Quiz", color = palette.goldAccent)
                                    }
                                }
                                if (studyText.isNotBlank()) {
                                    Spacer(Modifier.height(10.dp))
                                    Text(studyText, color = palette.textPrimary)
                                }
                            }
                        }

                        "EXPENSE" -> {
                            var amount by remember { mutableStateOf("") }
                            var category by remember { mutableStateOf("Food") }
                            var desc by remember { mutableStateOf("") }
                            GlassCard {
                                Text("Expense Tracker • This Month: PKR ${monthlyTotal.toInt()}", style = MaterialTheme.typography.titleMedium, color = palette.goldAccent)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = amount,
                                    onValueChange = { amount = it },
                                    label = { Text("Amount (PKR)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        amount.toDoubleOrNull()?.let { viewModel.addExpense(it, category, desc) }
                                        amount = ""
                                        desc = ""
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.primaryNeon)
                                ) { Text("Add Expense", color = Color.Black) }
                                Spacer(Modifier.height(8.dp))
                                expenses.take(10).forEach { e ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${e.amount.toInt()} PKR • ${e.category} (${e.description})", color = palette.textPrimary, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { viewModel.deleteExpense(e.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = palette.errorRed)
                                        }
                                    }
                                }
                            }
                        }

                        "TODO" -> {
                            var itemText by remember { mutableStateOf("") }
                            var listType by remember { mutableStateOf("TODO") }
                            GlassCard {
                                Text("Shopping & To-Do Lists", style = MaterialTheme.typography.titleMedium, color = palette.primaryNeon)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { listType = "TODO" }) {
                                        Text("To-Do", color = if (listType == "TODO") palette.primaryNeon else palette.textSecondary)
                                    }
                                    OutlinedButton(onClick = { listType = "SHOPPING" }) {
                                        Text("Shopping", color = if (listType == "SHOPPING") palette.primaryNeon else palette.textSecondary)
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(value = itemText, onValueChange = { itemText = it }, label = { Text("Add item") }, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.addTodo(itemText, listType)
                                        itemText = ""
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.primaryNeon)
                                ) { Text("Add to $listType", color = Color.Black) }
                                Spacer(Modifier.height(8.dp))
                                todos.forEach { t ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(checked = t.isDone, onCheckedChange = { viewModel.toggleTodo(t) })
                                        Text("[${t.listType}] ${t.title}", color = if (t.isDone) palette.textSecondary else palette.textPrimary, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { viewModel.deleteTodo(t.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = palette.errorRed)
                                        }
                                    }
                                }
                            }
                        }

                        "HEALTH" -> GlassCard {
                            Text("Phone Health & Screen Time", style = MaterialTheme.typography.titleMedium, color = palette.primaryNeon)
                            Spacer(Modifier.height(8.dp))
                            Text("Battery: ${phoneHealth.batteryPercent}% (${if (phoneHealth.isCharging) "Charging" else "Discharging"}) • Temp: ${phoneHealth.batteryTempCelsius}°C", color = palette.textPrimary)
                            Text("Storage: ${"%.1f".format(phoneHealth.freeStorageGb)} GB free / ${"%.1f".format(phoneHealth.totalStorageGb)} GB", color = palette.textPrimary)
                            Text("RAM: ${"%.1f".format(phoneHealth.availableRamGb)} GB free / ${"%.1f".format(phoneHealth.totalRamGb)} GB", color = palette.textPrimary)
                            Spacer(Modifier.height(6.dp))
                            phoneHealth.tips.forEach { tip ->
                                Text("• $tip", color = palette.goldAccent, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (screenTime.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                Text("Today's Top Screen Time:", color = palette.primaryNeon, style = MaterialTheme.typography.titleMedium)
                                screenTime.take(5).forEach { st ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(st.appLabel, color = palette.textPrimary)
                                        Text("${st.minutesToday} min", color = palette.textSecondary)
                                    }
                                }
                            }
                        }

                        "FOCUS" -> {
                            val active = settings.focusModeActiveUntilMs > System.currentTimeMillis()
                            GlassCard {
                                Text("Focus Mode (Blocks distracting apps via Accessibility)", style = MaterialTheme.typography.titleMedium, color = palette.primaryNeon)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = if (active) "Focus Mode is ACTIVE" else "Focus Mode is Inactive",
                                    color = if (active) palette.successGreen else palette.textSecondary
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { viewModel.startFocusMode(25) },
                                        colors = ButtonDefaults.buttonColors(containerColor = palette.primaryNeon)
                                    ) { Text("Start 25 min", color = Color.Black) }
                                    OutlinedButton(onClick = { viewModel.stopFocusMode() }) {
                                        Text("Stop Focus", color = palette.errorRed)
                                    }
                                }
                            }
                        }

                        "FIND" -> GlassCard {
                            Text("Find My Phone (Ringtone + Blinking Flashlight)", style = MaterialTheme.typography.titleMedium, color = palette.primaryNeon)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.triggerFindMyPhone() },
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.goldAccent)
                                ) { Text("Ring & Blink Now", color = Color.Black) }
                                OutlinedButton(onClick = { viewModel.stopFindMyPhone() }) {
                                    Text("Stop", color = palette.errorRed)
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
