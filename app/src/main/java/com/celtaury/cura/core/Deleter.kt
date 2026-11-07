package com.celtaury.cura.core

import android.app.Activity
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.celtaury.cura.R

/**
 * Shared delete flow. Android 11+ uses the system delete dialog (Play-ready);
 * older versions delete directly (media permissions already granted there).
 *
 * Usage:
 *   val delete = rememberDeleteController(onCleaned = { ... })
 *   delete(listOfUris)
 *
 * ── Why the ladder exists ────────────────────────────────────────────
 * [MediaStore.createDeleteRequest] is all-or-nothing: ONE bad URI in the
 * list refuses the whole request. Real-world triggers, WhatsApp media
 * especially (it manages its folder with direct file ops):
 *
 *   · ghost rows — MediaStore still lists a file that is already gone,
 *     so the provider can't identify the item
 *   · half-written / IS_PENDING items from interrupted downloads
 *   · very large queues — the request bundle stops fitting the Binder
 *     transaction (a WhatsApp folder can hold thousands of files)
 *   · OEM quirks refusing specific item types (voice notes, opus files)
 *
 * The old code swallowed the real exception and toasted a generic
 * "Couldn't start the system delete dialog" — for the whole queue. Now
 * the request walks a fallback ladder so one poisoned item can't sink
 * the rest:
 *
 *   1. one batch dialog for everything          (the happy path)
 *   2. retry the batch without dead rows        (ghosts probed via fd open)
 *   3. split into ≤100-item dialogs             (Binder-safe, isolates poison)
 *   4. per-item dialogs — each one is its own consent request
 *   5. direct contentResolver.delete() as the last resort, which also
 *      clears stale rows the provider refuses to touch
 *
 * Every refusal is logged under the "CuraDeleter" tag with the real
 * exception, so a device-specific refusal is diagnosable from logcat.
 */

private const val TAG = "CuraDeleter"
private const val CHUNK = 100

private fun firstReason(t: Throwable): String =
    t.message?.lineSequence()?.firstOrNull()?.take(90) ?: t.javaClass.simpleName

/**
 * Trash-first delete — the rule for EVERY path that removes a file without
 * the system dialog. From Android 10 on, flipping IS_TRASHED to 1 keeps the
 * file recoverable for ~30 days (identical outcome to Android 11+'s system
 * dialog). Only rows that can't be trashed (ghosts, pending items) and
 * Android 9- (no trash columns exist at all) fall through to a direct
 * delete. Returns true when the row is gone from the gallery.
 */
private fun trashOrDelete(context: Context, uri: Uri): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val trashed = runCatching {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_TRASHED, 1)
            }
            context.contentResolver.update(uri, values, null, null) > 0
        }.getOrDefault(false)
        if (trashed) return true
    }
    val rows = context.contentResolver.delete(uri, null, null)
    return rows > 0
}

@Composable
fun rememberDeleteController(
    onCleaned: (List<Uri>) -> Unit,
    onCancelled: () -> Unit = {}
): (List<Uri>) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val ladder = remember { SystemDeleteLadder(context, scope) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        ladder.onDialogResult(result.resultCode == Activity.RESULT_OK)
    }

    ladder.go = { sender ->
        runCatching {
            launcher.launch(IntentSenderRequest.Builder(sender).build())
        }.onFailure { e ->
            Log.e(TAG, "Couldn't launch the system dialog", e)
            ladder.stop(context.getString(R.string.del_dialog_failed, firstReason(e)))
        }
    }

    return { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ladder.onCleaned = onCleaned
                ladder.onCancelled = onCancelled
                ladder.start(uris)
            } else {
                // Pre-Android 11: no system dialog. Android 10 trashes first
                // (recoverable, same as everywhere else); Android 9- deletes
                // directly — media permissions already granted there.
                scope.launch {
                    val done = mutableListOf<Uri>()
                    withContext(Dispatchers.IO) {
                        uris.forEach { u ->
                            // Boolean is pinned by the call itself; an
                            // if-without-else as the runCatching tail left the
                            // type parameter unmentionable for K2 (2 errors).
                            runCatching { trashOrDelete(context, u) }
                                .onSuccess { gone -> if (gone) done.add(u) }
                                .onFailure { e ->
                                    Log.e(TAG, "Direct delete failed: $u", e)
                                }
                        }
                    }
                    if (done.isNotEmpty()) {
                        onCleaned(done.toList())
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.del_blocked),
                            Toast.LENGTH_LONG
                        ).show()
                        onCancelled()
                    }
                }
            }
        }
    }
}

/**
 * The retry ladder for Android 11+. Strictly sequential: at most one system
 * dialog is in flight at any time; every next step is launched from the
 * previous dialog's result (or synchronously when no dialog was needed).
 *
 * The only API-30+ call in here is [tryRequest]'s
 * [MediaStore.createDeleteRequest], which is SDK-guarded right where it
 * happens — so the class itself carries no @RequiresApi and stays lint-clean
 * even though minSdk is 26.
 *
 * Success is tracked per-URI ([grantedUris]), not as a count: a user who
 * grants one dialog and denies the next must not log the denied files as
 * deleted — the trash log only ever hears about real deletions.
 */
private class SystemDeleteLadder(
    private val context: Context,
    private val scope: CoroutineScope
) {
    var go: ((IntentSender) -> Unit)? = null
    var onCleaned: ((List<Uri>) -> Unit)? = null
    var onCancelled: (() -> Unit)? = null

    private val chunks = ArrayDeque<List<Uri>>()   // remaining batched dialogs
    private val singles = ArrayDeque<Uri>()        // remaining per-item requests
    private var pendingUris = emptyList<Uri>()     // items the in-flight dialog answers for
    private var userStopped = false                // user denied a dialog — stop popping more
    private var dialogsShown = 0                   // dialogs that actually reached the user
    private val grantedUris = mutableListOf<Uri>() // confirmed deleted (trashed or cleared)
    private val deniedUris = mutableListOf<Uri>()  // refused by user or platform

    fun start(input: List<Uri>) {
        chunks.clear(); singles.clear()
        pendingUris = emptyList()
        userStopped = false
        grantedUris.clear(); deniedUris.clear()

        val uris = sanitize(input)
        if (uris.isEmpty()) {
            onCancelled?.invoke()
            return
        }
        dialogsShown = 0

        scope.launch {
            // 1) one dialog for the whole queue — the happy path
            val whole = tryRequest(uris)
            if (whole != null) {
                Log.i(TAG, "System delete dialog for ${uris.size} item(s)")
                // the LIST goes in the dialog, not its size — launchBatch wants
                // the URIs the dialog answers for (C3: was uris.size, an Int)
                launchBatch(whole, uris)
                return@launch
            }

            // 2) probe the queue: rows whose file is already gone (or unreadable)
            //    poison the batch — when the queue is big, retry the survivors
            //    wholesale before falling apart into smaller pieces
            val alive = withContext(Dispatchers.IO) { readableOnly(uris) }
            val dead = uris.filter { it !in alive }

            if (dead.isNotEmpty() && alive.size > CHUNK) {
                val pruned = tryRequest(alive)
                if (pruned != null) {
                    Log.i(
                        TAG,
                        "Delete dialog for ${alive.size} item(s) after dropping ${dead.size} dead row(s)"
                    )
                    // C3: was alive.size (Int) — launchBatch needs the list
                    launchBatch(pruned, alive)
                    return@launch
                }
            }

            // 3) split it: survivors as chunk dialogs (or straight to per-item
            //    when the queue is small), dead rows last — a direct delete can
            //    still clear a stale row even when the provider refuses it
            if (alive.size > CHUNK) {
                alive.chunked(CHUNK).forEach { chunks.addLast(it) }
            } else {
                singles.addAll(alive)
            }
            singles.addAll(dead)
            Log.i(
                TAG,
                "Batch refused — falling back to ${chunks.size} chunk dialog(s) + ${singles.size} per-item request(s)"
            )
            launchNext()
        }
    }

    /** The system dialog (batch / chunk / single) came back. */
    fun onDialogResult(ok: Boolean) {
        val answered = pendingUris
        pendingUris = emptyList()
        if (ok) {
            grantedUris += answered
        } else {
            deniedUris += answered
            userStopped = true
        }
        launchNext()
    }

    /** A dialog could not even be launched (app backgrounded, etc.) — safe stop. */
    fun stop(reason: String?) {
        if (grantedUris.isNotEmpty()) {
            onCleaned?.invoke(grantedUris.toList())
        } else {
            Toast.makeText(
                context,
                reason ?: context.getString(R.string.del_dialog_failed_short),
                Toast.LENGTH_LONG
            ).show()
            onCancelled?.invoke()
        }
    }

    private fun launchNext() {
        if (userStopped) {
            finish()
            return
        }

        val single = singles.removeFirstOrNull()
        if (single != null) {
            val pi = tryRequest(listOf(single))
            if (pi != null) {
                dialogsShown++
                launchBatch(pi, listOf(single))
            } else {
                scope.launch(Dispatchers.IO) { directDelete(single) }
            }
            return
        }

        val chunk = chunks.removeFirstOrNull()
        if (chunk != null) {
            val pi = tryRequest(chunk)
            if (pi != null) {
                Log.i(TAG, "Chunked delete dialog for ${chunk.size} item(s)")
                launchBatch(pi, chunk)
            } else {
                Log.e(TAG, "Chunk refused (${chunk.size} items) — going item-by-item")
                singles.addAll(chunk)
                launchNext()
            }
            return
        }

        finish()
    }

    private suspend fun directDelete(uri: Uri) {
        try {
            val gone = trashOrDelete(context, uri)
            Log.i(TAG, "Direct delete $uri → ${if (gone) "trashed/cleared" else "refused"}")
            if (gone) grantedUris += uri else deniedUris += uri
        } catch (e: SecurityException) {
            // needs user consent that this flow can't provide — give up on it
            Log.e(TAG, "Direct delete blocked for $uri", e)
            deniedUris += uri
        } catch (e: Exception) {
            Log.e(TAG, "Direct delete failed for $uri", e)
            deniedUris += uri
        }
        withContext(Dispatchers.Main) { launchNext() }
    }

    private fun launchBatch(pi: PendingIntent, uris: List<Uri>) {
        pendingUris = uris
        dialogsShown++
        go?.invoke(pi.intentSender)
    }

    /** The single API-30+ call in the whole app, guarded right here. */
    private fun tryRequest(list: List<Uri>): PendingIntent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { MediaStore.createDeleteRequest(context.contentResolver, list) }
                .onFailure { e ->
                    Log.e(TAG, "createDeleteRequest refused (${list.size} item(s))", e)
                }
                .getOrNull()
        } else {
            null
        }

    private fun finish() {
        Log.i(
            TAG,
            "Delete finished: ${grantedUris.size} granted, ${deniedUris.size} refused, $dialogsShown dialog(s) shown"
        )
        if (grantedUris.isNotEmpty()) {
            onCleaned?.invoke(grantedUris.toList())
        } else {
            // Only speak up when nothing reached the user at all — a deliberate
            // "No" on the system dialog stays silent, that was their choice.
            if (deniedUris.isNotEmpty() && dialogsShown == 0) {
                Toast.makeText(
                    context,
                    context.getString(R.string.del_refused),
                    Toast.LENGTH_LONG
                ).show()
            }
            onCancelled?.invoke()
        }
    }

    /** Only MediaStore item URIs (content://media/.../<numeric id>) can be deleted. */
    private fun sanitize(uris: List<Uri>): List<Uri> =
        uris.distinct().filter { u ->
            u.authority == MediaStore.AUTHORITY &&
                u.lastPathSegment?.toLongOrNull() != null
        }

    /** Rows we can no longer open are ghosts — the file is already gone on disk. */
    private fun readableOnly(uris: List<Uri>): List<Uri> =
        uris.filter { u ->
            runCatching {
                context.contentResolver.openFileDescriptor(u, "r")?.use { true } ?: false
            }.getOrDefault(false)
        }
}

/**
 * Best-effort "Undo": from Android 10 on, Cura's deletions land in the system
 * Trash (IS_TRASHED — see [trashOrDelete]), so we flip it back. Returns the
 * number of restored files. Android 9- keeps no trash — nothing to restore.
 * If the platform refuses, callers show the "open Files app → Trash" hint.
 */
suspend fun tryRestoreFromTrash(context: Context, uris: List<Uri>): Int =
    withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext 0
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
