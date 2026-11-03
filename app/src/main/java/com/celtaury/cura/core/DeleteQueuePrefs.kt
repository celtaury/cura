package com.celtaury.cura.core

import android.content.Context

/**
 * Persists the user's "to delete" selection across app restarts.
 * Ensures that if a user leaves the app or turns off their screen,
 * their queued selections are never lost.
 */
object DeleteQueuePrefs {

    private const val PREFS = "cura_delete_queue"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getQueue(context: Context, scopeKey: String): Set<String> {
        val raw = prefs(context).getStringSet("q_$scopeKey", null) ?: return emptySet()
        return HashSet(raw)
    }

    fun setQueue(context: Context, scopeKey: String, uris: Set<String>) {
        prefs(context).edit().putStringSet("q_$scopeKey", HashSet(uris)).apply()
    }

    fun clear(context: Context, scopeKey: String) {
        prefs(context).edit().remove("q_$scopeKey").apply()
    }
}
