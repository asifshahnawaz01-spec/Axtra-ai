package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.AxtraConstants
import com.example.ui.components.OwnerLabelBadge
import com.example.ui.theme.LocalAxtraPalette
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onFinished: () -> Unit
) {
    val palette = LocalAxtraPalette.current
    val drawProgress = remember { Animatable(0f) }
    var showOwner by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        drawProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
        )
        showOwner = true
        delay(1000L)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(palette.backgroundTop, palette.backgroundBottom)
                )
            )
            .systemBarsPadding()
            .clickable { onFinished() }
            .testTag("splash_screen")
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Canvas(modifier = Modifier.size(160.dp)) {
                val p = drawProgress.value
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = minOf(cx, cy) - 10.dp.toPx()

                // Outer ring self-drawing
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(palette.primaryNeon, palette.secondaryAccent, palette.goldAccent, palette.primaryNeon)
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * p,
                    useCenter = false,
                    topLeft = Offset(cx - r, cy - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Stylized 'A' geometric core
                if (p > 0.2f) {
                    val lineP = ((p - 0.2f) / 0.8f).coerceIn(0f, 1f)
                    val top = Offset(cx, cy - r * 0.52f)
                    val left = Offset(cx - r * 0.42f, cy + r * 0.42f)
                    val right = Offset(cx + r * 0.42f, cy + r * 0.42f)

                    drawLine(
                        color = palette.primaryNeon,
                        start = top,
                        end = Offset(
                            top.x + (left.x - top.x) * lineP,
                            top.y + (left.y - top.y) * lineP
                        ),
                        strokeWidth = 5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = palette.secondaryAccent,
                        start = top,
                        end = Offset(
                            top.x + (right.x - top.x) * lineP,
                            top.y + (right.y - top.y) * lineP
                        ),
                        strokeWidth = 5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    if (lineP > 0.5f) {
                        val barP = (lineP - 0.5f) * 2f
                        val barY = cy + r * 0.1f
                        val startX = cx - r * 0.24f
                        val endX = startX + (r * 0.48f) * barP
                        drawLine(
                            color = palette.goldAccent,
                            start = Offset(startX, barY),
                            end = Offset(endX, barY),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(
                text = AxtraConstants.APP_NAME.uppercase(),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = palette.primaryNeon
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = AxtraConstants.APP_TAGLINE,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
            Spacer(Modifier.height(22.dp))
            AnimatedVisibility(visible = showOwner, enter = fadeIn(tween(500))) {
                OwnerLabelBadge(fontSizeSp = 17f)
            }
        }

        TextButton(
            onClick = onFinished,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("splash_skip_button")
        ) {
            Text("Skip", color = palette.textSecondary)
        }
    }
}
