package com.celtaury.cura

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.celtaury.cura.core.JunkCategory
import com.celtaury.cura.core.MediaItem
import com.celtaury.cura.core.SavedStats
import com.celtaury.cura.core.Scanner
import com.celtaury.cura.core.humanBytes
import com.celtaury.cura.core.rememberDeleteController
import com.celtaury.cura.core.tryRestoreFromTrash
import com.celtaury.cura.ui.theme.InterBlack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCleanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf("scanning") } // scanning / categories / review / done
    var scanStatus by remember { mutableStateOf("Warming up…") }
    var categories by remember { mutableStateOf<List<JunkCategory>>(emptyList()) }
    var includedKeys by remember { mutableStateOf(setOf<String>()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var sessionSaved by remember { mutableStateOf(0L) }
    var allTimeSaved by remember { mutableStateOf(0L) }
    var lastDeleted by remember { mutableStateOf(listOf<String>()) }
    var showUndoHint by remember { mutableStateOf(false) }

    // Keep-safe reminder if user tries to leave mid-flow
    var confirmExit by remember { mutableStateOf(false) }
    BackHandler(enabled = phase == "review") { confirmExit = true }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val found = mutableListOf<JunkCategory>()

            scanStatus = "Hunting empty files…"
            val empties = Scanner.emptyFiles(context)
            if (empties.isNotEmpty()) found += JunkCategory("empty", "Empty files", "Zero bytes, pure clutter", empties)

            scanStatus = "Spotting twins…"
            val dupes = Scanner.duplicateImages(context)
            if (dupes.isNotEmpty()) found += JunkCategory("dupes", "Duplicate photos", "Same picture, many copies", dupes)

            scanStatus = "Digging up installers…"
            val apks = Scanner.apkFiles(context)
            if (apks.isNotEmpty()) found += JunkCategory("apks", "APK installers", "Forgotten installer files", apks)

            scanStatus = "Leafing through screenshots…"
            val shots = Scanner.oldScreenshots(context, minAgeDays = 30)
            if (shots.isNotEmpty()) found += JunkCategory("shots", "Old screenshots", "Untouched for 30+ days", shots)

            // One file can qualify for several pickers (e.g. an old screenshot that
            // is also a duplicate). Keep it in the FIRST category only — the same
            // URI twice in the review list would crash LazyColumn with duplicate keys.
            val seen = HashSet<String>()
            val deduped = found.map { cat ->
                cat.copy(items = cat.items.filter { seen.add(it.uri.toString()) })
            }.filter { it.items.isNotEmpty() }
            categories = deduped
            includedKeys = deduped.map { it.key }.toSet()
        }
        phase = "categories"
    }

    // Refresh the all-time counter when the done screen appears (kept off the UI thread)
    LaunchedEffect(phase) {
        if (phase == "done") allTimeSaved = withContext(Dispatchers.IO) { SavedStats.savedBytes(context) }
    }

    val includedCategories = categories.filter { it.key in includedKeys }
    val reviewItems = includedCategories.flatMap { it.items }.distinctBy { it.uri.toString() }
    val selectedUris = reviewItems.filter { it.uri.toString() in selected }.map { it.uri }
    val selectedBytes = reviewItems
        .filter { it.uri.toString() in selected }
        .sumOf { it.sizeBytes }

    val delete = rememberDeleteController(onCleaned = {
        lastDeleted = selected.toList()
        sessionSaved = selectedBytes
        SavedStats.addClean(context, selectedBytes)
        phase = "done"
    })

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Quick Clean", fontWeight = FontWeight.Bold, fontFamily = InterBlack)
                        Text("Smart picks, reviewed by you", fontSize = 12.sp,
                            color = cs.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (phase == "review") confirmExit = true else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        bottomBar = {
            if (phase == "review" && reviewItems.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cs.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${selectedUris.size} picked", fontWeight = FontWeight.SemiBold)
                            Text(humanBytes(selectedBytes), fontSize = 12.sp,
                                color = cs.onPrimaryContainer)
                        }
                        Button(
                            onClick = { delete(selectedUris) },
                            enabled = selectedUris.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = cs.primary)
                        ) { Text("Clean 🧹") }
                    }
                }
            }
        }
    ) { padding ->
        Crossfade(targetState = phase, label = "quickclean_phase") { p ->
            when (p) {
                "scanning" -> Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = cs.primary,
                        trackColor = cs.surfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))
                    Crossfade(targetState = scanStatus, label = "scan_status") { s ->
                        Text(s, style = MaterialTheme.typography.titleMedium,
                            color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 32.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("🛡️ Keep-safe files are never touched",
                        fontSize = 12.sp, color = cs.onSurfaceVariant)
                }

                "categories" -> {
                    if (categories.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("🌿", fontSize = 52.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("You're already clean!",
                                style = MaterialTheme.typography.titleLarge)
                            Text("Nothing junky found this time.",
                                color = cs.onSurfaceVariant)
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = onBack) { Text("Back home") }
                        }
                    } else {
                        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                            Spacer(Modifier.height(8.dp))
                            Text("Found ${categories.sumOf { it.items.size }} things to reconsider",
                                style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(12.dp))
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(categories, key = { it.key }) { cat ->
                                    val isIn = cat.key in includedKeys
                                    Card(
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isIn) cs.primaryContainer
                                            else cs.surfaceVariant
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth()
                                                .clickable {
                                                    includedKeys =
                                                        if (isIn) includedKeys - cat.key
                                                        else includedKeys + cat.key
                                                }
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(cat.title, fontWeight = FontWeight.Bold)
                                                Text(cat.subtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = cs.onSurfaceVariant)
                                                Text(
                                                    "${cat.items.size} files · ${humanBytes(cat.totalBytes)}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            Checkbox(checked = isIn, onCheckedChange = {
                                                includedKeys =
                                                    if (it) includedKeys + cat.key
                                                    else includedKeys - cat.key
                                            })
                                        }
                                    }
                                }
                            }
                            Button(
                                onClick = {
                                    selected = reviewItems.map { it.uri.toString() }.toSet()
                                    phase = "review"
                                },
                                enabled = includedCategories.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = MaterialTheme.shapes.large
                            ) { Text("Continue to review", fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }

                "review" -> Column(Modifier.fillMaxSize().padding(padding)) {
                    Text(
                        "Uncheck anything you want to keep",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        includedCategories.forEach { cat ->
                            item(key = "h_${cat.key}") {
                                Text(
                                    "${cat.title} · ${cat.items.size}",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                )
                                HorizontalDivider(color = cs.outlineVariant)
                            }
                            items(cat.items, key = { "${cat.key}:${it.uri}" }) { item ->
                                ReviewRow(
                                    item = item,
                                    checked = item.uri.toString() in selected,
                                    onToggle = {
                                        val key = item.uri.toString()
                                        selected = if (key in selected) selected - key
                                        else selected + key
                                    }
                                )
                                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))
                            }
                        }
                        item { Spacer(Modifier.height(110.dp)) }
                    }
                }

                "done" -> Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = cs.tertiary,
                        modifier = Modifier.size(84.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("Storage cleaned!", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "You just freed ${humanBytes(sessionSaved)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurfaceVariant
                    )
                    Text(
                        "🌿 Cura has saved you ${humanBytes(allTimeSaved)} all-time",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.tertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Deleted media sits in your system Trash for ~30 days, just in case.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        TextButton(onClick = {
                            scope.launch {
                                val restored = tryRestoreFromTrash(
                                    context, lastDeleted.mapNotNull { runCatching {
                                        android.net.Uri.parse(it) }.getOrNull() }
                                )
                                if (restored > 0) {
                                    Toast.makeText(context, "Restored $restored files", Toast.LENGTH_SHORT).show()
                                    onBack()
                                } else showUndoHint = true
                            }
                        }) { Text("↩️ Undo") }
                    }
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = MaterialTheme.shapes.large) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Leave the review?") },
            text = { Text("Nothing has been cleaned yet. Your picks will be lost.") },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; onBack() }) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) { Text("Stay") }
            }
        )
    }

    if (showUndoHint) {
        AlertDialog(
            onDismissRequest = { showUndoHint = false },
            title = { Text("Undo didn't work") },
            text = { Text("Your system got stubborn. Open your Files app → Trash — the deleted files rest there for ~30 days.") },
            confirmButton = {
                TextButton(onClick = { showUndoHint = false }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun ReviewRow(item: MediaItem, checked: Boolean, onToggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isMedia = item.mime.startsWith("image/") || item.mime.startsWith("video/")
        if (isMedia) {
            AsyncImage(
                model = item.uri,
                contentDescription = null,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            RowIconBox(icon = Icons.Default.Description)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.displayName, maxLines = 1, fontWeight = FontWeight.Medium)
            Text(
                "${humanBytes(item.sizeBytes)}",
                fontSize = 12.sp,
                color = cs.onSurfaceVariant
            )
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun RowIconBox(icon: ImageVector) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
            .background(cs.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = cs.onSurfaceVariant)
    }
}
