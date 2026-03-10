package com.sidekick.opt_pal.ui.theme

import androidx.compose.ui.graphics.Color

// The "Stark" Palette - Hyper Minimalist

// Base
val AbsoluteWhite = Color(0xFFFFFFFF)
val OffWhite = Color(0xFFFAFAFA)
val SoftGray = Color(0xFFF4F4F5)

val AbsoluteBlack = Color(0xFF000000)
val OffBlack = Color(0xFF09090B)
val Charcoal = Color(0xFF18181B)

// Text Layers
val TextPrimaryLight = Color(0xFF09090B)
val TextSecondaryLight = Color(0xFF71717A)
val TextTertiaryLight = Color(0xFFA1A1AA)

val TextPrimaryDark = Color(0xFFFAFAFA)
val TextSecondaryDark = Color(0xFFA1A1AA)
val TextTertiaryDark = Color(0xFF52525B)

// The Single Accent - Electric Indigo
// Used only for primary actions and critical status
val ElectricIndigo = Color(0xFF4F46E5)
val ElectricIndigoLight = Color(0xFF6366F1)

// Semantic - Desaturated to maintain minimalism
val MinimalError = Color(0xFFEF4444)
val MinimalSuccess = Color(0xFF10B981)
val MinimalWarning = Color(0xFFF59E0B)

// Theme Mappings
val LightBackground = AbsoluteWhite
val LightSurface = OffWhite
val LightOnSurface = TextPrimaryLight

val DarkBackground = AbsoluteBlack
val DarkSurface = OffBlack
val DarkOnSurface = TextPrimaryDark

// Backward compatibility for existing screens
val GradientStart = ElectricIndigo
val GradientEnd = ElectricIndigoLight
val Success = MinimalSuccess
val SuccessLight = MinimalSuccess
val Blue600 = ElectricIndigo
