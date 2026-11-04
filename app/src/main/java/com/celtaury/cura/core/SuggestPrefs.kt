package com.celtaury.cura.core

import android.content.Context

/**
 * Long-term memory for the Suggest deck (overhaul round 1).
 *
 * Until now the suggestion brain was memory-only: kill the app and every file
 * you had judged came back as a "fresh" suggestion. SuggestPrefs makes the
 * memory survive restarts:
 *
 * - judged  : URIs decided anywhere (kept, queued, cleaned) — never suggested again
 * - skipped : "Not today" taps with a skip count — they sink in the ranking
 * - cards   : the live session as "mediaStoreId|tag" lines, so a killed app
 *             re-hydrates the same 10 cards via Scanner.itemsByIds
 *
 * Deck position + pending queue ride on the existing DeckPrefs under the
 * "suggest" deck id — same continuity model as every other deck.
 */
object SuggestPrefs {

    private const val PREFS = "cura_suggest"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── judged ───────────────────────────────────────────────────────

    fun judged(context: Context): Set<String> =
        prefs(context).getStringSet("judged", emptySet()) ?: emptySet()

    fun markJudged(context: Context, uri: String) {
        val updated = judged(context).toMutableSet()
        updated += uri
        prefs(context).edit().putStringSet("judged", updated).apply()
    }

    /**
     * Reverse lever for undo and cancelled deletes: the file is still on the
     * device, so it must stay eligible for future suggestions.
     */
    fun unjudge(context: Context, uri: String) {
        val updated = judged(context).toMutableSet()
        updated -= uri
        prefs(context).edit().putStringSet("judged", updated).apply()
    }

    // ── skipped ("uri\tcount" per line) ──────────────────────────────

    fun skipCounts(context: Context): Map<String, Int> {
        val raw = prefs(context).getString("skipped", null) ?: return emptyMap()
        val out = HashMap<String, Int>()
        raw.split("\n").filter { it.isNotBlank() }.forEach { line ->
            val parts = line.split("\t")
            if (parts.size == 2) {
                val count = parts[1].toIntOrNull() ?: 0
                if (count > 0) out[parts[0]] = count
            }
        }
        return out
    }

    fun markSkipped(context: Context, uri: String) {
        val current = skipCounts(context)
        val next = current.toMutableMap()
        next[uri] = (current[uri] ?: 0) + 1
        prefs(context).edit().putString(
            "skipped",
            next.entries.joinToString("\n") { "${it.key}\t${it.value}" }
        ).apply()
    }

    // ── session cards (order = deck order) ───────────────────────────

    fun savedCards(context: Context): List<Pair<Long, String>> {
        val raw = prefs(context).getString("cards", null) ?: return emptyList()
        return raw.split("\n").filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size == 2) parts[0].toLongOrNull()?.let { id -> id to parts[1] }
            else null
        }
    }

    fun setCards(context: Context, cards: List<Pair<Long, String>>) {
        prefs(context).edit()
            .putString("cards", cards.joinToString("\n") { "${it.first}|${it.second}" })
            .apply()
    }

    /** Consume the resting session (cards only — judged/skips are forever). */
    fun clearSession(context: Context) {
        prefs(context).edit().remove("cards").apply()
    }
}
