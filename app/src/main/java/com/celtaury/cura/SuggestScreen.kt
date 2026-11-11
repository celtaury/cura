package com.celtaury.cura

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celtaury.cura.core.DeckPrefs
import com.celtaury.cura.core.KeepSafe
import com.celtaury.cura.core.MediaItem
import com.celtaury.cura.core.PermissionGate
import com.celtaury.cura.core.SavedStats
import com.celtaury.cura.core.Scanner
import com.celtaury.cura.core.SuggestEngine
import com.celtaury.cura.core.SuggestPrefs
import com.celtaury.cura.core.SuggestionEngine
import com.celtaury.cura.core.humanBytes
import com.celtaury.cura.core.relativeDays
import com.celtaury.cura.core.rememberConfirmableDelete
import com.celtaury.cura.ui.deck.DeckCard
import com.celtaury.cura.ui.deck.SwipeActionItem
import com.celtaury.cura.ui.deck.SummaryPhase
import com.celtaury.cura.ui.deck.ViewModeToggle
import com.celtaury.cura.ui.preview.MediaPreviewDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage

/**
 * Suggested for you — the reason-first deck (overhaul round 1).
 *
 * Not a list of junk (Quick Clean owns that) — a daily dose of ~10 cards the
 * engine can best justify, each led by a WHY ("Dusty screenshot — saved 8
 * months ago") instead of a category. Judge with the same gestures and buttons
 * as every other deck: swipe right 🛡️ / left 🧹, or tap the buttons.
 *
 * Looks & navigation deliberately mirror SwipeDeckScreen: same phases, same
 * card composable (DeckCard), same summary/done flow, same resume behavior.
 */
private const val DECK_ID = "suggest"

/**
 * Chip labels for the deck's internal tag keys — localized (v2: the chip
 * finally speaks the user's language instead of raw English keys).
 * 0 = unknown tag (forward compatibility) → the raw key is shown as-is.
 */
@StringRes
private fun tagLabelRes(tag: String): Int = when (tag) {
    "Old screenshot" -> R.string.tag_old_screenshot
    "Duplicate" -> R.string.tag_duplicate
    "Stray installer" -> R.string.tag_stray_installer
    "Old photo" -> R.string.tag_old_photo
    "Burst extra" -> R.string.tag_burst_extra
    "Blurry photo" -> R.string.tag_blurry_photo
    "Plain screenshot" -> R.string.tag_plain_screenshot
    "Scanned document" -> R.string.tag_scanned_document
    else -> 0
}

@Composable
fun SuggestScreen(onBack: () -> Unit) {
    PermissionGate(
        title = stringResource(R.string.sug_title),
        description = stringResource(R.string.sug_perm_desc)
    ) {
        SuggestContent(onBack = onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    var phase by remember { mutableStateOf("loading") }
    var cards by remember { mutableStateOf<List<SuggestEngine.Suggestion>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var queue by remember { mutableStateOf(listOf<MediaItem>()) }
    var history by remember { mutableStateOf(listOf<Pair<MediaItem, String>>()) }
    var resumedAt by remember { mutableStateOf(-1) }
    var sessionSaved by remember { mutableStateOf(0L) }
    var allTimeSaved by remember { mutableStateOf(0L) }
    var previewItem by remember { mutableStateOf<MediaItem?>(null) }
    // List ⇄ deck view (Batch H): the Suggest deal can be judged as rows.
    var deckView by remember { mutableStateOf(DeckPrefs.isDeck(context, DECK_ID)) }

    // ── load: restore the resting session, else deal a fresh one ──
    LaunchedEffect(Unit) {
        // v2 one-shot: a pre-update resting session would keep dealing the
        // old ten cards and mask the new photo-mind families — clear it once.
        SuggestPrefs.ensureEngineFresh(context, 2)
        val seed = SuggestEngine.Session.consume()
        val saved = SuggestPrefs.savedCards(context)
        var restored: List<SuggestEngine.Suggestion> = emptyList()
        if (seed == null && saved.isNotEmpty()) {
            val items = Scanner.itemsByIds(context, saved.map { it.first })
            val byId = items.associateBy { it.id }
            restored = saved.mapNotNull { (id, tag) ->
                val item = byId[id] ?: return@mapNotNull null
                if (KeepSafe.contains(context, item.uri)) return@mapNotNull null
                SuggestEngine.Suggestion(item, tag, SuggestEngine.reasonFor(context, item, tag), 0)
            }
        }
        if (seed == null && restored.size >= 2) {
            val savedIndex = DeckPrefs.index(context, DECK_ID)
            val savedQueueUris = DeckPrefs.queue(context, DECK_ID).toSet()
            queue = restored.filter { it.item.uri.toString() in savedQueueUris }.map { it.item }
            index = savedIndex.coerceIn(0, restored.size)
            resumedAt = if (savedIndex in 1 until restored.size) savedIndex else -1
            cards = restored
            phase = when {
                index >= restored.size && queue.isNotEmpty() -> "summary"
                index >= restored.size -> "empty"
                else -> "deck"
            }
        } else {
            // a tile promise outranks the resting session (trade-off, round 1:
            // a pending queue from the resting session is cleared with it)
            dealFresh(context, seed) { fresh ->
                cards = fresh
                queue = emptyList()
                index = 0
                history = emptyList()
                resumedAt = -1
                phase = if (fresh.isEmpty()) "empty" else "deck"
            }
        }
    }

    LaunchedEffect(phase) {
        if (phase == "done") {
            allTimeSaved = withContext(Dispatchers.IO) { SavedStats.savedBytes(context) }
        }
    }

    fun persist() {
        DeckPrefs.setIndex(context, DECK_ID, index)
        DeckPrefs.setQueue(context, DECK_ID, queue.map { it.uri.toString() })
    }

    fun decide(item: MediaItem, action: String) {
        SuggestPrefs.markJudged(context, item.uri.toString())
        SuggestionEngine.observe(item, action)   // the tile's brain learns too
        if (action == "kept") {
            KeepSafe.add(context, item.uri, "Suggestions")
        } else {
            queue = queue + item
        }
        history = history + (item to action)
        index += 1
        persist()
        phase = if (index >= cards.size) {
            if (queue.isNotEmpty()) "summary" else "empty"
        } else "deck"
    }

    /** "Not today" — no judgment, the card just sinks in future rankings. */
    fun skip(item: MediaItem) {
        SuggestPrefs.markSkipped(context, item.uri.toString())
        index += 1
        persist()
        phase = if (index >= cards.size) {
            if (queue.isNotEmpty()) "summary" else "empty"
        } else "deck"
    }

    /**
     * Un-burial: a cancel or an undo must NOT permanently hide a file from
     * future suggestions — it is still on the device, so it stays eligible.
     * (decide() judges eagerly; this is the reverse lever.)
     */
    fun unbury(items: List<MediaItem>) {
        items.forEach {
            SuggestPrefs.unjudge(context, it.uri.toString())
            SuggestionEngine.forget(it)
        }
    }

    /**
     * Batch H: list-mode judgement — the deck's side effects, minus the
     * index arithmetic. The card leaves the deal instead, so the deck
     * resumes exactly at cards[index] when you switch back. Undo stays a
     * deck-gesture feature; the list is for fast row-by-row triage.
     */
    fun listDecide(item: MediaItem, action: String) {
        when (action) {
            "kept" -> {
                KeepSafe.add(context, item.uri, "Suggestions")
                SuggestionEngine.observe(item, "kept")
            }
            "queued" -> {
                SuggestionEngine.observe(item, action)
                queue = queue + item
            }
        }
        if (action == "skip") {
            SuggestPrefs.markSkipped(context, item.uri.toString())
        } else {
            SuggestPrefs.markJudged(context, item.uri.toString())
        }
        cards = cards.filterNot { it.item.uri == item.uri }
        SuggestPrefs.setCards(context, cards.map { it.item.id to it.tag })
        persist()
        phase = if (index >= cards.size) {
            if (queue.isNotEmpty()) "summary" else "empty"
        } else "deck"
    }

    fun undo() {
        val last = history.lastOrNull() ?: return
        history = history.dropLast(1)
        index -= 1
        unbury(listOf(last.first))
        if (last.second == "queued") {
            queue = queue.filterNot { it.uri == last.first.uri }
        } else {
            KeepSafe.remove(context, last.first.uri)
        }
        persist()
        phase = "deck"
    }

    // Summary "Confirm & Clean" routes through the same in-app confirm sheet
    // as every other flow — thumbnails + size before the system dialog.
    val confirmDelete = rememberConfirmableDelete(
        onCleaned = { cleaned ->
            // Only what Android actually deleted is credited and released;
            // denied items stay queued (and buried) for another confirm.
            val grantedUris = cleaned.map { it.uri }.toSet()
            val bytes = cleaned.sumOf { it.sizeBytes }
            SavedStats.addClean(context, bytes)
            sessionSaved += bytes
            queue = queue.filterNot { it.uri in grantedUris }
            if (queue.isEmpty()) {
                DeckPrefs.clear(context, DECK_ID)
                SuggestPrefs.clearSession(context)
                phase = "done"
            } else {
                persist()
                phase = "summary"
            }
        },
        onCancelled = { unbury(queue) }   // sheet or system dialog dismissed — retryable
    )

    val purge = rememberConfirmableDelete(
        onCleaned = { cleaned ->
            val grantedUris = cleaned.map { it.uri }.toSet()
            val bytes = cleaned.sumOf { it.sizeBytes }
            val count = cleaned.size
            SavedStats.addClean(context, bytes)
            sessionSaved += bytes
            queue = queue.filterNot { it.uri in grantedUris }
            history = emptyList()
            DeckPrefs.setQueue(context, DECK_ID, queue.map { it.uri.toString() })
            scope.launch {
                snack.showSnackbar(
                    context.resources.getQuantityString(R.plurals.common_purged_n, count, count, humanBytes(bytes))
                )
            }
        },
        onCancelled = { unbury(queue) }   // confirm sheet dismissed — retryable
    )

    /** Deal a fresh 10 — keeps freed-total for the visit, like a new round. */
    fun dealMore() {
        scope.launch {
            // clear BEFORE dealing — dealFresh writes the new session
            SuggestPrefs.clearSession(context)
            DeckPrefs.clear(context, DECK_ID)
            dealFresh(context, null) { fresh ->
                if (fresh.isEmpty()) {
                    scope.launch {
                        snack.showSnackbar(context.getString(R.string.sug_all_caught))
                    }
                    if (sessionSaved > 0L) phase = "done" else phase = "empty"
                } else {
                    cards = fresh
                    queue = emptyList()
                    index = 0
                    history = emptyList()
                    resumedAt = -1
                    phase = "deck"
                }
            }
        }
    }

    Scaffold(
        containerColor = cs.background,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            // Batch K (user ask): the title always says
                            // "Suggested" — the REASON belongs in the
                            // subtitle, where it reads as context, not as
                            // a screen name that keeps changing.
                            Text(stringResource(R.string.sug_title), fontWeight = FontWeight.Bold)
                            val currentCard = cards.getOrNull(index)
                            val why = if (phase == "deck") currentCard?.let { s ->
                                tagLabelRes(s.tag).takeIf { it != 0 }
                                    ?.let { stringResource(it) } ?: s.tag
                            } else null
                            Text(
                                why ?: stringResource(R.string.sug_sub),
                                fontSize = 12.sp,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back_cd))
                        }
                    },
                    actions = {
                        if (phase == "deck") {
                            ViewModeToggle(route = DECK_ID) { deckView = it }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
                )
                if (phase == "deck" && cards.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = { index.toFloat() / cards.size },
                            modifier = Modifier.weight(1f),
                            color = cs.primary,
                            trackColor = cs.surfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            (if (queue.isNotEmpty())
                                stringResource(R.string.common_counter_q, index + 1, cards.size, queue.size)
                            else
                                stringResource(R.string.common_counter, index + 1, cards.size)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurfaceVariant
                        )
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
                CircularProgressIndicator(modifier = Modifier.size(56.dp), color = cs.primary)
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.sug_loading), color = cs.onSurfaceVariant)
            }

            "empty" -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("🌿", fontSize = 52.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (sessionSaved > 0L) stringResource(R.string.common_deck_cleared) else stringResource(R.string.common_all_clean),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    stringResource(R.string.common_nothing_left),
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { dealMore() },
                    shape = MaterialTheme.shapes.large
                ) { Text(stringResource(R.string.sug_more), fontWeight = FontWeight.Bold) }
                TextButton(onClick = onBack) { Text(stringResource(R.string.common_back_home)) }
            }

            "deck" -> if (deckView) {
                // H fix: full scaffold padding — the deck must measure only
                // the visible area between top bar and buttons (same fix as
                // the shared deck; here the phase had NO padding at all).
                Box(modifier = Modifier.padding(padding)) {
                    DeckPhase(
                        cards = cards,
                        index = index,
                        queueSize = queue.size,
                        resumedAt = resumedAt,
                        canUndo = history.isNotEmpty(),
                        onDecide = ::decide,
                        onUndo = ::undo,
                        onSkip = ::skip,
                        onPreview = { previewItem = it },
                        onPurge = { purge(queue) }
                    )
                }
            } else {
                Box(modifier = Modifier.padding(padding)) {
                    SuggestListPhase(
                        cards = cards,
                        index = index,
                        queue = queue,
                        onDecide = ::listDecide,
                        onPreview = { previewItem = it },
                        onConfirm = { confirmDelete(queue) }
                    )
                }
            }

            "summary" -> SummaryPhase(
                queued = queue,
                onConfirm = { confirmDelete(queue) },
                onLater = onBack
            )

            "done" -> SuggestDonePhase(
                sessionSaved = sessionSaved,
                allTimeSaved = allTimeSaved,
                onDealMore = { dealMore() },
                onBack = onBack
            )
        }
    }

    previewItem?.let { item ->
        // Quick Look-style preview for EVERY mime — videos play inline now.
        // Its quick actions map to deck semantics — "Queue Delete" acts as
        // swipe left, "Keep Safe" as swipe right, so undo/history stay in sync.
        MediaPreviewDialog(
            item = item,
            isQueued = queue.any { it.uri == item.uri },
            onToggleQueue = {
                previewItem = null
                decide(item, "queued")
            },
            onKeepSafe = {
                previewItem = null
                decide(item, "kept")
            },
            onDismiss = { previewItem = null }
        )
    }
}

/**
 * Shared dealing logic for first load and "give me 10 more". Hands the result
 * back through [onDealt] so callers keep their own state fields.
 */
private suspend fun dealFresh(
    context: Context,
    seed: MediaItem?,
    onDealt: (List<SuggestEngine.Suggestion>) -> Unit
) {
    val fresh = SuggestEngine.buildSession(context, startItem = seed)
    SuggestPrefs.setCards(context, fresh.map { it.item.id to it.tag })
    DeckPrefs.setIndex(context, DECK_ID, 0)
    DeckPrefs.setQueue(context, DECK_ID, emptyList())
    onDealt(fresh)
}

// ───────────────────────── deck phase ─────────────────────────

@Composable
private fun DeckPhase(
    cards: List<SuggestEngine.Suggestion>,
    index: Int,
    queueSize: Int,
    resumedAt: Int,
    canUndo: Boolean,
    onDecide: (MediaItem, String) -> Unit,
    onUndo: () -> Unit,
    onSkip: (MediaItem) -> Unit,
    onPreview: (MediaItem) -> Unit,
    onPurge: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val current = cards.getOrNull(index)

    Column(Modifier.fillMaxSize()) {
        if (resumedAt in 1 until cards.size && index == resumedAt) {
            Text(
                stringResource(R.string.common_resumed_card, index + 1),
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
                    stringResource(R.string.common_n_queued_broom, queueSize),
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
        Spacer(Modifier.height(6.dp))

        // Batch K: the floating reason banner is gone — the tag pill and the
        // reason now ride ON the card itself (DeckCard badge), so the WHY is
        // glued to the photo it explains instead of hovering above the deck.

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            // Height-derived sizing (Batch H — same fix as the shared deck):
            // height first, width from the 0.72 aspect. The Batch F formula
            // fed maxHeight*0.72 in as a WIDTH and aspectRatio turned it
            // back into a ~full-area height.
            val cardHeight = minOf(maxHeight - 24.dp, (maxWidth - 48.dp) / 0.72f)
            val cardWidth = cardHeight * 0.72f
            // next cards peeking behind — identical stack to every deck
            for (depth in 2 downTo 1) {
                val next = cards.getOrNull(index + depth)
                if (next != null) {
                    DeckCard(
                        item = next.item,
                        modifier = Modifier
                            .width(cardWidth)
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
                        item = current.item,
                        modifier = Modifier
                            .width(cardWidth)
                            .aspectRatio(0.72f),
                        interactive = true,
                        onDecide = onDecide,
                        onPreview = onPreview,
                        badgeLabel = tagLabelRes(current.tag).takeIf { it != 0 }
                            ?.let { stringResource(it) } ?: current.tag,
                        badgeReason = current.reason
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
            // Batch K: Clean LEFT, Keep RIGHT — same swap as every deck, so
            // the buttons finally match the gestures (left = clean).
            Button(
                onClick = { current?.let { onDecide(it.item, "queued") } },
                modifier = Modifier.weight(1f).height(54.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.errorContainer,
                    contentColor = cs.onErrorContainer
                )
            ) { Text(stringResource(R.string.common_btn_clean), fontWeight = FontWeight.Bold) }
            TextButton(onClick = onUndo, enabled = canUndo && index > 0) {
                Text(stringResource(R.string.common_btn_undo))
            }
            Button(
                onClick = { current?.let { onDecide(it.item, "kept") } },
                modifier = Modifier.weight(1f).height(54.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.tertiaryContainer,
                    contentColor = cs.onTertiaryContainer
                )
            ) { Text(stringResource(R.string.common_btn_keep), fontWeight = FontWeight.Bold) }
        }
        TextButton(
            onClick = { current?.let { onSkip(it.item) } },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                stringResource(R.string.sug_not_today),
                fontSize = 12.sp,
                color = cs.onSurfaceVariant
            )
        }
    }
}

// ───────────────────────── done phase ─────────────────────────

@Composable
private fun SuggestDonePhase(
    sessionSaved: Long,
    allTimeSaved: Long,
    onDealMore: () -> Unit,
    onBack: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var pop by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { pop = true }
    val scale by animateFloatAsState(
        targetValue = if (pop) 1f else 0.4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "suggestDoneScale"
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
        Text(stringResource(R.string.common_cleaned_done), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.common_freed_now, humanBytes(sessionSaved)),
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurfaceVariant
        )
        Text(
            stringResource(R.string.common_freed_all, humanBytes(allTimeSaved)),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.tertiary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDealMore,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.large
        ) { Text(stringResource(R.string.sug_more), fontWeight = FontWeight.Bold) }
        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.common_back_home)) }
    }
}

// ───────────────────────── list mode (Batch H) ─────────────────────────

/**
 * List mode for the Suggest deal (Batch H): the same un-judged tail the
 * deck shows (cards.drop(index)), but scannable rows with the tag chip and
 * the reason in plain sight. Batch K: rows judge with THE shared gesture
 * ([SwipeActionItem]) - swipe left queues, swipe right keeps, tap previews -
 * exactly like the Social Cleaner. No more tap-pill menu per row; "Not
 * today" stays a deck-mode action.
 */
@Composable
private fun SuggestListPhase(
    cards: List<SuggestEngine.Suggestion>,
    index: Int,
    queue: List<MediaItem>,
    onDecide: (MediaItem, String) -> Unit,
    onPreview: (MediaItem) -> Unit,
    onConfirm: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val pending = cards.drop(index)
    val queuedUris = queue.map { it.uri.toString() }.toSet()

    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.sug_list_header, pending.size),
            fontSize = 12.sp,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pending, key = { it.item.uri.toString() }) { s ->
                val item = s.item
                val queued = item.uri.toString() in queuedUris
                SwipeActionItem(
                    isQueued = queued,
                    onSwipeLeft = { onDecide(item, "queued") },
                    onSwipeRight = { onDecide(item, "kept") }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(cs.surface)
                            .clickable { onPreview(item) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.mime.startsWith("image/") || item.mime.startsWith("video/")) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(cs.surfaceVariant)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                tagLabelRes(s.tag).takeIf { it != 0 }
                                    ?.let { stringResource(it) } ?: s.tag,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = cs.onPrimaryContainer,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(cs.primaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                s.reason,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                item.displayName + " · " +
                                    stringResource(R.string.deck_card_meta, humanBytes(item.sizeBytes), relativeDays(item.dateAddedSec))
                                        .substringAfter("· "),
                                fontSize = 11.sp,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (queued) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.list_chip_queued),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = cs.onErrorContainer,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(cs.errorContainer)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = onConfirm,
            enabled = queue.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .height(48.dp),
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = cs.errorContainer,
                contentColor = cs.onErrorContainer
            )
        ) {
            Text(
                stringResource(R.string.list_confirm_n, queue.size, humanBytes(queue.sumOf { it.sizeBytes })),
                fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1
            )
        }
    }
}

