package com.celtaury.cura.core

import android.content.Context

/**
 * Deck continuity (UX v2 decision 22): leave a review deck anytime and your
 * position AND pending queue survive — per deck, persisted.
 *
 * - index: how many cards the user has already judged
 * - queue: URIs swiped left (marked for cleaning), newline-joined to keep order
 *
 * A successful clean clears both. Swiping right saves instantly to KeepSafe,
 * so "kept" decisions need no extra memory here.
 */
object DeckPrefs {

    private const val PREFS = "cura_decks"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun index(context: Context, deckId: String): Int =
        prefs(context).getInt("idx_$deckId", 0)

    fun setIndex(context: Context, deckId: String, index: Int) {
        prefs(context).edit().putInt("idx_$deckId", index).apply()
    }

    fun queue(context: Context, deckId: String): List<String> {
        val raw = prefs(context).getString("q_$deckId", null) ?: return emptyList()
        return raw.split("\n").filter { it.isNotBlank() }
    }

    fun setQueue(context: Context, deckId: String, uris: List<String>) {
        prefs(context).edit().putString("q_$deckId", uris.joinToString("\n")).apply()
    }

    /** Clear a deck's memory — used after a successful clean. */
    fun clear(context: Context, deckId: String) {
        prefs(context).edit()
            .remove("idx_$deckId")
            .remove("q_$deckId")
            .apply()
    }

    /** First-run coach marks (Batch C): shown once, ever, per install. */
    fun coachDone(context: Context): Boolean =
        prefs(context).getBoolean("coach_done", false)

    fun setCoachDone(context: Context) {
        prefs(context).edit().putBoolean("coach_done", true).apply()
    }

    /**
     * Batch H: deck ⇄ list view preference, per tool route. Default is the
     * deck — the list is the alternative for people who'd rather scan rows.
     */
    fun isDeck(context: Context, deckId: String): Boolean =
        prefs(context).getBoolean("view_$deckId", true)

    fun setDeck(context: Context, deckId: String, deck: Boolean) {
        prefs(context).edit().putBoolean("view_$deckId", deck).apply()
    }
}
