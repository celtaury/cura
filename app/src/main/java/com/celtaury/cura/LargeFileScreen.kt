package com.celtaury.cura

import androidx.compose.runtime.Composable
import com.celtaury.cura.core.Scanner
import com.celtaury.cura.ui.deck.DeckFlow

/**
 * Large Files, UX v2: the whole screen is now the shared Swipe Deck.
 * Big card, oldest judgement made with a flick: right = keep safe, left = queue.
 */
@Composable
fun LargeFilesScreen(onBack: () -> Unit) {
    DeckFlow(
        route = "large_files",
        title = "Large Files",
        subtitle = "30 MB and up · swipe to judge",
        loader = { context -> Scanner.largeFiles(context, minBytes = 30L * 1024 * 1024) },
        onBack = onBack
    )
}
