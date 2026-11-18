package com.celtaury.cura.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Batch M: the one-time donation prompt.
 *
 * Metric (deliberately boring): a counter of FINISHED clean sessions. The
 * dialog asks once — after the SECOND completed session, so the user has
 * felt the value twice and it never interrupts day one. Dismissing marks it
 * shown forever; the Settings → Support card stays the standing way to
 * donate (CuraLinks.DONATION_URL).
 */
object DonationPrefs {

    private const val PREFS = "cura_donation"
    private const val KEY_SESSIONS = "clean_sessions"
    private const val KEY_SHOWN = "popup_shown"

    private const val SHOW_AFTER_SESSIONS = 2

    /** Call when a clean session finishes (deck, Quick Clean, Suggest). */
    fun noteSessionDone(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sessions = p.getInt(KEY_SESSIONS, 0) + 1
        p.edit().putInt(KEY_SESSIONS, sessions).apply()
        if (sessions >= SHOW_AFTER_SESSIONS && !p.getBoolean(KEY_SHOWN, false)) {
            DonationPrompt.request()
        }
    }

    /** The Settings → Support card opens the dialog on demand — always. */
    fun requestNow() {
        DonationPrompt.request()
    }

    fun markShown(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHOWN, true).apply()
        DonationPrompt.dismiss()
    }
}

/** App-wide flag the MainActivity host observes — no DI, no event bus. */
object DonationPrompt {
    var pending by mutableStateOf(false)
        private set

    fun request() {
        pending = true
    }

    fun dismiss() {
        pending = false
    }
}
