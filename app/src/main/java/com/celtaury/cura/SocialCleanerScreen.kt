package com.celtaury.cura

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.celtaury.cura.core.DeleteQueuePrefs
import com.celtaury.cura.core.DeckPrefs
import com.celtaury.cura.core.KeepSafe
import com.celtaury.cura.core.MediaItem
import com.celtaury.cura.core.PermissionGate
import com.celtaury.cura.core.SavedStats
import com.celtaury.cura.core.Scanner
import com.celtaury.cura.core.SuggestionEngine
import com.celtaury.cura.core.humanBytes
import com.celtaury.cura.core.relativeDays
import com.celtaury.cura.core.rememberConfirmableDelete
import com.celtaury.cura.ui.deck.DeckCard
import com.celtaury.cura.ui.deck.ViewModeToggle
import com.celtaury.cura.ui.preview.MediaPreviewDialog
import com.celtaury.cura.ui.theme.InterBlack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/** Social type chips — Scanner keys stay English, only the label translates. */
private fun typeLabelRes(type: String): Int = when (type) {
    "All" -> R.string.common_all
    "Images" -> R.string.soc_type_images
    "Videos" -> R.string.soc_type_videos
    "Audio" -> R.string.soc_type_audio
    "Docs" -> R.string.soc_type_docs
    "Stickers" -> R.string.soc_type_stickers
    else -> R.string.common_all
}

/**
 * Social Cleaner with Material 3 Expressive UI:
 * - Checkmarks removed entirely for a cleaner, modern look
 * - Swiping left queues the item into the delete list (with expressive animations)
 * - Swiping right saves/protects the item to Keep-safe (with instant Undo action)
 * - Clicking thumbnail opens instant Apple Quick Look-style media preview
 * - Persistent delete queue saved across app restarts
 * - Persistent floating bar with "Delete selected" and confirmation dialog
 */
/** One Keep-safe action that can still be undone (burst-style, list edition).
 *  Named per-file (Social…) — K2 flags same-name top-level classes as redeclaration
 *  even when private, so every file owns a uniquely-named type. */
private data class SocialPendingKeep(val item: MediaItem, val wasQueued: Boolean)

@Composable
fun SocialCleanerScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    PermissionGate(
        title = stringResource(R.string.soc_title),
        description = stringResource(R.string.soc_desc)
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
    val snackbarHostState = remember { SnackbarHostState() }

    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedType by remember { mutableStateOf("All") }
    var queuedUris by remember { mutableStateOf(DeleteQueuePrefs.getQueue(context, "social")) }
    var previewItem by remember { mutableStateOf<MediaItem?>(null) }
    // Resolved at composition for non-composable callbacks (snackbar labels).
    val undoLabel = stringResource(R.string.common_btn_undo)

    // List ⇄ deck view (Batch H) + deck cursor state. The cursor never
    // advances by hand: every judgement removes the current card from the
    // pool (keep → the item leaves `items`; clean → the uri joins
    // deckJudged), so the next card slides into place on its own.
    var deckView by remember { mutableStateOf(DeckPrefs.isDeck(context, "social")) }
    var deckCursor by remember { mutableStateOf(0) }
    var deckJudged by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(Unit) {
        items = withContext(Dispatchers.IO) {
            Scanner.whatsappMedia(context)
        }
        loading = false
    }

    // Persist delete queue so selections survive app exits
    LaunchedEffect(queuedUris) {
        DeleteQueuePrefs.setQueue(context, "social", queuedUris)
    }

    // Batch H: a different type filter re-deals the deck.
    LaunchedEffect(selectedType) {
        deckCursor = 0
    }

    // ── Keep Safe undo, volley edition ──
    // One undo slot that ACCUMULATES: every keep joins the currently-undoable
    // group and the snackbar restarts its timer showing the running count.
    // Tapping Undo rolls the whole volley back; letting it expire commits.
    // (Before: each new snackbar silently killed the previous one's Undo, so
    // two quick swipes right left you with one undoable item and one stuck.)
    var pendingKeeps by remember { mutableStateOf(listOf<SocialPendingKeep>()) }
    var keepBaseItems by remember { mutableStateOf<List<MediaItem>?>(null) }
    var keepUndoJob by remember { mutableStateOf<Job?>(null) }

    fun commitKeeps() {
        keepUndoJob?.cancel()
        pendingKeeps = emptyList()
        keepBaseItems = null
    }

    fun keepItemSafe(item: MediaItem) {
        if (keepBaseItems == null) keepBaseItems = items   // first keep of this volley: remember the list
        pendingKeeps = pendingKeeps + SocialPendingKeep(item, item.uri.toString() in queuedUris)
        KeepSafe.add(context, item.uri, "WhatsApp")
        SuggestionEngine.observe(item, "kept")
        items = items.filter { it.uri != item.uri }
        queuedUris = queuedUris - item.uri.toString()

        keepUndoJob?.cancel()                              // old waiter dies; its items stay pending
        snackbarHostState.currentSnackbarData?.dismiss()
        keepUndoJob = scope.launch {
            val group = pendingKeeps
            val result = snackbarHostState.showSnackbar(
                message = if (group.size == 1)
                    context.getString(R.string.common_saved_keepsafe, item.displayName)
                else
                    context.resources.getQuantityString(R.plurals.common_kept_safe_n, group.size, group.size),
                actionLabel = undoLabel,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                group.forEach { KeepSafe.remove(context, it.item.uri) }
                items = keepBaseItems ?: items             // exact original list, original positions
                group.forEach { pk ->
                    if (pk.wasQueued) queuedUris = queuedUris + pk.item.uri.toString()
                }
                pendingKeeps = emptyList()
                keepBaseItems = null
                Toast.makeText(
                    context,
                    context.resources.getQuantityString(R.plurals.common_prot_removed_n, group.size, group.size),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                pendingKeeps = emptyList()
                keepBaseItems = null
            }
        }
    }

    // Confirmable delete with preview sheet
    val confirmDelete = rememberConfirmableDelete(
        onCleaned = { cleaned ->
            commitKeeps()   // cleaned items are gone for good — a stale keep-undo must not resurrect them
            // Only what Android actually deleted leaves the screen and the
            // counter; denied items stay listed AND queued for another try.
            val granted = cleaned.map { it.uri.toString() }.toSet()
            val freedBytes = cleaned.sumOf { it.sizeBytes }

            if (freedBytes > 0L) {
                SavedStats.addClean(context, freedBytes)
            }

            items = items.filter { it.uri.toString() !in granted }
            queuedUris = queuedUris - granted
            Toast.makeText(context, context.getString(R.string.common_cleaned_ok), Toast.LENGTH_SHORT).show()
        }
    )

    val buckets = remember(items) {
        items.groupBy { Scanner.socialTypeOf(it) }
    }

    val availableTypes = remember(buckets) {
        val standard = listOf("Images", "Videos", "Audio", "Docs")
        val result = mutableListOf("All")
        standard.forEach { type ->
            if (buckets[type]?.isNotEmpty() == true) {
                result.add(type)
            }
        }
        if (buckets["Stickers"]?.isNotEmpty() == true) {
            result.add("Stickers")
        }
        result
    }

    val displayedItems = remember(items, selectedType) {
        if (selectedType == "All") {
            items
        } else {
            items.filter { Scanner.socialTypeOf(it) == selectedType }
        }
    }

    val queuedItemsList = remember(displayedItems, queuedUris) {
        displayedItems.filter { it.uri.toString() in queuedUris }
    }
    val queuedBytes = queuedItemsList.sumOf { it.sizeBytes }
    val totalBytes = items.sumOf { it.sizeBytes }

    Scaffold(
        containerColor = cs.background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 76.dp)
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.soc_title),
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterBlack
                        )
                        Text(
                            stringResource(R.string.soc_hint),
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
                actions = {
                    if (!loading && items.isNotEmpty()) {
                        ViewModeToggle(route = "social") { deckView = it }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { padding ->
        Crossfade(targetState = loading, label = "social_loading") { isLoading ->
            if (isLoading) {
                Column(
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
                    Text(
                        stringResource(R.string.soc_loading),
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.common_ks_never),
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant
                    )
                }
            } else if (items.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("💬✨", fontSize = 54.sp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.soc_tidy),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.soc_none),
                        color = cs.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onBack,
                        shape = CircleShape
                    ) {
                        Text(stringResource(R.string.common_back_home))
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

                        // ── Type Filter Chips ──
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(availableTypes) { type ->
                                val count = if (type == "All") items.size else buckets[type]?.size ?: 0
                                FilterChip(
                                    selected = selectedType == type,
                                    onClick = { selectedType = type },
                                    label = { Text("${stringResource(typeLabelRes(type))} ($count)") },
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
                                        pluralStringResource(R.plurals.soc_holding_n, items.size, items.size),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = cs.onSurface
                                    )
                                    Text(
                                        stringResource(R.string.soc_total_sel, humanBytes(totalBytes), queuedUris.size),
                                        fontSize = 12.sp,
                                        color = if (queuedUris.isNotEmpty()) cs.error else cs.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        // Union / subtract the SHOWN items only —
                                        // never wipe queued items from other tabs.
                                        val shown = displayedItems.map { it.uri.toString() }.toSet()
                                        queuedUris = if (shown.isNotEmpty() && shown.all { it in queuedUris }) {
                                            queuedUris - shown
                                        } else {
                                            queuedUris + shown
                                        }
                                    },
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        if (displayedItems.isNotEmpty() && displayedItems.all { it.uri.toString() in queuedUris })
                                            stringResource(R.string.common_clear_queue)
                                        else
                                            stringResource(R.string.common_queue_all),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // ── Items list (list mode): swipe left queue / right save ──
                        if (!deckView) {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 96.dp)
                        ) {
                            items(displayedItems, key = { it.uri.toString() }) { item ->
                                val isQueued = item.uri.toString() in queuedUris

                                SwipeActionItem(
                                    isQueued = isQueued,
                                    onSwipeLeft = {
                                        // Swipe Left: add to delete queue (or toggle off if already queued)
                                        queuedUris = if (isQueued) {
                                            queuedUris - item.uri.toString()
                                        } else {
                                            queuedUris + item.uri.toString()
                                        }
                                    },
                                    onSwipeRight = {
                                        keepItemSafe(item)
                                    }
                                ) {
                                    val animatedBgColor by animateColorAsState(
                                        targetValue = if (isQueued)
                                            cs.errorContainer.copy(alpha = 0.35f)
                                        else
                                            cs.surface,
                                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                                        label = "cardBgColor"
                                    )
                                    val animatedBorderColor by animateColorAsState(
                                        targetValue = if (isQueued)
                                            cs.error.copy(alpha = 0.65f)
                                        else
                                            cs.outlineVariant.copy(alpha = 0.35f),
                                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                                        label = "cardBorderColor"
                                    )
                                    val animatedCornerRadius by animateDpAsState(
                                        targetValue = if (isQueued) 22.dp else 18.dp,
                                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                                        label = "cardCornerRadius"
                                    )

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                queuedUris = if (isQueued) {
                                                    queuedUris - item.uri.toString()
                                                } else {
                                                    queuedUris + item.uri.toString()
                                                }
                                            },
                                        shape = RoundedCornerShape(animatedCornerRadius),
                                        colors = CardDefaults.cardColors(containerColor = animatedBgColor),
                                        border = BorderStroke(1.dp, animatedBorderColor)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Thumbnail (clickable for instant Apple Quick Look preview)
                                            val type = Scanner.socialTypeOf(item)
                                            val isVisual = type == "Images" || type == "Videos" || type == "Stickers"
                                            if (isVisual) {
                                                AsyncImage(
                                                    model = item.uri,
                                                    contentDescription = item.displayName,
                                                    modifier = Modifier
                                                        .size(56.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .clickable { previewItem = item },
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else if (type == "Audio") {
                                                Box(
                                                    modifier = Modifier
                                                        .size(56.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(cs.secondaryContainer)
                                                        .clickable { previewItem = item },
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
                                                        .clickable { previewItem = item },
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
                                                    item.displayName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = cs.onSurface
                                                )
                                                Spacer(Modifier.height(3.dp))
                                                Text(
                                                    stringResource(R.string.soc_item_meta, humanBytes(item.sizeBytes), relativeDays(item.dateAddedSec), stringResource(typeLabelRes(type))),
                                                    fontSize = 12.sp,
                                                    color = cs.onSurfaceVariant
                                                )
                                            }

                                            // Material 3 Expressive Queued Badge
                                            AnimatedVisibility(
                                                visible = isQueued,
                                                enter = scaleIn(spring(dampingRatio = 0.6f)) + fadeIn(),
                                                exit = scaleOut(spring(dampingRatio = 0.8f)) + fadeOut()
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .padding(start = 8.dp)
                                                        .clip(CircleShape)
                                                        .background(cs.errorContainer)
                                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Delete,
                                                            contentDescription = stringResource(R.string.prev_queued_short),
                                                            tint = cs.onErrorContainer,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Text(
                                                            stringResource(R.string.prev_queued_short),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = cs.onErrorContainer
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        } else {
                            // ── Deck mode (Batch H): judge this tab as cards —
                            // same height-derived sizing as every deck.
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                val cardHeight = minOf(maxHeight - 24.dp, (maxWidth - 48.dp) / 0.72f)
                                val deckCardWidth = cardHeight * 0.72f
                                val deckItems = displayedItems.filter { it.uri.toString() !in deckJudged }
                                val currentCard = deckItems.getOrNull(deckCursor)
                                if (currentCard == null) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text("✨", fontSize = 44.sp)
                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            stringResource(R.string.soc_deck_done),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            stringResource(R.string.soc_deck_done_hint),
                                            fontSize = 12.sp,
                                            color = cs.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    // next two peeking behind — same stack as every deck
                                    for (depth in 2 downTo 1) {
                                        val next = deckItems.getOrNull(deckCursor + depth)
                                        if (next != null) {
                                            DeckCard(
                                                item = next,
                                                modifier = Modifier
                                                    .width(deckCardWidth)
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
                                                onPreview = { previewItem = it }
                                            )
                                        }
                                    }
                                    key(currentCard.uri) {
                                        DeckCard(
                                            item = currentCard,
                                            modifier = Modifier
                                                .width(deckCardWidth)
                                                .aspectRatio(0.72f),
                                            interactive = true,
                                            onDecide = { item, action ->
                                                if (action == "kept") {
                                                    // keepItemSafe drops the item from the
                                                    // pool itself; the volley-undo snackbar
                                                    // still rolls it back into place
                                                    keepItemSafe(item)
                                                } else {
                                                    // the card leaves the deck by joining the
                                                    // judged set; the action bar's
                                                    // Delete-selected button purges it
                                                    deckJudged = deckJudged + item.uri.toString()
                                                    queuedUris = queuedUris + item.uri.toString()
                                                }
                                            },
                                            onPreview = { previewItem = it }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Persistent Floating Action Bar (Delete Queued / Queue All) ──
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth()
                            .shadow(14.dp, CircleShape),
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
                                    if (queuedUris.isNotEmpty())
                                        stringResource(R.string.common_n_queued, queuedUris.size)
                                    else
                                        pluralStringResource(R.plurals.common_items_n, displayedItems.size, displayedItems.size),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (queuedUris.isNotEmpty()) cs.error else cs.onSurface
                                )
                                Text(
                                    if (queuedUris.isNotEmpty())
                                        humanBytes(queuedBytes)
                                    else
                                        humanBytes(displayedItems.sumOf { it.sizeBytes }),
                                    fontSize = 12.sp,
                                    color = cs.onSurfaceVariant
                                )
                            }

                            if (queuedUris.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        confirmDelete(queuedItemsList)
                                    },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = cs.error
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.common_delete_btn),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.soc_delete_selected, queuedUris.size),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Button(
                                    onClick = {
                                        queuedUris = displayedItems.map { it.uri.toString() }.toSet()
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
                                    Text(stringResource(R.string.common_select_all), fontWeight = FontWeight.Bold)
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
            isQueued = target.uri.toString() in queuedUris,
            onToggleQueue = {
                val uriStr = target.uri.toString()
                queuedUris = if (uriStr in queuedUris) queuedUris - uriStr else queuedUris + uriStr
            },
            onKeepSafe = {
                keepItemSafe(target)
                previewItem = null
            },
            onDismiss = { previewItem = null }
        )
    }
}

/**
 * Material 3 Expressive swipable item container:
 * - Expressive bouncy springs with dynamic icon scaling and opacity
 * - Swipe left: reveals red Queue Delete background with smooth icon expansion
 * - Swipe right: reveals green Keep Safe background with smooth icon expansion
 */
@Composable
private fun SwipeActionItem(
    isQueued: Boolean,
    modifier: Modifier = Modifier,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    content: @Composable () -> Unit
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thresholdPx = with(density) { 85.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
    ) {
        val currentOffset = offsetX.value
        val progress = (abs(currentOffset) / thresholdPx).coerceIn(0f, 1.4f)
        val iconScale = (0.6f + progress * 0.45f).coerceIn(0.6f, 1.15f)
        val alpha = progress.coerceIn(0f, 1f)

        if (currentOffset > 8f) {
            // Swiping right: Green (Keep Safe)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFF388E3C)),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier
                        .padding(start = 22.dp)
                        .scale(iconScale),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = stringResource(R.string.common_keep_cd),
                        tint = Color.White.copy(alpha = alpha),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        stringResource(R.string.common_keep_btn),
                        color = Color.White.copy(alpha = alpha),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        } else if (currentOffset < -8f) {
            // Swiping left: Red (Queue / Delete)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier
                        .padding(end = 22.dp)
                        .scale(iconScale),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (isQueued) stringResource(R.string.common_remove_queue) else stringResource(R.string.common_queue_delete),
                        color = Color.White.copy(alpha = alpha),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Icon(
                        if (isQueued) Icons.Default.RemoveCircleOutline else Icons.Default.Delete,
                        contentDescription = stringResource(R.string.common_queue_delete),
                        tint = Color.White.copy(alpha = alpha),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Foreground content with bouncy spring snapback
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(isQueued) {
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
                            // Action FIRST — instant state change — then the
                            // snapback in parallel. Chaining the action after a
                            // soft spring delayed feedback by ~2 s (bug #6).
                            when {
                                offsetX.value > thresholdPx -> onSwipeRight()
                                offsetX.value < -thresholdPx -> onSwipeLeft()
                            }
                            scope.launch {
                                offsetX.animateTo(
                                    0f,
                                    spring(dampingRatio = 0.8f, stiffness = 700f)
                                )
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 700f))
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}
