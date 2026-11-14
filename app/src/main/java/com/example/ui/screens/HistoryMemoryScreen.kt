package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.GlassCard
import com.example.ui.theme.LocalAxtraPalette
import com.example.ui.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryMemoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier
) {
    val palette = LocalAxtraPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()

    val dateFormatter = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val content = viewModel.exportLogsJson()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray())
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("history_memory_screen")
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = palette.surfaceGlass,
            contentColor = palette.primaryNeon
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Action Log") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Long-Term Memory") })
        }

        Spacer(Modifier.height(10.dp))

        if (selectedTab == 0) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search command, intent, or result...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("history_search_input")
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch("axtra_action_logs.json") }) {
                    Icon(Icons.Filled.Download, contentDescription = null, tint = palette.primaryNeon)
                    Spacer(Modifier.width(6.dp))
                    Text("Export JSON", color = palette.primaryNeon)
                }
                OutlinedButton(onClick = { viewModel.clearLogs() }) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = palette.errorRed)
                    Spacer(Modifier.width(6.dp))
                    Text("Clear All", color = palette.errorRed)
                }
            }
            Spacer(Modifier.height(10.dp))

            if (logs.isEmpty()) {
                GlassCard {
                    Text("No action history recorded yet.", color = palette.textSecondary)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(logs, key = { it.id }) { log ->
                        GlassCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(log.intent, style = MaterialTheme.typography.labelMedium, color = palette.goldAccent)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(dateFormatter.format(Date(log.timestamp)), style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        imageVector = if (log.success) Icons.Filled.CheckCircle else Icons.Filled.Error,
                                        contentDescription = null,
                                        tint = if (log.success) palette.successGreen else palette.errorRed
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Command: ${log.commandText}", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                            Text("Result: ${log.resultText}", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                            if (log.stepsSummary.isNotBlank()) {
                                Text("Steps: ${log.stepsSummary}", style = MaterialTheme.typography.labelMedium, color = palette.primaryNeon)
                            }
                        }
                    }
                }
            }
        } else {
            var newKey by remember { mutableStateOf("") }
            var newVal by remember { mutableStateOf("") }
            GlassCard {
                Text("Add Long-Term Memory Fact", style = MaterialTheme.typography.titleMedium, color = palette.primaryNeon)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = newKey, onValueChange = { newKey = it }, label = { Text("Label (e.g. Car Number)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = newVal, onValueChange = { newVal = it }, label = { Text("Value (e.g. LEA-4521)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.addMemory(newKey, newVal)
                        newKey = ""
                        newVal = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primaryNeon)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.Black)
                    Spacer(Modifier.width(6.dp))
                    Text("Save Memory", color = Color.Black)
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(memories, key = { it.id }) { mem ->
                    GlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mem.memoryKey, style = MaterialTheme.typography.titleMedium, color = palette.goldAccent)
                                Text(mem.memoryValue, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
                            }
                            IconButton(onClick = { viewModel.deleteMemory(mem.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete Memory", tint = palette.errorRed)
                            }
                        }
                    }
                }
            }
        }
    }
}
