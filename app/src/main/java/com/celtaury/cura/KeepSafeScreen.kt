package com.celtaury.cura

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.celtaury.cura.core.KeepSafe
import com.celtaury.cura.core.MediaItem
import com.celtaury.cura.core.humanBytes
import com.celtaury.cura.core.relativeDays
import com.celtaury.cura.ui.preview.MediaPreviewDialog
import com.celtaury.cura.ui.theme.InterBlack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ProtectedEntry(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val mime: String,
    val path: String,
    val source: String,
    val savedTimestamp: Long
) {
    fun toMediaItem() = MediaItem(
        id = uri.lastPathSegment?.toLongOrNull() ?: 0L,
        displayName = name,
        sizeBytes = sizeBytes,
        uri = uri,
        dateAddedSec = dateAddedSec,
        path = path,
        mime = mime
    )
}

/**
 * Keep-safe Screen with Material 3 Expressive UI:
 * - Filter by Source (WhatsApp, Screenshots, Camera, Other)
 * - Filter by Time (All Time, Last 7 Days, Last 30 Days)
 * - Tap thumbnail for instant Apple Quick Look preview
 * - Unprotect with full Undo Snackbar support
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeepSafeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var entries by remember { mutableStateOf<List<ProtectedEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedSourceFilter by remember { mutableStateOf("All Sources") }
    var selectedTimeFilter by remember { mutableStateOf("All Time") }
    var previewItem by remember { mutableStateOf<MediaItem?>(null) }

    suspend fun loadEntries() {
        val allUris = KeepSafe.all(context)
        val loaded = withContext(Dispatchers.IO) {
            allUris.mapNotNull { str ->
                runCatching {
                    val uri = Uri.parse(str)
                    resolveEntry(context, uri)
                }.getOrNull()
            }.sortedByDescending { it.savedTimestamp }
        }
        entries = loaded
        loading = false
    }

    LaunchedEffect(Unit) {
        loadEntries()
    }

    // Dynamic sources based on protected items
    val availableSources = remember(entries) {
        val set = mutableSetOf("All Sources")
        entries.forEach { set.add(it.source) }
        set.toList()
    }

    val timeFilters = listOf("All Time", "Last 7 Days", "Last 30 Days")

    // Filtered entries
    val filteredEntries = remember(entries, selectedSourceFilter, selectedTimeFilter) {
        val now = System.currentTimeMillis()
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000

        entries.filter { item ->
            val matchesSource = selectedSourceFilter == "All Sources" || item.source == selectedSourceFilter
            val effectiveTime = if (item.savedTimestamp > 0L) item.savedTimestamp else item.dateAddedSec * 1000L
            val matchesTime = when (selectedTimeFilter) {
                "Last 7 Days" -> (now - effectiveTime) <= sevenDaysMs
                "Last 30 Days" -> (now - effectiveTime) <= thirtyDaysMs
                else -> true
            }
            matchesSource && matchesTime
        }
    }

    val totalBytes = entries.sumOf { it.sizeBytes }

    Scaffold(
        containerColor = cs.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Keep-safe",
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterBlack
                        )
                        Text(
                            "Protected from every cleaner",
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
        Crossfade(targetState = loading, label = "keepsafe_loading") { isLoading ->
            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(60.dp),
                        color = cs.primary,
                        trackColor = cs.surfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Loading protected files…",
                        color = cs.onSurfaceVariant
                    )
                }
            } else if (entries.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE8F5E9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(42.dp)
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Nothing protected yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Swipe right on any card in Quick Clean, Social Cleaner, or review decks to keep it safe forever.",
                        color = cs.onSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(Modifier.height(6.dp))

                    // ── Summary Header Card ──
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cs.surface),
                        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFE8F5E9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${entries.size} protected files",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = cs.onSurface
                                )
                                Text(
                                    "${humanBytes(totalBytes)} safely shielded from cleaning",
                                    fontSize = 12.sp,
                                    color = cs.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── Filter Chips Row (Source & Time) ──
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        // Source filters
                        items(availableSources) { source ->
                            FilterChip(
                                selected = selectedSourceFilter == source,
                                onClick = { selectedSourceFilter = source },
                                label = { Text(source) },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = cs.primaryContainer,
                                    selectedLabelColor = cs.onPrimaryContainer
                                )
                            )
                        }
                        // Time filters
                        items(timeFilters) { time ->
                            FilterChip(
                                selected = selectedTimeFilter == time,
                                onClick = { selectedTimeFilter = time },
                                label = { Text(time) },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = cs.secondaryContainer,
                                    selectedLabelColor = cs.onSecondaryContainer
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // ── Protected Files List ──
                    if (filteredEntries.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("🔍", fontSize = 38.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No files match these filters",
                                fontWeight = FontWeight.Bold,
                                color = cs.onSurface
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    selectedSourceFilter = "All Sources"
                                    selectedTimeFilter = "All Time"
                                },
                                shape = CircleShape
                            ) {
                                Text("Reset Filters")
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(filteredEntries, key = { it.uri.toString() }) { entry ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = cs.surface),
                                    border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Thumbnail (Tap to open instant Apple Quick Look preview)
                                        val isVisual = entry.mime.startsWith("image/") ||
                                            entry.mime.startsWith("video/")
                                        if (isVisual) {
                                            AsyncImage(
                                                model = entry.uri,
                                                contentDescription = entry.name,
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .clickable { previewItem = entry.toMediaItem() },
                                                contentScale = ContentScale.Crop
                                            )
                                        } else if (entry.mime.startsWith("audio/")) {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(cs.secondaryContainer)
                                                    .clickable { previewItem = entry.toMediaItem() },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.PlayCircle,
                                                    contentDescription = null,
                                                    tint = cs.onSecondaryContainer,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(cs.surfaceVariant)
                                                    .clickable { previewItem = entry.toMediaItem() },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Description,
                                                    contentDescription = null,
                                                    tint = cs.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Spacer(Modifier.width(14.dp))

                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                entry.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = cs.onSurface
                                            )
                                            Spacer(Modifier.height(3.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    humanBytes(entry.sizeBytes),
                                                    fontSize = 12.sp,
                                                    color = cs.onSurfaceVariant
                                                )
                                                Text("·", fontSize = 12.sp, color = cs.onSurfaceVariant)
                                                // Source Badge
                                                Box(
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(cs.primaryContainer.copy(alpha = 0.6f))
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        entry.source,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = cs.onPrimaryContainer
                                                    )
                                                }
                                            }
                                        }

                                        // ── Unprotect button with Undo Snackbar ──
                                        IconButton(
                                            onClick = {
                                                val removed = entry
                                                KeepSafe.remove(context, removed.uri)
                                                entries = entries.filterNot { it.uri == removed.uri }

                                                scope.launch {
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = "Unprotected \"${removed.name}\"",
                                                        actionLabel = "Undo",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        KeepSafe.add(context, removed.uri, removed.source)
                                                        entries = (entries + removed).sortedByDescending { it.savedTimestamp }
                                                        Toast.makeText(context, "Protection restored 🛡️", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.RemoveCircleOutline,
                                                contentDescription = "Remove protection",
                                                tint = cs.error.copy(alpha = 0.8f)
                                            )
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

    // ── Instant Apple Quick Look-Style Media Preview Dialog ──
    previewItem?.let { target ->
        MediaPreviewDialog(
            item = target,
            isQueued = false,
            onToggleQueue = {
                // In Keep-safe, unprotecting is available directly
                val removed = entries.firstOrNull { it.uri == target.uri }
                if (removed != null) {
                    KeepSafe.remove(context, target.uri)
                    entries = entries.filterNot { it.uri == target.uri }
                    previewItem = null
                    Toast.makeText(context, "Unprotected 🛡️", Toast.LENGTH_SHORT).show()
                }
            },
            onKeepSafe = {
                previewItem = null
            },
            onDismiss = { previewItem = null }
        )
    }
}

private fun resolveEntry(context: Context, uri: Uri): ProtectedEntry? {
    val metaSource = KeepSafe.sourceOf(context, uri)
    val savedTime = KeepSafe.savedTimeOf(context, uri)

    return try {
        val proj = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.MIME_TYPE,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaStore.MediaColumns.RELATIVE_PATH
            else
                MediaStore.MediaColumns.DATA
        )

        context.contentResolver.query(uri, proj, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val nameIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val mimeIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val pathIdx = c.getColumnIndexOrThrow(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        MediaStore.MediaColumns.RELATIVE_PATH
                    else
                        MediaStore.MediaColumns.DATA
                )

                val path = c.getString(pathIdx) ?: ""
                val source = when {
                    !metaSource.isNullOrBlank() && metaSource != "Manual" -> metaSource
                    path.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
                    path.contains("screenshot", ignoreCase = true) -> "Screenshots"
                    path.contains("camera", ignoreCase = true) || path.contains("dcim", ignoreCase = true) -> "Camera"
                    path.contains("download", ignoreCase = true) -> "Downloads"
                    else -> "Gallery"
                }

                ProtectedEntry(
                    uri = uri,
                    name = c.getString(nameIdx) ?: uri.lastPathSegment ?: "Protected file",
                    sizeBytes = c.getLong(sizeIdx),
                    dateAddedSec = c.getLong(dateIdx),
                    mime = c.getString(mimeIdx) ?: "",
                    path = path,
                    source = source,
                    savedTimestamp = savedTime
                )
            } else null
        } ?: fallbackEntry(uri, metaSource, savedTime)
    } catch (_: Exception) {
        fallbackEntry(uri, metaSource, savedTime)
    }
}

private fun fallbackEntry(uri: Uri, metaSource: String?, savedTime: Long): ProtectedEntry {
    val name = uri.lastPathSegment ?: "Protected file"
    val source = if (!metaSource.isNullOrBlank()) metaSource else "Gallery"
    return ProtectedEntry(
        uri = uri,
        name = name,
        sizeBytes = 0L,
        dateAddedSec = System.currentTimeMillis() / 1000L,
        mime = "image/*",
        path = "",
        source = source,
        savedTimestamp = savedTime
    )
}
