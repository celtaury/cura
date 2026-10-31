package com.celtaury.cura

import android.app.Activity
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.celtaury.cura.core.MediaItem
import com.celtaury.cura.core.KeepSafe
import com.celtaury.cura.core.PermissionGate
import com.celtaury.cura.core.SavedStats
import com.celtaury.cura.core.Scanner
import com.celtaury.cura.core.humanBytes
import com.celtaury.cura.core.relativeDays
import com.celtaury.cura.core.rememberDeleteController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LargeFilesScreen(onBack: () -> Unit) {
    PermissionGate(
        title = "Welcome to Cura",
        description = "We need media access to find the big files hiding in your storage. Nothing is touched without your say-so."
    ) {
        LargeFilesContent(onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LargeFilesContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var files by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var filter by remember { mutableStateOf("all") }
    var previewUri by remember { mutableStateOf<Uri?>(null) }

    suspend fun rescan() {
        loading = true
        files = Scanner.largeFiles(context, minBytes = 30L * 1024 * 1024)
        loading = false
    }

    LaunchedEffect(Unit) { rescan() }

    val delete = rememberDeleteController(onCleaned = {
        val bytes = files.filter { it.uri.toString() in selected }.sumOf { it.sizeBytes }
        SavedStats.addClean(context, bytes)
        Toast.makeText(context, "Freed ${humanBytes(bytes)} 🌿", Toast.LENGTH_SHORT).show()
        scope.launch {
            selected = emptySet()
            rescan()
        }
    })

    val filtered = files.filter { item ->
        when (filter) {
            "video" -> item.mime.startsWith("video/")
            "image" -> item.mime.startsWith("image/")
            "audio" -> item.mime.startsWith("audio/")
            else -> true
        }
    }
    val selectedUris = files.filter { it.uri.toString() in selected }.map { it.uri }
    val selectedBytes = files.filter { it.uri.toString() in selected }.sumOf { it.sizeBytes }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Large Files", fontWeight = FontWeight.Bold)
                        Text("30 MB and up · swipe to judge",
                            fontSize = 12.sp, color = cs.onSurfaceVariant)
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
                            Text("${selected.size} picked", fontWeight = FontWeight.SemiBold)
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
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        ) {
            // Filter chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("all" to "All", "image" to "Photos", "video" to "Videos",
                    "audio" to "Audio")) { (key, label) ->
                    FilterChip(
                        selected = filter == key,
                        onClick = { filter = key },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // Hero card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cs.secondaryContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Large files found", style = MaterialTheme.typography.titleSmall,
                        color = cs.onSecondaryContainer)
                    Text(
                        "${filtered.size} files · ${humanBytes(filtered.sumOf { it.sizeBytes })}",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        "Swipe right 🛡️ to keep safe · swipe left 🧹 to pick for cleaning",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            if (loading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = cs.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Analyzing storage…", color = cs.onSurfaceVariant)
                }
            } else if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("🌿", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("No large files here. You're clean!",
                        color = cs.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.uri.toString() }) { item ->
                        LargeFileRow(
                            item = item,
                            checked = item.uri.toString() in selected,
                            onToggle = {
                                val key = item.uri.toString()
                                selected = if (key in selected) selected - key else selected + key
                            },
                            onKeep = {
                                KeepSafe.add(context, item.uri)
                                files = files.filterNot { it.uri == item.uri }
                                selected = selected - item.uri.toString()
                                Toast.makeText(context, "Kept safe 🛡️", Toast.LENGTH_SHORT).show()
                            },
                            onPreview = { previewUri = item.uri }
                        )
                        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }
    }

    previewUri?.let { uri ->
        VideoPreviewSheet(videoUri = uri, onDismiss = { previewUri = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LargeFileRow(
    item: MediaItem,
    checked: Boolean,
    onToggle: () -> Unit,
    onKeep: () -> Unit,
    onPreview: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val isVideo = item.mime.startsWith("video/")

    // rememberUpdatedState keeps actions fresh inside the dismiss state lambda
    val currentToggle by rememberUpdatedState(onToggle)
    val currentKeep by rememberUpdatedState(onKeep)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { currentKeep(); false }
                SwipeToDismissBoxValue.EndToStart -> { currentToggle(); false }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        when (dismissState.targetValue) {
                            SwipeToDismissBoxValue.StartToEnd -> cs.tertiaryContainer
                            else -> cs.errorContainer
                        }
                    ),
                contentAlignment = if (dismissState.targetValue ==
                    SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart
                else Alignment.CenterEnd
            ) {
                Text(
                    if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd)
                        "🛡️ Keep safe" else "🧹 Pick",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(cs.surface)
                .clickable(onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail (play icon ON the image now — fixed overlay)
            Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (isVideo) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .clickable(onClick = onPreview),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = "Preview video",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.displayName, maxLines = 1, fontWeight = FontWeight.Medium)
                Text(
                    "${humanBytes(item.sizeBytes)} · ${relativeDays(item.dateAddedSec)}",
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant
                )
            }
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoPreviewSheet(videoUri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Preview", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(14.dp))
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close Preview")
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
