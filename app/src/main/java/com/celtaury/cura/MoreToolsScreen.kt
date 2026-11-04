package com.celtaury.cura

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celtaury.cura.core.SavedStats
import com.celtaury.cura.core.humanBytes
import com.celtaury.cura.ui.theme.InterBlack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreToolsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit = {}) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var showReset by remember { mutableStateOf(false) }

    // Stats are read off the UI thread — StatFs/SharedPreferences in composition caused jank
    var savedBytes by remember { mutableStateOf(0L) }
    var sessions by remember { mutableStateOf(0) }
    var storage by remember { mutableStateOf<StorageInfo?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            savedBytes = SavedStats.savedBytes(context)
            sessions = SavedStats.cleanSessions(context)
            storage = getDeviceStorageInfo(context)
        }
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text("More Tools", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Stats card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cs.tertiaryContainer)
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Your impact", style = MaterialTheme.typography.titleSmall,
                        color = cs.onTertiaryContainer)
                    Text(
                        humanBytes(savedBytes),
                        style = MaterialTheme.typography.displaySmall,
                        color = cs.onTertiaryContainer
                    )
                    Text(
                        "freed across $sessions clean session${if (sessions == 1) "" else "s"} 🌿",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onTertiaryContainer
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Storage summary
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cs.surface),
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Device storage", style = MaterialTheme.typography.titleSmall,
                        color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        storage?.let { "${humanBytes(it.usedBytes)} used of ${humanBytes(it.totalBytes)}" } ?: "…",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        storage?.let { "${humanBytes(it.freeBytes)} free · ${(it.percentageUsed * 100).toInt()}% full" } ?: "Calculating…",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Menu rows
            MenuRow(title = "Manage Keep-safe 🛡️", subtitle = "See and unprotect saved files") {
                onNavigate("keepsafe")
            }
            MenuRow(title = "Reset saved-counter", subtitle = "Start the impact counter fresh") {
                showReset = true
            }

            Spacer(Modifier.height(20.dp))

            // ── Legacy tools (still work, just live in the attic now) ──
            Text(
                "Legacy tools",
                style = MaterialTheme.typography.titleSmall,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            MenuRow(
                title = "Large Files · legacy",
                subtitle = "Older tool — files over 50 MB, biggest first"
            ) {
                onNavigate("large_files")
            }
            MenuRow(
                title = "Photo Organizer · legacy",
                subtitle = "Older tool — group your gallery by month"
            ) {
                onNavigate("organize")
            }

            Spacer(Modifier.height(20.dp))

            // About
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant)
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("CURA", fontFamily = InterBlack, fontSize = 18.sp)
                    Text(
                        "Storage care, not storage stress. v0.8 · flow build.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Reset the counter?") },
            text = { Text("The \"Cura has saved you X\" number goes back to zero. Your files are untouched.") },
            confirmButton = {
                TextButton(onClick = {
                    SavedStats.reset(context)
                    showReset = false
                    onBack()
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showReset = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = cs.onSurfaceVariant
            )
        }
    }
}
