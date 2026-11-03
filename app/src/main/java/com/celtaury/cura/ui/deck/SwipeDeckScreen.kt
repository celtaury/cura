package com.celtaury.cura.ui.deck

import android.content.Context
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.celtaury.cura.core.DeckPrefs
import com.celtaury.cura.core.KeepSafe
import com.celtaury.cura.core.MediaItem
import com.celtaury.cura.core.PermissionGate
import com.celtaury.cura.core.SavedStats
import com.celtaury.cura.core.Scanner
import com.celtaury.cura.core.SuggestionEngine
import com.celtaury.cura.core.humanBytes
import com.celtaury.cura.core.monthLabelOf
import com.celtaury.cura.core.relativeDays
import com.celtaury.cura.core.rememberConfirmableDelete
import com.celtaury.cura.core.rememberDeleteController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * In-memory handoff for decks whose items are already known
 * (Quick Clean categories, Social Cleaner tabs). The caller fills this
 * right before navigating to the deck route.
 */
object DeckSource {
    var id: String = ""
    var items: List<MediaItem> = emptyList()

    fun set(id: String, items: List<MediaItem>) {
        this.id = id
        this.items = items
    }
}

private class DeckSpec(
    val title: String,
    val subtitle: String,
    val loader: (suspend (Context) -> List<MediaItem>)? = null
)

/** One calendar month inside a deck: its key and where its cards live. */
private data class MonthSlice(val key: String, val indexes: List<Int>)

/** Route → deck registry. loader == null → memory-backed via [DeckSource]. */
private val deckSpecs = mapOf(
    "deck_gallery" to DeckSpec("Gallery Review", "Oldest first · swipe to judge") { Scanner.galleryDeck(it) },
    "deck_oldmedia" to DeckSpec("Old Media", "Untouched for 60+ days") { Scanner.oldMedia(it) },
    "deck_empty" to DeckSpec("Empty Files", "Zero bytes, pure clutter"),
    "deck_apks" to DeckSpec("APK Installers", "Forgotten installer files"),
    "deck_dupes" to DeckSpec("Duplicate Photos", "Same picture, many copies"),
    "deck_shots" to DeckSpec("Old Screenshots", "Untouched for 30+ days"),
    "deck_qc_all" to DeckSpec("Quick Clean Review", "Everything the scan found"),
    "deck_soc_all" to DeckSpec("WhatsApp Media", "All types · swipe to judge"),
    "deck_soc_images" to DeckSpec("WhatsApp Images", "Swipe to judge"),
    "deck_soc_videos" to DeckSpec("WhatsApp Videos", "Swipe to judge"),
    "deck_soc_docs" to DeckSpec("WhatsApp Documents", "Deleting breaks chat media"),
    "deck_soc_audio" to DeckSpec("WhatsApp Audio", "Voice notes live here — care!"),
    "deck_soc_stickers" to DeckSpec("WhatsApp Stickers", "Tiny files · usually harmless")
)

/** Entry used by MainActivity for every "deck_" route. */
@Composable
fun DeckFlowForRoute(route: String, onBack: () -> Unit) {
    val spec = deckSpecs[route] ?: DeckSpec("Review", "Swipe to judge")
    DeckFlow(
        route = route,
        title = spec.title,
        subtitle = spec.subtitle,
        loader = spec.loader,
        onBack = onBack
    )
}

/** Public entry for tool screens that own their scan (Large Files, Old Screenshots). */
@Composable
fun DeckFlow(
    route: String,
    title: String,
    subtitle: String,
    loader: (suspend (Context) -> List<MediaItem>)?,
    onBack: () -> Unit
) {
    PermissionGate(
        title = title,
        description = "Cura needs media access to show things worth reviewing. " +
            "Nothing is touched without your say-so."
    ) {
        DeckContent(
            route = route,
            title = title,
            subtitle = subtitle,
            loader = loader,
            onBack = onBack
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeckContent(
    route: String,
    title: String,
    subtitle: String,
    loader: (suspend (Context) -> List<MediaItem>)?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var index by remember { mutableStateOf(0) }
    var queue by remember { mutableStateOf(listOf<MediaItem>()) }
    var phase by remember { mutableStateOf("loading") } // deck / summary / done / empty
    var history by remember { mutableStateOf(listOf<Pair<MediaItem, String>>()) }
    var resumedAt by remember { mutableStateOf(-1) }
    var previewUri by remember { mutableStateOf<Uri?>(null) }
    var sessionSaved by remember { mutableStateOf(0L) }
    var allTimeSaved by remember { mutableStateOf(0L) }
    var pickerOpen by remember { mutableStateOf(false) }

    // Per-month slices of the deck (#7): month key → where its cards live.
    val monthSlices = remember(items) {
        val byKey = LinkedHashMap<String, MutableList<Int>>()
        items.forEachIndexed { i, m ->
            byKey.getOrPut(Scanner.monthKeyOf(m.dateAddedSec)) { mutableListOf() }.add(i)
        }
        byKey.entries.sortedBy { it.key }
            .map { (k, idxs) -> MonthSlice(k, idxs.toList()) }
    }

    // ── load: scanner-backed or memory-backed, then resume position + queue ──
    LaunchedEffect(route) {
        val loaded = withContext(Dispatchers.IO) {
            loader?.invoke(context)
                ?.filter { !KeepSafe.contains(context, it.uri) }
                ?: (if (DeckSource.id == route) DeckSource.items else emptyList())
        }
        val savedIndex = DeckPrefs.index(context, route)
        val savedQueueUris = DeckPrefs.queue(context, route).toSet()
        val restoredQueue = loaded.filter { it.uri.toString() in savedQueueUris }
        index = savedIndex.coerceIn(0, loaded.size)
        queue = restoredQueue
        items = loaded
        resumedAt = if (savedIndex in 1 until loaded.size) savedIndex else -1
        loading = false
        phase = when {
            loaded.isEmpty() -> "empty"
            index >= loaded.size -> if (queue.isNotEmpty()) "summary" else "empty"
            else -> "deck"
        }
    }

    LaunchedEffect(phase) {
        if (phase == "done") {
            allTimeSaved = withContext(Dispatchers.IO) { SavedStats.savedBytes(context) }
        }
    }

    fun persist() {
        DeckPrefs.setIndex(context, route, index)
        DeckPrefs.setQueue(context, route, queue.map { it.uri.toString() })
    }

    fun decide(item: MediaItem, action: String) {
        SuggestionEngine.observe(item, action)   // Batch 4: feed the leaf-tile brain
        if (action == "kept") {
            KeepSafe.add(context, item.uri)
        } else {
            queue = queue + item
        }
        history = history + (item to action)
        index += 1
        persist()
        phase = if (index >= items.size) {
            if (queue.isNotEmpty()) "summary" else "empty"
        } else "deck"
    }

    fun undo() {
        val last = history.lastOrNull() ?: return
        history = history.dropLast(1)
        index -= 1
        if (last.second == "queued") {
            queue = queue.filterNot { it.uri == last.first.uri }
        } else {
            KeepSafe.remove(context, last.first.uri)
        }
        persist()
        phase = "deck"
    }

    val delete = rememberDeleteController(onCleaned = {
        val bytes = queue.sumOf { it.sizeBytes }
        SavedStats.addClean(context, bytes)
        sessionSaved += bytes
        DeckPrefs.clear(context, route)
        phase = "done"
    })

    // Mid-deck purge: tap the queued pill → confirm sheet → blast, keep judging.
    val purge = rememberConfirmableDelete(onCleaned = {
        val bytes = queue.sumOf { it.sizeBytes }
        val count = queue.size
        SavedStats.addClean(context, bytes)
        sessionSaved += bytes
        queue = emptyList()
        history = emptyList()
        DeckPrefs.setQueue(context, route, emptyList())
        scope.launch {
            snack.showSnackbar(
                "Purged $count item${if (count == 1) "" else "s"} · ${humanBytes(bytes)} freed 🎉"
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
                            Text(title, fontWeight = FontWeight.Bold)
                            Text(subtitle, fontSize = 12.sp, color = cs.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
                )
                if (phase == "deck" && items.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = { index.toFloat() / items.size },
                            modifier = Modifier.weight(1f),
                            color = cs.primary,
                            trackColor = cs.surfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            ("${index + 1} / ${items.size}" +
                                if (queue.isNotEmpty()) " · ${queue.size} queued" else ""),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurfaceVariant
                        )
                    }
                    if (monthSlices.size > 1) {
                        val curKey = items.getOrNull(index)
                            ?.let { Scanner.monthKeyOf(it.dateAddedSec) }
                        val curSlice = monthSlices.firstOrNull { s -> s.key == curKey }
                        val curLeft = curSlice?.indexes?.count { p -> p >= index } ?: 0
                        val curLabel = curSlice?.let { s -> monthLabelOf(s.key) } ?: ""
                        Row(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(cs.surfaceVariant)
                                .clickable { pickerOpen = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "📅 $curLabel · $curLeft left ▾",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    ) { padding ->
        when (phase) {
            "loading" -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = cs.primary, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(14.dp))
                Text("Gathering things to review…", color = cs.onSurfaceVariant)
            }

            "empty" -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("🌿", fontSize = 52.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (sessionSaved > 0L) "Deck cleared!" else "All clean ✨",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    "Nothing left to judge here.",
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onBack, shape = MaterialTheme.shapes.large) {
                    Text("Back home")
                }
            }

            "deck" -> DeckPhase(
                items = items,
                index = index,
                queueSize = queue.size,
                resumedAt = resumedAt,
                canUndo = history.isNotEmpty(),
                onDecide = ::decide,
                onUndo = ::undo,
                onPreview = { previewUri = it },
                onPurge = { purge(queue) }
            )

            "summary" -> SummaryPhase(
                queued = queue,
                onConfirm = { delete(queue.map { it.uri }) },
                onLater = onBack
            )

            "done" -> DonePhase(
                sessionSaved = sessionSaved,
                allTimeSaved = allTimeSaved,
                onBack = onBack
            )
        }
    }

    previewUri?.let { uri ->
        VideoPreviewSheet(videoUri = uri, onDismiss = { previewUri = null })
    }

    if (pickerOpen) {
        MonthPickerSheet(
            slices = monthSlices,
            currentIndex = index,
            currentKey = items.getOrNull(index)?.let { Scanner.monthKeyOf(it.dateAddedSec) },
            onPick = { slice ->
                pickerOpen = false
                slice.indexes.filter { p -> p >= index }.minOrNull()?.let { target ->
                    index = target
                    persist()
                }
            },
            onDismiss = { pickerOpen = false }
        )
    }
}

// ───────────────────────── deck phase ─────────────────────────

@Composable
private fun DeckPhase(
    items: List<MediaItem>,
    index: Int,
    queueSize: Int,
    resumedAt: Int,
    canUndo: Boolean,
    onDecide: (MediaItem, String) -> Unit,
    onUndo: () -> Unit,
    onPreview: (Uri) -> Unit,
    onPurge: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val current = items.getOrNull(index)

    Column(Modifier.fillMaxSize()) {
        if (resumedAt in 1 until items.size && index == resumedAt) {
            Text(
                "Resumed where you left off · card #${index + 1}",
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
        if (queueSize > 0) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(cs.errorContainer)
                    .clickable(onClick = onPurge)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$queueSize queued 🧹",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onErrorContainer
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "tap to purge",
                    fontSize = 12.sp,
                    color = cs.onErrorContainer.copy(alpha = 0.7f)
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            // next cards peeking behind
            for (depth in 2 downTo 1) {
                val next = items.getOrNull(index + depth)
                if (next != null) {
                    DeckCard(
                        item = next,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .aspectRatio(0.72f)
                            .graphicsLayer {
                                val shrink = 1f - 0.05f * depth
                                scaleX = shrink
                                scaleY = shrink
                                translationY = 40f * depth
                                alpha = 1f - 0.25f * depth
                            },
                        interactive = false,
                        onDecide = { _, _ -> },
                        onPreview = onPreview
                    )
                }
            }
            if (current != null) {
                key(index) {
                    DeckCard(
                        item = current,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .aspectRatio(0.72f),
                        interactive = true,
                        onDecide = onDecide,
                        onPreview = onPreview
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { current?.let { onDecide(it, "kept") } },
                modifier = Modifier.weight(1f).height(54.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.tertiaryContainer,
                    contentColor = cs.onTertiaryContainer
                )
            ) { Text("🛡️ Keep", fontWeight = FontWeight.Bold) }
            TextButton(onClick = onUndo, enabled = canUndo && index > 0) {
                Text("↺ Undo")
            }
            Button(
                onClick = { current?.let { onDecide(it, "queued") } },
                modifier = Modifier.weight(1f).height(54.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.errorContainer,
                    contentColor = cs.onErrorContainer
                )
            ) { Text("🧹 Clean", fontWeight = FontWeight.Bold) }
        }
        Text(
            "Swipe right 🛡️ to keep safe · swipe left 🧹 to queue for cleaning",
            fontSize = 11.sp,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp)
        )
    }
}

@Composable
private fun DeckCard(
    item: MediaItem,
    modifier: Modifier = Modifier,
    interactive: Boolean,
    onDecide: (MediaItem, String) -> Unit,
    onPreview: (Uri) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val thresholdPx = with(LocalDensity.current) { 130.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val isVideo = item.mime.startsWith("video/")
    val isMedia = item.mime.startsWith("image/") || isVideo

    val cardModifier = if (interactive) {
        modifier
            .graphicsLayer {
                translationX = offsetX.value
                rotationZ = (offsetX.value / 40f).coerceIn(-10f, 10f)
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val x = offsetX.value
                        scope.launch {
                            when {
                                x > thresholdPx -> {
                                    offsetX.animateTo(2200f, tween(230))
                                    onDecide(item, "kept")
                                }
                                x < -thresholdPx -> {
                                    offsetX.animateTo(-2200f, tween(230))
                                    onDecide(item, "queued")
                                }
                                else -> offsetX.animateTo(0f, spring())
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offsetX.animateTo(0f, spring()) } },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + amount) }
                    }
                )
            }
    } else modifier

    Box(cardModifier.clip(RoundedCornerShape(28.dp))) {
        // colored reveal behind the card while dragging
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    when {
                        offsetX.value > 20f -> cs.tertiaryContainer
                        offsetX.value < -20f -> cs.errorContainer
                        else -> cs.surface
                    }
                )
        )
        // card content fades out as the swipe reveal takes over
        Column(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    alpha = when {
                        offsetX.value > 20f ->
                            1f - (offsetX.value / thresholdPx).coerceIn(0f, 1f) * 0.9f
                        offsetX.value < -20f ->
                            1f - (kotlin.math.abs(offsetX.value) / thresholdPx).coerceIn(0f, 1f) * 0.9f
                        else -> 1f
                    }
                }
                .background(cs.surface)
        ) {
            // media area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(cs.surfaceVariant)
            ) {
                if (isMedia) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (isVideo) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { onPreview(item.uri) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayCircle,
                                contentDescription = "Preview video",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No visual preview",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            // info strip
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(item.displayName, maxLines = 1, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${humanBytes(item.sizeBytes)} · ${relativeDays(item.dateAddedSec)}" +
                        if (!isMedia) " · file" else "",
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant
                )
            }
        }
        // swipe hints
        if (offsetX.value > 20f) {
            SwipeHint(
                text = "KEEP SAFE 🛡️",
                color = cs.onTertiaryContainer,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(20.dp),
                alpha = (offsetX.value / thresholdPx).coerceIn(0f, 1f)
            )
        }
        if (offsetX.value < -20f) {
            SwipeHint(
                text = "CLEAN 🧹",
                color = cs.onErrorContainer,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(20.dp),
                alpha = (kotlin.math.abs(offsetX.value) / thresholdPx).coerceIn(0f, 1f)
            )
        }
    }
}

@Composable
private fun SwipeHint(text: String, color: Color, modifier: Modifier = Modifier, alpha: Float) {
    Text(
        text,
        color = color,
        fontWeight = FontWeight.Black,
        fontSize = 22.sp,
        modifier = modifier.graphicsLayer { this.alpha = alpha }
    )
}

// ───────────────────────── month picker (#7) ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthPickerSheet(
    slices: List<MonthSlice>,
    currentIndex: Int,
    currentKey: String?,
    onPick: (MonthSlice) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(
                "Jump to a month",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Per-month progress · tap a month to jump to its first un-judged card",
                fontSize = 12.sp,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(slices, key = { it.key }) { slice ->
                    val left = slice.indexes.count { p -> p >= currentIndex }
                    val isCurrent = slice.key == currentKey
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCurrent) cs.primaryContainer else Color.Transparent)
                            .clickable(enabled = left > 0 && !isCurrent) { onPick(slice) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            monthLabelOf(slice.key),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            when {
                                isCurrent -> "you are here"
                                left > 0 -> "$left to judge"
                                else -> "✓ judged"
                            },
                            fontSize = 12.sp,
                            color = if (left > 0 && !isCurrent) cs.primary else cs.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// ───────────────────────── summary phase ─────────────────────────

@Composable
private fun SummaryPhase(
    queued: List<MediaItem>,
    onConfirm: () -> Unit,
    onLater: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val bytes = queued.sumOf { it.sizeBytes }
    val risky = queued.any {
        !(it.mime.startsWith("image/") || it.mime.startsWith("video/"))
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Text("🧺", fontSize = 56.sp)
        Spacer(Modifier.height(14.dp))
        Text("Ready when you are", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "${queued.size} item${if (queued.size == 1) "" else "s"} · ~${humanBytes(bytes)}",
            style = MaterialTheme.typography.titleLarge,
            color = cs.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(queued, key = { it.uri.toString() }) { item ->
                val isMedia = item.mime.startsWith("image/") || item.mime.startsWith("video/")
                if (isMedia) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(14.dp))
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
            }
        }
        if (risky) {
            Spacer(Modifier.height(14.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cs.errorContainer)
            ) {
                Text(
                    "⚠️ Documents, audio or stickers are in this batch. Deleting them " +
                        "can show \"media deleted\" inside your chats.",
                    fontSize = 13.sp,
                    color = cs.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onConfirm,
            enabled = queued.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.large
        ) { Text("Confirm & Clean 🧹", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onLater, modifier = Modifier.padding(top = 6.dp)) {
            Text("Keep for later — your queue is saved")
        }
        Spacer(Modifier.height(18.dp))
    }
}

// ───────────────────────── done phase ─────────────────────────

@Composable
private fun DonePhase(sessionSaved: Long, allTimeSaved: Long, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var pop by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { pop = true }
    val scale by animateFloatAsState(
        targetValue = if (pop) 1f else 0.4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "doneScale"
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = cs.tertiary,
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        )
        Spacer(Modifier.height(16.dp))
        Text("Cleaned! 🎉", style = MaterialTheme.typography.headlineMedium)
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
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.large
        ) { Text("Done", fontWeight = FontWeight.Bold) }
    }
}

// ───────────────────────── video preview ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoPreviewSheet(videoUri: Uri, onDismiss: () -> Unit) {
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
