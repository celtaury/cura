package com.celtaury.cura

import androidx.compose.runtime.Composable
import com.celtaury.cura.core.Scanner
import com.celtaury.cura.ui.deck.DeckFlow
import com.celtaury.cura.ui.deck.DeckFlowForRoute
import androidx.compose.ui.res.stringResource

/**
 * Screenshots deck, UX v2: shared Swipe Deck over the 30-day-old screenshot pile.
 */
@Composable
fun OldScreenshotsScreen(onBack: () -> Unit) {
    DeckFlow(
        route = "screenshots",
        title = stringResource(R.string.deck_shots_title),
        subtitle = stringResource(R.string.os_sub),
        loader = { context -> Scanner.oldScreenshots(context, minAgeDays = 30) },
        onBack = onBack
    )
}

/**
 * Old Media deck (UX v2): photos & videos untouched for 60+ days.
 * Screenshots and chat-app media are excluded — they live in their own decks.
 */
@Composable
fun OldMediaScreen(onBack: () -> Unit) {
    DeckFlowForRoute(route = "deck_oldmedia", onBack = onBack)
}
