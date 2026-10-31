package com.celtaury.cura.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── Brand anchors ─────────────────────────────────────────────
val Petrol = Color(0xFF234B54)          // primary brand — buttons, pills, bars
val PetrolDeep = Color(0xFF1E464E)      // dark containers
val PetrolNight = Color(0xFF0D1416)     // dark background
val Mint = Color(0xFF96D3C6)            // dark-mode primary
val Leaf = Color(0xFF6FA287)            // "fresh & clean" celebration green
val LeafContainer = Color(0xFFDFF2E4)
val AccentOrange = Color(0xFFE8833A)    // Photo Compress accent (per Figma)
val AccentOrangeContainer = Color(0xFFFFE8CC)   // light mode card background
val OnAccentOrange = Color(0xFF7A3E0E)
val AccentOrangeContainerDark = Color(0xFF533012) // dark mode card background
val OnAccentOrangeDark = Color(0xFFFFD9B0)

// Storage bar: petrol → leaf (matches the Figma gradient fill)
val StorageFillGradient = Brush.horizontalGradient(
    colors = listOf(Petrol, Color(0xFF4F6F6B), Leaf)
)

// Home top glow: soft mint fading out (Figma background vibe)
val HomeGlowGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFFD8F2DC), Color(0xFFEFF7EE), Color.Transparent)
)

// Dark mode home glow
val HomeGlowGradientDark = Brush.verticalGradient(
    colors = listOf(Color(0xFF12312F), Color(0xFF0F1A1C), Color.Transparent)
)
