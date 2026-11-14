package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.components.GlassCard
import com.example.ui.components.OwnerLabelBadge
import com.example.ui.theme.LocalAxtraPalette
import com.example.utils.PermissionUtils

@Composable
fun OnboardingScreen(
    permissionUtils: PermissionUtils,
    onFinish: () -> Unit
) {
    val palette = LocalAxtraPalette.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val items = remember { permissionUtils.getAllPermissionItems() }

    var currentIndex by remember { mutableIntStateOf(0) }
    var refreshTick by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshTick++
    }

    val currentItem = items[currentIndex]
    val isGranted = remember(currentIndex, refreshTick) {
        permissionUtils.isGranted(currentItem.id)
    }
    val grantedCount = remember(refreshTick) {
        items.count { permissionUtils.isGranted(it.id) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(palette.backgroundTop, palette.backgroundBottom)))
            .systemBarsPadding()
            .padding(20.dp)
            .testTag("onboarding_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Setup Permissions",
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.primaryNeon
                    )
                    Text(
                        text = "${currentIndex + 1} of ${items.size} • $grantedCount granted",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textSecondary
                    )
                }
                OwnerLabelBadge(fontSizeSp = 12f)
            }

            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { (currentIndex + 1).toFloat() / items.size.toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = palette.primaryNeon,
                trackColor = palette.surfaceElevated
            )

            Spacer(Modifier.height(22.dp))

            GlassCard(
                modifier = Modifier.weight(1f),
                borderColor = if (isGranted) palette.successGreen else palette.borderGlow
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isGranted) Icons.Filled.CheckCircle else Icons.Filled.Security,
                        contentDescription = currentItem.titleEn,
                        tint = if (isGranted) palette.successGreen else palette.primaryNeon,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = currentItem.titleEn,
                            style = MaterialTheme.typography.titleLarge,
                            color = palette.textPrimary
                        )
                        Text(
                            text = currentItem.titleUr,
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.goldAccent
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Why it is needed:",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.primaryNeon
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${currentItem.whyNeededEn}\n${currentItem.whyNeededUr}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.textPrimary
                )

                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = palette.successGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "What Axtra will NOT do:",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.successGreen
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${currentItem.willNotDoEn}\n${currentItem.willNotDoUr}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )

                Spacer(Modifier.weight(1f))

                if (isGranted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = "Granted", tint = palette.successGreen)
                        Spacer(Modifier.width(8.dp))
                        Text("Granted / اجازت مل چکی ہے", color = palette.successGreen, style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    Button(
                        onClick = {
                            if (currentItem.isSpecialAccess) {
                                runCatching { context.startActivity(permissionUtils.buildSpecialAccessIntent(currentItem.id)) }
                            } else if (currentItem.runtimePermissions.isNotEmpty()) {
                                permLauncher.launch(currentItem.runtimePermissions.toTypedArray())
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("onboarding_allow_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primaryNeon)
                    ) {
                        Text("Allow / اجازت دیں", color = Color.Black)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (currentIndex > 0) currentIndex--
                        else onFinish()
                    },
                    modifier = Modifier.testTag("onboarding_back_skip_btn")
                ) {
                    Text(if (currentIndex > 0) "Previous" else "Skip Wizard", color = palette.textSecondary)
                }

                TextButton(
                    onClick = {
                        if (currentIndex < items.lastIndex) currentIndex++
                        else onFinish()
                    },
                    modifier = Modifier.testTag("onboarding_next_btn")
                ) {
                    Text(
                        text = if (currentIndex < items.lastIndex) "Skip / Next →" else "Finish & Start Axtra",
                        color = palette.goldAccent
                    )
                }
            }
        }
    }
}
