package com.focusai.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Clean White palette ─────────────────────────────────────────────────────
// Seed: vivid indigo-blue (#4361EE) — energetic, modern, great on white.
// Secondary: deep teal — harmonious contrast accent.
// All surface / background tokens are pure or near-pure white so the whole
// app reads as a clean white canvas.

val Indigo500   = Color(0xFF4361EE)
val IndigoLight = Color(0xFFE4E9FF)
val IndigoDark  = Color(0xFF001270)

val Teal600     = Color(0xFF0096A0)
val TealLight   = Color(0xFFB3F0F5)
val TealDark    = Color(0xFF002023)

val PureWhite   = Color(0xFFFFFFFF)
val NearWhite   = Color(0xFFF8F9FF)   // subtle blue-tinted surface
val LightSurface= Color(0xFFF1F2F8)   // card / elevated surface
val OutlineColor= Color(0xFFD0D3E3)

val Ink900      = Color(0xFF1A1C2E)   // near-black body text
val Ink500      = Color(0xFF44475A)   // secondary text
val Ink200      = Color(0xFF8B8FA8)   // disabled / hint

val ErrorRed    = Color(0xFFBA1A1A)

private val LightColorScheme = lightColorScheme(
    primary               = Indigo500,
    onPrimary             = PureWhite,
    primaryContainer      = IndigoLight,
    onPrimaryContainer    = IndigoDark,

    secondary             = Teal600,
    onSecondary           = PureWhite,
    secondaryContainer    = TealLight,
    onSecondaryContainer  = TealDark,

    tertiary              = Color(0xFF7B57BA),
    onTertiary            = PureWhite,
    tertiaryContainer     = Color(0xFFEFE5FF),
    onTertiaryContainer   = Color(0xFF2A0066),

    error                 = ErrorRed,
    onError               = PureWhite,
    errorContainer        = Color(0xFFFFDAD6),
    onErrorContainer      = Color(0xFF410002),

    background            = PureWhite,
    onBackground          = Ink900,

    surface               = PureWhite,
    onSurface             = Ink900,
    surfaceVariant        = LightSurface,
    onSurfaceVariant      = Ink500,

    outline               = OutlineColor,
    outlineVariant        = Color(0xFFE8EAED),
    scrim                 = Color(0xFF000000),
    inverseSurface        = Ink900,
    inverseOnSurface      = NearWhite,
    inversePrimary        = Color(0xFFBAC3FF),
)

@Composable
fun FocusAITheme(content: @Composable () -> Unit) {
    // The app always uses the clean white (light) theme as per design spec.
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
