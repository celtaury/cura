package com.celtaury.cura.core

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared delete flow. Android 11+ uses the system delete dialog (Play-ready);
 * older versions delete directly (media permissions already granted there).
 *
 * Usage:
 *   val delete = rememberDeleteController(onCleaned = { ... })
 *   delete(listOfUris)
 */
@Composable
fun rememberDeleteController(onCleaned: () -> Unit): (List<Uri>) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) onCleaned()
    }

    return remember(onCleaned) {
        { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    runCatching {
                        val pi = MediaStore.createDeleteRequest(context.contentResolver, uris)
                        launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    }.onFailure {
                        Toast.makeText(
                            context,
                            "Couldn't start the system delete dialog",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    scope.launch {
                        var failures = 0
                        withContext(Dispatchers.IO) {
                            uris.forEach { u ->
                                runCatching { context.contentResolver.delete(u, null, null) }
                                    .onFailure { failures++ }
                            }
                        }
                        when {
                            failures == 0 -> onCleaned()
                            failures == uris.size -> Toast.makeText(
                                context,
                                "This Android version blocked the delete 🥲",
                                Toast.LENGTH_LONG
                            ).show()
                            else -> onCleaned() // partial success — the rescan shows the truth
                        }
                    }
                }
            }
        }
    }
}

/**
 * Best-effort "Undo": on Android 11+ media deletions land in the system Trash,
 * so we try to flip IS_TRASHED back. Returns the number of restored files.
 * If the platform refuses, callers show the "open Files app → Trash" hint.
 */
suspend fun tryRestoreFromTrash(context: Context, uris: List<Uri>): Int =
    withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext 0
        var restored = 0
        uris.forEach { u ->
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_TRASHED, 0)
                }
                if (context.contentResolver.update(u, values, null, null) > 0) restored++
            }
        }
        restored
    }
