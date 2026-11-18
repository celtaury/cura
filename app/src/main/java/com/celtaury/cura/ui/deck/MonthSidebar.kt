package com.celtaury.cura.ui.deck

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
 * 1.0.2: the left drawer is GONE. Two users in a row reported "the month
 * picker did nothing" and the drawer's AnimatedVisibility path was the last
 * suspect standing — so the sidebar now renders as a ModalBottomSheet, the
 * exact mechanism the burst keep-picker uses, which is proven to open on
 * every device this app ships to. Same API, same call sites (deck pill /
 * calendar button / swipe-up, Browse calendar / month headers): a titled
 * sheet, one row per month, every row tappable — in the deck a month ahead
 * jumps to its first un-judged card, a passed month rewinds to its first
 * card, and the CURRENT month rewinds too (1.0.1). In Browse it scrolls the
 * grid to that month's header.
 *
 * [sliceDetail] renders each row's trailing text (deck: "N to judge" /
 * "revisit"; Browse: "N files · X MB") and [enabled] decides which rows are
 * tappable (the deck passes { true } since 1.0.1 — no dead rows, ever).
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    if (!visible) return
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = cs.surface
    ) {
        Text(
            stringResource(R.string.deck_jump_month),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
        )
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp)
        ) {
            slices.forEach { slice ->
                val isEnabled = enabled(slice)
                val rowAlpha = if (isEnabled) 1f else 0.45f
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = isEnabled) { onPick(slice) }
                        .padding(horizontal = 24.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        monthLabelOf(slice.key),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = cs.onSurface,
                        modifier = Modifier.alpha(rowAlpha)
                    )
                    Text(
                        sliceDetail(slice),
                        fontSize = 12.sp,
                        color = detailColor(slice),
                        modifier = Modifier.alpha(rowAlpha)
                    )
                }
            }
            if (slices.isEmpty()) {
                Text(
                    stringResource(R.string.common_nothing_left),
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
}
