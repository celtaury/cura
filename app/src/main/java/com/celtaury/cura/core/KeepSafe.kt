package com.celtaury.cura.core

import android.content.Context
import android.net.Uri

/**
 * The "Keep-safe" list: files the user marked as protected.
 * Every cleaner in Cura respects this list — nothing on it ever gets suggested for deletion.
 */
object KeepSafe {

    private const val PREFS = "cura_keepsafe"
    private const val KEY = "protected_uris"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): Set<String> =
        prefs(context).getStringSet(KEY, emptySet()) ?: emptySet()

    fun contains(context: Context, uri: Uri): Boolean = all(context).contains(uri.toString())

    fun add(context: Context, uri: Uri) {
        val set = all(context).toMutableSet()
        set.add(uri.toString())
        prefs(context).edit().putStringSet(KEY, set).apply()
    }

    fun remove(context: Context, uri: Uri) {
        val set = all(context).toMutableSet()
        set.remove(uri.toString())
        prefs(context).edit().putStringSet(KEY, set).apply()
    }
}
