package com.celtaury.cura

import android.app.Activity
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// --- YOUR ORIGINAL GREEN COLOR PALETTE ---
// --- DEEP PETROL PALETTE ---
val BgGradientStart = Color(0xFFF4F6F8)
val BgGradientEnd = Color(0xFFF4F6F8)
val BarFill = Color(0xFF2A4B54)         // Deep Petrol / Slate Teal
val TextColor = Color(0xFF0F172A)       // Cool slate black
val TextMuted = Color(0xFF64748B)       // Boring cool gray
val CardBackground = Color(0xFFFFFFFF)

// Petrol gradient for progress bar
val BorderGradient = Brush.linearGradient(colors = listOf(Color(0xFF2A4B54), Color(0xFF1E363D), Color(0xFF2A4B54)))

// KEEP THE OLD GREEN FOR QUICK CLEAN BUTTON
val QuickCleanBorderGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFFBEFFC3),
        Color(0xFF90EE90),
        Color(0xFFBEFFC3)
    )
)

val TrackBackground = Color(0xFFE2E8F0)

data class FeatureCard(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val iconBg: Color,
    val iconTint: Color
)

@Composable
fun DashboardScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    // Ensure status bar icons are dark in Light Mode
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = BgGradientStart.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    val features = listOf(
        FeatureCard("Large Files", Icons.Default.CleaningServices, "large_files", Color(0xFFE8F5E9), Color(0xFF2E7D32)),
        FeatureCard("Organize Images", Icons.Default.Image, "images", Color(0xFFE3F2FD), Color(0xFF1565C0)),
        FeatureCard("WhatsApp Cleaner", Icons.Default.Message, "whatsapp", Color(0xFFE0F2F1), Color(0xFF00796B)),
        FeatureCard("Image Optimizer", Icons.Default.PhotoLibrary, "optimize", Color(0xFFFFF3E0), Color(0xFFE65100)),
        FeatureCard("Old Screenshots", Icons.Default.Screenshot, "screenshots", Color(0xFFF3E5F5), Color(0xFF7B1FA2)),
        FeatureCard("App Manager", Icons.Default.HelpOutline, "quiz", Color(0xFFFFEBEE), Color(0xFFC62828)),
        FeatureCard("More Tools", Icons.Default.MoreHoriz, "etc", Color(0xFFECEFF1), Color(0xFF455A64))
    )

    var storageInfo by remember {mutableStateOf<StorageInfo?>(null)}
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100) // Simulate a delay
        storageInfo = getDeviceStorageInfo(context)
    }

    fun formatBytes(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return String.format("%.1f GB", gb)
    }

    val targetProgress = storageInfo?.percentageUsed ?: 0f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 1500, easing = EaseOutCubic), // Smooth, natural easing
        label = "progress"
    )

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF4FFF2))) {
        // Gradient Top Background Glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(BgGradientStart, BgGradientEnd, Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // --- APP NAME HEADER ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "CURA",
                    fontSize = 22.sp,
                    fontFamily = InterBlack,
                    color = TextColor,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- EXPRESSIVE STORAGE HERO CARD ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.05f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Device Storage",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMuted
                        )
                        // Pill badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(BarFill)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (storageInfo != null) {
                                    "${(storageInfo!!.percentageUsed * 100).toInt()}% Full"
                                } else {
                                    "..."
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (storageInfo != null) {
                            "${formatBytes(storageInfo!!.usedBytes)} used of ${formatBytes(storageInfo!!.totalBytes)}"
                        } else {
                            "Calculating..."
                        },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextColor
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Clean Expressive Progress Track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(TrackBackground)
                    ) {
                        // The Animated Fill (Using a gradient so it doesn't look boring)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedProgress)
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(BarFill, Color(0xFF90EE90))
                                    )
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- QUICK CLEAN BUTTON WITH GRADIENT BORDER ---
            Box(
                modifier = Modifier
                    .width(195.dp)
                    .height(52.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(26.dp))
                    .background(QuickCleanBorderGradient)
                    .padding(1.5.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(CardBackground)
                    .clickable { /* TODO */ },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Quick Clean",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextColor
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- FEATURE GRID ---
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(features) { feature ->
                    FeatureItem(feature = feature) {
                        onNavigate(feature.route)
                    }
                }
            }
        }
    }
}

@Composable
fun FeatureItem(feature: FeatureCard, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(115.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(feature.iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = feature.icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = feature.iconTint
                )
            }
            Text(
                text = feature.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextColor
            )
        }
    }
}