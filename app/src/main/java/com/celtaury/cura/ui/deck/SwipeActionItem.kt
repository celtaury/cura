package com.celtaury.cura.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celtaury.cura.R
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * THE list-row gesture, shared (Batch K).
 *
 * One swipable row container for EVERY judging list in the app — Quick
 * Clean, Social Cleaner, Large Files list mode, Suggest list mode — so the
 * interaction is finally identical everywhere:
 *
 *   · swipe LEFT  → queue for deletion (or un-queue when already queued)
 *   · swipe RIGHT → keep safe
 *   · tap         → whatever the content does (usually preview)
 *   · no selection state, no checkboxes — the action fires the INSTANT the
 *     finger lifts past the threshold, then the row springs back in
 *     parallel ("action first, snapback second" — the old ~2 s delay bug).
 *
 * Visuals: red reveal behind a left drag, green behind a right drag, icon
 * scales up with drag progress. Previously QuickCleanScreen and
 * SocialCleanerScreen each carried a private copy of this exact composable;
 * they now both render this one, and the list modes that never had swipes
 * (Large Files, Suggest) inherit the same gesture for free.
 */
@Composable
fun SwipeActionItem(
    isQueued: Boolean,
    modifier: Modifier = Modifier,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    content: @Composable () -> Unit
) {
    val offsetX = remember { androidx.compose.animation.core.Animatable(0f) }
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
            // Swiping right: green (Keep Safe)
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
            // Swiping left: red (Queue / un-queue)
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
                        if (isQueued) stringResource(R.string.common_remove_queue)
                        else stringResource(R.string.common_queue_delete),
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

        // Foreground content with the bouncy spring snapback
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
                            // soft spring delayed feedback by ~2 s (old bug #6).
                            when {
                                offsetX.value > thresholdPx -> onSwipeRight()
                                offsetX.value < -thresholdPx -> onSwipeLeft()
                            }
                            scope.launch {
                                offsetX.animateTo(
                                    0f,
                                    androidx.compose.animation.core.spring(
                                        dampingRatio = 0.8f, stiffness = 700f
                                    )
                                )
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(
                                    0f,
                                    androidx.compose.animation.core.spring(
                                        dampingRatio = 0.8f, stiffness = 700f
                                    )
                                )
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}
