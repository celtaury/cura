package com.celtaury.cura.ui.browse

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.celtaury.cura.core.DeckPrefs
import com.celtaury.cura.core.KeepSafe
import com.celtaury.cura.core.MediaItem
import com.celtaury.cura.core.PermissionGate
import com.celtaury.cura.core.SavedStats
import com.celtaury.cura.core.Scanner
import com.celtaury.cura.core.humanBytes
import com.celtaury.cura.core.monthLabelOf
import com.celtaury.cura.core.rememberConfirmableDelete
import com.celtaury.cura.ui.preview.MediaPreviewDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.celtaury.cura.R

/** DeckPrefs key so the browse queue survives leaving the screen. */
private const val BROWSE_ROUTE = "browse_gallery"

/**
 * Browse mode (#5 + #3): the gallery as month sections you can raid.
 * Same swipe engine as the deck, tiled — left 🧹 queues, right 🛡️ keeps safe,
 * tap the queued pill to purge the whole batch with the shared confirm sheet.
 */
@Composable
fun BrowseGalleryScreen(onBack: () -> Unit) {
    PermissionGate(
        title = stringResource(R.string.dash_open_browse),
        description = stringResource(R.string.browse_perm_desc)
    ) {
        BrowseContent(onBack = onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    var months by remember { mutableStateOf<List<Pair<String, List<MediaItem>>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var queued by remember { mutableStateOf(listOf<MediaItem>()) }
    var hidden by remember { mutableStateOf(setOf<String>()) }
    var previewItem by remember { mutableStateOf<MediaItem?>(null) }

    // Keep-safe undo, volley edition: keeps accumulate into ONE undoable group.
    // Each new keep restarts the snackbar timer with the running count; Undo
    // rolls the whole volley back, letting it expire commits.
    var pendingKeeps by remember { mutableStateOf(listOf<MediaItem>()) }
    var keepUndoJob by remember { mutableStateOf<Job?>(null) }

    fun keepItemSafe(item: MediaItem) {
        pendingKeeps = pendingKeeps + item
        KeepSafe.add(context, item.uri, "Browse Gallery")
        hidden = hidden + item.uri.toString()

        keepUndoJob?.cancel()
        snack.currentSnackbarData?.dismiss()
        keepUndoJob = scope.launch {
            val group = pendingKeeps
            val result = snack.showSnackbar(
                message = if (group.size == 1)
                    context.getString(R.string.common_saved_keepsafe, item.displayName)
                else
                    context.resources.getQuantityString(R.plurals.common_kept_safe_n, group.size, group.size),
                actionLabel = "Undo",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                group.forEach { KeepSafe.remove(context, it.uri) }
                hidden = hidden - group.map { it.uri.toString() }.toSet()
                pendingKeeps = emptyList()
                Toast.makeText(
                    context,
                    context.resources.getQuantityString(R.plurals.common_prot_removed_n, group.size, group.size),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                pendingKeeps = emptyList()
            }
        }
    }

    LaunchedEffect(Unit) {
        val groups = withContext(Dispatchers.IO) { Scanner.galleryByMonth(context) }
        val savedUris = DeckPrefs.queue(context, BROWSE_ROUTE).toSet()
        queued = groups.flatMap { it.second }.filter { it.uri.toString() in savedUris }
        months = groups
        loading = false
    }

    fun setQueued(item: MediaItem, want: Boolean) {
        queued = if (want) queued + item else queued.filterNot { it.uri == item.uri }
        DeckPrefs.setQueue(context, BROWSE_ROUTE, queued.map { it.uri.toString() })
    }

    val purge = rememberConfirmableDelete(onCleaned = {
        val bytes = queued.sumOf { it.sizeBytes }
        val count = queued.size
        SavedStats.addClean(context, bytes)
        keepUndoJob?.cancel()   // purged files are gone — a stale keep-undo must not un-hide them
        pendingKeeps = emptyList()
        hidden = hidden + queued.map { it.uri.toString() }.toSet()
        queued = emptyList()
        DeckPrefs.setQueue(context, BROWSE_ROUTE, emptyList())
        scope.launch {
            snack.showSnackbar(
                context.resources.getQuantityString(R.plurals.common_purged_n, count, count, humanBytes(bytes))
            )
        }
    })

    Scaffold(
        containerColor = cs.background,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.dash_open_browse), fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.browse_hint),
                                fontSize = 12.sp,
                                color = cs.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back_cd))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
                )
                if (queued.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .padding(start = 20.dp, bottom = 8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(cs.errorContainer)
                            .clickable { purge(queued) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.common_n_queued_broom, queued.size),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onErrorContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.common_tap_purge),
                            fontSize = 12.sp,
                            color = cs.onErrorContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    ) { padding ->
        when {
            loading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = cs.primary, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.browse_loading), color = cs.onSurfaceVariant)
            }

            months.all { it.second.isEmpty() } -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("🌿", fontSize = 52.sp)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.browse_empty), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.browse_empty_body),
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                months.forEach { (key, monthItems) ->
                    val visible = monthItems.filter { it.uri.toString() !in hidden }
                    if (visible.isEmpty()) return@forEach

                    item(key = "header_$key", span = { GridItemSpan(3) }) {
                        val queuedHere = visible.count { v -> queued.any { it.uri == v.uri } }
                        Column(Modifier.padding(top = 10.dp, bottom = 2.dp)) {
                            Text(
                                monthLabelOf(key),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                pluralStringResource(R.plurals.common_files_bytes, visible.size, visible.size, humanBytes(visible.sumOf { it.sizeBytes })) +
                                    if (queuedHere > 0) " · " + stringResource(R.string.common_n_queued, queuedHere) else "",
                                fontSize = 12.sp,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                    items(visible, key = { "tile_${it.uri}" }) { m ->
                        val isQueued = queued.any { it.uri == m.uri }
                        BrowseTile(
                            item = m,
                            isQueued = isQueued,
                            onSwipeLeft = { setQueued(m, !isQueued) },
                            onSwipeRight = { keepItemSafe(m) },
                            onTap = { previewItem = m }
                        )
                    }
                }
            }
        }
    }

    previewItem?.let { target ->
        val isQueued = queued.any { it.uri == target.uri }
        MediaPreviewDialog(
            item = target,
            isQueued = isQueued,
            onToggleQueue = { setQueued(target, !isQueued) },
            onKeepSafe = {
                keepItemSafe(target)
                previewItem = null
            },
            onDismiss = { previewItem = null }
        )
    }
}

@Composable
private fun BrowseTile(
    item: MediaItem,
    isQueued: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onTap: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val thresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val isVideo = item.mime.startsWith("video/")
    val isMedia = item.mime.startsWith("image/") || isVideo

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .graphicsLayer {
                translationX = offsetX.value
                rotationZ = (offsetX.value / 40f).coerceIn(-8f, 8f)
            }
            .background(
                when {
                    offsetX.value > 20f -> cs.tertiaryContainer
                    offsetX.value < -20f -> cs.errorContainer
                    else -> cs.surfaceVariant
                }
            )
            .clickable(onClick = onTap)
            .pointerInput(item.uri) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val x = offsetX.value
                        // Action first, fly-off in parallel — instant feedback
                        when {
                            x > thresholdPx -> {
                                onSwipeRight()
                                scope.launch {
                                    offsetX.animateTo(600f, tween(180))
                                    offsetX.snapTo(0f)
                                }
                            }
                            x < -thresholdPx -> {
                                onSwipeLeft()
                                scope.launch {
                                    offsetX.animateTo(-600f, tween(180))
                                    offsetX.snapTo(0f)
                                }
                            }
                            else -> scope.launch { offsetX.animateTo(0f, tween(180)) }
                        }
                    },
                    onDragCancel = { scope.launch { offsetX.snapTo(0f) } },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + amount) }
                    }
                )
            }
    ) {
        if (isMedia) {
            AsyncImage(
                model = item.uri,
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = item.displayName,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        if (isVideo) {
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = stringResource(R.string.common_preview_video_cd),
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.Center).size(30.dp)
            )
        }
        if (isQueued) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(cs.error)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text("🧹", fontSize = 11.sp)
            }
        }
    }
}
