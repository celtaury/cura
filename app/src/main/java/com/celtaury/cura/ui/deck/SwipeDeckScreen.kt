package com.celtaury.cura.ui.deck

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CalendarMonth
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.celtaury.cura.core.DeckPrefs
import com.celtaury.cura.core.DonationPrefs
import com.celtaury.cura.core.KeepSafe
import com.celtaury.cura.core.MediaItem
import com.celtaury.cura.core.PhotoMind
import com.celtaury.cura.core.PermissionGate
import com.celtaury.cura.core.SavedStats
import com.celtaury.cura.core.Scanner
import com.celtaury.cura.core.SuggestionEngine
import com.celtaury.cura.core.humanBytes
import com.celtaury.cura.core.monthLabelOf
import com.celtaury.cura.core.relativeDays
import com.celtaury.cura.core.rememberConfirmableDelete
import com.celtaury.cura.ui.preview.MediaPreviewDialog
import com.celtaury.cura.ui.theme.CuraDesign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.celtaury.cura.R

private class DeckSpec(
    val titleRes: Int,
    val subtitleRes: Int,
    val loader: (suspend (Context) -> List<MediaItem>)? = null
)

/** One judged card in the in-memory undo trail (newest last). */
private data class DeckDecision(
    val item: MediaItem,
    val action: String,
    /** Whole burst judged with one swipe (empty = a single card). */
    val group: List<MediaItem> = emptyList(),
    /** Batch L "mixed": the burst picker split one group into keeps + deletes. */
    val keptUris: Set<String> = emptySet(),
    val queuedUris: Set<String> = emptySet()
)

/**
 * Route → deck registry. Every deck is scanner-backed; the eleven
 * never-navigated memory-backed routes (deck_qc_all, deck_soc_*, …) were
 * removed in Batch F — they could only ever render an empty deck.
 */
private val deckSpecs = mapOf(
    "deck_gallery" to DeckSpec(R.string.deck_gallery_title, R.string.deck_gallery_subtitle) { Scanner.galleryDeck(it) },
    "deck_oldmedia" to DeckSpec(R.string.deck_oldmedia_title, R.string.deck_oldmedia_subtitle) { Scanner.oldMedia(it) }
)

/** Entry used by MainActivity for every "deck_" route. */
@Composable
fun DeckFlowForRoute(route: String, onBack: () -> Unit) {
    val spec = deckSpecs[route] ?: DeckSpec(R.string.deck_fb_title, R.string.deck_fb_subtitle)
    DeckFlow(
        route = route,
        title = stringResource(spec.titleRes),
        subtitle = stringResource(spec.subtitleRes),
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
        description = stringResource(R.string.deck_perm_desc)
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
    var history by remember { mutableStateOf(listOf<DeckDecision>()) }
    var resumedAt by remember { mutableStateOf(-1) }
    var previewItem by remember { mutableStateOf<MediaItem?>(null) }
    var sessionSaved by remember { mutableStateOf(0L) }
    var allTimeSaved by remember { mutableStateOf(0L) }
    var pickerOpen by remember { mutableStateOf(false) }
    // Batch L: the burst stack currently open in the keep-picker (null = closed).
    var pickingBurst by remember { mutableStateOf<List<MediaItem>?>(null) }

    // Per-month slices of the deck (#7): month key → where its cards live.
    // Batch I: keyed by CAPTURE month (monthKeyOf(item)) — DATE_ADDED is the
    // copy day on migrated libraries and collapsed every file into one
    // bucket, which hid the picker and disabled swipe-up entirely.
    val monthSlices = remember(items) {
        val byKey = LinkedHashMap<String, MutableList<Int>>()
        items.forEachIndexed { i, m ->
            byKey.getOrPut(Scanner.monthKeyOf(m)) { mutableListOf() }.add(i)
        }
        byKey.entries.sortedBy { it.key }
            .map { (k, idxs) -> MonthSlice(k, idxs.toList()) }
    }

    // Burst groups (Batch H, gallery deck): uri → the burst it belongs to.
    // Consecutive camera shots sharing a moment judge as ONE card — the
    // badge on the card says how many, one swipe decides them all.
    // Batch M: computed ASYNC inside the load effect — look-alike chains
    // decode tiny thumbnails, which must never run during composition.
    var burstIndex by remember { mutableStateOf<Map<String, List<MediaItem>>>(emptyMap()) }

    // Month-end gate (Batch H): the month key that was just finished, or
    // null while no gate is showing. Set by monthGateCheck() when a
    // decision crosses into a new month.
    var monthGate by remember { mutableStateOf<String?>(null) }

    fun monthGateCheck(judgedLast: MediaItem) {
        val next = items.getOrNull(index) ?: return
        if (Scanner.monthKeyOf(judgedLast) !=
            Scanner.monthKeyOf(next)
        ) {
            monthGate = Scanner.monthKeyOf(judgedLast)
        }
    }

    // List ⇄ deck view (Batch H): Large Files can be judged as rows too.
    var deckView by remember { mutableStateOf(DeckPrefs.isDeck(context, route)) }

    // ── load: scanner-backed or memory-backed, then resume position + queue ──
    LaunchedEffect(route) {
        val loaded = withContext(Dispatchers.IO) {
            loader?.invoke(context)
                ?.filter { !KeepSafe.contains(context, it.uri) }
                ?: emptyList()
        }
        // Batch M: burst groups over the loaded deck — naming rules plus the
        // new look-alike chains (suspend, IO, dHash-cached, so re-entering
        // the deck re-groups instantly). One card per burst in every mode.
        val groups = if (route == "deck_gallery") {
            PhotoMind.burstGroupsOf(context, loaded)
        } else {
            emptyList()
        }
        val savedIndex = DeckPrefs.index(context, route)
        val savedQueueUris = DeckPrefs.queue(context, route).toSet()
        val restoredQueue = loaded.filter { it.uri.toString() in savedQueueUris }
        index = savedIndex.coerceIn(0, loaded.size)
        queue = restoredQueue
        items = loaded
        burstIndex = groups
            .flatMap { g -> g.map { m -> m.uri.toString() to g } }
            .toMap()
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
            // Batch M: a finished clean session counts toward the one-time
            // donation prompt (shows after the 2nd completed session).
            DonationPrefs.noteSessionDone(context)
        }
    }

    fun persist() {
        DeckPrefs.setIndex(context, route, index)
        DeckPrefs.setQueue(context, route, queue.map { it.uri.toString() })
    }

    fun decide(item: MediaItem, action: String) {
        // Same-card double-fire guard: during the 230 ms fly-off the gesture
        // is still live on the old card, and a preview tap can race a swipe —
        // a second decide() for the card already being judged would advance
        // the index twice and, before the dedupe below, put the same URI
        // twice into a LazyRow keyed by URI (an instant Compose crash).
        // One card, one decision per visit — a legit re-decide always passes
        // through undo() first, which pops the matching history entry.
        if (history.lastOrNull()?.item?.uri == item.uri) return
        // Burst grouping (Batch H): one swipe judges every still-pending
        // shot of the burst the current card belongs to. All members share
        // the verdict; undo rewinds the whole burst as one decision.
        val burst = burstIndex[item.uri.toString()]
            ?.filter { m -> items.indexOfFirst { it.uri == m.uri } >= index }
            ?.sortedBy { m -> items.indexOfFirst { it.uri == m.uri } }
            .orEmpty()
        // Batch M hotfix: a burst is never bulk-cleaned by ONE action — a
        // Clean button or a list row lands here too, and one misflick must
        // not queue a whole shoot (or a trip) for deletion. The keep-picker
        // decides what survives; Keep stays group-wide (the safe direction).
        if (action == "queued" && burst.size > 1) {
            pickingBurst = burst
            return
        }
        val members = if (burst.size > 1) burst else listOf(item)
        for (m in members) {
            if (m.uri != item.uri) SuggestionEngine.observe(m, action)
            if (action == "kept") {
                KeepSafe.add(context, m.uri)
            } else if (queue.none { it.uri == m.uri }) {
                queue = queue + m
            }
        }
        history = history + DeckDecision(item, action, if (burst.size > 1) burst else emptyList())
        index = members.maxOf { m -> items.indexOfFirst { it.uri == m.uri } + 1 }
        persist()
        monthGateCheck(members.last())
        phase = if (index >= items.size) {
            if (queue.isNotEmpty()) "summary" else "empty"
        } else "deck"
    }

    fun undo() {
        if (history.isEmpty()) return
        // Single-card undo: every tap rewinds EXACTLY ONE decision — the
        // newest. Tap again to step back another card. No volley grouping:
        // one action, one undo, fully predictable.
        val last = history.last()
        // Batch H: a burst judgement is ONE decision covering every member.
        val members = last.group.ifEmpty { listOf(last.item) }
        if (last.action == "mixed") {
            // Batch L: the burst picker split ONE group into keeps and deletes
            // — rewind each member to its own side of the verdict.
            for (m in members) {
                when {
                    m.uri.toString() in last.keptUris -> KeepSafe.remove(context, m.uri)
                    m.uri.toString() in last.queuedUris ->
                        queue = queue.filterNot { it.uri == m.uri }
                }
            }
        } else for (m in members) {
            if (last.action == "queued") {
                queue = queue.filterNot { it.uri == m.uri }
            } else {
                KeepSafe.remove(context, m.uri)
            }
        }
        index = members.minOf { m -> items.indexOfFirst { it.uri == m.uri } }
            .coerceAtLeast(0)
        history = history.dropLast(1)
        persist()
        phase = "deck"
        scope.launch {
            snack.showSnackbar(context.getString(R.string.deck_back_one))
        }
    }

    // Summary "Confirm & Clean" routes through the same in-app confirm sheet
    // as every other flow — thumbnails + size before the system dialog.
    val confirmDelete = rememberConfirmableDelete(onCleaned = { cleaned ->
        // `cleaned` is what Android ACTUALLY deleted — a half-denied batch
        // must not credit the denied bytes or clear a queue still holding them.
        val bytes = cleaned.sumOf { it.sizeBytes }
        SavedStats.addClean(context, bytes)
        sessionSaved += bytes
        val grantedUris = cleaned.map { it.uri }.toSet()
        queue = queue.filterNot { it.uri in grantedUris }
        if (queue.isEmpty()) {
            DeckPrefs.clear(context, route)
            phase = "done"
        } else {
            persist()          // denied items stay queued for another confirm
            phase = "summary"
        }
    })

    // Mid-deck purge: tap the queued pill → confirm sheet → blast, keep judging.
    val purge = rememberConfirmableDelete(onCleaned = { cleaned ->
        val bytes = cleaned.sumOf { it.sizeBytes }
        val count = cleaned.size
        SavedStats.addClean(context, bytes)
        sessionSaved += bytes
        val grantedUris = cleaned.map { it.uri }.toSet()
        queue = queue.filterNot { it.uri in grantedUris }
        history = emptyList()
        DeckPrefs.setQueue(context, route, queue.map { it.uri.toString() })
        scope.launch {
            snack.showSnackbar(
                context.resources.getQuantityString(R.plurals.common_purged_n, count, count, humanBytes(bytes))
            )
        }
    })

    // Batch L: burst picker verdict — one group, two fates. Keeps land in
    // Keep Safe immediately; deletes join the queue and open THE SAME
    // confirm sheet as every other delete in the app (the safety retreat
    // holds: nothing leaves the device without the thumbnail sheet).
    fun applyBurstPick(members: List<MediaItem>, keep: List<MediaItem>) {
        val rep = members.first()
        var deletes: List<MediaItem> = emptyList()
        if (history.lastOrNull()?.item?.uri != rep.uri) {
            val keepUris = keep.map { it.uri.toString() }.toSet()
            deletes = members.filter { it.uri.toString() !in keepUris }
            keep.forEach { KeepSafe.add(context, it.uri) }
            for (m in deletes) {
                if (queue.none { it.uri == m.uri }) queue = queue + m
            }
            history = history + DeckDecision(
                rep, "mixed", members,
                keepUris, deletes.map { it.uri.toString() }.toSet()
            )
            index = members.maxOf { m -> items.indexOfFirst { it.uri == m.uri } + 1 }
            persist()
            monthGateCheck(members.last())
            phase = if (index >= items.size) {
                if (queue.isNotEmpty()) "summary" else "empty"
            } else "deck"
        }
        pickingBurst = null
        if (deletes.isNotEmpty()) purge(deletes)
    }

    // First-run coach marks (Batch C): teach the three gestures once, ever.
    var showCoach by remember { mutableStateOf(!DeckPrefs.coachDone(context)) }
    if (showCoach && phase == "deck") {
        DeckCoachMarks(onDone = {
            showCoach = false
            DeckPrefs.setCoachDone(context)
        })
    }

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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back_cd))
                        }
                    },
                    actions = {
                        // Batch I/L: month picking must ALWAYS be reachable in
                        // the swipe-up deck — the calendar button sits in the
                        // top bar beside the pill and the swipe-up gesture.
                        // Batch L: shown even with ONE month, so the picker is
                        // never invisible on a collapsed library.
                        if (phase == "deck" && monthSlices.isNotEmpty()) {
                            IconButton(onClick = { pickerOpen = true }) {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = stringResource(R.string.deck_jump_month)
                                )
                            }
                        }
                        if (route == "large_files" && phase == "deck") {
                            ViewModeToggle(route = "large_files") { deckView = it }
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
                            (if (queue.isNotEmpty())
                                stringResource(R.string.common_counter_q, index + 1, items.size, queue.size)
                            else
                                stringResource(R.string.common_counter, index + 1, items.size)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurfaceVariant
                        )
                    }
                    // Batch L: the pill shows even with one month — it names
                    // the bucket and opens the picker, so the feature is
                    // never silently hidden.
                    if (monthSlices.isNotEmpty()) {
                        val curKey = items.getOrNull(index)
                            ?.let { Scanner.monthKeyOf(it) }
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
                                stringResource(R.string.deck_month_pill, curLabel, curLeft),
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
                Text(stringResource(R.string.deck_loading), color = cs.onSurfaceVariant)
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
                Button(onClick = onBack, shape = MaterialTheme.shapes.large) {
                    Text(stringResource(R.string.common_back_home))
                }
            }

            "deck" -> if (!deckView && route == "large_files") {
                // Large Files list mode (Batch H): same queue & confirm
                // state as the deck, scannable rows instead of the card.
                Box(modifier = Modifier.padding(padding)) {
                    DeckListMode(
                        items = items,
                        queuedUris = queue.map { it.uri.toString() }.toSet(),
                        onKeep = { item ->
                            KeepSafe.add(context, item.uri)
                        },
                        onToggleQueue = { item ->
                            queue = if (queue.any { it.uri == item.uri }) {
                                queue.filterNot { it.uri == item.uri }
                            } else {
                                queue + item
                            }
                            persist()
                        },
                        onConfirm = { confirmDelete(queue) },
                        onPreview = { previewItem = it }
                    )
                }
            } else {
                // H fix: the deck phase applies the FULL scaffold padding —
                // bottom-only padding let the deck measure the space behind
                // the top bar too, so the card centered over the wrong
                // region: it rode up under the progress bar and pooled all
                // its slack above the buttons (0.9.10 screenshots).
                Box(modifier = Modifier.padding(padding)) {
                    DeckPhase(
                        items = items,
                        index = index,
                        queueSize = queue.size,
                        resumedAt = resumedAt,
                        canUndo = history.isNotEmpty(),
                        burstIndex = burstIndex,
                        onSwipeUp = if (monthSlices.isNotEmpty()) ({ pickerOpen = true }) else null,
                        onOpenBurstPick = { pickingBurst = it },
                        onDecide = ::decide,
                        onUndo = ::undo,
                        onPreview = { previewItem = it },
                        onPurge = { purge(queue) }
                    )
                }
            }

            "summary" -> Box(
                modifier = Modifier.padding(padding)
            ) {
                SummaryPhase(
                    queued = queue,
                    onConfirm = { confirmDelete(queue) },
                    onLater = onBack
                )
            }

            "done" -> DonePhase(
                sessionSaved = sessionSaved,
                allTimeSaved = allTimeSaved,
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
                if (queue.any { it.uri == item.uri }) {
                    // "Release" must actually release: remove the item from
                    // the queue and rewind the matching decision (the card
                    // under preview is the one the deck is showing). Before,
                    // this path called decide() again — re-queueing the very
                    // item it promised to un-queue and skipping a card.
                    queue = queue.filterNot { it.uri == item.uri }
                    val last = history.lastOrNull()
                    if (last?.item?.uri == item.uri && last.action == "queued") {
                        history = history.dropLast(1)
                        index = (index - 1).coerceAtLeast(0)
                        SuggestionEngine.forget(item)
                    }
                    persist()
                    phase = if (index >= items.size) {
                        if (queue.isNotEmpty()) "summary" else "empty"
                    } else "deck"
                } else {
                    decide(item, "queued")
                }
            },
            onKeepSafe = {
                previewItem = null
                decide(item, "kept")
            },
            onDismiss = { previewItem = null }
        )
    }

    // Month sidebar (Batch H): opens from the month pill OR a swipe UP on
    // the card. Replaces the old bottom-sheet picker.
    MonthSidebar(
        visible = pickerOpen,
        slices = monthSlices,
        currentIndex = index,
        currentKey = items.getOrNull(index)?.let { Scanner.monthKeyOf(it) },
        onPick = { slice ->
            pickerOpen = false
            // Batch I: month picking that actually PICKS — forward to the
            // first un-judged card of a month ahead, or BACKWARD into a
            // month already passed (skipped months were untappable before:
            // the sidebar confused "behind the cursor" with "judged").
            // Cards from the new cursor forward become live again; the
            // purge queue and the undo trail survive untouched.
            val target = slice.indexes.filter { p -> p >= index }.minOrNull()
                ?: slice.indexes.first()
            if (target != index) {
                index = target
                persist()
            }
        },
        onDismiss = { pickerOpen = false }
    )

    // Batch L: the burst keep-picker — one stack card, N photos, choose the
    // survivors. Deletes route through the same confirm sheet as everything.
    pickingBurst?.let { members ->
        BurstPicker(
            members = members,
            onConfirm = { keep -> applyBurstPick(members, keep) },
            onDismiss = { pickingBurst = null }
        )
    }

    // Month-end gate (Batch H): finishing a month pauses the deck —
    // "October is clean → continue?" — instead of silently sliding into
    // the next one. The deck is ALREADY showing the next month's first
    // card behind the gate; Continue just reveals it.
    monthGate?.let { doneKey ->
        val nextKey = items.getOrNull(index)
            ?.let { Scanner.monthKeyOf(it) }
        if (nextKey != null) {
            MonthGateOverlay(
                doneLabel = monthLabelOf(doneKey),
                nextLabel = monthLabelOf(nextKey),
                nextCount = items.drop(index)
                    .takeWhile { Scanner.monthKeyOf(it) == nextKey }
                    .size,
                onContinue = { monthGate = null },
                onLater = {
                    monthGate = null
                    onBack()
                }
            )
        } else {
            monthGate = null
        }
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
    burstIndex: Map<String, List<MediaItem>> = emptyMap(),
    onSwipeUp: (() -> Unit)? = null,
    onDecide: (MediaItem, String) -> Unit,
    onUndo: () -> Unit,
    onPreview: (MediaItem) -> Unit,
    // Batch L: called with the current card's pending burst members when
    // the stack card is tapped — opens the keep-picker.
    onOpenBurstPick: (List<MediaItem>) -> Unit = {},
    onPurge: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val current = items.getOrNull(index)
    // Batch L: the pending members of the current card's burst — non-empty
    // turns the card into a STACK: tap opens the keep-picker, the sheets
    // behind are plain stand-ins, and one verdict covers every member.
    val burstMembers = current?.let { cur ->
        burstIndex[cur.uri.toString()]
            ?.filter { m -> items.indexOfFirst { it.uri == m.uri } >= index }
            ?.sortedBy { m -> items.indexOfFirst { it.uri == m.uri } }
            ?.takeIf { it.size > 1 }
    } ?: emptyList()
    val burstCount = burstMembers.size

    // Swipe side glow: -1f = card being thrown left (clean → red), +1f =
    // thrown right (keep → green). DeckCard reports drag frames and a 0f
    // release; the state fades the glow out on release (Batch F — it used
    // to pop off instantly).
    val glowState = remember { DeckGlowState() }

    // Purge pill pulse: bump-scale the thumb-zone CTA each time the queue
    // grows, so it announces itself while you judge. Also fires once on
    // entry when a saved queue was restored.
    val purgePulse = remember { Animatable(1f) }
    var lastQueueSize by remember { mutableIntStateOf(0) }
    LaunchedEffect(queueSize) {
        if (queueSize > lastQueueSize) {
            purgePulse.snapTo(1.22f)
            purgePulse.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f))
        }
        lastQueueSize = queueSize
    }

    Column(Modifier.fillMaxSize()) {
        if (resumedAt in 1 until items.size && index == resumedAt) {
            Text(
                stringResource(R.string.common_resumed_card, index + 1),
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
        // (purge pill moved to the thumb zone below the action buttons)
        Spacer(Modifier.height(10.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            // Card sizing (Batch H — reverts the Batch F rule): pick the
            // HEIGHT first (the whole visible deck area minus breathing
            // room) and derive the width from the 0.72 aspect. Batch F
            // computed a width from `maxHeight * 0.72f` and let aspectRatio
            // turn it right back into a ~full-area height — on a fullscreen
            // phone that, plus the top padding being ignored, pushed the
            // card over the progress bar and left a dead gap above the
            // buttons. Height-derived sizing can never do either.
            val cardHeight = minOf(maxHeight - 24.dp, (maxWidth - 48.dp) / 0.72f)
            val cardWidth = cardHeight * 0.72f
            // the side being thrown toward lights up (Batch F: radial wash,
            // no more top/bottom clipping; knobs live in CuraDesign)
            DeckSideGlow(state = glowState)
            // next cards peeking behind. Batch L: while a burst stack is on
            // top, the members behind it are NOT real previews — plain
            // sheets stand in until the whole group is judged.
            if (burstMembers.isEmpty()) {
                for (depth in 2 downTo 1) {
                    val next = items.getOrNull(index + depth)
                    if (next != null) {
                        DeckCard(
                            item = next,
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
            } else {
                for (depth in 2 downTo 1) {
                    Box(
                        modifier = Modifier
                            .width(cardWidth)
                            .aspectRatio(0.72f)
                            .graphicsLayer {
                                val shrink = 1f - 0.05f * depth
                                scaleX = shrink
                                scaleY = shrink
                                translationY = 40f * depth
                                alpha = 1f - 0.25f * depth
                            }
                            .clip(RoundedCornerShape(CuraDesign.radiusDeckCard))
                            .background(cs.surfaceVariant)
                    )
                }
            }
            if (current != null) {
                key(index) {
                    DeckCard(
                        item = current,
                        modifier = Modifier
                            .width(cardWidth)
                            .aspectRatio(0.72f),
                        interactive = true,
                        onDecide = onDecide,
                        onPreview = onPreview,
                        onDragProgress = { glowState.onDrag(it) },
                        onSwipeUp = onSwipeUp,
                        burstCount = burstCount,
                        onOpenBurst = if (burstMembers.isNotEmpty())
                            ({ onOpenBurstPick(burstMembers) }) else null
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
            // Batch K: Clean now sits LEFT and Keep RIGHT — the buttons finally
            // match the gestures they mirror (swipe left = clean, swipe right =
            // keep) instead of fighting them.
            Button(
                onClick = { current?.let { onDecide(it, "queued") } },
                modifier = Modifier.weight(1f).height(54.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.errorContainer,
                    contentColor = cs.onErrorContainer
                )
            ) { Text(stringResource(R.string.common_btn_clean), fontWeight = FontWeight.Bold) }
            TextButton(onClick = onUndo, enabled = canUndo) {
                Text(stringResource(R.string.common_btn_undo))
            }
            Button(
                onClick = { current?.let { onDecide(it, "kept") } },
                modifier = Modifier.weight(1f).height(54.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.tertiaryContainer,
                    contentColor = cs.onTertiaryContainer
                )
            ) { Text(stringResource(R.string.common_btn_keep), fontWeight = FontWeight.Bold) }
        }
        // thumb-zone purge CTA — pulses when the queue grows
        if (queueSize > 0) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = purgePulse.value
                            scaleY = purgePulse.value
                        }
                        .clip(RoundedCornerShape(999.dp))
                        .background(cs.errorContainer)
                        .clickable(onClick = onPurge)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.common_queued_purge, queueSize),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = cs.onErrorContainer
                    )
                }
            }
        }
        // Batch K: the "swipe right 🛡️ … " legend is gone — the deck reads
        // clean and minimal, and the first-run coach marks (Batch C) remain
        // the tutor that teaches the gestures.
    }
}

@Composable
internal fun DeckCard(
    item: MediaItem,
    modifier: Modifier = Modifier,
    interactive: Boolean,
    onDecide: (MediaItem, String) -> Unit,
    onPreview: (MediaItem) -> Unit,
    onDragProgress: ((Float) -> Unit)? = null,
    onSwipeUp: (() -> Unit)? = null,
    burstCount: Int = 0,
    // Batch L: non-null on a burst stack — the media tap opens the
    // keep-picker instead of the single-photo preview.
    onOpenBurst: (() -> Unit)? = null,
    // Batch K (Suggest redesign): the WHY rides ON the card — a colored tag
    // pill plus the engine's one-line reason, rendered in the info strip.
    // Null for every other deck, which keeps their info strips unchanged.
    badgeLabel: String? = null,
    badgeReason: String? = null
) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val thresholdPx = with(LocalDensity.current) { 130.dp.toPx() }
    val swipeUp = onSwipeUp
    val offsetX = remember { Animatable(0f) }
    // Batch N hotfix: pointerInput(Unit) captures these ONCE per card, but
    // burstIndex arrives async (the load effect hashes for up to ~3.5 s) and
    // a card can outlive the state it was composed with — a stale capture
    // made a burst flick fly the card OFF-SCREEN and then "refuse" in
    // decide(), leaving a blank deck with the picker open. Live reads keep
    // the gesture on the latest values without restarting the detector
    // mid-drag.
    val liveDecide by rememberUpdatedState(onDecide)
    val liveItem by rememberUpdatedState(item)
    val liveDragProgress by rememberUpdatedState(onDragProgress)
    val liveOpenBurst by rememberUpdatedState(onOpenBurst)
    val isVideo = item.mime.startsWith("video/")
    val isMedia = item.mime.startsWith("image/") || isVideo

    val cardModifier = if (interactive) {
        modifier
            .graphicsLayer {
                translationX = offsetX.value
                rotationZ = (offsetX.value / 40f).coerceIn(-10f, 10f)
            }
            .pointerInput(Unit) {
                // Batch N hotfix: dragX mirrors what offsetX WILL be — each
                // frame's snapTo runs in a coroutine, so reading offsetX here
                // could lag a frame behind the finger. Glow frames are now
                // invoked SYNCHRONOUSLY in the drag callback (they only write
                // state), so a frame can never land after onDragEnd's release
                // and re-light the side wash — the Batch K "green/red light
                // persists" bug, back via the burst refusal path.
                var dragX = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragX = 0f
                        scope.launch { offsetX.snapTo(0f) }
                    },
                    onDragEnd = {
                        // the glow belongs to the drag — release it the moment
                        // the finger lifts, whichever way the card flies
                        liveDragProgress?.invoke(0f)
                        val x = dragX
                        scope.launch {
                            when {
                                x > thresholdPx -> {
                                    offsetX.animateTo(2200f, tween(230))
                                    liveDecide(liveItem, "kept")
                                }
                                x < -thresholdPx -> {
                                    // Batch M hotfix: a flick must never throw
                                    // a whole burst away — snap back and open
                                    // the keep-picker instead.
                                    val openBurst = liveOpenBurst
                                    if (openBurst != null) {
                                        offsetX.animateTo(0f, spring())
                                        openBurst()
                                    } else {
                                        offsetX.animateTo(-2200f, tween(230))
                                        liveDecide(liveItem, "queued")
                                    }
                                }
                                else -> offsetX.animateTo(0f, spring())
                            }
                        }
                    },
                    onDragCancel = {
                        liveDragProgress?.invoke(0f)
                        scope.launch { offsetX.animateTo(0f, spring()) }
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragX += amount
                        // -1f..1f: how far into a full throw this drag is
                        liveDragProgress?.invoke(
                            (dragX / thresholdPx).coerceIn(-1f, 1f)
                        )
                        scope.launch {
                            offsetX.snapTo(dragX)
                        }
                    }
                )
            }
            .then(
                if (swipeUp != null) {
                    // Vertical drag alongside the judge swipe (Batch H): a
                    // clean upward flick opens the month sidebar. The two
                    // detectors live in separate pointerInput blocks, so
                    // Compose routes each gesture by its dominant axis.
                    Modifier.pointerInput(swipeUp) {
                        var totalY = 0f
                        detectVerticalDragGestures(
                            onDragStart = { totalY = 0f },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                totalY += amount
                            },
                            onDragEnd = {
                                if (-totalY > 96.dp.toPx()) swipeUp()
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    } else modifier

    Box(cardModifier.clip(RoundedCornerShape(CuraDesign.radiusDeckCard))) {
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
                    // tap the media to Quick Look it (only the active card);
                    // Batch L: on a burst stack the tap opens the keep-picker
                    .clickable(enabled = interactive) {
                        if (onOpenBurst != null) onOpenBurst() else onPreview(item)
                    }
            ) {
                if (isMedia) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (isVideo) {
                        // purely decorative — the parent media Box handles the tap
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayCircle,
                                contentDescription = stringResource(R.string.common_preview_video_cd),
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
                            stringResource(R.string.deck_no_preview),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            // info strip
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                if (badgeLabel != null) {
                    Text(
                        badgeLabel.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = cs.onPrimaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(cs.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                    if (badgeReason != null) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            badgeReason,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                }
                if (burstCount > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.deck_burst_badge, burstCount),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = cs.onPrimaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(cs.primaryContainer)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.deck_burst_hint),
                            fontSize = 11.sp,
                            color = cs.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(item.displayName, maxLines = 1, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.deck_card_meta, humanBytes(item.sizeBytes), relativeDays(item.dateAddedSec)) +
                        if (!isMedia) " · " + stringResource(R.string.common_file_word) else "",
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant
                )
            }
        }
        // swipe hints
        if (offsetX.value > 20f) {
            SwipeHint(
                text = stringResource(R.string.deck_label_keep),
                color = cs.onTertiaryContainer,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(20.dp),
                alpha = (offsetX.value / thresholdPx).coerceIn(0f, 1f)
            )
        }
        if (offsetX.value < -20f) {
            SwipeHint(
                text = stringResource(R.string.deck_label_clean),
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

/**
 * Swipe-side glow state (Batch F). DeckCard keeps its old contract — drag
 * frames report -1f..1f, and onDragEnd/onDragCancel signal release with 0f —
 * so nothing in the gesture layer changed. The state translates that into:
 *   · instant tracking while the finger is down,
 *   · a soft FADE-OUT on release (the old glow snapped off, which read as
 *     a flicker),
 *   · and the fade cancels itself the moment a new drag starts.
 */
class DeckGlowState {
    var dragValue by mutableFloatStateOf(0f)
        private set
    var dragTick by mutableIntStateOf(0)
        private set
    var releaseTick by mutableIntStateOf(0)
        private set
    var releaseStart = 0f
        private set

    fun onDrag(value: Float) {
        if (value == 0f) {
            if (dragValue != 0f) {
                releaseStart = dragValue
                // Batch N hotfix: zero the tracked value WITH the release —
                // dragValue used to stay at the last frame, so any effect
                // restart that fell into the tracking branch after the
                // release re-pinned the wash fully lit and it froze there
                // (the returned "green/red light persists" report).
                dragValue = 0f
                releaseTick += 1
            }
        } else {
            dragValue = value
            dragTick += 1
        }
    }
}

/**
 * Swipe side glow. The side the card is being thrown toward lights up:
 * green on the right (keep safe), red on the left (clean).
 *
 * Batch F restyle (user report: the glow "clipped" hard at the top and
 * bottom edges and vanished without fading):
 *   · each side is now a RADIAL wash anchored at its screen edge — no more
 *     rectangular band with visible cutoffs at the top and bottom;
 *   · alpha still eases quadratically and is read inside the draw phase,
 *     so a drag never recomposes.
 * Colors, ceiling and fade time live in [CuraDesign] (design language).
 */
@Composable
private fun DeckSideGlow(
    state: DeckGlowState,
    modifier: Modifier = Modifier
) {
    var display by remember { mutableFloatStateOf(0f) }
    // Batch K bugfix (user report: the green/red wash "still persists after I
    // swipe left/right"): the old fade condition compared releaseTick against
    // dragTick — but dragTick increments EVERY drag frame (dozens per swipe),
    // while releaseTick grows by one. `releaseTick > dragTick` could only be
    // true for a zero-frame drag, so the fade branch never ran and the else
    // branch just re-pinned display at the last drag value: the glow froze
    // fully lit until the next drag. Releases are now tracked against a
    // seen-release counter instead, which only ever moves here.
    var seenRelease by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.dragTick, state.releaseTick) {
        if (state.releaseTick > seenRelease) {
            seenRelease = state.releaseTick
            animate(
                initialValue = state.releaseStart,
                targetValue = 0f,
                animationSpec = tween(CuraDesign.glowFadeMs, easing = FastOutSlowInEasing)
            ) { value, _ -> display = value }
            display = 0f   // guarantee a clean zero even if the tween is cut short
        } else {
            display = state.dragValue
        }
    }

    Box(modifier.fillMaxSize()) {
        // left — delete
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = (-display).coerceIn(0f, 1f)
                    alpha = p * p * CuraDesign.glowMaxAlpha
                }
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(CuraDesign.glowDelete, Color.Transparent),
                            center = Offset(0f, size.height / 2f),
                            radius = size.height * 0.85f
                        )
                    )
                }
        )
        // right — keep
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = display.coerceIn(0f, 1f)
                    alpha = p * p * CuraDesign.glowMaxAlpha
                }
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(CuraDesign.glowSave, Color.Transparent),
                            center = Offset(size.width, size.height / 2f),
                            radius = size.height * 0.85f
                        )
                    )
                }
        )
    }
}

// ───────────────────────── month sidebar ─────────────────────────
// Batch K: MonthSidebar + MonthSlice moved to ui/deck/MonthSidebar.kt so
// Browse Gallery opens the SAME picker ("use the month pick everywhere").
// The deck keeps all three openers: month pill tap, top-bar calendar
// button, swipe UP on the card.

/**
 * Month-end gate (Batch H): the moment the last card of a month is judged
 * and cards from a NEW month wait behind it, the deck stops and asks —
 * "October is clean → continue with November?" — instead of silently
 * sliding into the next month. [Continue] reveals the next month in place;
 * the escape hatch leaves the deck (position + queue are already saved).
 */
@Composable
private fun MonthGateOverlay(
    doneLabel: String,
    nextLabel: String,
    nextCount: Int,
    onContinue: () -> Unit,
    onLater: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onContinue,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(cs.scrim.copy(alpha = 0.6f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = cs.surface)
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("🌿", fontSize = 44.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.deck_month_done_title, doneLabel),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.deck_month_done_next, nextLabel, nextCount),
                        fontSize = 15.sp,
                        color = cs.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = MaterialTheme.shapes.large
                    ) { Text(stringResource(R.string.common_continue), fontWeight = FontWeight.Bold) }
                    TextButton(
                        onClick = onLater,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            stringResource(R.string.deck_month_done_later),
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────── summary phase ─────────────────────────

@Composable
internal fun SummaryPhase(
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
        Text(stringResource(R.string.deck_ready), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            pluralStringResource(R.plurals.deck_queue_summary, queued.size, queued.size, humanBytes(bytes)),
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
                    stringResource(R.string.deck_mixed_warning),
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
        ) { Text(stringResource(R.string.common_confirm_clean), fontWeight = FontWeight.Bold) }
        TextButton(onClick = onLater, modifier = Modifier.padding(top = 6.dp)) {
            Text(stringResource(R.string.deck_keep_later))
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
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.large
        ) { Text(stringResource(R.string.common_done), fontWeight = FontWeight.Bold) }
    }
}

/**
 * First-run coach marks (Batch C): one overlay teaching the three deck
 * gestures in plain words. Shown once ever (DeckPrefs); tap anywhere or
 * "Got it" to dismiss.
 */
@Composable
private fun DeckCoachMarks(onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(cs.scrim.copy(alpha = 0.6f))
                .pointerInput(Unit) { detectTapGestures { onDone() } }
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = cs.surface)
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(
                        stringResource(R.string.deck_coach_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.deck_coach_body),
                        fontSize = 15.sp,
                        lineHeight = 26.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.deck_coach_note),
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = MaterialTheme.shapes.large
                    ) { Text(stringResource(R.string.deck_coach_ok), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ───────────────────────── end of SwipeDeckScreen ─────────────────────────
