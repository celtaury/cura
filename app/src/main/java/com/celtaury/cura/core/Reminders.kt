package com.celtaury.cura.core

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import com.celtaury.cura.MainActivity
import com.celtaury.cura.R

/**
 * 1.0 (user request): "Add notification option (also add as popup after user
 * first clean session) to enable notification to notify user every X day/week
 * (adjustable), default 2 day once."
 *
 * Three small pieces, following the Donation.kt pattern:
 * - [RemindersPrefs] — SharedPreferences: enabled, interval days (default 2),
 *   and whether the first-session popup has had its one chance.
 * - [Reminders] — AlarmManager scheduling (inexact, Doze-safe, no special
 *   permission) + the notification channel. The receiver re-arms the next
 *   alarm, so the chain self-perpetuates; [ReminderBootReceiver] restarts it
 *   after reboot or app update.
 * - [ReminderPrompt] — app-wide compose flag the MainActivity host observes,
 *   exactly like DonationPrompt. Set once after the FIRST finished clean
 *   session; the popup (ReminderDialog.kt) offers the toggle + interval.
 */
object RemindersPrefs {

    private const val PREFS = "cura_reminders"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_INTERVAL = "interval_days"
    private const val KEY_ASKED = "popup_asked"

    const val DEFAULT_INTERVAL_DAYS = 2

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    /** Toggling is the single place that arms/cancels the alarm. */
    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, on).apply()
        if (on) Reminders.schedule(context) else Reminders.cancel(context)
    }

    fun intervalDays(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_INTERVAL, DEFAULT_INTERVAL_DAYS)

    fun setIntervalDays(context: Context, days: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_INTERVAL, days.coerceIn(1, 30)).apply()
        // keep the armed alarm on the new cadence
        if (enabled(context)) Reminders.schedule(context)
    }

    fun asked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ASKED, false)

    fun markAsked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ASKED, true).apply()
    }

    /**
     * Called from every finished clean session (deck, Quick Clean, Suggest —
     * the same call sites as DonationPrefs.noteSessionDone). Two jobs:
     * offer the popup once, and re-anchor an active reminder so "every 2
     * days" counts from the LAST sweep — a fresh clean resets the clock.
     */
    fun noteSessionDone(context: Context) {
        if (enabled(context)) Reminders.schedule(context)
        if (!asked(context)) ReminderPrompt.request()
    }
}

/** App-wide flag the MainActivity host observes — no DI, no event bus. */
object ReminderPrompt {
    var pending by mutableStateOf(false)
        private set

    fun request() {
        pending = true
    }

    fun dismiss() {
        pending = false
    }
}

object Reminders {

    const val CHANNEL_ID = "cura_reminders"
    private const val REQUEST_CODE = 1001
    const val NOTIFICATION_ID = 2001

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.rem_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Arm the NEXT reminder one interval out — inexact is fine for days. */
    fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val interval = RemindersPrefs.intervalDays(context) * 86_400_000L
        am.cancel(alarmIntent(context))
        am.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + interval,
            alarmIntent(context)
        )
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(alarmIntent(context))
    }

    /** Tap → open the app (MainActivity is the single entry point). */
    fun contentIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

/** Fires one nudge, then re-arms the chain. Never a hard dependency. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!RemindersPrefs.enabled(context)) return
        Reminders.ensureChannel(context)
        val notification = NotificationCompat.Builder(context, Reminders.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.rem_notif_title))
            .setContentText(context.getString(R.string.rem_notif_body))
            .setAutoCancel(true)
            .setContentIntent(Reminders.contentIntent(context))
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.notify(Reminders.NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked since scheduling — stay quiet,
            // the chain keeps ticking and Android handles permission state.
        }
        Reminders.schedule(context)
    }
}

/**
 * AlarmManager registrations do not survive reboot OR app update — this
 * receiver re-arms the chain in both cases, but only if the user asked for
 * reminders in the first place.
 */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!RemindersPrefs.enabled(context)) return
        Reminders.ensureChannel(context)
        Reminders.schedule(context)
    }
}
