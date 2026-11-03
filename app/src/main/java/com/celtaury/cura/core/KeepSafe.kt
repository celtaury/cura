package com.celtaury.cura.core

import android.content.Context
import android.net.Uri

/**
 * The "Keep-safe" list: files the user marked as protected.
 * Every cleaner in Cura respects this list — nothing on it ever gets suggested for deletion.
 * Stores protected URIs along with saved timestamps and source origins.
 */
object KeepSafe {

    private const val PREFS = "cura_keepsafe"
    private const val KEY = "protected_uris"
    private const val KEY_META = "meta_"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): Set<String> =
        prefs(context).getStringSet(KEY, emptySet()) ?: emptySet()

    fun contains(context: Context, uri: Uri): Boolean = all(context).contains(uri.toString())

    fun add(context: Context, uri: Uri, source: String = "Manual") {
        val set = all(context).toMutableSet()
        set.add(uri.toString())
        val now = System.currentTimeMillis()
        prefs(context).edit()
            .putStringSet(KEY, set)
            .putString("$KEY_META${uri}", "$now|$source")
            .apply()
    }

    fun remove(context: Context, uri: Uri) {
        val set = all(context).toMutableSet()
        set.remove(uri.toString())
        prefs(context).edit()
            .putStringSet(KEY, set)
            .remove("$KEY_META${uri}")
            .apply()
    }

    fun sourceOf(context: Context, uri: Uri): String? {
        val meta = prefs(context).getString("$KEY_META${uri}", null) ?: return null
        return meta.substringAfter('|', "")
    }

    fun savedTimeOf(context: Context, uri: Uri): Long {
        val meta = prefs(context).getString("$KEY_META${uri}", null) ?: return 0L
        return meta.substringBefore('|').toLongOrNull() ?: 0L
    }
}
