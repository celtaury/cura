package com.celtaury.cura.ui.deck

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.celtaury.cura.R
import com.celtaury.cura.core.MediaItem
import com.celtaury.cura.core.humanBytes
import com.celtaury.cura.core.relativeDays

/**
 * List mode for the scanner-backed decks (Batch H) — Large Files first.
 * Same state as the deck (queue of URIs to clean, KeepSafe for keeps), a
 * different body: scannable rows instead of one big card. The toggle lives
 * in the tool's top bar ([ViewModeToggle]).
 *
 * Row semantics mirror the deck gestures exactly:
 *   · Keep → KeepSafe (the deck keeps skipping kept items after a reload);
 *   · Clean → queue toggle; queued rows carry a chip so the choice is loud;
 *   · Confirm & Clean runs the same confirm sheet as the deck's summary.
 */
@Composable
fun DeckListMode(
    items: List<MediaItem>,
    queuedUris: Set<String>,
    onKeep: (MediaItem) -> Unit,
    onToggleQueue: (MediaItem) -> Unit,
    onConfirm: () -> Unit,
    onPreview: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    // Kept-this-session rows stay visible with a chip (no list churn) —
    // KeepSafe itself is already persisted by the caller.
    var keptUris by remember { mutableStateOf(setOf<String>()) }
    val shown = items.filter { it.uri.toString() !in keptUris || it.uri.toString() in queuedUris }
    val queuedCount = shown.count { it.uri.toString() in queuedUris }
    val queuedBytes = shown.filter { it.uri.toString() in queuedUris }.sumOf { it.sizeBytes }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.list_header_shown, shown.size, humanBytes(shown.sumOf { it.sizeBytes })),
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (queuedCount > 0)
                    stringResource(R.string.list_header_queued, queuedCount, humanBytes(queuedBytes))
                else "",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.error
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp, vertical = 4.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(shown, key = { it.uri.toString() }) { item ->
                ListModeRow(
                    item = item,
                    queued = item.uri.toString() in queuedUris,
                    kept = item.uri.toString() in keptUris,
                    onKeep = {
                        keptUris = keptUris + item.uri.toString()
                        onKeep(item)
                    },
                    onToggleQueue = { onToggleQueue(item) },
                    onPreview = { onPreview(item) }
                )
            }
        }

        // ── fixed footer: the same confirm path as every deck summary ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val uris = shown.map { it.uri.toString() }.toSet()
                    val allQueued = uris.isNotEmpty() && uris.all { it in queuedUris }
                    shown.forEach { item ->
                        val q = item.uri.toString() in queuedUris
                        if (allQueued && q) onToggleQueue(item)
                        if (!allQueued && !q) onToggleQueue(item)
                    }
                },
                enabled = shown.isNotEmpty(),
                shape = RoundedCornerShape(999.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 14.dp, vertical = 10.dp
                )
            ) {
                Text(
                    stringResource(
                        if (shown.isNotEmpty() && shown.all { it.uri.toString() in queuedUris })
                            R.string.common_clear_queue else R.string.common_queue_all
                    ),
                    fontSize = 12.sp
                )
            }
            Button(
                onClick = onConfirm,
                enabled = queuedCount > 0,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.errorContainer,
                    contentColor = cs.onErrorContainer
                )
            ) {
                Text(
                    stringResource(R.string.list_confirm_n, queuedCount, humanBytes(queuedBytes)),
                    fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ListModeRow(
    item: MediaItem,
    queued: Boolean,
    kept: Boolean,
    onKeep: () -> Unit,
    onToggleQueue: () -> Unit,
    onPreview: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val isMedia = item.mime.startsWith("image/") || item.mime.startsWith("video/")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surface)
            .clickable(onClick = onPreview)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMedia) {
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
                    .background(cs.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.displayName, maxLines = 1, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.deck_card_meta, humanBytes(item.sizeBytes), relativeDays(item.dateAddedSec)),
                fontSize = 12.sp,
                color = cs.onSurfaceVariant
            )
            if (queued || kept) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(if (kept) R.string.list_chip_kept else R.string.list_chip_queued),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = if (kept) cs.onTertiaryContainer else cs.onErrorContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (kept) cs.tertiaryContainer else cs.errorContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.common_btn_keep),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (kept) cs.onSurfaceVariant else cs.tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(enabled = !kept, onClick = onKeep)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Text(
                if (queued) stringResource(R.string.list_unqueue) else stringResource(R.string.common_btn_clean),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = cs.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onToggleQueue)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
