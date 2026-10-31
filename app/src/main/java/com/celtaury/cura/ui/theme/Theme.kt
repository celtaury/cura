package com.celtaury.cura.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Petrol,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E6E3),
    onPrimaryContainer = Color(0xFF0B252B),
    secondary = Color(0xFF4F6F6B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E8E3),
    onSecondaryContainer = Color(0xFF10201D),
    tertiary = Leaf,
    onTertiary = Color.White,
    tertiaryContainer = LeafContainer,
    onTertiaryContainer = Color(0xFF12291C),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF4F8F3),
    onBackground = Color(0xFF101B1E),
    surface = Color.White,
    onSurface = Color(0xFF101B1E),
    surfaceVariant = Color(0xFFECF2F0),
    onSurfaceVariant = Color(0xFF41514F),
    outline = Color(0xFF71817E),
    outlineVariant = Color(0xFFC4D2CF),
    surfaceContainerLow = Color(0xFFF0F5F3),
    surfaceContainer = Color(0xFFECF2F0),
    surfaceContainerHigh = Color(0xFFE6EDEA)
)

private val DarkScheme = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF06272C),
    primaryContainer = PetrolDeep,
    onPrimaryContainer = Color(0xFFC8E9E2),
    secondary = Color(0xFFA8C8C2),
    onSecondary = Color(0xFF10201D),
    secondaryContainer = Color(0xFF243B37),
    onSecondaryContainer = Color(0xFFD3E8E2),
    tertiary = Color(0xFF9BD1AE),
    onTertiary = Color(0xFF0B2917),
    tertiaryContainer = Color(0xFF1E4634),
    onTertiaryContainer = Color(0xFFD5F2DE),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = PetrolNight,
    onBackground = Color(0xFFDDE7E4),
    surface = Color(0xFF131B1D),
    onSurface = Color(0xFFDDE7E4),
    surfaceVariant = Color(0xFF1A2427),
    onSurfaceVariant = Color(0xFFAEBDBA),
    outline = Color(0xFF788885),
    outlineVariant = Color(0xFF31413F),
    surfaceContainerLow = Color(0xFF161F21),
    surfaceContainer = Color(0xFF1A2427),
    surfaceContainerHigh = Color(0xFF232E31)
)

@Composable
fun CuraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = CuraTypography,
        content = content
    )
}
