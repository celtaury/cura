package com.celtaury.cura

import androidx.compose.runtime.Composable
import com.celtaury.cura.core.Scanner
import com.celtaury.cura.ui.deck.DeckFlow
import androidx.compose.ui.res.stringResource

/**
 * Large Files, UX v2: the whole screen is now the shared Swipe Deck.
 * Big card, oldest judgement made with a flick: right = keep safe, left = queue.
 */
@Composable
fun LargeFilesScreen(onBack: () -> Unit) {
    DeckFlow(
        route = "large_files",
        title = stringResource(R.string.lf_title),
        subtitle = stringResource(R.string.lf_sub),
        loader = { context -> Scanner.largeFiles(context, minBytes = 30L * 1024 * 1024) },
        onBack = onBack
    )
}
