package com.celtaury.cura.core

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.celtaury.cura.R

/**
 * In-app bulk delete confirmation (#6): shows exactly what is about to be
 * deleted — thumbnails, count, total size — BEFORE the system dialog fires.
 *
 * Every flow calls this instead of the raw delete controller — including the
 * swipe deck's summary, so selected items are always confirmed in-app first.
 * On success the batch lands in [TrashLog], which powers the Trash screen.
 *
 * Batch C: with [InstantDeletePrefs] enabled, the sheet is skipped entirely —
 * the same system flow (and the same trash log) fires straight away.
 *
 * Usage:
 *   val delete = rememberConfirmableDelete(onCleaned = { ... })
 *   delete(listOfMediaItems)  // sheet first, then the Android delete dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberConfirmableDelete(
    onCleaned: () -> Unit,
    onCancelled: () -> Unit = {}
): (List<MediaItem>) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<List<MediaItem>?>(null) }
    // The batch now facing the system dialog — kept so the recently-deleted
    // log can be written the moment Android reports success (Batch B).
    var inFlight by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    val systemDelete = rememberDeleteController(
        onCleaned = { deletedUris ->
            // Only the URIs Android actually deleted land in the log — a
            // batch the user half-denied must not pollute Trash & recovery.
            val gone = deletedUris.toSet()
            // Uri vs Set<Uri> — comparing uri.toString() (String) against this
            // set is a K2 type-parameter inference error, caught on device.
            val batch = inFlight.filter { it.uri in gone }
            if (batch.isNotEmpty()) {
                scope.launch(Dispatchers.IO) {
                    TrashLog.record(
                        context,
                        batch.map { item ->
                            TrashLog.Entry(
                                uri = item.uri.toString(),
                                name = item.displayName,
                                bytes = item.sizeBytes,
                                trashedAtMs = System.currentTimeMillis()
                            )
                        }
                    )
                }
            }
            onCleaned()
        },
        onCancelled = onCancelled
    )

    // Instant delete (Batch C): confident users skip the sheet entirely.
    // Same system flow, same trash log — just without the "are you sure?".
    LaunchedEffect(Unit) { InstantDeletePrefs.load(context) }
    val instant = InstantDeletePrefs.enabled

    val candidates = pending
    if (candidates != null && !instant) {
        ConfirmDeleteSheet(
            items = candidates,
            onConfirm = {
                inFlight = candidates
                pending = null
                systemDelete(candidates.map { it.uri })
            },
            onDismiss = {
                pending = null
                onCancelled()
            }
        )
    }

    // Instant mode: the picked batch fires straight through the SAME system
    // flow — inFlight is set first so success still writes the trash log.
    LaunchedEffect(candidates, instant) {
        if (instant && candidates != null) {
            inFlight = candidates
            pending = null
            systemDelete(candidates.map { it.uri })
        }
    }

    return remember(onCleaned) {
        { picked: List<MediaItem> ->
            if (picked.isNotEmpty()) pending = picked
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmDeleteSheet(
    items: List<MediaItem>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val bytes = items.sumOf { it.sizeBytes }
    

    // Expressive entrance: the sheet card slides fully into view in one go
    // (skipPartiallyExpanded), then the UI appears with opacity — the header
    // leads and the body trails 70 ms later, both on a soft spring.
    var headerIn by remember { mutableStateOf(false) }
    var bodyIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        headerIn = true
        delay(70)
        bodyIn = true
    }
    val headerT by animateFloatAsState(
        targetValue = if (headerIn) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
        label = "confirmHeaderIn"
    )
    val bodyT by animateFloatAsState(
        targetValue = if (bodyIn) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 320f),
        label = "confirmBodyIn"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = headerT
                        translationY = (1f - headerT) * 36f
                    }
            ) {
                Text(
                    pluralStringResource(R.plurals.cd_title, items.size, items.size),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.cd_about, humanBytes(bytes)),
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = bodyT
                        translationY = (1f - bodyT) * 48f
                    }
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.uri.toString() }) { item ->
                        val isMedia = item.mime.startsWith("image/") ||
                            item.mime.startsWith("video/")
                        if (isMedia) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = item.displayName,
                                modifier = Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            // APKs, docs, empty files — no bitmap to show, so an icon
                            // beats a blank square
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = item.displayName,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.error)
                ) {
                    Text(
                        pluralStringResource(R.plurals.cd_button, items.size, items.size, humanBytes(bytes)),
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cd_keep))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
