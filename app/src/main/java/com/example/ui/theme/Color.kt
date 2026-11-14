package com.example.ui.theme

import androidx.compose.ui.graphics.Color

data class AxtraPalette(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val surfaceGlass: Color,
    val surfaceElevated: Color,
    val primaryNeon: Color,
    val secondaryAccent: Color,
    val goldAccent: Color,
    val borderGlow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val successGreen: Color,
    val errorRed: Color
)

val CyberBluePalette = AxtraPalette(
    backgroundTop = Color(0xFF060B18),
    backgroundBottom = Color(0xFF0C152B),
    surfaceGlass = Color(0xCC111B33),
    surfaceElevated = Color(0xFF172546),
    primaryNeon = Color(0xFF00E5FF),
    secondaryAccent = Color(0xFF8B5CF6),
    goldAccent = Color(0xFFFFD54F),
    borderGlow = Color(0x5500E5FF),
    textPrimary = Color(0xFFF3F7FF),
    textSecondary = Color(0xFFA6B6D9),
    successGreen = Color(0xFF22C55E),
    errorRed = Color(0xFFFF4D6D)
)

val RoyalGoldPalette = AxtraPalette(
    backgroundTop = Color(0xFF0D0A06),
    backgroundBottom = Color(0xFF1A140B),
    surfaceGlass = Color(0xCC1E170D),
    surfaceElevated = Color(0xFF292013),
    primaryNeon = Color(0xFFFFD54F),
    secondaryAccent = Color(0xFFFF8A3D),
    goldAccent = Color(0xFFFFE082),
    borderGlow = Color(0x66FFD54F),
    textPrimary = Color(0xFFFFFBF2),
    textSecondary = Color(0xFFD6C5A3),
    successGreen = Color(0xFF22C55E),
    errorRed = Color(0xFFFF5A5F)
)

val MatrixGreenPalette = AxtraPalette(
    backgroundTop = Color(0xFF030C06),
    backgroundBottom = Color(0xFF071A0E),
    surfaceGlass = Color(0xCC0A2112),
    surfaceElevated = Color(0xFF10301C),
    primaryNeon = Color(0xFF00FF88),
    secondaryAccent = Color(0xFF00E5FF),
    goldAccent = Color(0xFFFFD54F),
    borderGlow = Color(0x5500FF88),
    textPrimary = Color(0xFFEBFFF3),
    textSecondary = Color(0xFF91C9A8),
    successGreen = Color(0xFF00FF88),
    errorRed = Color(0xFFFF4D6D)
)

val PureAmoledPalette = AxtraPalette(
    backgroundTop = Color(0xFF000000),
    backgroundBottom = Color(0xFF04060D),
    surfaceGlass = Color(0xCC0B0E17),
    surfaceElevated = Color(0xFF121826),
    primaryNeon = Color(0xFF38BDF8),
    secondaryAccent = Color(0xFFA855F7),
    goldAccent = Color(0xFFFFD54F),
    borderGlow = Color(0x4438BDF8),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF94A3B8),
    successGreen = Color(0xFF22C55E),
    errorRed = Color(0xFFFF4D6D)
)
