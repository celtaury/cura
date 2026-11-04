package com.celtaury.cura

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import com.celtaury.cura.core.KeepSafe
import com.celtaury.cura.core.MediaItem
import com.celtaury.cura.core.SavedStats
import com.celtaury.cura.core.Scanner
import com.celtaury.cura.core.SuggestEngine
import com.celtaury.cura.core.SuggestionEngine
import com.celtaury.cura.core.hasMediaAccess
import com.celtaury.cura.core.humanBytes
import com.celtaury.cura.core.mediaPermissions
import com.celtaury.cura.core.relativeDays
import com.celtaury.cura.core.rememberConfirmableDelete
import com.celtaury.cura.ui.theme.AccentOrange
import com.celtaury.cura.ui.theme.AccentOrangeContainer
import com.celtaury.cura.ui.theme.AccentOrangeContainerDark
import com.celtaury.cura.ui.theme.HomeGlowGradient
import com.celtaury.cura.ui.theme.HomeGlowGradientDark
import com.celtaury.cura.ui.theme.InterBlack
import com.celtaury.cura.ui.theme.Leaf
import com.celtaury.cura.ui.theme.Mint
import com.celtaury.cura.ui.theme.Petrol
import com.celtaury.cura.ui.theme.StorageFillGradient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val orangeContainer = if (dark) AccentOrangeContainerDark else AccentOrangeContainer

    var storageInfo by remember { mutableStateOf<StorageInfo?>(null) }
    var savedBytes by remember { mutableLongStateOf(0L) }
    var pick by remember { mutableStateOf<SuggestionEngine.Pick?>(null) }
    var peekingItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var pickVersion by remember { mutableIntStateOf(0) }
    var sheetItem by remember { mutableStateOf<MediaItem?>(null) }
    var mediaOk by remember { mutableStateOf(hasMediaAccess(context)) }

    val grantLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        mediaOk = hasMediaAccess(context)
        pickVersion++
    }

    suspend fun refreshAll() {
        savedBytes = SavedStats.savedBytes(context)
        storageInfo = withContext(Dispatchers.IO) { getDeviceStorageInfo(context) }
        pick = SuggestionEngine.pick(context)
        peekingItems = withContext(Dispatchers.IO) {
            if (hasMediaAccess(context)) {
                Scanner.galleryDeck(context).take(2)
            } else {
                emptyList()
            }
        }
    }

    // Data sync: refresh when returning to Dashboard
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { refreshAll() }
    }

    LaunchedEffect(pickVersion, mediaOk) {
        refreshAll()
    }

    val targetProgress = storageInfo?.percentageUsed ?: 0f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 1200, easing = EaseOutCubic),
        label = "storageProgress"
    )

    // Confirmable delete with in-app preview before the OS delete dialog
    val confirmDelete = rememberConfirmableDelete(onCleaned = {
        sheetItem?.let {
            SavedStats.addClean(context, it.sizeBytes)
            SuggestionEngine.observe(it, "queued")
        }
        sheetItem = null
        pickVersion++
        scope.launch { refreshAll() }
        Toast.makeText(context, "Cleaned — Cura remembers 🌿", Toast.LENGTH_SHORT).show()
    })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
    ) {
        // Soft mint glow at top
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
                .padding(bottom = 36.dp)
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
                    fontSize = 28.sp,
                    fontFamily = InterBlack,
                    letterSpacing = 1.sp,
                    color = cs.onBackground
                )
                IconButton(onClick = { onNavigate("settings") }) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = cs.onBackground
                    )
                }
            }

            // ── Permission Notice ──
            if (!mediaOk) {
                Spacer(Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cs.errorContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Photo access needed",
                                fontWeight = FontWeight.Bold,
                                color = cs.onErrorContainer
                            )
                            Text(
                                "Grant access so Cura can scan and suggest cleanups.",
                                fontSize = 12.sp,
                                color = cs.onErrorContainer
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = { grantLauncher.launch(mediaPermissions()) },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = cs.onErrorContainer,
                                contentColor = cs.errorContainer
                            )
                        ) {
                            Text("Grant")
                        }
                    }
                }
            }

            if (savedBytes > 0L) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(cs.tertiaryContainer)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
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

            // ── Storage Hero Card ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = cs.surface),
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.35f))
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(22.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Device Storage",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = cs.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Petrol)
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                if (storageInfo != null)
                                    "${(storageInfo!!.percentageUsed * 100).toInt()}% Full"
                                else "…",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (storageInfo != null)
                            "${humanBytes(storageInfo!!.usedBytes)} used of ${humanBytes(storageInfo!!.totalBytes)}"
                        else "Calculating…",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface
                    )
                    Spacer(Modifier.height(16.dp))
                    // Progress Track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(cs.surfaceVariant.copy(alpha = 0.7f))
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

            Spacer(Modifier.height(20.dp))

            // ── Quick Clean Button with glowing border ──
            val glowBrush = Brush.horizontalGradient(
                colors = listOf(Mint, Leaf, Mint)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .height(52.dp)
                    .align(Alignment.CenterHorizontally)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        ambientColor = Leaf.copy(alpha = 0.35f),
                        spotColor = Leaf.copy(alpha = 0.45f)
                    )
                    .clip(CircleShape)
                    .background(cs.surface)
                    .border(BorderStroke(1.8.dp, glowBrush), CircleShape)
                    .clickable { onNavigate("quick_clean") },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Quick Clean",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface
                )
            }

            Spacer(Modifier.height(22.dp))

            // ── Discovery Row: Recommendation Card + 2 Stacked Feature Cards ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Left: Tall Recommendation Card
                Card(
                    modifier = Modifier
                        .weight(1.05f)
                        .fillMaxSize()
                        .clickable(enabled = pick != null) {
                            val p = pick ?: return@clickable
                            if (p.deckRoute.isEmpty()) {
                                sheetItem = p.item
                            } else {
                                // the overhaul promise: the tile's card LEADS the deck
                                SuggestEngine.Session.seed(p.item)
                                onNavigate("suggest")
                            }
                        },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cs.surface),
                    border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Box(Modifier.fillMaxSize()) {
                        val p = pick
                        if (p != null) {
                            AsyncImage(
                                model = p.item.uri,
                                contentDescription = p.reason,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // Dark gradient overlay at bottom for text contrast
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.65f)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.85f)
                                            )
                                        )
                                    )
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(14.dp)
                            ) {
                                Text(
                                    when {
                                        p.deckRoute.isEmpty() -> "Abandoned Photo Found!"
                                        else -> p.familyTitle
                                    },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    when {
                                        p.deckRoute.isEmpty() ->
                                            "${p.item.displayName} · ${relativeDays(p.item.dateAddedSec)}"
                                        else ->
                                            "${p.reason} · ${p.familyCount} files · ${humanBytes(p.familyBytes)}"
                                    },
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(cs.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("🌿", fontSize = 40.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "All caught up",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = cs.onSurface
                                )
                                Text(
                                    "Gallery is fresh & clean",
                                    fontSize = 11.sp,
                                    color = cs.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Right: Two stacked feature cards
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    MiniFeatureCard(
                        modifier = Modifier.weight(1f),
                        title = "Social Cleaner",
                        icon = Icons.AutoMirrored.Filled.Message,
                        iconBg = Color(0xFFDCF8C6),
                        iconTint = Color(0xFF1E5B22)
                    ) { onNavigate("social") }

                    MiniFeatureCard(
                        modifier = Modifier.weight(1f),
                        title = "Photo Compress",
                        icon = Icons.Default.AutoAwesome,
                        iconBg = orangeContainer,
                        iconTint = AccentOrange
                    ) { onNavigate("compress") }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Secondary Tiles (Review Gallery removed to avoid redundancy) ──
            val tiles = listOf(
                FeatureTile(
                    "Browse Gallery",
                    Icons.Default.CalendarMonth,
                    "browse_gallery",
                    cs.secondaryContainer,
                    cs.secondary
                ),
                FeatureTile(
                    "Keep-safe",
                    Icons.Default.Favorite,
                    "keepsafe",
                    cs.errorContainer,
                    cs.error
                ),
                FeatureTile(
                    "More Tools",
                    Icons.Default.MoreHoriz,
                    "more",
                    cs.surfaceVariant,
                    cs.onSurfaceVariant
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                tiles.forEach { tile ->
                    FeatureItem(
                        tile = tile,
                        modifier = Modifier.weight(1f)
                    ) {
                        onNavigate(tile.route)
                    }
                }
            }

            Spacer(Modifier.height(36.dp))

            // ── Bottom Peeking Gallery Deck: "Swipe Up" (Not glued to UI) ──
            var dragUp by remember { mutableFloatStateOf(0f) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                dragUp += amount
                                if (dragUp < -70f) {
                                    dragUp = 0f
                                    onNavigate("deck_gallery")
                                }
                            },
                            onDragEnd = { dragUp = 0f },
                            onDragCancel = { dragUp = 0f }
                        )
                    }
                    .clickable { onNavigate("deck_gallery") },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Swipe Up",
                    fontSize = 24.sp,
                    fontFamily = InterBlack,
                    fontWeight = FontWeight.Bold,
                    color = cs.onBackground
                )
                Spacer(Modifier.height(10.dp))

                // Floating tilted cards peeking from the bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val backItem = peekingItems.getOrNull(1) ?: peekingItems.firstOrNull()
                    val frontItem = peekingItems.firstOrNull()

                    // Back Card (tilted left)
                    Box(
                        modifier = Modifier
                            .size(width = 210.dp, height = 150.dp)
                            .offset(x = (-20).dp, y = 20.dp)
                            .graphicsLayer { rotationZ = -7f }
                            .shadow(8.dp, RoundedCornerShape(24.dp))
                            .clip(RoundedCornerShape(24.dp))
                            .background(cs.surface)
                            .border(
                                BorderStroke(1.5.dp, Color.White.copy(alpha = 0.8f)),
                                RoundedCornerShape(24.dp)
                            )
                    ) {
                        if (backItem != null) {
                            AsyncImage(
                                model = backItem.uri,
                                contentDescription = null,
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
                                Text("📸", fontSize = 32.sp)
                            }
                        }
                    }

                    // Front Card (tilted right)
                    Box(
                        modifier = Modifier
                            .size(width = 220.dp, height = 160.dp)
                            .offset(x = 18.dp, y = 10.dp)
                            .graphicsLayer { rotationZ = 5f }
                            .shadow(14.dp, RoundedCornerShape(24.dp))
                            .clip(RoundedCornerShape(24.dp))
                            .background(cs.surface)
                            .border(
                                BorderStroke(1.5.dp, Color.White.copy(alpha = 0.9f)),
                                RoundedCornerShape(24.dp)
                            )
                    ) {
                        if (frontItem != null) {
                            AsyncImage(
                                model = frontItem.uri,
                                contentDescription = null,
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
                                Text("🖼️", fontSize = 36.sp)
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        )
    }

    // ── Abandoned Photo Preview Sheet with confirmable delete ──
    sheetItem?.let { item ->
        ForgottenPhotoSheet(
            item = item,
            onKeep = {
                val kept = item
                KeepSafe.add(context, kept.uri, "Suggestions")
                SuggestionEngine.observe(kept, "kept")
                sheetItem = null
                pickVersion++
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "Saved to Keep-safe 🛡️",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        KeepSafe.remove(context, kept.uri)
                        pickVersion++
                        Toast.makeText(context, "Protection removed", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onClean = {
                val candidate = sheetItem
                sheetItem = null
                if (candidate != null) {
                    confirmDelete(listOf(candidate))
                }
            },
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
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface
            )
            Spacer(Modifier.height(2.dp))
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
            .height(104.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tile.iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    tile.icon,
                    contentDescription = null,
                    tint = tile.iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                tile.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
                maxLines = 1
            )
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
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            AsyncImage(
                model = item.uri,
                contentDescription = item.displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Abandoned Photo Found!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${item.displayName} · ${humanBytes(item.sizeBytes)} · added ${relativeDays(item.dateAddedSec)}",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "This photo has remained untouched in your gallery for months. Would you like to keep it safe or delete it?",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onKeep,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Keep Safe")
                }
                Button(
                    onClick = onClean,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = cs.error)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Delete")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
