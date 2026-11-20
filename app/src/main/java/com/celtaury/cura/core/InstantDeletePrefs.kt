package com.celtaury.cura.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * "Instant delete" — REDEFINED in 1.0.3 (user report: the confirm button
 * must NEVER be removed; what should change is the grace period):
 *
 * The in-app confirm sheet always shows, for everyone. What this toggle
 * changes is how long Cura's trashed copies rest: flagged entries are
 * hard-purged by Cura after [TrashLog.INSTANT_GRACE_DAYS] days (5) instead
 * of riding out Android's usual ~30-day sweep — a shorter leash, "just in
 * case". The system dialog on Android 11+ still belongs to the OS.
 * Default OFF.
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
