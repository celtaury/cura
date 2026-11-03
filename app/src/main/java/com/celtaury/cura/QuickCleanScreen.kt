package com.celtaury.cura

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.celtaury.cura.core.JunkCategory
import com.celtaury.cura.core.KeepSafe
import com.celtaury.cura.core.SavedStats
import com.celtaury.cura.core.Scanner
import com.celtaury.cura.core.SuggestionEngine
import com.celtaury.cura.core.humanBytes
import com.celtaury.cura.core.relativeDays
import com.celtaury.cura.core.rememberConfirmableDelete
import com.celtaury.cura.ui.theme.InterBlack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCleanScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme

    var phase by remember { mutableStateOf("scanning") }
    var scanStatus by remember { mutableStateOf("Warming up…") }
    var categories by remember { mutableStateOf<List<JunkCategory>>(emptyList()) }
    var selectedCategoryKey by remember { mutableStateOf("all") }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val found = mutableListOf<JunkCategory>()

            scanStatus = "Hunting empty files…"
            val empties = Scanner.emptyFiles(context)
            if (empties.isNotEmpty()) {
                found += JunkCategory("empty", "Empty files", "Zero bytes, pure clutter", empties)
            }

            scanStatus = "Spotting duplicates…"
            val dupes = Scanner.duplicateImages(context)
            if (dupes.isNotEmpty()) {
                found += JunkCategory("dupes", "Duplicate photos", "Same picture, many copies", dupes)
            }

            scanStatus = "Digging up installers…"
            val apks = Scanner.apkFiles(context)
            if (apks.isNotEmpty()) {
                found += JunkCategory("apks", "APK installers", "Forgotten installer files", apks)
            }

            scanStatus = "Leafing through screenshots…"
            val shots = Scanner.oldScreenshots(context, minAgeDays = 30)
            if (shots.isNotEmpty()) {
                found += JunkCategory("shots", "Old screenshots", "Untouched for 30+ days", shots)
            }

            val seen = HashSet<String>()
            categories = found.map { cat ->
                cat.copy(items = cat.items.filter { seen.add(it.uri.toString()) })
            }.filter { it.items.isNotEmpty() }
        }
        phase = "ready"
    }

    // Confirmable delete sheet before system deletion
    val confirmDelete = rememberConfirmableDelete(
        onCleaned = {
            val allItems = categories.flatMap { it.items }
            val deletedItems = allItems.filter { it.uri.toString() in selectedUris }
            val freedBytes = deletedItems.sumOf { it.sizeBytes }

            if (freedBytes > 0L) {
                SavedStats.addClean(context, freedBytes)
            }

            categories = categories.map { cat ->
                cat.copy(items = cat.items.filter { it.uri.toString() !in selectedUris })
            }.filter { it.items.isNotEmpty() }

            selectedUris = emptySet()
            Toast.makeText(context, "Cleaned successfully 🌿", Toast.LENGTH_SHORT).show()
        }
    )

    // Items to display based on category filter
    val displayedItems = remember(categories, selectedCategoryKey) {
        if (selectedCategoryKey == "all") {
            categories.flatMap { it.items }.distinctBy { it.uri.toString() }
        } else {
            categories.firstOrNull { it.key == selectedCategoryKey }?.items ?: emptyList()
        }
    }

    val totalItems = categories.sumOf { it.items.size }
    val totalBytes = categories.sumOf { it.totalBytes }
    val selectedItemsList = remember(displayedItems, selectedUris) {
        displayedItems.filter { it.uri.toString() in selectedUris }
    }
    val selectedBytes = selectedItemsList.sumOf { it.sizeBytes }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Quick Clean",
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterBlack
                        )
                        Text(
                            "Swipe left to delete · swipe right to save",
                            fontSize = 12.sp,
                            color = cs.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { padding ->
        Crossfade(targetState = phase, label = "quickclean_phase") { currentPhase ->
            when (currentPhase) {
                "scanning" -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = cs.primary,
                        trackColor = cs.surfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))
                    Crossfade(targetState = scanStatus, label = "scan_status") { status ->
                        Text(
                            status,
                            style = MaterialTheme.typography.titleMedium,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "🛡️ Keep-safe files are never touched",
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant
                    )
                }

                else -> {
                    if (categories.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("🌿", fontSize = 54.sp)
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "You're already clean!",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Nothing junky found this time.",
                                color = cs.onSurfaceVariant
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = onBack,
                                shape = CircleShape
                            ) {
                                Text("Back home")
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Spacer(Modifier.height(6.dp))

                                // ── Category Filter Chips ──
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    item {
                                        FilterChip(
                                            selected = selectedCategoryKey == "all",
                                            onClick = { selectedCategoryKey = "all" },
                                            label = { Text("All ($totalItems)") },
                                            shape = CircleShape,
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = cs.primaryContainer,
                                                selectedLabelColor = cs.onPrimaryContainer
                                            )
                                        )
                                    }
                                    items(categories, key = { it.key }) { cat ->
                                        FilterChip(
                                            selected = selectedCategoryKey == cat.key,
                                            onClick = { selectedCategoryKey = cat.key },
                                            label = { Text("${cat.title} (${cat.items.size})") },
                                            shape = CircleShape,
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = cs.primaryContainer,
                                                selectedLabelColor = cs.onPrimaryContainer
                                            )
                                        )
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                // ── Summary Header Card ──
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = cs.surface
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        cs.outlineVariant.copy(alpha = 0.35f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                "Found $totalItems suggestions",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = cs.onSurface
                                            )
                                            Text(
                                                "${humanBytes(totalBytes)} recoverable storage",
                                                fontSize = 12.sp,
                                                color = cs.onSurfaceVariant
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                selectedUris = if (selectedUris.size == displayedItems.size) {
                                                    emptySet()
                                                } else {
                                                    displayedItems.map { it.uri.toString() }.toSet()
                                                }
                                            },
                                            shape = CircleShape,
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                if (selectedUris.size == displayedItems.size) "Deselect All" else "Select All",
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                // ── Items List with Swipe Left to Delete / Right to Save ──
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(bottom = 90.dp)
                                ) {
                                    items(displayedItems, key = { it.uri.toString() }) { item ->
                                        val isSelected = item.uri.toString() in selectedUris

                                        SwipeActionItem(
                                            onSwipeLeft = {
                                                // Swipe Left: trigger single-item confirmable delete
                                                selectedUris = selectedUris + item.uri.toString()
                                                confirmDelete(listOf(item))
                                            },
                                            onSwipeRight = {
                                                // Swipe Right: Save/Never suggest again
                                                KeepSafe.add(context, item.uri)
                                                SuggestionEngine.observe(item, "kept")
                                                categories = categories.map { cat ->
                                                    cat.copy(items = cat.items.filter { it.uri != item.uri })
                                                }.filter { it.items.isNotEmpty() }
                                                selectedUris = selectedUris - item.uri.toString()
                                                Toast.makeText(
                                                    context,
                                                    "Saved to Keep-safe 🛡️",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        ) {
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedUris = if (isSelected) {
                                                            selectedUris - item.uri.toString()
                                                        } else {
                                                            selectedUris + item.uri.toString()
                                                        }
                                                    },
                                                shape = RoundedCornerShape(18.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isSelected)
                                                        cs.primaryContainer.copy(alpha = 0.35f)
                                                    else cs.surface
                                                ),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSelected) cs.primary.copy(alpha = 0.5f)
                                                    else cs.outlineVariant.copy(alpha = 0.35f)
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Checkbox(
                                                        checked = isSelected,
                                                        onCheckedChange = { checked ->
                                                            selectedUris = if (checked) {
                                                                selectedUris + item.uri.toString()
                                                            } else {
                                                                selectedUris - item.uri.toString()
                                                            }
                                                        }
                                                    )
                                                    Spacer(Modifier.width(8.dp))

                                                    // Thumbnail / Icon
                                                    val isMedia = item.mime.startsWith("image/") ||
                                                        item.mime.startsWith("video/")
                                                    if (isMedia) {
                                                        AsyncImage(
                                                            model = item.uri,
                                                            contentDescription = item.displayName,
                                                            modifier = Modifier
                                                                .size(54.dp)
                                                                .clip(RoundedCornerShape(12.dp)),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(54.dp)
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(cs.surfaceVariant),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Description,
                                                                contentDescription = null,
                                                                tint = cs.onSurfaceVariant
                                                            )
                                                        }
                                                    }

                                                    Spacer(Modifier.width(12.dp))

                                                    Column(Modifier.weight(1f)) {
                                                        Text(
                                                            item.displayName,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            color = cs.onSurface
                                                        )
                                                        Spacer(Modifier.height(2.dp))
                                                        Text(
                                                            "${humanBytes(item.sizeBytes)} · added ${relativeDays(item.dateAddedSec)}",
                                                            fontSize = 12.sp,
                                                            color = cs.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // ── Persistent Floating Action Bar (Delete Selected / Clean All) ──
                            Card(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .fillMaxWidth()
                                    .shadow(12.dp, CircleShape),
                                shape = CircleShape,
                                colors = CardDefaults.cardColors(containerColor = cs.surface),
                                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            if (selectedUris.isNotEmpty())
                                                "${selectedUris.size} selected"
                                            else
                                                "${displayedItems.size} items",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = cs.onSurface
                                        )
                                        Text(
                                            if (selectedUris.isNotEmpty())
                                                humanBytes(selectedBytes)
                                            else
                                                humanBytes(displayedItems.sumOf { it.sizeBytes }),
                                            fontSize = 12.sp,
                                            color = cs.onSurfaceVariant
                                        )
                                    }

                                    if (selectedUris.isNotEmpty()) {
                                        Button(
                                            onClick = {
                                                confirmDelete(selectedItemsList)
                                            },
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = cs.error
                                            )
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "Delete Selected (${selectedUris.size})",
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                selectedUris = displayedItems.map { it.uri.toString() }.toSet()
                                                confirmDelete(displayedItems)
                                            },
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = cs.primary
                                            )
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text("Clean All", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reusable swipable item container:
 * - Swipe left: reveals red Delete background -> calls [onSwipeLeft]
 * - Swipe right: reveals green Keep Safe background -> calls [onSwipeRight]
 */
@Composable
private fun SwipeActionItem(
    modifier: Modifier = Modifier,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    content: @Composable () -> Unit
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thresholdPx = with(density) { 90.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
    ) {
        val currentOffset = offsetX.value

        if (currentOffset > 8f) {
            // Swiping right: Green (Keep Safe)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFF388E3C)),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.padding(start = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = "Keep Safe",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        "Keep Safe",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        } else if (currentOffset < -8f) {
            // Swiping left: Red (Delete)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier.padding(end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Delete",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Foreground content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                val next = (offsetX.value + dragAmount)
                                    .coerceIn(-thresholdPx * 1.5f, thresholdPx * 1.5f)
                                offsetX.snapTo(next)
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value > thresholdPx) {
                                    offsetX.animateTo(
                                        0f,
                                        spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                    )
                                    onSwipeRight()
                                } else if (offsetX.value < -thresholdPx) {
                                    offsetX.animateTo(
                                        0f,
                                        spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                    )
                                    onSwipeLeft()
                                } else {
                                    offsetX.animateTo(
                                        0f,
                                        spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f)
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}
