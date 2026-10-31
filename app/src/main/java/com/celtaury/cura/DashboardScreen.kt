package com.celtaury.cura

import android.widget.Toast
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.celtaury.cura.core.MediaItem
import com.celtaury.cura.core.KeepSafe
import com.celtaury.cura.core.SavedStats
import com.celtaury.cura.core.Scanner
import com.celtaury.cura.core.humanBytes
import com.celtaury.cura.core.relativeDays
import com.celtaury.cura.core.rememberDeleteController
import com.celtaury.cura.ui.theme.AccentOrange
import com.celtaury.cura.ui.theme.AccentOrangeContainer
import com.celtaury.cura.ui.theme.AccentOrangeContainerDark
import com.celtaury.cura.ui.theme.HomeGlowGradient
import com.celtaury.cura.ui.theme.HomeGlowGradientDark
import com.celtaury.cura.ui.theme.InterBlack
import com.celtaury.cura.ui.theme.StorageFillGradient

private data class FeatureTile(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val iconBg: Color,
    val iconTint: Color
)

@Composable
fun DashboardScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val orangeContainer = if (dark) AccentOrangeContainerDark else AccentOrangeContainer

    var storageInfo by remember { mutableStateOf<StorageInfo?>(null) }
    var savedBytes by remember { mutableStateOf(0L) }
    var forgotten by remember { mutableStateOf<MediaItem?>(null) }
    var sheetItem by remember { mutableStateOf<MediaItem?>(null) }

    LaunchedEffect(Unit) {
        savedBytes = SavedStats.savedBytes(context)
        storageInfo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            getDeviceStorageInfo(context)
        }
        forgotten = Scanner.forgottenPhoto(context)
    }

    val targetProgress = storageInfo?.percentageUsed ?: 0f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 1200, easing = EaseOutCubic),
        label = "storageProgress"
    )

    val deleteForgotten = rememberDeleteController(onCleaned = {
        sheetItem?.let { SavedStats.addClean(context, it.sizeBytes) }
        sheetItem = null
        forgotten = null
        Toast.makeText(context, "Cleaned — Cura remembers 🌿", Toast.LENGTH_SHORT).show()
    })

    Box(modifier = Modifier.fillMaxSize().background(cs.background)) {
        // Soft mint glow at the top (Figma vibe) — dark-aware
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(if (dark) HomeGlowGradientDark else HomeGlowGradient)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(14.dp))

            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "CURA",
                    fontSize = 26.sp,
                    fontFamily = InterBlack,
                    letterSpacing = 1.sp,
                    color = cs.onBackground
                )
                IconButton(onClick = { onNavigate("more") }) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = "More tools",
                        tint = cs.onBackground)
                }
            }

            if (savedBytes > 0L) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(cs.tertiaryContainer)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        "🌿 Cura has saved you ${humanBytes(savedBytes)} so far",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onTertiaryContainer
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Storage hero card (Figma) ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = cs.surface),
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(Modifier.fillMaxWidth().padding(22.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Device Storage",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(cs.primary)
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                if (storageInfo != null)
                                    "${(storageInfo!!.percentageUsed * 100).toInt()}% Full" else "…",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = cs.onPrimary
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (storageInfo != null)
                            "${humanBytes(storageInfo!!.usedBytes)} used of ${humanBytes(storageInfo!!.totalBytes)}"
                        else "Calculating…",
                        style = MaterialTheme.typography.titleLarge,
                        color = cs.onSurface
                    )
                    Spacer(Modifier.height(18.dp))
                    // Progress track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(cs.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(7.dp))
                                .background(StorageFillGradient)
                        )
                    }
                    if (storageInfo != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${humanBytes(storageInfo!!.freeBytes)} free",
                            fontSize = 12.sp,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Quick Clean (Figma pill: white + green border) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .height(54.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(27.dp))
                    .background(cs.tertiaryContainer)
                    .clickable { onNavigate("quick_clean") },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Quick Clean",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onTertiaryContainer
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Discovery row (Figma): abandoned photo + 2 feature cards ──
            Row(
                modifier = Modifier.fillMaxWidth().height(230.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left: abandoned photo discovery card
                Card(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxSize()
                        .clickable(enabled = forgotten != null) { sheetItem = forgotten },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cs.surface),
                    border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            if (forgotten != null) {
                                AsyncImage(
                                    model = forgotten!!.uri,
                                    contentDescription = "Forgotten photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(cs.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🌿", fontSize = 34.sp)
                                }
                            }
                        }
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(
                                if (forgotten != null) "Abandoned Photo Found!" else "Nothing forgotten",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = cs.onSurface
                            )
                            if (forgotten != null) {
                                Text(
                                    "${forgotten!!.displayName} · ${relativeDays(forgotten!!.dateAddedSec)}",
                                    fontSize = 11.sp,
                                    color = cs.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Right: two stacked feature cards
                Column(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MiniFeatureCard(
                        modifier = Modifier.weight(1f),
                        title = "Social Cleaner",
                        icon = Icons.Default.Message,
                        iconBg = cs.tertiaryContainer,
                        iconTint = cs.tertiary
                    ) { onNavigate("social") }
                    MiniFeatureCard(
                        modifier = Modifier.weight(1f),
                        title = "Photo Compress",
                        icon = Icons.Default.PhotoLibrary,
                        iconBg = orangeContainer,
                        iconTint = AccentOrange
                    ) { onNavigate("compress") }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── All features grid (2 columns) ──
            val tiles = listOf(
                FeatureTile("Large Files", Icons.Default.CleaningServices, "large_files",
                    cs.primaryContainer, cs.primary),
                FeatureTile("Old Screenshots", Icons.Default.Screenshot, "screenshots",
                    cs.secondaryContainer, cs.onSecondaryContainer),
                FeatureTile("Organize Images", Icons.Default.Image, "organize",
                    cs.tertiaryContainer, cs.onTertiaryContainer),
                FeatureTile("Keep-safe", Icons.Default.Favorite, "keepsafe",
                    cs.errorContainer, cs.error),
                FeatureTile("More Tools", Icons.Default.MoreHoriz, "more",
                    cs.surfaceVariant, cs.onSurfaceVariant),
                FeatureTile("Coming soon", Icons.Default.AutoAwesome, "soon",
                    cs.surfaceVariant, cs.onSurfaceVariant)
            )
            tiles.chunked(2).forEach { rowTiles ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    rowTiles.forEach { tile ->
                        FeatureItem(
                            tile = tile,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (tile.route == "soon") {
                                Toast.makeText(context, "On the roadmap 🚧", Toast.LENGTH_SHORT).show()
                            } else {
                                onNavigate(tile.route)
                            }
                        }
                    }
                    if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    // ── Abandoned photo preview sheet ──
    sheetItem?.let { item ->
        ForgottenPhotoSheet(
            item = item,
            onKeep = {
                KeepSafe.add(context, item.uri)
                forgotten = null
                sheetItem = null
                Toast.makeText(context, "Added to Keep-safe 🛡️", Toast.LENGTH_SHORT).show()
            },
            onClean = { deleteForgotten(listOf(item.uri)) },
            onDismiss = { sheetItem = null }
        )
    }
}

@Composable
private fun MiniFeatureCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun FeatureItem(
    tile: FeatureTile,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = modifier
            .height(112.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tile.iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(tile.icon, contentDescription = null, tint = tile.iconTint,
                    modifier = Modifier.size(22.dp))
            }
            Text(tile.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForgottenPhotoSheet(
    item: MediaItem,
    onKeep: () -> Unit,
    onClean: () -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            AsyncImage(
                model = item.uri,
                contentDescription = item.displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(14.dp))
            Text("Abandoned Photo Found!", style = MaterialTheme.typography.titleLarge)
            Text(
                "${item.displayName} · ${humanBytes(item.sizeBytes)} · taken ${relativeDays(item.dateAddedSec)}",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "This one has been sitting in your gallery, untouched. Keep it safe, or let it go?",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    onClick = onKeep,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.large
                ) { Text("Keep it 🛡️") }
                TextButton(
                    onClick = onClean,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.large
                ) { Text("Clean it 🧹", color = cs.error) }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
