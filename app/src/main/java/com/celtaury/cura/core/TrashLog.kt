package com.celtaury.cura.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cura's own recently-deleted log (Batch B).
 *
 * Why this exists: Android keeps deleted media in its system Trash for ~30
 * days, but hides most of that trash from third-party apps — and only
 * Android 11+ even lets an app ASK for trash-only rows
 * (QUERY_ARG_MATCH_TRASHED), which several OEM builds answer inconsistently
 * (0-row results reported in the wild). So the one thing Cura can always
 * promise is its own record of what IT deleted: what / when / how big.
 * The Trash screen reads this log as the primary source and treats any
 * system-trash rows it manages to see as a bonus, never as the promise.
 *
 * Storage: one JSON array in SharedPreferences, capped at [MAX_ENTRIES]
 * and pruned after 31 days — by then Android has purged the trash anyway.
 */
object TrashLog {

    private const val PREFS = "cura_trash_log"
    private const val KEY = "entries"
    private const val MAX_ENTRIES = 500
    private const val KEEP_MS = 31L * 24 * 60 * 60 * 1000

    /** Android's own trash window — the honest basis for "purges in ~N days". */
    const val TRASH_WINDOW_DAYS = 30L

    /** One file Cura deleted. [uri] lets restore try the IS_TRASHED flip-back. */
    data class Entry(
        val uri: String,
        val name: String,
        val bytes: Long,
        val trashedAtMs: Long
    )

    /** Record a successful delete. Call from Dispatchers.IO. Dedupes by URI. */
    fun record(context: Context, items: List<Entry>) {
        if (items.isEmpty()) return
        val prefs = prefs(context)
        val current = parse(prefs.getString(KEY, null) ?: "[]").toMutableList()
        for (item in items) {
            current.removeAll { it.uri == item.uri }
            current.add(item)
        }
        save(prefs, current)
    }

    /** Newest first, pruned. Call from Dispatchers.IO. */
    fun entries(context: Context): List<Entry> {
        val prefs = prefs(context)
        val all = parse(prefs.getString(KEY, null) ?: "[]")
        val fresh = all.filter { System.currentTimeMillis() - it.trashedAtMs < KEEP_MS }
        if (fresh.size != all.size) save(prefs, fresh)
        return fresh.sortedByDescending { it.trashedAtMs }
    }

    /** A restored file is no longer "recently deleted" — drop it. Call from IO. */
    fun removeRestored(context: Context, uris: Set<String>) {
        if (uris.isEmpty()) return
        val prefs = prefs(context)
        val current = parse(prefs.getString(KEY, null) ?: "[]")
        save(prefs, current.filterNot { it.uri in uris })
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun save(prefs: SharedPreferences, list: List<Entry>) {
        val capped = list.sortedByDescending { it.trashedAtMs }.take(MAX_ENTRIES)
        val arr = JSONArray()
        capped.forEach { entry ->
            arr.put(
                JSONObject().apply {
                    put("uri", entry.uri)
                    put("name", entry.name)
                    put("bytes", entry.bytes)
                    put("trashedAtMs", entry.trashedAtMs)
                }
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun parse(raw: String): List<Entry> = runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Entry(
                uri = o.optString("uri"),
                name = o.optString("name", "(file)"),
                bytes = o.optLong("bytes"),
                trashedAtMs = o.optLong("trashedAtMs")
            )
        }.filter { it.uri.isNotBlank() }
    }.getOrDefault(emptyList())
}
