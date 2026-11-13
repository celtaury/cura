package com.celtaury.cura

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.celtaury.cura.core.DeleteQueuePrefs
import com.celtaury.cura.core.DonationPrefs
import com.celtaury.cura.core.JunkCategory
import com.celtaury.cura.core.KeepSafe
import com.celtaury.cura.core.MediaItem
import com.celtaury.cura.core.PhotoMind
import com.celtaury.cura.core.SavedStats
import com.celtaury.cura.core.Scanner
import com.celtaury.cura.core.SuggestionEngine
import com.celtaury.cura.core.humanBytes
import com.celtaury.cura.core.relativeDays
import com.celtaury.cura.core.rememberConfirmableDelete
import com.celtaury.cura.ui.deck.SwipeActionItem
import com.celtaury.cura.ui.preview.MediaPreviewDialog
import com.celtaury.cura.ui.theme.InterBlack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/** One Keep-safe action that can still be undone (burst-style, list edition).
 *  Named per-file (Quick…) — K2 flags same-name top-level classes as redeclaration
 *  even when private, so every file owns a uniquely-named type. */
private data class QuickPendingKeep(val item: MediaItem, val wasQueued: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCleanScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var phase by remember { mutableStateOf("scanning") }
    val warming = stringResource(R.string.qq_warming)
    var scanStatus by remember { mutableStateOf(warming) }
    // Resolved at composition for non-composable callbacks (snackbar labels).
    val undoLabel = stringResource(R.string.common_btn_undo)
    var categories by remember { mutableStateOf<List<JunkCategory>>(emptyList()) }
    var selectedCategoryKey by remember { mutableStateOf("all") }
    var queuedUris by remember { mutableStateOf(DeleteQueuePrefs.getQueue(context, "quick_clean")) }
    var previewItem by remember { mutableStateOf<MediaItem?>(null) }

    // Batch K retreat (user: "the quick clean is dangerous asf"): the one-tap
    // "Clean now" hero that fired a whole-library batch is GONE. The screen
    // now lands directly in the swipe list — quick comes from the swipes,
    // and nothing ever leaves the device except through the per-item queue
    // and the thumbnail confirm sheet.
    var lastFreedBytes by remember { mutableStateOf(0L) }
    var lastFreedCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val found = mutableListOf<JunkCategory>()

            scanStatus = context.getString(R.string.qq_scan_dupes)
            val dupes = Scanner.duplicateImages(context)
            if (dupes.isNotEmpty()) {
                found += JunkCategory("dupes", context.getString(R.string.qq_cat_dupes), context.getString(R.string.qq_cat_dupes_sub), dupes)
            }

            scanStatus = context.getString(R.string.qq_scan_apks)
            val apks = Scanner.apkFiles(context)
            if (apks.isNotEmpty()) {
                found += JunkCategory("apks", context.getString(R.string.qq_cat_apks), context.getString(R.string.qq_cat_apks_sub), apks)
            }

            // ── Batch E: the photo mind — burst / blurry / boring / docs ──
            // Slotted BEFORE old screenshots: the sharper reason wins the
            // first-claim dedupe below, so a mostly-blank screenshot lands in
            // "Boring" and "Old screenshots" keeps the genuinely interesting rest.
            scanStatus = context.getString(R.string.qq_scan_mind)
            val mind = PhotoMind.scan(context)
            if (mind.burstExtras.isNotEmpty()) {
                found += JunkCategory("burst", context.getString(R.string.qq_cat_burst), context.getString(R.string.qq_cat_burst_sub), mind.burstExtras)
            }
            if (mind.blurry.isNotEmpty()) {
                found += JunkCategory("blur", context.getString(R.string.qq_cat_blur), context.getString(R.string.qq_cat_blur_sub), mind.blurry)
            }
            if (mind.boringShots.isNotEmpty()) {
                found += JunkCategory("boring", context.getString(R.string.qq_cat_boring), context.getString(R.string.qq_cat_boring_sub), mind.boringShots)
            }
            if (mind.documents.isNotEmpty()) {
                found += JunkCategory("docs", context.getString(R.string.qq_cat_docs), context.getString(R.string.qq_cat_docs_sub), mind.documents)
            }

            scanStatus = context.getString(R.string.qq_scan_shots)
            val shots = Scanner.oldScreenshots(context, minAgeDays = 30)
            if (shots.isNotEmpty()) {
                found += JunkCategory("shots", context.getString(R.string.qq_cat_shots), context.getString(R.string.qq_cat_shots_sub), shots)
            }

            val seen = HashSet<String>()
            categories = found.map { cat ->
                cat.copy(items = cat.items.filter { seen.add(it.uri.toString()) })
            }.filter { it.items.isNotEmpty() }
        }
        // Queue entries whose files no longer scan (deleted outside the app)
        // would otherwise sit in the FAB count forever — drop them once.
        val scanned = categories.flatMap { it.items }.map { it.uri.toString() }.toSet()
        queuedUris = queuedUris intersect scanned
        phase = "review"          // Batch K: straight into the swipe list
    }

    // Persist delete queue so selections survive app exits
    LaunchedEffect(queuedUris) {
        DeleteQueuePrefs.setQueue(context, "quick_clean", queuedUris)
    }

    // ── Keep Safe undo, volley edition (same as Social Cleaner) ──
    // Every keep joins the currently-undoable group; the snackbar restarts its
    // timer with the running count. Undo rolls the whole volley back; letting
    // it expire commits. (Before: each new snackbar killed the previous undo.)
    var pendingKeeps by remember { mutableStateOf(listOf<QuickPendingKeep>()) }
    var keepBaseCategories by remember { mutableStateOf<List<JunkCategory>?>(null) }
    var keepUndoJob by remember { mutableStateOf<Job?>(null) }

    fun commitKeeps() {
        keepUndoJob?.cancel()
        pendingKeeps = emptyList()
        keepBaseCategories = null
    }

    // Keep Safe with Undo capability
    fun keepItemSafe(item: MediaItem) {
        if (keepBaseCategories == null) keepBaseCategories = categories
        pendingKeeps = pendingKeeps + QuickPendingKeep(item, item.uri.toString() in queuedUris)
        KeepSafe.add(context, item.uri, "Quick Clean")
        SuggestionEngine.observe(item, "kept")
        categories = categories.map { cat ->
            cat.copy(items = cat.items.filter { it.uri != item.uri })
        }.filter { it.items.isNotEmpty() }
        queuedUris = queuedUris - item.uri.toString()

        keepUndoJob?.cancel()
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
                categories = keepBaseCategories ?: categories
                group.forEach { pk ->
                    if (pk.wasQueued) queuedUris = queuedUris + pk.item.uri.toString()
                }
                pendingKeeps = emptyList()
                keepBaseCategories = null
                Toast.makeText(
                    context,
                    context.resources.getQuantityString(R.plurals.common_prot_removed_n, group.size, group.size),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                pendingKeeps = emptyList()
                keepBaseCategories = null
            }
        }
    }

    // Confirmable delete sheet before system deletion
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

            categories = categories.map { cat ->
                cat.copy(items = cat.items.filter { it.uri.toString() !in granted })
            }.filter { it.items.isNotEmpty() }

            queuedUris = queuedUris - granted

            // Batch J: the celebration knows exactly what was freed — a
            // half-denied system dialog only credits what Android reports.
            lastFreedBytes = freedBytes
            lastFreedCount = cleaned.size
            if (freedBytes > 0L) {
                phase = "done"
                // Batch M: a finished Quick Clean counts toward the one-time
                // donation prompt (shows after the 2nd completed session).
                DonationPrefs.noteSessionDone(context)
            }

            Toast.makeText(context, context.getString(R.string.common_cleaned_ok), Toast.LENGTH_SHORT).show()
        }
    )

    // A category that just emptied must not leave a dead chip selected —
    // the list would render blank with nothing highlighted.
    LaunchedEffect(categories) {
        if (selectedCategoryKey != "all" && categories.none { it.key == selectedCategoryKey }) {
            selectedCategoryKey = "all"
        }
    }

    // Items to display based on category filter
    val displayedItems = remember(categories, selectedCategoryKey) {
        if (selectedCategoryKey == "all") {
            categories.flatMap { it.items }.distinctBy { it.uri.toString() }
        } else {
            categories.firstOrNull { it.key == selectedCategoryKey }?.items ?: emptyList()
        }
    }

    val totalItems = categories.sumOf { it.items.size }
    val totalBytes = categories.sumOf { it.totalBytes }
    val queuedItemsList = remember(displayedItems, queuedUris) {
        displayedItems.filter { it.uri.toString() in queuedUris }
    }
    val queuedBytes = queuedItemsList.sumOf { it.sizeBytes }

    // Batch K: the hero state is gone with the hero.

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
                            stringResource(R.string.qq_title),
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterBlack
                        )
                        Text(
                            stringResource(R.string.qq_hint),
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
        }
    ) { padding ->
        Crossfade(targetState = phase, label = "quickclean_phase") { currentPhase ->
            when (currentPhase) {
                "scanning" -> Column(
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
                    Crossfade(targetState = scanStatus, label = "scan_status") { status ->
                        Text(
                            status,
                            style = MaterialTheme.typography.titleMedium,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (categories.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.qq_scan_running, humanBytes(totalBytes)),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.primary
                        )
                    }
                    Text(
                        stringResource(R.string.common_ks_never),
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant
                    )
                }

                // Batch K: the ONLY working surface — the swipe list. The
                // Batch J "one-tap Clean now" hero was retired (user: "the
                // quick clean is dangerous asf"): quick now means fast
                // swipes, not a blind batch delete.
                "review" -> {
                    if (categories.isEmpty()) {
                        JunkFreeEmpty(padding, onBack = onBack)
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

                                // ── Category Filter Chips ──
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    item {
                                        FilterChip(
                                            selected = selectedCategoryKey == "all",
                                            onClick = { selectedCategoryKey = "all" },
                                            label = { Text(stringResource(R.string.common_all_n, totalItems)) },
                                            shape = CircleShape,
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = cs.primaryContainer,
                                                selectedLabelColor = cs.onPrimaryContainer
                                            )
                                        )
                                    }
                                    items(categories, key = { it.key }) { cat ->
                                        FilterChip(
                                            selected = selectedCategoryKey == cat.key,
                                            onClick = { selectedCategoryKey = cat.key },
                                            label = { Text(stringResource(R.string.common_name_n, cat.title, cat.items.size)) },
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
                                                context.resources.getQuantityString(R.plurals.qq_found_n, totalItems, totalItems),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = cs.onSurface
                                            )
                                            Text(
                                                stringResource(R.string.qq_recoverable, humanBytes(totalBytes), queuedUris.size),
                                                fontSize = 12.sp,
                                                color = if (queuedUris.isNotEmpty()) cs.error else cs.onSurfaceVariant
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                // Union / subtract the SHOWN items only —
                                                // never wipe queued items from other filters.
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

                                // ── Items List with Swipe Left to Queue Delete / Right to Save ──
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
                                                label = "qc_cardBgColor"
                                            )
                                            val animatedBorderColor by animateColorAsState(
                                                targetValue = if (isQueued)
                                                    cs.error.copy(alpha = 0.65f)
                                                else
                                                    cs.outlineVariant.copy(alpha = 0.35f),
                                                animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                                                label = "qc_cardBorderColor"
                                            )
                                            val animatedCornerRadius by animateDpAsState(
                                                targetValue = if (isQueued) 22.dp else 18.dp,
                                                animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                                                label = "qc_cardCornerRadius"
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
                                                    val isMedia = item.mime.startsWith("image/") ||
                                                        item.mime.startsWith("video/")
                                                    if (isMedia) {
                                                        AsyncImage(
                                                            model = item.uri,
                                                            contentDescription = item.displayName,
                                                            modifier = Modifier
                                                                .size(56.dp)
                                                                .clip(RoundedCornerShape(14.dp))
                                                                .clickable { previewItem = item },
                                                            contentScale = ContentScale.Crop
                                                        )
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
                                                            stringResource(R.string.common_item_meta, humanBytes(item.sizeBytes), relativeDays(item.dateAddedSec)),
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
                                                stringResource(R.string.qq_delete_queued, queuedUris.size),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                // Add the shown items — keep whatever other
                                                // filters already had queued.
                                                queuedUris = queuedUris + displayedItems.map { it.uri.toString() }.toSet()
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
                                            Text(stringResource(R.string.common_queue_all), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Batch J/K: celebration after a successful clean ──
                "done" -> DonePanel(
                    padding = padding,
                    freedBytes = lastFreedBytes,
                    freedCount = lastFreedCount,
                    remainingItems = totalItems,
                    onBack = onBack,
                    onKeepGoing = { phase = "review" }
                )
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
 * Batch J: shared "nothing junky" celebration — shown by the hero when a
 * scan comes back empty and by the review list when keeps empty it.
 */
@Composable
private fun JunkFreeEmpty(padding: PaddingValues, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🌿", fontSize = 54.sp)
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.qq_already_clean),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.qq_none_found), color = cs.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack, shape = CircleShape) {
            Text(stringResource(R.string.common_back_home))
        }
    }
}

/**
 * Batch J: post-clean celebration — what the last batch actually freed
 * (per Android's own report) and what is left, with one tap to keep going.
 * The happy path ends here; nobody must reverse-engineer a list.
 */
@Composable
private fun DonePanel(
    padding: PaddingValues,
    freedBytes: Long,
    freedCount: Int,
    remainingItems: Int,
    onBack: () -> Unit,
    onKeepGoing: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🎉", fontSize = 54.sp)
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.qq_done_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.qq_done_freed, humanBytes(freedBytes)),
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            fontFamily = InterBlack,
            color = cs.primary
        )
        Text(
            pluralStringResource(R.plurals.common_items_n, freedCount, freedCount),
            fontSize = 13.sp,
            color = cs.onSurfaceVariant
        )
        if (remainingItems > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                pluralStringResource(R.plurals.qq_hero_items_n, remainingItems, remainingItems),
                fontSize = 13.sp,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onKeepGoing, shape = CircleShape) {
                Text(stringResource(R.string.qq_keep_going))
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack, shape = CircleShape) {
            Text(stringResource(R.string.common_back_home))
        }
    }
}
