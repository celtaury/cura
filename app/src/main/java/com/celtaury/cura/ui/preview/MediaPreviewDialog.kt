package com.celtaury.cura.ui.preview

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.celtaury.cura.core.MediaItem
import com.celtaury.cura.core.humanBytes
import com.celtaury.cura.core.relativeDays
import kotlin.math.abs
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.celtaury.cura.R

/** Which action the current preview drag is armed for.
 *  Prefixed (Preview…) — K2 flags same-name top-level types across files, even private. */
private enum class PreviewDragTarget { None, Queue, Keep, Close }

/**
 * Fast, Apple Quick Look-style media preview dialog with Material 3 Expressive motion:
 * - Instant rendering from memory cache
 * - "Queued ✓" badge in the header when the item is already queued for cleanup
 * - Videos PLAY inline — media3 ExoPlayer, tap toggles, drag still judges
 * - Immersive dark backdrop
 * - File details & metadata
 * - Gestures that ANNOUNCE themselves: the action row at the bottom is the
 *   legend — tap it or swipe it, same result. While dragging, the matching
 *   chip lights up, a big "Release · …" pill appears on the media, and a
 *   haptic tick confirms the threshold is crossed. No guesswork.
 */
@Composable
fun MediaPreviewDialog(
    item: MediaItem,
    isQueued: Boolean,
    onToggleQueue: () -> Unit,
    onKeepSafe: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val dragX = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    val gestureThreshold = with(LocalDensity.current) { 110.dp.toPx() }
    // The "armed" state shows before the commit threshold, so people see what
    // will happen while there is still time to change their mind.
    val armThreshold = gestureThreshold * 0.62f

    // Dominant-axis drag target — recomposes only when the armed action flips.
    val dragTarget by remember {
        derivedStateOf {
            val x = dragX.value
            val y = dragY.value
            val ax = abs(x)
            val ay = abs(y)
            when {
                ax >= ay && ax >= armThreshold -> if (x < 0f) PreviewDragTarget.Queue else PreviewDragTarget.Keep
                ay > ax && y >= armThreshold -> PreviewDragTarget.Close
                else -> PreviewDragTarget.None
            }
        }
    }

    // Haptic tick each time the drag arms a new action — felt, not guessed.
    LaunchedEffect(dragTarget) {
        if (dragTarget != PreviewDragTarget.None) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // ── Inline video playback (the old VideoPreviewSheet is retired) ──
    val isVideo = item.mime.startsWith("video/")
    val context = LocalContext.current
    val exoPlayer = if (isVideo) remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(item.uri))
            prepare()
            playWhenReady = true
        }
    } else null
    var isPlaying by remember(item.uri) { mutableStateOf(true) }
    var videoWasPlaying by remember { mutableStateOf(false) }
    DisposableEffect(item.uri) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer?.addListener(listener)
        onDispose {
            exoPlayer?.removeListener(listener)
            exoPlayer?.release()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            // Entrance pop-in (scale 0.85→1 + fade) as a plain animated Box —
            // deliberately NOT AnimatedVisibility: inside a Column/Box scope
            // Kotlin prefers the ColumnScope.AnimatedVisibility overload, which
            // on recent Compose versions rejects the old call shape and turns
            // into a red "no applicable signature" IDE error. The dialog itself
            // already animates on open.
            val appear = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                appear.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 420f))
            }
            Box(
                modifier = Modifier.graphicsLayer {
                    val s = 0.85f + 0.15f * appear.value
                    scaleX = s
                    scaleY = s
                    alpha = appear.value
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {} // Consume click inside
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ── Top Header Bar ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.displayName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(R.string.common_item_meta, humanBytes(item.sizeBytes), relativeDays(item.dateAddedSec)),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }

                        if (isQueued) {
                            Text(
                                stringResource(R.string.prev_queued_short),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.85f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

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

                    // ── Media Content Preview ──
                    val isMedia = item.mime.startsWith("image/") || item.mime.startsWith("video/")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationX = dragX.value
                                translationY = dragY.value
                                alpha = 1f - (maxOf(abs(dragX.value), abs(dragY.value)) / (gestureThreshold * 1.6f))
                                    .coerceIn(0f, 0.55f)
                            }
                            .pointerInput(item.uri) {
                                // Tap toggles playback (video only; no-op for
                                // images). Coexists with the drag detector:
                                // clean taps fire, drags get consumed.
                                if (isVideo) {
                                    detectTapGestures(
                                        onTap = { exoPlayer?.let { p -> p.playWhenReady = !p.playWhenReady } }
                                    )
                                }
                            }
                            .pointerInput(item.uri) {
                                detectDragGestures(
                                    onDragStart = {
                                        // Flinging the card shouldn't fight the
                                        // soundtrack — pause, resume on snap-back.
                                        if (isVideo && exoPlayer?.isPlaying == true) {
                                            videoWasPlaying = true
                                            exoPlayer?.pause()
                                        }
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        scope.launch {
                                            // Axis lock: the media slides ONLY toward the
                                            // action it will perform — no diagonal mush.
                                            val ax = abs(dragX.value + amount.x)
                                            val ay = abs(dragY.value + amount.y)
                                            if (ax >= ay) {
                                                dragX.snapTo((dragX.value + amount.x).coerceIn(-620f, 620f))
                                                if (abs(dragY.value) > 1f) dragY.snapTo(dragY.value * 0.7f)
                                            } else {
                                                dragY.snapTo((dragY.value + amount.y).coerceIn(-620f, 620f))
                                                if (abs(dragX.value) > 1f) dragX.snapTo(dragX.value * 0.7f)
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        val x = dragX.value
                                        val y = dragY.value
                                        when {
                                            // left = queue · right = keep — deck parity
                                            abs(x) > gestureThreshold && abs(x) >= abs(y) -> {
                                                if (x < 0f) onToggleQueue() else onKeepSafe()
                                                onDismiss()
                                            }
                                            y > gestureThreshold && y > abs(x) -> onDismiss()
                                            else -> scope.launch {
                                                dragX.animateTo(
                                                    0f,
                                                    spring(dampingRatio = 0.75f, stiffness = 500f)
                                                )
                                                dragY.animateTo(
                                                    0f,
                                                    spring(dampingRatio = 0.75f, stiffness = 500f)
                                                )
                                                if (videoWasPlaying) {
                                                    videoWasPlaying = false
                                                    exoPlayer?.play()
                                                }
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        scope.launch {
                                            dragX.animateTo(
                                                0f,
                                                spring(dampingRatio = 0.75f, stiffness = 500f)
                                            )
                                            dragY.animateTo(
                                                0f,
                                                spring(dampingRatio = 0.75f, stiffness = 500f)
                                            )
                                            if (videoWasPlaying) {
                                                videoWasPlaying = false
                                                exoPlayer?.play()
                                            }
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isMedia) {
                            if (isVideo) {
                                // Real playback via media3 — the old
                                // VideoPreviewSheet bottom-sheet is gone.
                                // useController = false: OUR gesture layer owns
                                // touch (tap = play/pause, drag = judge).
                                // Rounded corners skipped: SurfaceView won't
                                // reliably clip.
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            player = exoPlayer
                                            useController = false
                                        }
                                    },
                                    update = { it.player = exoPlayer },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                AsyncImage(
                                    model = item.uri,
                                    contentDescription = item.displayName,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(18.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            if (isVideo && !isPlaying) {
                                // Paused badge — the ONLY time the play glyph shows.
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.PlayCircle,
                                        contentDescription = "Paused",
                                        tint = Color.White,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                        } else {
                            // Non-visual document/audio card
                            Box(
                                modifier = Modifier
                                    .size(160.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color.White.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.Description,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(54.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        item.mime.substringAfterLast('/'),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // ── "Release · …" pill: announces the armed action ──
                        PreviewPill(visible = dragTarget != PreviewDragTarget.None) {
                            val (label, tint) = when (dragTarget) {
                                PreviewDragTarget.Queue ->
                                    // `to` at END of line: K2 rejects a leading infix
                                    // operator ("Function invocation 'to(...)' expected")
                                    (if (isQueued) stringResource(R.string.prev_release_unqueue) else stringResource(R.string.prev_release_queue)) to
                                        MaterialTheme.colorScheme.error
                                PreviewDragTarget.Keep -> stringResource(R.string.prev_release_keep) to Color(0xFF388E3C)
                                PreviewDragTarget.Close -> stringResource(R.string.prev_release_close) to Color(0xFF444444)
                                PreviewDragTarget.None -> "" to Color.Transparent
                            }
                            Text(
                                label,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(tint.copy(alpha = 0.88f))
                                    .padding(horizontal = 18.dp, vertical = 10.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── Gesture row: the buttons ARE the legend ──
                    // Same mapping as the swipes — tap or swipe, same result.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PreviewGestureChip(
                            label = if (isQueued) stringResource(R.string.prev_queued_badge) else stringResource(R.string.prev_queue_delete),
                            active = dragTarget == PreviewDragTarget.Queue,
                            activeColor = MaterialTheme.colorScheme.error,
                            onClick = {
                                onToggleQueue()
                                onDismiss()
                            }
                        )
                        PreviewGestureChip(
                            label = stringResource(R.string.prev_close),
                            active = dragTarget == PreviewDragTarget.Close,
                            activeColor = Color(0xFF666666),
                            onClick = onDismiss
                        )
                        PreviewGestureChip(
                            label = stringResource(R.string.prev_keep),
                            active = dragTarget == PreviewDragTarget.Keep,
                            activeColor = Color(0xFF388E3C),
                            onClick = {
                                onKeepSafe()
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * One tappable gesture chip. Doubles as the gesture legend: lights up and
 * grows while the matching drag is armed, so the mapping is impossible to miss.
 */
@Composable
private fun PreviewGestureChip(
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (active) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 420f),
        label = "chipScale"
    )
    val bg by animateColorAsState(
        targetValue = if (active) activeColor.copy(alpha = 0.38f) else Color(0xFF222222).copy(alpha = 0.88f),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 420f),
        label = "chipBg"
    )
    Text(
        label,
        color = if (active) Color.White else Color.White.copy(alpha = 0.85f),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

/**
 * Entrance for the "Release · …" pill: scale 0.8→1 + fade in, fade out.
 *
 * Deliberately wraps the FULLY-QUALIFIED top-level
 * androidx.compose.animation.AnimatedVisibility. Inside a Column/Box scope,
 * Kotlin prefers the ColumnScope.AnimatedVisibility overload over the
 * top-level function; on recent Compose versions that candidate no longer
 * accepts the old call shape, and the call turns into a red "no applicable
 * signature" IDE error. A fully-qualified call cannot be hijacked by scope.
 */
@Composable
private fun PreviewPill(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = 380f), initialScale = 0.8f) + fadeIn(),
        exit = fadeOut()
    ) {
        content()
    }
}
