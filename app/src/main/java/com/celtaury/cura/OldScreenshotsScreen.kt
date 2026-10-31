package com.celtaury.cura

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
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
import com.celtaury.cura.core.relativeDays
import com.celtaury.cura.core.rememberDeleteController
import kotlinx.coroutines.launch

@Composable
fun OldScreenshotsScreen(onBack: () -> Unit) {
    PermissionGate(
        title = "Old Screenshots",
        description = "We'll find screenshots that have been gathering dust for over 30 days. You review, we clean."
    ) {
        OldScreenshotsContent(onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OldScreenshotsContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var shots by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(setOf<String>()) }

    suspend fun rescan() {
        loading = true
        shots = Scanner.oldScreenshots(context, minAgeDays = 30)
        selected = shots.map { it.uri.toString() }.toSet() // pre-select all, user unchecks
        loading = false
    }

    LaunchedEffect(Unit) { rescan() }

    val selectedUris = shots.filter { it.uri.toString() in selected }.map { it.uri }
    val selectedBytes = shots.filter { it.uri.toString() in selected }.sumOf { it.sizeBytes }

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
                        Text("Old Screenshots", fontWeight = FontWeight.Bold)
                        Text("Older than 30 days", fontSize = 12.sp, color = cs.onSurfaceVariant)
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
            if (selected.isNotEmpty() && !loading) {
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
        if (loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = cs.primary)
                Spacer(Modifier.height(12.dp))
                Text("Looking for dust…", color = cs.onSurfaceVariant)
            }
        } else if (shots.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("📸✨", fontSize = 48.sp)
                Spacer(Modifier.height(8.dp))
                Text("No dusty screenshots. Nice!", color = cs.onSurfaceVariant)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${shots.size} found · ${humanBytes(shots.sumOf { it.sizeBytes })}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        if (selected.size == shots.size) "Uncheck all" else "Check all",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.primary,
                        modifier = Modifier.clickable {
                            selected = if (selected.size == shots.size) emptySet()
                            else shots.map { it.uri.toString() }.toSet()
                        }
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(shots, key = { it.uri.toString() }) { item ->
                        val checked = item.uri.toString() in selected
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    val key = item.uri.toString()
                                    selected = if (key in selected) selected - key
                                    else selected + key
                                }
                        ) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = item.displayName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    "${humanBytes(item.sizeBytes)} · ${relativeDays(item.dateAddedSec)}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    val key = item.uri.toString()
                                    selected = if (key in selected) selected - key
                                    else selected + key
                                },
                                modifier = Modifier.align(Alignment.TopEnd)
                            )
                        }
                    }
                }
            }
        }
    }
}
