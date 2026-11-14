package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.actions.PendingConfirmation
import com.example.actions.StepStatus
import com.example.actions.TaskStepUi
import com.example.core.AxtraConstants
import com.example.ui.theme.LocalAxtraPalette

@Composable
fun OwnerLabelBadge(
    modifier: Modifier = Modifier,
    fontSizeSp: Float = 15f
) {
    val palette = LocalAxtraPalette.current
    val transition = rememberInfiniteTransition(label = "owner_shimmer")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_x"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            palette.goldAccent,
            Color.White,
            palette.primaryNeon,
            palette.goldAccent
        ),
        start = Offset(shift - 200f, 0f),
        end = Offset(shift + 120f, 80f)
    )

    Surface(
        shape = RoundedCornerShape(50),
        color = palette.surfaceElevated.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, palette.goldAccent.copy(alpha = 0.65f)),
        modifier = modifier
    ) {
        Text(
            text = AxtraConstants.OWNER_LABEL,
            style = TextStyle(
                brush = shimmerBrush,
                fontWeight = FontWeight.Bold,
                fontSize = fontSizeSp.sp,
                letterSpacing = 1.5.sp
            ),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalAxtraPalette.current
    val shape = RoundedCornerShape(24.dp)
    val effectiveBorder = borderColor ?: palette.borderGlow

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        palette.surfaceGlass,
                        palette.surfaceElevated.copy(alpha = 0.72f)
                    )
                )
            )
            .border(1.dp, effectiveBorder, shape)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun TaskProgressCard(
    steps: List<TaskStepUi>,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAxtraPalette.current
    GlassCard(modifier = modifier, borderColor = palette.primaryNeon.copy(alpha = 0.7f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Executing Task Plan",
                style = MaterialTheme.typography.titleMedium,
                color = palette.primaryNeon
            )
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.testTag("cancel_task_button"),
                border = BorderStroke(1.dp, palette.errorRed)
            ) {
                Icon(Icons.Filled.StopCircle, contentDescription = "Cancel", tint = palette.errorRed, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Stop", color = palette.errorRed)
            }
        }
        Spacer(Modifier.height(10.dp))
        steps.forEach { step ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                when (step.status) {
                    StepStatus.DONE -> Icon(Icons.Filled.CheckCircle, contentDescription = "Done", tint = palette.successGreen, modifier = Modifier.size(18.dp))
                    StepStatus.FAILED -> Icon(Icons.Filled.Error, contentDescription = "Failed", tint = palette.errorRed, modifier = Modifier.size(18.dp))
                    StepStatus.RUNNING -> CircularProgressIndicator(strokeWidth = 2.dp, color = palette.primaryNeon, modifier = Modifier.size(18.dp))
                    StepStatus.PENDING -> Icon(Icons.Filled.RadioButtonUnchecked, contentDescription = "Pending", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = step.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (step.status == StepStatus.PENDING) palette.textSecondary else palette.textPrimary
                )
            }
        }
    }
}

@Composable
fun ConfirmationOverlayCard(
    confirmation: PendingConfirmation,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAxtraPalette.current
    GlassCard(modifier = modifier, borderColor = palette.goldAccent) {
        Text(
            text = "${confirmation.title} (${confirmation.secondsRemaining}s)",
            style = MaterialTheme.typography.titleMedium,
            color = palette.goldAccent
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = confirmation.description,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Say 'Haan / ہاں' to confirm or 'Nahi / نہیں' to cancel",
            style = MaterialTheme.typography.labelMedium,
            color = palette.textSecondary
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .testTag("confirm_cancel_btn"),
                border = BorderStroke(1.dp, palette.errorRed)
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = palette.errorRed, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cancel (Nahi)", color = palette.errorRed)
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .weight(1f)
                    .testTag("confirm_yes_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = palette.successGreen)
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Confirm (Haan)", color = Color.Black)
            }
        }
    }
}
