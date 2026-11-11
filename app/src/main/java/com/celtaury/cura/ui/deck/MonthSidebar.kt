package com.celtaury.cura.ui.deck

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celtaury.cura.R
import com.celtaury.cura.core.monthLabelOf

/** One calendar month inside a deck/browsable list: its key and its indexes. */
data class MonthSlice(val key: String, val indexes: List<Int>)

/**
 * THE month picker, shared (Batch K — "use the month pick everywhere, and
 * make it work").
 *
 * A LEFT-SIDE drawer: opened by the deck's month pill, the top-bar calendar
 * button, a swipe UP on the deck card — and now also by the Browse gallery's
 * calendar button and tappable month headers. Every month is pickable: in
 * the deck a month ahead jumps to its first un-judged card and a passed
 * month REWINDS to its first card (skipped months come back instead of
 * reading as "judged"); in Browse it scrolls the grid to that month.
 *
 * [sliceDetail] renders each row's trailing text (deck: "N to judge" /
 * "revisit"; Browse: "N files · X MB") and [enabled] decides which rows are
 * tappable (deck: everything but the current month; Browse: all).
 */
@Composable
fun MonthSidebar(
    visible: Boolean,
    slices: List<MonthSlice>,
    currentIndex: Int,
    currentKey: String?,
    onPick: (MonthSlice) -> Unit,
    onDismiss: () -> Unit,
    sliceDetail: @Composable (MonthSlice) -> String = { slice ->
        val left = slice.indexes.count { it >= currentIndex }
        when {
            slice.key == currentKey -> stringResource(R.string.deck_here)
            left > 0 -> stringResource(R.string.deck_n_to_judge, left)
            else -> stringResource(R.string.deck_month_revisit)
        }
    },
    enabled: (MonthSlice) -> Boolean = { it.key != currentKey },
    detailColor: @Composable (MonthSlice) -> Color = { slice ->
        val left = slice.indexes.count { it >= currentIndex }
        val cs = MaterialTheme.colorScheme
        when {
            left > 0 && slice.key != currentKey -> cs.primary
            slice.key != currentKey -> cs.tertiary
            else -> cs.onSurfaceVariant
        }
    }
) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(cs.scrim.copy(alpha = 0.45f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .background(cs.surface)
                    .padding(20.dp)
            ) {
                Text(
                    stringResource(R.string.deck_jump_month),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.deck_month_sub),
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.fillMaxHeight()) {
                    items(slices, key = { it.key }) { slice ->
                        val isCurrent = slice.key == currentKey
                        val rowEnabled = enabled(slice)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCurrent) cs.primaryContainer else Color.Transparent)
                                .clickable(enabled = rowEnabled) { onPick(slice) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                monthLabelOf(slice.key),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                sliceDetail(slice),
                                fontSize = 12.sp,
                                color = detailColor(slice)
                            )
                        }
                    }
                }
            }
        }
    }
}
