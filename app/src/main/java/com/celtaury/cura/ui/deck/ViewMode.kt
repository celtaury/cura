package com.celtaury.cura.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celtaury.cura.R
import com.celtaury.cura.core.DeckPrefs

/**
 * Deck ⇄ list view toggle (Batch H). A compact two-segment pill that lives
 * in a tool's top bar; the choice persists per route via [DeckPrefs], so
 * every tool remembers how you like to judge it.
 *
 * [route] is the deck/tool id ("large_files", "suggest", "social", …);
 * [onModeChanged] lets the host re-render when the segment flips.
 */
@Composable
fun ViewModeToggle(
    route: String,
    onModeChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var deck by remember { mutableStateOf(DeckPrefs.isDeck(context, route)) }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(cs.surfaceVariant)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ViewModeSegment(
            label = stringResource(R.string.common_view_deck),
            selected = deck,
            selectedColor = cs.primaryContainer,
            selectedContent = cs.onPrimaryContainer
        ) {
            if (!deck) {
                deck = true
                DeckPrefs.setDeck(context, route, true)
                onModeChanged(true)
            }
        }
        ViewModeSegment(
            label = stringResource(R.string.common_view_list),
            selected = !deck,
            selectedColor = cs.primaryContainer,
            selectedContent = cs.onPrimaryContainer
        ) {
            if (deck) {
                deck = false
                DeckPrefs.setDeck(context, route, false)
                onModeChanged(false)
            }
        }
    }
}

@Composable
private fun ViewModeSegment(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    selectedContent: Color,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Text(
        label,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        color = if (selected) selectedContent else cs.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) selectedColor else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
