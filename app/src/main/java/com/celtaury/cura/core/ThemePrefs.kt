package com.celtaury.cura.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Theme override (Settings v1): System / Light / Dark.
 * Holds a Compose state so flipping the chip re-themes the whole app live.
 */
object ThemePrefs {

    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2

    private const val PREFS = "cura_settings"
    private const val KEY = "theme_mode"

    private val loaded = mutableStateOf(false)

    /** -1 until [load] runs; then SYSTEM/LIGHT/DARK. Compose-observed. */
    var mode by mutableStateOf(SYSTEM)
        private set

    fun load(context: Context) {
        if (loaded.value) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mode = prefs.getInt(KEY, SYSTEM)
        loaded.value = true
    }

    fun setMode(context: Context, value: Int) {
        mode = value.coerceIn(SYSTEM, DARK)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY, mode).apply()
    }
}
