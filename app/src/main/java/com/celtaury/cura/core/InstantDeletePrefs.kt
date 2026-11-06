package com.celtaury.cura.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * "Instant delete" (Batch C, Settings toggle): skip Cura's confirm sheet and
 * fire the delete flow straight away — for confident users who don't want
 * the "are you sure?" beat.
 *
 * Honest by design: the system dialog on Android 11+ still shows (that one
 * belongs to the OS — Cura can't skip it), and every file still lands in
 * Trash & recovery for ~30 days. Default OFF — the confirm sheet stays the
 * safe default for everyone else.
 */
object InstantDeletePrefs {

    private const val PREFS = "cura_settings"
    private const val KEY = "instant_delete"

    private val loaded = mutableStateOf(false)

    /** Compose-observed: flipping the Switch updates every delete flow live. */
    var enabled by mutableStateOf(false)
        private set

    fun load(context: Context) {
        if (loaded.value) return
        enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY, false)
        loaded.value = true
    }

    fun set(context: Context, value: Boolean) {
        enabled = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, value).apply()
    }
}
