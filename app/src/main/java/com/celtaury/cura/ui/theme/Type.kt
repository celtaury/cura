package com.celtaury.cura.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.celtaury.cura.R

val InterBlack = FontFamily(Font(R.font.inter_18pt, FontWeight.Black))

// Inter Black carries the display voice (CURA wordmark, big numbers);
// everything else stays on the platform default for readability.
val CuraTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = InterBlack, fontWeight = FontWeight.Black),
        displayMedium = base.displayMedium.copy(fontFamily = InterBlack, fontWeight = FontWeight.Black),
        displaySmall = base.displaySmall.copy(fontFamily = InterBlack, fontWeight = FontWeight.Black),
        headlineLarge = base.headlineLarge.copy(fontFamily = InterBlack, fontWeight = FontWeight.Black),
        headlineMedium = base.headlineMedium.copy(fontFamily = InterBlack, fontWeight = FontWeight.Black),
        titleLarge = base.titleLarge.copy(fontFamily = InterBlack, fontWeight = FontWeight.Black)
    )
}
