package com.celtaury.cura.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celtaury.cura.R
import com.celtaury.cura.StorageInfo
import com.celtaury.cura.core.KeepSafe
import com.celtaury.cura.core.SavedStats
import com.celtaury.cura.core.TrashLog
import com.celtaury.cura.core.humanBytes
import com.celtaury.cura.core.relativeDays
import com.celtaury.cura.getDeviceStorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Summary (Batch G): the stats home. It inherits the tile Browse held — the
 * month-raid gallery died because the swipe decks already cover judging —
 * and answers one question: what has Cura actually done for me?
 *
 * Honest-data design: every number here is real. The impact figure counts
 * only bytes Android actually deleted (SavedStats); the history is Cura's
 * own deletion log (TrashLog, newest first, ~31-day retention — Android
 * hides most of the system trash from third-party apps, so this log, not
 * the OS trash, is the source of truth); the shield count is the live
 * Keep-safe set. Nothing is estimated, nothing is inflated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme

    var savedBytes by remember { mutableLongStateOf(0L) }
    var sessions by remember { mutableIntStateOf(0) }
    var storage by remember { mutableStateOf<StorageInfo?>(null) }
    var kept by remember { mutableIntStateOf(0) }
    var history by remember { mutableStateOf<List<TrashLog.Entry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            savedBytes = SavedStats.savedBytes(context)
            sessions = SavedStats.cleanSessions(context)
            storage = getDeviceStorageInfo(context)
            kept = KeepSafe.all(context).size
            history = TrashLog.entries(context)
        }
        loading = false
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.summary_title),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.summary_subtitle),
                            fontSize = 12.sp,
                            color = cs.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back_cd)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Your impact ── (same numbers as More Tools, given room to breathe)
            item(key = "impact") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cs.tertiaryContainer)
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(
                            stringResource(R.string.more_impact),
                            style = MaterialTheme.typography.titleSmall,
                            color = cs.onTertiaryContainer
                        )
                        Text(
                            if (loading) "…" else humanBytes(savedBytes),
                            style = MaterialTheme.typography.displaySmall,
                            color = cs.onTertiaryContainer
                        )
                        Text(
                            pluralStringResource(R.plurals.more_sessions_n, sessions, sessions),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onTertiaryContainer
                        )
                    }
                }
            }

            // ── Device storage ──
            item(key = "storage") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cs.surface)
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(
                            stringResource(R.string.more_storage),
                            style = MaterialTheme.typography.titleSmall,
                            color = cs.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            storage?.let {
                                stringResource(
                                    R.string.common_used_of,
                                    humanBytes(it.usedBytes),
                                    humanBytes(it.totalBytes)
                                )
                            } ?: "…",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            storage?.let {
                                stringResource(
                                    R.string.more_free_pct,
                                    humanBytes(it.freeBytes),
                                    (it.percentageUsed * 100).toInt()
                                )
                            } ?: "…",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Kept safe ──
            item(key = "kept") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cs.surface)
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(
                            stringResource(R.string.summary_kept_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = cs.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (kept == 0) stringResource(R.string.summary_kept_zero)
                            else pluralStringResource(R.plurals.summary_kept_n, kept, kept),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            // ── Recent deletions: Cura's own log, the user's history ──
            if (!loading) {
                item(key = "hist_header") {
                    Text(
                        stringResource(R.string.summary_history_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (history.isEmpty()) {
                    item(key = "hist_empty") {
                        Text(
                            stringResource(R.string.summary_history_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                    }
                } else {
                    items(history, key = { it.uri }) { entry ->
                        DeletionRow(entry)
                    }
                }
            }

            item(key = "footer_space") {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun DeletionRow(entry: TrashLog.Entry) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Delete,
            contentDescription = null,
            tint = cs.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                relativeDays(entry.trashedAtMs / 1000),
                fontSize = 12.sp,
                color = cs.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            humanBytes(entry.bytes),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurfaceVariant
        )
    }
}
