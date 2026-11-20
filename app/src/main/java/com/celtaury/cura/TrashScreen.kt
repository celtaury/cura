package com.celtaury.cura

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import com.celtaury.cura.core.CuraStrings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celtaury.cura.core.TrashLog
import com.celtaury.cura.core.humanBytes
import com.celtaury.cura.core.openSystemFilesApp
import com.celtaury.cura.core.rememberDeleteController
import com.celtaury.cura.core.rememberRestoreController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri

/**
 * One row in the recovery list. Two sources feed it:
 *  - fromLog = true  → Cura's own recently-deleted log (always available)
 *  - fromLog = false → a row the system trash query actually returned (bonus)
 */
private data class TrashedFile(
    val uri: Uri,
    val name: String,
    val bytes: Long,
    /** Days the trash keeps it: 30 normally, 5 for Instant-delete entries (1.0.3). */
    val graceDays: Long,
    /** Epoch ms the trash purges it (exact for live rows, an estimate for log rows). */
    val expiresAt: Long?,
    /** When Cura deleted it (log rows only). */
    val trashedAtMs: Long?,
    val fromLog: Boolean
)

/**
 * Trash & recovery (Batch B): everything Cura deleted recently, restorable
 * in one tap — no trip to the Files app required.
 *
 * Honest-data design: Android hides most of the system trash from third-party
 * apps (and only Android 11+ lets us even ask for trash-only rows, which some
 * OEMs answer with zero rows). So the PRIMARY source is Cura's own log of
 * what it deleted (what / when / size); system-trash rows are merged in when
 * Android returns them. The screen copy says exactly that.
 *
 * Written to the elderly test: short words, big buttons. Since Batch F the
 * screen also offers "Delete everything" — a double-confirmed purge (in-app
 * dialog first, Android's own consent dialog on 11+), so nothing vanishes
 * in one accidental tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    val files = remember { mutableStateListOf<TrashedFile>() }
    var loading by remember { mutableStateOf(true) }
    // Android 9 and older keep no queryable trash — fall back to instructions.
    var listSupported by remember { mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) }
    var restoring by remember { mutableStateOf(false) }

    // Log first (always works), system-trash rows merged in as a bonus.
    // (Declared BEFORE the purge/restore code below on purpose: Kotlin local
    //  functions cannot be referenced before their declaration point.)
    suspend fun reload() = withContext(Dispatchers.IO) {
        // 1.0.3: Instant-delete entries older than 5 days are hard-deleted
        // HERE, not at Android's 30-day leisure — that is the whole point
        // of the shortened grace.
        TrashLog.purgeInstantExpired(context)
        val logged = TrashLog.entries(context).map { entry ->
            val grace = if (entry.instant) TrashLog.INSTANT_GRACE_DAYS else TrashLog.TRASH_WINDOW_DAYS
            TrashedFile(
                // 1.0.3: logged URIs can go stale (row renamed/swept by the
                // OEM) — restore and thumbnails go through the LIVE row.
                uri = refreshTrashedUri(context, entry.uri.toUri(), entry.name, entry.bytes),
                name = entry.name,
                bytes = entry.bytes,
                graceDays = grace,
                expiresAt = entry.trashedAtMs + grace * 86_400_000L,
                trashedAtMs = entry.trashedAtMs,
                fromLog = true
            )
        }
        val seenUris = logged.map { it.uri.toString() }.toSet()
        val live = queryTrash(context).filter { row -> row.uri.toString() !in seenUris }
        files.clear()
        files.addAll(logged + live)
    }

    // ── Delete everything (Batch F) — this screen's one destructive act.
    var purgeOpen by remember { mutableStateOf(false) }
    var purging by remember { mutableStateOf(false) }

    fun requestPurge() {
        if (purging || restoring || files.isEmpty()) return
        purgeOpen = true
    }

    val purgeController = rememberDeleteController(
        onCleaned = { deletedUris ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    // A purge is an EXIT, not another delete: the entries
                    // must LEAVE the log. (Only the confirm-sheet wrapper
                    // re-records deletes; the raw controller doesn't.)
                    TrashLog.removeRestored(context, deletedUris.map { it.toString() }.toSet())
                }
                reload()
                purging = false
            }
        },
        onCancelled = { purging = false },
        permanent = true
    )

    fun purgeConfirmed() {
        purging = true
        scope.launch {
            val (live, ghosts) = withContext(Dispatchers.IO) {
                files.toList().partition { rowExists(context, it.uri) }
            }
            if (ghosts.isNotEmpty()) {
                // Rows the OS already purged (its ~30-day sweep): deleting
                // them again is impossible — purging means leaving the log.
                TrashLog.removeRestored(context, ghosts.map { it.uri.toString() }.toSet())
            }
            if (live.isEmpty()) {
                reload()
                purging = false
            } else {
                purgeController(live.map { it.uri })
            }
        }
    }

    LaunchedEffect(Unit) {
        if (listSupported) reload()
        loading = false
    }

    // 1.0.3 (user report: "hard to actually recover image"): restore walks
    // the same ladder the delete flow has — silent flip-back first, then
    // the system's own un-trash consent dialog (createTrashRequest(false))
    // for whatever the platform refused. The silent-only pass used to fail
    // on most real content, because those rows belong to other apps.
    var restoreTotal by remember { mutableStateOf(0) }
    val restoreController = rememberRestoreController(
        onRestored = { restoredUris ->
            scope.launch {
                Toast.makeText(
                    context,
                    restoreToastMessage(context, restoredUris.size, restoreTotal),
                    Toast.LENGTH_LONG
                ).show()
                withContext(Dispatchers.IO) {
                    // Only entries that ACTUALLY came back may leave the log —
                    // a file the platform refused keeps its record (and its
                    // place on this screen) for another try.
                    TrashLog.removeRestored(context, restoredUris.map { it.toString() }.toSet())
                }
                reload()
                restoring = false
            }
        },
        onCancelled = {
            scope.launch {
                Toast.makeText(
                    context,
                    restoreToastMessage(context, 0, restoreTotal),
                    Toast.LENGTH_LONG
                ).show()
                restoring = false
            }
        }
    )

    fun requestRestore(picked: List<TrashedFile>) {
        if (restoring || picked.isEmpty()) return
        restoring = true
        restoreTotal = picked.size
        restoreController(picked.map { it.uri })
    }

    // Hoisted into composition (a composable read): survives locale changes
    // and keeps the LocalContext resource lint quiet.
    val filesHint = stringResource(R.string.common_files_hint)

    fun openFilesApp() {
        // 1.0.3 (user report: "go to files dont actually work"): a real
        // ladder over the Files apps instead of one fragile package pin.
        if (!openSystemFilesApp(context)) {
            Toast.makeText(context, filesHint, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.trash_title), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            stringResource(R.string.trash_subtitle),
                            fontSize = 12.sp,
                            color = cs.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back_cd))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            when {
                // ── Pre-Android-10: no queryable trash, same honest instructions ──
                !listSupported -> {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = cs.surface)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Text(
                                stringResource(R.string.trash_files_only),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.trash_files_steps),
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { openFilesApp() },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = MaterialTheme.shapes.large
                            ) { Text(stringResource(R.string.trash_open_files)) }
                        }
                    }
                }

                loading ->
                    Text(
                        stringResource(R.string.trash_loading),
                        fontSize = 13.sp,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp)
                    )

                // ── Empty: reassure, and explain what WILL happen ──
                files.isEmpty() -> {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = cs.surface)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(20.dp)) {
                            Text(stringResource(R.string.trash_empty_title), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.trash_empty_body),
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // ── The list: Cura's log first, soonest-to-vanish first ──
                else -> {
                    val totalBytes = files.sumOf { it.bytes }
                    Text(
                        pluralStringResource(R.plurals.trash_waiting, files.size, files.size, humanBytes(totalBytes)),
                        fontSize = 13.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
                    )
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(files, key = { it.uri.toString() }) { file ->
                            TrashRow(file = file, onRestore = { requestRestore(listOf(file)) })
                        }
                    }
                    Button(
                        onClick = { requestRestore(files.toList()) },
                        enabled = !restoring,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(52.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(
                            if (restoring) stringResource(R.string.trash_restoring) else stringResource(R.string.trash_restore_all),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(
                        onClick = { requestPurge() },
                        enabled = !purging && !restoring,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.trash_purge_btn),
                            color = cs.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    TextButton(
                        onClick = { openFilesApp() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.trash_open_files_instead)) }
                    // 1.0.3 (user report: "show where the trash is"): the
                    // exact steps under the button, not buried in Settings.
                    Text(
                        stringResource(R.string.trash_files_steps),
                        fontSize = 11.sp,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    )
                    Text(
                        stringResource(R.string.trash_footer),
                        fontSize = 11.sp,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                    )
                }
            }
        }
    }

    if (purgeOpen) {
        AlertDialog(
            onDismissRequest = { purgeOpen = false },
            title = { Text(stringResource(R.string.trash_purge_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.trash_purge_body, files.size, files.size,
                        humanBytes(files.sumOf { it.bytes })
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    purgeOpen = false
                    purgeConfirmed()
                }) {
                    Text(
                        stringResource(R.string.trash_purge_btn),
                        color = cs.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { purgeOpen = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun TrashRow(file: TrashedFile, onRestore: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val thumbFallback = rememberVectorPainter(Icons.Default.Description)
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1.0.3 (user report: thumbnails not showing): trashed rows are
            // hidden from the plain queries Coil runs, so its load 404s and
            // the doc icon showed instead. Decode ourselves — the thumbnail
            // API first, the raw bytes second; the icon stays the honest
            // fallback when the platform refuses both.
            val context = LocalContext.current
            val thumb by produceState<ImageBitmap?>(initialValue = null, key1 = file.uri) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.loadThumbnail(
                            file.uri, android.util.Size(160, 160), null
                        )
                    }.recoverCatching {
                        context.contentResolver.openInputStream(file.uri)?.use { input ->
                            android.graphics.BitmapFactory.decodeStream(
                                input,
                                null,
                                android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }
                            )
                        }
                    }.getOrNull()?.asImageBitmap()
                }
            }
            if (thumb != null) {
                Image(
                    bitmap = thumb!!,
                    contentDescription = file.name,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                val ext = file.name.substringAfterLast('.', "").uppercase().take(4)
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(cs.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (ext.isNotEmpty() && ext.length in 2..4) {
                        Text(
                            text = ext,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurfaceVariant
                        )
                    } else {
                        Icon(
                            thumbFallback,
                            contentDescription = file.name,
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    metaLine(file),
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant
                )
            }
            TextButton(onClick = onRestore) { Text(stringResource(R.string.trash_restore)) }
        }
    }
}

/**
 * Toast copy for a restore attempt. Lives outside composition on purpose:
 * two of the three messages carry runtime quantities, and stringResource /
 * pluralStringResource are @Composable-only — there is no composable API for
 * a plural formatted inside a coroutine callback.
 *
 * Suppressed: LocalContextGetResourceValueCall would prefer stringResource,
 * but these strings are consumed the instant they are built (a Toast that
 * shows and dies) — nothing is cached across configuration changes, so the
 * stale-read risk the check guards against cannot reach the user.
 */
@SuppressLint("LocalContextGetResourceValueCall")
private fun restoreToastMessage(context: Context, restored: Int, total: Int): String = when {
    restored == total -> context.resources.getQuantityString(
        R.plurals.trash_back_gallery, restored, restored
    )
    restored > 0 -> context.getString(R.string.trash_partial, restored, total)
    else -> context.getString(R.string.trash_locked)
}

/**
 * 1.0.3: a logged URI may no longer resolve (the row was swept, renamed or
 * re-ided by the OEM) — look the trashed row back up by name and size to restore /
 * draw thumbnails against the LIVE id. Returns the original URI when
 * nothing better is found. Call from Dispatchers.IO.
 */
private fun refreshTrashedUri(context: Context, uri: Uri, name: String, bytes: Long = 0L): Uri {
    if (rowExists(context, uri)) return uri
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return uri
    val onR = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    val collections = listOf(
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    )
    val hasSize = bytes > 0L
    for (collection in collections) {
        val found = runCatching {
            if (onR) {
                val bundle = Bundle().apply {
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                    val sel = if (hasSize) {
                        "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.SIZE} = ?"
                    } else {
                        "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                    }
                    val args = if (hasSize) arrayOf(name, bytes.toString()) else arrayOf(name)
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, sel)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                }
                context.contentResolver.query(
                    collection, arrayOf(MediaStore.MediaColumns._ID), bundle, null
                )
            } else {
                val sel = if (hasSize) {
                    "${MediaStore.MediaColumns.IS_TRASHED} = 1 AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.SIZE} = ?"
                } else {
                    "${MediaStore.MediaColumns.IS_TRASHED} = 1 AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                }
                val args = if (hasSize) arrayOf(name, bytes.toString()) else arrayOf(name)
                context.contentResolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    sel,
                    args,
                    null
                )
            }
        }.recoverCatching {
            // OEM refused the bundle — the plain selection still works on Q
            val sel = if (hasSize) {
                "${MediaStore.MediaColumns.IS_TRASHED} = 1 AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.SIZE} = ?"
            } else {
                "${MediaStore.MediaColumns.IS_TRASHED} = 1 AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            }
            val args = if (hasSize) arrayOf(name, bytes.toString()) else arrayOf(name)
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                sel,
                args,
                null
            )
        }.getOrNull()
        found?.use { reader ->
            if (reader.moveToFirst()) {
                return ContentUris.withAppendedId(collection, reader.getLong(0))
            }
        }
    }
    return uri
}

/**
 * True when the MediaStore row still exists. Log entries the OS already
 * swept (its ~30-day trash purge) are ghosts — for them, "deleting" can
 * only mean leaving Cura's log.
 */
private fun rowExists(context: Context, uri: Uri): Boolean = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bundle = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
        }
        context.contentResolver
            .query(uri, arrayOf(MediaStore.MediaColumns._ID), bundle, null)
            ?.use { it.count > 0 } == true
    } else {
        context.contentResolver
            .query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            ?.use { it.count > 0 } == true
    }
}.getOrDefault(false)

/** "deleted 2 days ago · 1.4 MB · purges in ~28 days" — plain, honest words. */
private fun metaLine(file: TrashedFile): String {
    val parts = mutableListOf<String>()
    if (file.fromLog && file.trashedAtMs != null) parts.add(agoLabel(file.trashedAtMs))
    parts.add(humanBytes(file.bytes))
    parts.add(purgeLabel(file))
    return parts.joinToString(" · ")
}

private fun agoLabel(trashedAtMs: Long): String {
    val days = (System.currentTimeMillis() - trashedAtMs) / 86_400_000L
    return when {
        days <= 0 -> CuraStrings.s(R.string.trash_deleted_today)
        days == 1L -> CuraStrings.s(R.string.trash_deleted_yesterday)
        else -> CuraStrings.s(R.string.trash_deleted_ago, days)
    }
}

/** Days left before Android purges it; null when we simply don't know. */
private fun purgeLabel(file: TrashedFile): String {
    val now = System.currentTimeMillis()
    val daysLeft: Long? = when {
        file.fromLog && file.trashedAtMs != null ->
            ((file.trashedAtMs + file.graceDays * 86_400_000L - now) / 86_400_000L)
                .coerceAtLeast(0L)
        !file.fromLog && file.expiresAt != null ->
            ((file.expiresAt - now) / 86_400_000L).coerceAtLeast(0L)
        else -> null
    }
    return when {
        daysLeft == null -> CuraStrings.s(R.string.trash_recoverable)
        daysLeft <= 0 -> CuraStrings.s(R.string.trash_purges_now)
        daysLeft == 1L -> CuraStrings.s(R.string.trash_purges_tomorrow)
        file.fromLog -> CuraStrings.s(R.string.trash_purges_tilde, daysLeft)
        else -> CuraStrings.s(R.string.trash_purges_in, daysLeft)
    }
}

/**
 * Best-effort peek at the system trash — a BONUS on top of Cura's own log,
 * never the promise. Android 11+ must be told to return trashed rows at all
 * (QUERY_ARG_MATCH_TRASHED / MATCH_ONLY); Android 10 honors the plain
 * IS_TRASHED selection. Either way an OEM may return nothing — handled.
 */
private suspend fun queryTrash(context: Context): List<TrashedFile> =
    withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext emptyList()
        val onR = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val found = mutableListOf<TrashedFile>()
        val collections = listOf(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        )
        for (collection in collections) {
            val projection = mutableListOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE
            )
            if (onR) projection.add(MediaStore.MediaColumns.DATE_EXPIRES)
            val cursor = runCatching {
                if (onR) {
                    val bundle = Bundle().apply {
                        putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                        putString(
                            ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                            "${MediaStore.MediaColumns.DATE_EXPIRES} ASC"
                        )
                    }
                    context.contentResolver.query(collection, projection.toTypedArray(), bundle, null)
                } else {
                    context.contentResolver.query(
                        collection,
                        projection.toTypedArray(),
                        "${MediaStore.MediaColumns.IS_TRASHED} = 1",
                        null,
                        null
                    )
                }
            }.getOrNull() ?: runCatching {
                // OEM refused the bundle — fall back to the plain selection
                context.contentResolver.query(
                    collection,
                    projection.toTypedArray(),
                    "${MediaStore.MediaColumns.IS_TRASHED} = 1",
                    null,
                    null
                )
            }.getOrNull()
            cursor?.use { reader ->
                val idCol = reader.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = reader.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = reader.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val expCol = if (onR) reader.getColumnIndex(MediaStore.MediaColumns.DATE_EXPIRES) else -1
                while (reader.moveToNext()) {
                    val expires = if (onR && expCol >= 0 && !reader.isNull(expCol)) {
                        // MediaStore timestamps are in seconds; we compare in millis
                        reader.getLong(expCol) * 1000L
                    } else {
                        null
                    }
                    found.add(
                        TrashedFile(
                            uri = ContentUris.withAppendedId(collection, reader.getLong(idCol)),
                            name = reader.getString(nameCol)?.ifBlank { "(file)" } ?: "(file)",
                            bytes = if (reader.isNull(sizeCol)) 0L else reader.getLong(sizeCol),
                            graceDays = TrashLog.TRASH_WINDOW_DAYS,
                            expiresAt = expires,
                            trashedAtMs = null,
                            fromLog = false
                        )
                    )
                }
            }
        }
        found.sortedWith(compareBy { it.expiresAt ?: Long.MAX_VALUE })
    }
