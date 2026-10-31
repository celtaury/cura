package com.celtaury.cura

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.celtaury.cura.core.MediaItem
import com.celtaury.cura.core.PermissionGate
import com.celtaury.cura.core.SavedStats
import com.celtaury.cura.core.Scanner
import com.celtaury.cura.core.humanBytes
import com.celtaury.cura.core.rememberDeleteController
import kotlinx.coroutines.launch

@Composable
fun SocialCleanerScreen(onBack: () -> Unit) {
    PermissionGate(
        title = "Social Cleaner",
        description = "WhatsApp, Telegram and friends love to hoard media. See exactly what they're keeping, then clean what you don't need."
    ) {
        SocialCleanerContent(onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SocialCleanerContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var bucket by remember { mutableStateOf("All") }
    var selected by remember { mutableStateOf(setOf<String>()) }

    suspend fun rescan() {
        loading = true
        items = Scanner.whatsappMedia(context)
        selected = emptySet()
        loading = false
    }

    LaunchedEffect(Unit) { rescan() }

    val buckets = items.groupBy { Scanner.bucketOf(it) }
    val bucketNames = listOf("All") + buckets.keys.filter { it != "Other" }.sorted() +
        (if (buckets.containsKey("Other")) listOf("Other") else emptyList())

    val visible = if (bucket == "All") items else buckets[bucket] ?: emptyList()
    val selectedUris = visible.filter { it.uri.toString() in selected }.map { it.uri }
    val selectedBytes = visible.filter { it.uri.toString() in selected }.sumOf { it.sizeBytes }

    val delete = rememberDeleteController(onCleaned = {
        SavedStats.addClean(context, selectedBytes)
        Toast.makeText(context, "Freed ${humanBytes(selectedBytes)} 🌿", Toast.LENGTH_SHORT).show()
        scope.launch { rescan() }
    })

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Social Cleaner", fontWeight = FontWeight.Bold)
                        Text("Media hoarded by chat apps", fontSize = 12.sp,
                            color = cs.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
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
                            colors = ButtonDefaults.buttonColors(containerColor = cs.primary)
                        ) { Text("Clean 🧹") }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Totals card
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cs.tertiaryContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Chat app media found", style = MaterialTheme.typography.titleSmall,
                        color = cs.onTertiaryContainer)
                    Text(
                        "${items.size} files · ${humanBytes(items.sumOf { it.sizeBytes })}",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // Bucket chips with sizes
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(bucketNames) { name ->
                    val size = if (name == "All") items.sumOf { it.sizeBytes }
                    else buckets[name]?.sumOf { it.sizeBytes } ?: 0L
                    val count = if (name == "All") items.size else buckets[name]?.size ?: 0
                    FilterChip(
                        selected = bucket == name,
                        onClick = { bucket = name },
                        label = { Text(if (name == "All") "All" else "$name · ${humanBytes(size)} ($count)") }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            if (loading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = cs.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Auditing chat media…", color = cs.onSurfaceVariant)
                }
            } else if (visible.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("💬✨", fontSize = 44.sp)
                    Text("Nothing here. Your chats are tidy!",
                        color = cs.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    items(visible, key = { it.uri.toString() }) { item ->
                        val checked = item.uri.toString() in selected
                        val isMedia = item.mime.startsWith("image/") || item.mime.startsWith("video/")
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    val k = item.uri.toString()
                                    selected = if (k in selected) selected - k else selected + k
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isMedia) {
                                AsyncImage(
                                    model = item.uri,
                                    contentDescription = null,
                                    modifier = Modifier.size(52.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(52.dp)
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
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.displayName, maxLines = 1, fontWeight = FontWeight.Medium)
                                Text(
                                    "${humanBytes(item.sizeBytes)} · ${Scanner.bucketOf(item)}",
                                    fontSize = 12.sp, color = cs.onSurfaceVariant
                                )
                            }
                            Checkbox(checked = checked, onCheckedChange = {
                                val k = item.uri.toString()
                                selected = if (k in selected) selected - k else selected + k
                            })
                        }
                    }
                    item { Spacer(Modifier.height(110.dp)) }
                }
            }
        }
    }
}
