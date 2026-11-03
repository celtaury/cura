package com.celtaury.cura.core

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * In-app bulk delete confirmation (#6): shows exactly what is about to be
 * deleted — thumbnails, count, total size — BEFORE the system dialog fires.
 *
 * The Swipe Deck keeps its own full-screen summary; every other flow
 * (compress, organize, tools) calls this instead of the raw delete controller.
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
    var pending by remember { mutableStateOf<List<MediaItem>?>(null) }
    val systemDelete = rememberDeleteController(
        onCleaned = onCleaned,
        onCancelled = onCancelled
    )

    val candidates = pending
    if (candidates != null) {
        ConfirmDeleteSheet(
            items = candidates,
            onConfirm = {
                pending = null
                systemDelete(candidates.map { it.uri })
            },
            onDismiss = {
                pending = null
                onCancelled()
            }
        )
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
    val noun = if (items.size == 1) "item" else "items"

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(
                "Delete ${items.size} $noun?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "About ${humanBytes(bytes)} · they land in Trash first (recoverable ~30 days)",
                fontSize = 13.sp,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
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
                    "Delete ${items.size} $noun · ${humanBytes(bytes)}",
                    fontWeight = FontWeight.Bold
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Keep them")
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
