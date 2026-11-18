package com.celtaury.cura.ui.deck

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.celtaury.cura.R
import com.celtaury.cura.core.MediaItem

/**
 * Batch L: the burst keep-picker. A burst no longer parades its frames past
 * the deck one by one — the stack card opens THIS sheet: every frame as a
 * tile, one confirm applies the split. Keeps go to Keep Safe; deletes join
 * the queue and open the same confirm sheet as every other delete in the
 * app. The representative frame (first member) starts as the preselected
 * keeper.
 *
 * Batch O (user report: "on burst I can't preview the image I want … at
 * least I need a way to preview images without squinting"): the whole-tile
 * tap no longer flips keep/delete — that gesture now OPENS the full-screen
 * paged viewer ([BurstPreviewDialog]), where every frame can be seen
 * full-size and flipped through like a gallery. Judging moved to the
 * corner badge chip (and a long-press, for muscle memory from Batch L) in
 * the grid, and to a full-width "Keep this one" chip inside the viewer —
 * so you can flip through the burst and mark keepers without ever
 * squinting at a 3-column thumbnail.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun BurstPicker(
    members: List<MediaItem>,
    // 1.0.1 (user report: keep marks vanished on reopen): the sheet now
    // starts from the marks made last time and reports every flip back,
    // so closing and reopening a burst never loses your triage.
    initialKeep: Set<String> = emptySet(),
    onKeeps: (Set<String>) -> Unit = {},
    onConfirm: (List<MediaItem>) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var keepUris by remember(members) {
        mutableStateOf(
            initialKeep.ifEmpty { setOf(members.first().uri.toString()) }
        )
    }
    // Batch O: full-screen viewer state — -1 = closed, else the member index.
    var previewAt by remember(members) { mutableIntStateOf(-1) }
    fun toggleKeep(m: MediaItem) {
        val uri = m.uri.toString()
        val next = if (uri in keepUris) keepUris - uri else keepUris + uri
        keepUris = next
        onKeeps(next)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(
                stringResource(R.string.deck_burst_pick_title, members.size),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.deck_burst_pick_sub),
                fontSize = 13.sp,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().height(340.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(members, key = { _, m -> m.uri.toString() }) { at, m ->
                    val kept = m.uri.toString() in keepUris
                    Box(
                        modifier = Modifier
                            .aspectRatio(0.8f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(cs.surfaceVariant)
                            .border(
                                width = if (kept) 3.dp else 1.dp,
                                color = if (kept) cs.primary else cs.outlineVariant,
                                shape = RoundedCornerShape(14.dp)
                            )
                            // Batch O: tap = SEE the photo full-size (the whole
                            // point of this sheet is choosing, and choosing
                            // needs looking); long-press keeps the old
                            // flip-keep gesture for fast triage.
                            .combinedClickable(
                                onClick = { previewAt = at },
                                onLongClick = { toggleKeep(m) }
                            )
                    ) {
                        AsyncImage(
                            model = m.uri,
                            contentDescription = m.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        // the judge chip — corner badge flips keep ⇄ delete
                        Text(
                            if (kept) "✓" else "🗑",
                            fontSize = 16.sp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .clip(CircleShape)
                                .background(if (kept) cs.primary else cs.scrim.copy(alpha = 0.55f))
                                .clickable { toggleKeep(m) }
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
                Button(
                    onClick = {
                        onConfirm(members.filter { it.uri.toString() in keepUris })
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        stringResource(
                            R.string.deck_burst_pick_confirm,
                            keepUris.size,
                            members.size - keepUris.size
                        ),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (previewAt >= 0 && previewAt < members.size) {
        BurstPreviewDialog(
            members = members,
            initialIndex = previewAt,
            keepUris = keepUris,
            onToggleKeep = ::toggleKeep,
            onDismiss = { previewAt = -1 }
        )
    }
}

/**
 * Batch O: full-screen Quick-Look-style viewer for ONE burst, paged.
 * Swipe left/right (or flip pages) to compare every frame of the series at
 * full size — the "search easily" ask. The bottom chip toggles the CURRENT
 * photo's keep verdict without leaving the viewer, so a burst can be judged
 * entirely full-screen: flip, flip, keep, flip, done.
 */
@Composable
private fun BurstPreviewDialog(
    members: List<MediaItem>,
    initialIndex: Int,
    keepUris: Set<String>,
    onToggleKeep: (MediaItem) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { members.size }
    )
    val current = members[pagerState.currentPage.coerceIn(0, members.lastIndex)]
    val kept = current.uri.toString() in keepUris

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {} // consume clicks inside
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── header: position counter · filename · close ──
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        stringResource(R.string.prev_burst_pos, pagerState.currentPage + 1, members.size),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                    Text(
                        current.displayName,
                        color = Color.White,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.prev_close_cd),
                            tint = Color.White
                        )
                    }
                }

                // ── the frames, full-size, paged ──
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { page ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = members[page].uri,
                            contentDescription = members[page].displayName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(18.dp))
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                // ── the verdict chip for the photo ON SCREEN ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BurstPreviewChip(
                        label = if (kept) stringResource(R.string.prev_burst_keeping)
                        else stringResource(R.string.prev_burst_keep_this),
                        active = kept,
                        activeColor = Color(0xFF388E3C),
                        onClick = { onToggleKeep(current) }
                    )
                    BurstPreviewChip(
                        label = stringResource(R.string.prev_burst_done),
                        active = false,
                        activeColor = Color(0xFF666666),
                        onClick = onDismiss
                    )
                }
                Text(
                    stringResource(R.string.prev_burst_swipe_hint),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/** One tappable chip in the burst viewer — the verdict buttons. */
@Composable
private fun BurstPreviewChip(
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Text(
        label,
        color = if (active) Color.White else Color.White.copy(alpha = 0.85f),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) activeColor.copy(alpha = 0.38f) else Color(0xFF222222).copy(alpha = 0.88f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}
