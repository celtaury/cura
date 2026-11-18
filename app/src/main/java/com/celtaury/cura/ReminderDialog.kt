package com.celtaury.cura

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.celtaury.cura.core.ReminderPrompt
import com.celtaury.cura.core.RemindersPrefs
import com.celtaury.cura.core.Reminders

/** The five cadences — the popup and the Settings card share this exact list. */
internal val reminderIntervals: List<Pair<Int, Int>> = listOf(
    1 to R.string.rem_int_1d,
    2 to R.string.rem_int_2d,
    3 to R.string.rem_int_3d,
    7 to R.string.rem_int_1w,
    14 to R.string.rem_int_2w
)

internal fun reminderIntervalLabel(days: Int): Int =
    reminderIntervals.firstOrNull { it.first == days }?.second ?: R.string.rem_int_2d

/**
 * 1.0: shared enable/disable flow for the reminder toggle, used by BOTH the
 * first-session popup and the Settings card. On Android 13+ enabling asks
 * for POST_NOTIFICATIONS first; the preference is saved either way (a denial
 * just means the nudges won't display — Android drops them silently — and
 * the toast points to system settings).
 */
@Composable
internal fun rememberReminderToggle(): (Context, Boolean) -> Unit {
    val context = LocalContext.current
    var pendingEnable by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted && pendingEnable) {
            Toast.makeText(
                context,
                context.getString(R.string.rem_perm_denied),
                Toast.LENGTH_LONG
            ).show()
        }
        RemindersPrefs.setEnabled(context, pendingEnable)
        pendingEnable = false
    }
    return { _, enable ->
        pendingEnable = enable
        val needsAsk = enable &&
            Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsAsk) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            RemindersPrefs.setEnabled(context, enable)
            pendingEnable = false
        }
    }
}

/**
 * The one-time reminder offer, shown after the FIRST finished clean session
 * (RemindersPrefs.noteSessionDone — same moment as the donation counter).
 * Asks once, ever; "Not now" marks it asked. Settings → Cleaning & deletes →
 * Reminders stays reachable for changing cadence or turning it off later.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderPopupDialog() {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme

    if (!ReminderPrompt.pending) return

    var days by remember {
        mutableIntStateOf(RemindersPrefs.intervalDays(context))
    }
    val toggle = rememberReminderToggle()

    fun settle() {
        RemindersPrefs.markAsked(context)
        ReminderPrompt.dismiss()
    }

    AlertDialog(
        onDismissRequest = ::settle,
        title = {
            Text(
                stringResource(R.string.rem_popup_title),
                fontWeight = FontWeight.Bold,
                color = cs.onSurface
            )
        },
        text = {
            Column {
                Text(stringResource(R.string.rem_popup_body), color = cs.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    reminderIntervals.forEach { (value, label) ->
                        FilterChip(
                            selected = days == value,
                            onClick = { days = value },
                            label = { Text(stringResource(label)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                RemindersPrefs.setIntervalDays(context, days)
                toggle(context, true)
                settle()
            }) {
                Text(
                    stringResource(R.string.rem_popup_enable),
                    fontWeight = FontWeight.Bold,
                    color = cs.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = ::settle) {
                Text(stringResource(R.string.rem_popup_later), color = cs.onSurfaceVariant)
            }
        }
    )
}
