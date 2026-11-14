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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.auth.GoogleAuthHelper
import com.example.auth.GoogleSignInOutcome
import com.example.core.AxtraConstants
import com.example.core.OrbState
import com.example.ui.components.AiOrbCanvas
import com.example.ui.components.GlassCard
import com.example.ui.components.OwnerLabelBadge
import com.example.ui.theme.LocalAxtraPalette
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    authHelper: GoogleAuthHelper,
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAxtraPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val autoOutcome = authHelper.tryAutoSignIn(context)
        if (autoOutcome is GoogleSignInOutcome.Success) {
            onSignedIn()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(palette.backgroundTop, palette.backgroundBottom)))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AiOrbCanvas(
                orbState = if (isLoading) OrbState.THINKING else OrbState.IDLE,
                rmsLevel = 0f,
                lowEndMode = false,
                size = 156.dp,
                onClick = {}
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = AxtraConstants.APP_NAME,
                style = MaterialTheme.typography.headlineMedium,
                color = palette.primaryNeon
            )
            Spacer(Modifier.height(6.dp))
            OwnerLabelBadge(fontSizeSp = 15f)
            Spacer(Modifier.height(20.dp))

            GlassCard {
                Icon(
                    imageVector = Icons.Filled.CloudSync,
                    contentDescription = null,
                    tint = palette.goldAccent,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Connect Your Google Account",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.textPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Sign in with Google to securely sync your Axtra memories, notes, expenses, and routines to Firebase Cloud Firestore and unlock Gemini AI.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "اپنی یادداشتیں، نوٹس اور روٹینز کلاؤڈ میں محفوظ کرنے کے لیے گوگل اکاؤنٹ سے سائن ان کریں۔",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.goldAccent
                )
                Spacer(Modifier.height(18.dp))

                errorMessage?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.errorRed,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )
                }

                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            when (val outcome = authHelper.signInWithGoogle(context)) {
                                is GoogleSignInOutcome.Success -> onSignedIn()
                                is GoogleSignInOutcome.Cancelled -> errorMessage = outcome.message
                                is GoogleSignInOutcome.NoSavedAccounts -> errorMessage = outcome.message
                                is GoogleSignInOutcome.Error -> errorMessage = outcome.message
                            }
                            isLoading = false
                        }
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primaryNeon),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("google_sign_in_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.Black,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Login,
                            contentDescription = null,
                            tint = Color.Black
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = "Sign in with Google",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}
