package com.celtaury.cura.core

import android.content.Context

/**
 * The continuity hook: "Cura has saved you X GB" — persisted across launches.
 * Every successful clean feeds this counter.
 */
object SavedStats {

    private const val PREFS = "cura_stats"
    private const val KEY_SAVED = "saved_bytes_total"
    private const val KEY_CLEANS = "clean_sessions"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun savedBytes(context: Context): Long =
        prefs(context).getLong(KEY_SAVED, 0L)

    fun cleanSessions(context: Context): Int =
        prefs(context).getInt(KEY_CLEANS, 0)

    /** Record one completed clean session of [bytes] freed. */
    fun addClean(context: Context, bytes: Long) {
        val p = prefs(context)
        p.edit()
            .putLong(KEY_SAVED, p.getLong(KEY_SAVED, 0L) + bytes)
            .putInt(KEY_CLEANS, p.getInt(KEY_CLEANS, 0) + 1)
            .apply()
    }
}
