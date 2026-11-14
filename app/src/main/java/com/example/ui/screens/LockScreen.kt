package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.core.AxtraConstants
import com.example.ui.components.GlassCard
import com.example.ui.components.OwnerLabelBadge
import com.example.ui.theme.LocalAxtraPalette
import com.example.utils.BiometricLockHelper

@Composable
fun LockScreen(
    hasPinSet: Boolean,
    verifyPin: (String) -> Boolean,
    onUnlocked: () -> Unit
) {
    val palette = LocalAxtraPalette.current
    val context = LocalContext.current
    var pinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(palette.backgroundTop, palette.backgroundBottom)))
            .systemBarsPadding()
            .padding(24.dp)
            .testTag("lock_screen"),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(borderColor = palette.goldAccent) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = palette.goldAccent, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(10.dp))
                Text(AxtraConstants.APP_NAME, style = MaterialTheme.typography.titleLarge, color = palette.primaryNeon)
                Spacer(Modifier.height(8.dp))
                OwnerLabelBadge(fontSizeSp = 15f)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Owner authentication required to access Axtra",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
                Spacer(Modifier.height(16.dp))

                if (hasPinSet) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it.take(8) },
                        label = { Text("Enter Security PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("lock_pin_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (verifyPin(pinInput)) onUnlocked()
                            else errorMsg = "غلط PIN، دوبارہ کوشش کریں۔"
                        },
                        modifier = Modifier.fillMaxWidth().testTag("lock_pin_unlock_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primaryNeon)
                    ) {
                        Text("Unlock with PIN", color = Color.Black)
                    }
                    Spacer(Modifier.height(8.dp))
                }

                OutlinedButton(
                    onClick = {
                        val activity = context as? FragmentActivity
                        if (activity != null && BiometricLockHelper.canAuthenticate(activity)) {
                            BiometricLockHelper.promptUnlock(
                                activity = activity,
                                onSuccess = onUnlocked,
                                onError = { errorMsg = it }
                            )
                        } else if (!hasPinSet) {
                            onUnlocked()
                        } else {
                            errorMsg = "Biometric unavailable; use PIN."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("lock_biometric_btn")
                ) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = palette.primaryNeon)
                    Spacer(Modifier.size(8.dp))
                    Text("Use Biometric / Device Lock", color = palette.primaryNeon)
                }

                errorMsg?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = palette.errorRed, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
