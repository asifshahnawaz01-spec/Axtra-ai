package com.example.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.OrbState
import com.example.ui.theme.LocalAxtraPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AiOrbCanvas(
    orbState: OrbState,
    rmsLevel: Float,
    lowEndMode: Boolean,
    size: Dp = 216.dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAxtraPalette.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val reduceMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "orb_transition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (lowEndMode) 3600 else 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_phase"
    )
    val rotationDeg by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (orbState == OrbState.THINKING) 1800 else 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation"
    )

    val animatedRms by animateFloatAsState(
        targetValue = if (orbState == OrbState.LISTENING) rmsLevel.coerceIn(0f, 1f) else 0f,
        animationSpec = spring(stiffness = 380f),
        label = "rms_spring"
    )

    val activePhase = if (reduceMotion) 0f else phase
    val activeRot = if (reduceMotion) 0f else rotationDeg

    val primaryColor = when (orbState) {
        OrbState.ERROR -> palette.errorRed
        OrbState.SPEAKING -> palette.goldAccent
        else -> palette.primaryNeon
    }
    val secondaryColor = when (orbState) {
        OrbState.ERROR -> Color(0xFFFF8A80)
        OrbState.THINKING -> palette.secondaryAccent
        else -> palette.secondaryAccent
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .testTag("home_ai_orb")
            .semantics { contentDescription = "Axtra AI Voice Orb, status ${orbState.name}" }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxR = minOf(this.size.width, this.size.height) / 2f
            val breath = (sin(activePhase.toDouble()).toFloat() + 1f) * 0.5f

            // Outer aura radial glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.28f + 0.16f * animatedRms + 0.08f * breath),
                        secondaryColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxR
                ),
                radius = maxR,
                center = center
            )

            // Listening pulsing concentric rings
            if (orbState == OrbState.LISTENING || orbState == OrbState.SPEAKING) {
                val ringCount = if (lowEndMode) 2 else 3
                for (i in 1..ringCount) {
                    val ringRadius = maxR * (0.58f + i * 0.11f + animatedRms * 0.14f + 0.03f * sin((activePhase + i).toDouble()).toFloat())
                    drawCircle(
                        color = primaryColor.copy(alpha = (0.45f / i).coerceIn(0.1f, 0.5f)),
                        radius = ringRadius,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }

            // Thinking rotating swirl arc + orbiting particles
            rotate(degrees = activeRot, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(primaryColor, secondaryColor, palette.goldAccent, primaryColor),
                        center = center
                    ),
                    startAngle = 0f,
                    sweepAngle = if (orbState == OrbState.THINKING) 290f else 220f,
                    useCenter = false,
                    topLeft = Offset(center.x - maxR * 0.66f, center.y - maxR * 0.66f),
                    size = androidx.compose.ui.geometry.Size(maxR * 1.32f, maxR * 1.32f),
                    style = Stroke(width = if (orbState == OrbState.THINKING) 5.dp.toPx() else 3.dp.toPx(), cap = StrokeCap.Round)
                )

                val particles = if (lowEndMode) 3 else 6
                for (p in 0 until particles) {
                    val angleRad = (2.0 * PI / particles) * p
                    val orbitR = maxR * (if (orbState == OrbState.THINKING) 0.74f else 0.68f)
                    val px = center.x + (cos(angleRad) * orbitR).toFloat()
                    val py = center.y + (sin(angleRad) * orbitR).toFloat()
                    drawCircle(
                        color = if (p % 2 == 0) primaryColor else palette.goldAccent,
                        radius = if (orbState == OrbState.THINKING) 4.dp.toPx() else 2.5.dp.toPx(),
                        center = Offset(px, py)
                    )
                }
            }

            // Core glass sphere
            val coreR = maxR * (0.48f + 0.03f * breath + 0.05f * animatedRms)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.85f),
                        secondaryColor.copy(alpha = 0.75f),
                        palette.backgroundTop
                    ),
                    center = Offset(center.x - coreR * 0.2f, center.y - coreR * 0.2f),
                    radius = coreR * 1.2f
                ),
                radius = coreR,
                center = center
            )

            // Speaking waveform ripple bars inside core
            if (orbState == OrbState.SPEAKING) {
                for (b in -3..3) {
                    val barH = coreR * (0.18f + 0.36f * ((sin((activePhase * 2f + b * 0.9f).toDouble()).toFloat() + 1f) * 0.5f))
                    val x = center.x + b * (coreR * 0.16f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.9f),
                        start = Offset(x, center.y - barH),
                        end = Offset(x, center.y + barH),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        if (orbState != OrbState.SPEAKING) {
            Icon(
                imageVector = when (orbState) {
                    OrbState.ERROR -> Icons.Filled.WarningAmber
                    OrbState.THINKING -> Icons.Filled.GraphicEq
                    else -> Icons.Filled.Mic
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(38.dp)
            )
        }
    }
}
