package com.celtaury.cura.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Batch 4 — the brain behind the dashboard leaf tile.
 *
 * UX rule: the leaf tile IS the suggestion. No menu, no separate suggestions
 * screen — the engine picks ONE real item, the tile renders it with the photo
 * embedded, and tapping jumps straight into the deck that owns it.
 *
 * Two feeding sources:
 *  1. Cheap background scans at pick time (screenshots, old media, forgotten).
 *  2. Live swipe judgments — the deck reports every decision via [observe].
 *     Judged items are never suggested again, and families the user tends to
 *     queue gain affinity while families they keep lose it.
 */
object SuggestionEngine {

    /** One concrete suggestion for the leaf tile. */
    data class Pick(
        val item: MediaItem,       // the photo shown inside the tile
        val reason: String,        // "Added 8 mo ago"
        val deckRoute: String,     // "" → single-photo sheet flow (forgotten)
        val familyTitle: String,   // "Old Screenshots"
        val familyCount: Int,      // how many like it are waiting
        val familyBytes: Long      // their combined size
    )

    private data class Family(
        val route: String,
        val title: String,
        val items: List<MediaItem>,
        val score: Int
    )

    // URIs the user already judged in any deck — never leaf-suggest these again.
    private val judged: MutableSet<String> =
        java.util.Collections.synchronizedSet(HashSet<String>())

    // family → (queued − kept) running score from live swipe judgments.
    private val affinity = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** The deck calls this for every judged card (kept or queued). */
    fun observe(item: MediaItem, action: String) {
        judged.add(item.uri.toString())
        familyOf(item)?.let { family ->
            affinity.merge(family, if (action == "kept") -1 else +1) { old, add -> old + add }
        }
    }

    /** Undo / cancelled-delete path — a reversed decision re-opens the file. */
    fun forget(item: MediaItem) {
        judged.remove(item.uri.toString())
    }

    /**
     * The current best suggestion, or null when the gallery has nothing new
     * to offer (the tile then rests on its 🌿 face).
     *
     * Ranking: old screenshots > old media > forgotten photo, with the live
     * affinity added on top — a user who keeps queueing screenshots pushes
     * old media aside, and a user who keeps everything silences the picker.
     */
    suspend fun pick(context: Context): Pick? = withContext(Dispatchers.IO) {
        // in-session judgments AND persisted ones (SuggestPrefs) both bury a file
        val buried = judged + SuggestPrefs.judged(context)
        val shots = Scanner.oldScreenshots(context, minAgeDays = 30)
            .filter { it.uri.toString() !in buried }
        val olds = Scanner.oldMedia(context, minAgeDays = 60)
            .filter { it.uri.toString() !in buried }
        val forgotten = Scanner.forgottenPhoto(context)
            ?.takeIf { it.uri.toString() !in buried }

        val families = listOfNotNull(
            if (shots.isNotEmpty()) {
                Family("deck_shots", "Old Screenshots", shots, 3 + (affinity["shots"] ?: 0))
            } else null,
            if (olds.isNotEmpty()) {
                Family("deck_oldmedia", "Old Media", olds, 2 + (affinity["old"] ?: 0))
            } else null
        ).filter { it.score > 0 }

        val best = families.maxByOrNull { it.score }
        when {
            best != null -> {
                val shown = best.items.random()
                Pick(
                    item = shown,
                    reason = reasonFor(shown),
                    deckRoute = best.route,
                    familyTitle = best.title,
                    familyCount = best.items.size,
                    familyBytes = best.items.sumOf { it.sizeBytes }
                )
            }
            forgotten != null -> Pick(
                item = forgotten,
                reason = "Abandoned photo",
                deckRoute = "",
                familyTitle = "Abandoned Photo Found!",
                familyCount = 1,
                familyBytes = forgotten.sizeBytes
            )
            else -> null
        }
    }

    /**
     * Fresh family list for the leaf-tile tap. Memory-backed decks
     * (deck_shots) get their items via DeckSource right before navigating;
     * scanner-backed ones (deck_oldmedia) would rescan anyway.
     */
    suspend fun familyItems(context: Context, deckRoute: String): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val items = when (deckRoute) {
                "deck_shots" -> Scanner.oldScreenshots(context, minAgeDays = 30)
                "deck_oldmedia" -> Scanner.oldMedia(context, minAgeDays = 60)
                else -> emptyList()
            }
            items.filter { it.uri.toString() !in judged }
        }

    /** Cheap classifier used at swipe time — no I/O, safe to call per card. */
    fun familyOf(item: MediaItem): String? {
        val p = item.path.lowercase()
        val n = item.displayName.lowercase()
        return when {
            p.contains("screenshot") || n.startsWith("screenshot") ||
                n.startsWith("screen-") || p.contains("screenrecorder") -> "shots"
            p.contains("whatsapp") || p.contains("telegram") ||
                p.contains("instagram") -> "social"
            item.dateAddedSec < System.currentTimeMillis() / 1000 - 60L * 86400 -> "old"
            else -> null
        }
    }

    private fun reasonFor(item: MediaItem): String =
        "Added ${relativeDays(item.dateAddedSec)}"
}
