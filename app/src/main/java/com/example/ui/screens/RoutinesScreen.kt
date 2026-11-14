package com.example.ui.screens

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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.GlassCard
import com.example.ui.theme.LocalAxtraPalette
import com.example.ui.viewmodel.RoutinesViewModel

@Composable
fun RoutinesScreen(
    viewModel: RoutinesViewModel,
    modifier: Modifier = Modifier
) {
    val palette = LocalAxtraPalette.current
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val testStatus by viewModel.testStatus.collectAsStateWithLifecycle()

    var showEditor by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("routines_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Routines & Automation", style = MaterialTheme.typography.titleLarge, color = palette.primaryNeon)
                Text("One voice phrase or trigger runs multiple steps", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
            }
            Button(
                onClick = { showEditor = true },
                colors = ButtonDefaults.buttonColors(containerColor = palette.primaryNeon),
                modifier = Modifier.testTag("create_routine_btn")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.Black)
                Spacer(Modifier.width(4.dp))
                Text("New", color = Color.Black)
            }
        }

        testStatus?.let { status ->
            Spacer(Modifier.height(8.dp))
            GlassCard(borderColor = palette.goldAccent) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(status, color = palette.goldAccent, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.clearTestStatus() }) {
                        Text("OK", color = palette.primaryNeon)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(routines, key = { it.id }) { routine ->
                val steps = remember(routine.stepsJson) { viewModel.decodeSteps(routine.stepsJson) }
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(routine.name, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                            Text("Trigger: ${routine.triggerType} (${routine.triggerValue})", style = MaterialTheme.typography.labelMedium, color = palette.goldAccent)
                        }
                        Switch(
                            checked = routine.isEnabled,
                            onCheckedChange = { viewModel.toggleRoutine(routine) },
                            colors = SwitchDefaults.colors(checkedThumbColor = palette.primaryNeon)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    steps.forEachIndexed { idx, step ->
                        Text("${idx + 1}. $step", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.runRoutine(routine) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Test routine", tint = palette.primaryNeon)
                            Spacer(Modifier.width(4.dp))
                            Text("Test", color = palette.primaryNeon)
                        }
                        if (!routine.isBuiltIn) {
                            OutlinedButton(onClick = { viewModel.deleteRoutine(routine.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete routine", tint = palette.errorRed)
                                Spacer(Modifier.width(4.dp))
                                Text("Delete", color = palette.errorRed)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showEditor) {
        RoutineEditorDialog(
            onDismiss = { showEditor = false },
            onSave = { name, triggerType, triggerVal, steps ->
                viewModel.saveCustomRoutine(0, name, triggerType, triggerVal, steps)
                showEditor = false
            }
        )
    }
}

@Composable
private fun RoutineEditorDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, triggerType: String, triggerVal: String, steps: List<String>) -> Unit
) {
    val palette = LocalAxtraPalette.current
    var name by remember { mutableStateOf("") }
    var triggerType by remember { mutableStateOf("VOICE") }
    var triggerVal by remember { mutableStateOf("") }
    var stepInput by remember { mutableStateOf("") }
    val steps = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceElevated,
        title = { Text("Create Custom Routine", color = palette.primaryNeon) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Routine Name") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("VOICE", "TIME", "CHARGING", "WIFI").forEach { t ->
                        TextButton(onClick = { triggerType = t }) {
                            Text(t, color = if (triggerType == t) palette.primaryNeon else palette.textSecondary)
                        }
                    }
                }
                OutlinedTextField(value = triggerVal, onValueChange = { triggerVal = it }, label = { Text("Trigger phrase / time") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = stepInput,
                        onValueChange = { stepInput = it },
                        label = { Text("Add step command") },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (stepInput.isNotBlank()) {
                            steps.add(stepInput.trim())
                            stepInput = ""
                        }
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add step", tint = palette.primaryNeon)
                    }
                }
                steps.forEachIndexed { idx, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${idx + 1}. $s", color = palette.textPrimary, modifier = Modifier.weight(1f))
                        if (idx > 0) {
                            IconButton(onClick = {
                                val prev = steps[idx - 1]
                                steps[idx - 1] = s
                                steps[idx] = prev
                            }) {
                                Icon(Icons.Filled.ArrowUpward, contentDescription = "Move Up", tint = palette.primaryNeon)
                            }
                        }
                        if (idx < steps.lastIndex) {
                            IconButton(onClick = {
                                val next = steps[idx + 1]
                                steps[idx + 1] = s
                                steps[idx] = next
                            }) {
                                Icon(Icons.Filled.ArrowDownward, contentDescription = "Move Down", tint = palette.primaryNeon)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, triggerType, triggerVal, steps.toList()) },
                enabled = name.isNotBlank() && steps.isNotEmpty()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
