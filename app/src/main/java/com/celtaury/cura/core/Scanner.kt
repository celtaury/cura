package com.celtaury.cura.core

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * One scan result row from MediaStore.
 * Works on Android 8 (API 26) through current — RELATIVE_PATH on Q+, DATA path below.
 */
data class MediaItem(
    val id: Long,
    val displayName: String,
    val sizeBytes: Long,
    val uri: Uri,
    val dateAddedSec: Long,
    val path: String,
    val mime: String
)

data class JunkCategory(
    val key: String,
    val title: String,
    val subtitle: String,
    val items: List<MediaItem>
) {
    val totalBytes: Long get() = items.sumOf { it.sizeBytes }
}

/**
 * All MediaStore scanning in one place. Play-ready: only READ_MEDIA_* permissions,
 * no MANAGE_EXTERNAL_STORAGE. Everything filters through the Keep-safe list.
 */
object Scanner {

    // ── low-level query helpers ──────────────────────────────────────

    private fun projection(): Array<String> {
        val base = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.MIME_TYPE
        )
        base.add(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaStore.MediaColumns.RELATIVE_PATH
            else
                MediaStore.MediaColumns.DATA
        )
        return base.toTypedArray()
    }

    private fun mediaUri(kind: String): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (kind) {
                "images" -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                "video" -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                "audio" -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                "files" -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                "downloads" -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
                else -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            }
        } else {
            when (kind) {
                "images" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Files.getContentUri("external")
            }
        }

    private fun query(
        context: Context,
        uri: Uri,
        selection: String? = null,
        args: Array<String>? = null,
        order: String? = null,
        limitInOrder: Boolean = false
    ): List<MediaItem> {
        val out = mutableListOf<MediaItem>()
        try {
            context.contentResolver.query(uri, projection(), selection, args, order)?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val iDate = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val iPath = c.getColumnIndexOrThrow(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        MediaStore.MediaColumns.RELATIVE_PATH
                    else MediaStore.MediaColumns.DATA
                )
                while (c.moveToNext()) {
                    out.add(
                        MediaItem(
                            id = c.getLong(iId),
                            displayName = c.getString(iName) ?: "Unknown",
                            sizeBytes = c.getLong(iSize),
                            uri = Uri.withAppendedPath(uri, c.getLong(iId).toString()),
                            dateAddedSec = c.getLong(iDate),
                            path = c.getString(iPath) ?: "",
                            mime = c.getString(iMime) ?: ""
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // permission revoked mid-session — return what we can
        } catch (_: IllegalArgumentException) {
            // collection not available on this OS level — skip silently
        }
        return out
    }

    private fun isProtected(context: Context, item: MediaItem): Boolean =
        KeepSafe.contains(context, item.uri)

    private fun isVideo(item: MediaItem) =
        item.mime.startsWith("video/") ||
            item.displayName.endsWith(".mp4", true) ||
            item.displayName.endsWith(".mov", true) ||
            item.displayName.endsWith(".mkv", true) ||
            item.displayName.endsWith(".webm", true)

    /**
     * Public predicate: does this item live in a screenshots / screen-recording
     * bucket? Single source of truth for every screen — the deck's family
     * router, old-media exclusion and the photo mind all must agree. (Was
     * three private copies; one of them compared a lowercased path against
     * "screenRecorder" and could never match — fixed here.)
     */
    fun isScreenshotLike(item: MediaItem): Boolean {
        val p = item.path.lowercase()
        val n = item.displayName.lowercase()
        return p.contains("screenshot") || n.startsWith("screenshot") ||
            n.startsWith("screen-") || n.startsWith("screen_recording") ||
            p.contains("screenrecorder")
    }

    private fun isImage(item: MediaItem) =
        item.mime.startsWith("image/") ||
            item.displayName.endsWith(".jpg", true) ||
            item.displayName.endsWith(".jpeg", true) ||
            item.displayName.endsWith(".png", true) ||
            item.displayName.endsWith(".webp", true) ||
            item.displayName.endsWith(".heic", true)

    // ── public scans ─────────────────────────────────────────────────

    /** All media above [minBytes], biggest first. Includes audio per user decision. */
    suspend fun largeFiles(context: Context, minBytes: Long = 50L * 1024 * 1024): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val kinds = listOf("images", "video", "audio")
            val all = mutableListOf<MediaItem>()
            for (k in kinds) {
                all += query(
                    context,
                    mediaUri(k),
                    selection = "${MediaStore.MediaColumns.SIZE} > ?",
                    args = arrayOf(minBytes.toString()),
                    order = "${MediaStore.MediaColumns.SIZE} DESC"
                )
            }
            all.filter { !isProtected(context, it) }.sortedByDescending { it.sizeBytes }
        }

    /** Screenshots older than [minAgeDays]. */
    suspend fun oldScreenshots(context: Context, minAgeDays: Int = 30): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() / 1000 - minAgeDays * 86400L
            query(
                context,
                mediaUri("images"),
                selection = "${MediaStore.MediaColumns.DATE_ADDED} < ?",
                args = arrayOf(cutoff.toString()),
                order = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            ).filter {
                isScreenshotLike(it)
            }.filter { !isProtected(context, it) }
        }

    /**
     * WhatsApp media only (v1 scope — Telegram/Instagram land in v1.5/v2).
     * WhatsApp keeps its files under Android/media/com.whatsapp, which MediaStore
     * indexes for us, so plain media permissions are enough.
     */
    suspend fun whatsappMedia(context: Context): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val kinds = listOf("images", "video", "audio", "files")
            val all = mutableListOf<MediaItem>()
            for (k in kinds) all += query(context, mediaUri(k))
            all.filter {
                val p = it.path.lowercase()
                p.contains("whatsapp")
            }.filter { !isProtected(context, it) }
                .distinctBy { it.uri }
        }

    /** Social Cleaner type tab. Stickers first so they never masquerade as Images. */
    fun socialTypeOf(item: MediaItem): String {
        val p = item.path.lowercase()
        val n = item.displayName.lowercase()
        return when {
            p.contains("sticker") || (n.endsWith(".webp") && p.contains("whatsapp")) -> "Stickers"
            item.mime.startsWith("image/") -> "Images"
            item.mime.startsWith("video/") -> "Videos"
            item.mime.startsWith("audio/") -> "Audio"
            else -> "Docs"
        }
    }

    /**
     * Old media deck: photos & videos untouched for [minAgeDays]+.
     * Excludes screenshots (they have their own deck) and chat-app folders
     * (the Social Cleaner owns those) — one file, one surface.
     */
    suspend fun oldMedia(context: Context, minAgeDays: Int = 60): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() / 1000 - minAgeDays * 86400L
            val kinds = listOf("images", "video")
            val all = mutableListOf<MediaItem>()
            for (k in kinds) {
                all += query(
                    context,
                    mediaUri(k),
                    selection = "${MediaStore.MediaColumns.DATE_ADDED} < ? AND ${MediaStore.MediaColumns.SIZE} > 0",
                    args = arrayOf(cutoff.toString())
                )
            }
            all.filter {
                val p = it.path.lowercase()
                val isShot = isScreenshotLike(it)
                val isSocial = p.contains("whatsapp") || p.contains("telegram") ||
                    p.contains("instagram")
                !isShot && !isSocial
            }.filter { !isProtected(context, it) }
                .distinctBy { it.uri }
                .sortedBy { it.dateAddedSec }   // oldest first — the dusty stuff leads
                .take(600)
        }

    /**
     * The gallery review deck (dashboard peek-card): everything, oldest first.
     * Capped at 500 — a deck is for deciding, not for marathons.
     */
    suspend fun galleryDeck(context: Context): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val kinds = listOf("images", "video")
            val all = mutableListOf<MediaItem>()
            for (k in kinds) {
                all += query(
                    context,
                    mediaUri(k),
                    selection = "${MediaStore.MediaColumns.SIZE} > 0",
                    order = "${MediaStore.MediaColumns.DATE_ADDED} ASC"
                )
            }
            all.filter { !isProtected(context, it) }
                .distinctBy { it.uri }
                .sortedBy { it.dateAddedSec }
                .take(500)
        }

    /** Zero-byte files found in visible collections (capped — some devices have thousands). */
    suspend fun emptyFiles(context: Context): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<MediaItem>()
            out += query(
                context,
                mediaUri("files"),
                selection = "${MediaStore.MediaColumns.SIZE} = 0",
                order = "${MediaStore.MediaColumns.DISPLAY_NAME} ASC"
            )
            out.filter { !isProtected(context, it) && it.displayName.isNotBlank() }.take(500)
        }

    /** Stray .apk files (usually forgotten downloads). */
    suspend fun apkFiles(context: Context): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<MediaItem>()
            val sel = "LOWER(${MediaStore.MediaColumns.DISPLAY_NAME}) LIKE ? AND ${MediaStore.MediaColumns.SIZE} > 0"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                out += query(context, mediaUri("downloads"), sel, arrayOf("%.apk"))
            }
            out += query(context, mediaUri("files"), sel, arrayOf("%.apk"))
            out.distinctBy { it.uri }.filter { !isProtected(context, it) }
        }

    /**
     * Duplicate images (same content, MD5-checked).
     * Prescreen: only files that share their EXACT byte size with another file can
     * be twins — so we hash only those instead of reading every candidate.
     * On a normal gallery this turns a multi-GB read into a few MB.
     */
    suspend fun duplicateImages(context: Context): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val candidates = query(
                context,
                mediaUri("images"),
                selection = "${MediaStore.MediaColumns.SIZE} BETWEEN ? AND ?",
                args = arrayOf((64 * 1024).toString(), (200L * 1024 * 1024).toString()),
                order = "${MediaStore.MediaColumns.SIZE} DESC"
            ).take(800)
            val sameSize = candidates.groupBy { it.sizeBytes }.filterValues { it.size > 1 }
            val toHash = sameSize.values.flatten().take(300)
            val byHash = HashMap<String, MutableList<MediaItem>>()
            for (item in toHash) {
                val h = md5Of(context, item.uri) ?: continue
                byHash.getOrPut(h) { mutableListOf() }.add(item)
            }
            byHash.values.filter { it.size > 1 }.flatten().filter { !isProtected(context, it) }
        }

    /**
     * Batch E — full image inventory for the photo mind: every non-empty
     * image, oldest first, keep-safe items already excluded. Chat-app media
     * stays in (the Social Cleaner owns those folders) so callers must apply
     * their own ownership rules — the mind skips them before decoding.
     */
    suspend fun imageInventory(context: Context): List<MediaItem> =
        withContext(Dispatchers.IO) {
            query(
                context,
                mediaUri("images"),
                selection = "${MediaStore.MediaColumns.SIZE} > 0",
                order = "${MediaStore.MediaColumns.DATE_ADDED} ASC"
            ).filter { !isProtected(context, it) }
        }

    /** One old, forgotten photo for the dashboard discovery card. */
    suspend fun forgottenPhoto(context: Context): MediaItem? =
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() / 1000 - 180L * 86400
            val old = query(
                context,
                mediaUri("images"),
                selection = "${MediaStore.MediaColumns.DATE_ADDED} < ? AND ${MediaStore.MediaColumns.SIZE} > 0",
                args = arrayOf(cutoff.toString()),
                order = "${MediaStore.MediaColumns.DATE_ADDED} ASC LIMIT 60"
            ).filter { !isProtected(context, it) }
            old.randomOrNull()
        }

    /** Images grouped for the organizer. Returns monthKey ("2026-10") → items, newest month first. */
    suspend fun imagesByMonth(context: Context): List<Pair<String, List<MediaItem>>> =
        withContext(Dispatchers.IO) {
            val all = query(
                context,
                mediaUri("images"),
                selection = "${MediaStore.MediaColumns.SIZE} > 0",
                order = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )
            val groups = LinkedHashMap<String, MutableList<MediaItem>>()
            for (item in all) {
                val key = monthKeyOf(item.dateAddedSec)
                groups.getOrPut(key) { mutableListOf() }.add(item)
            }
            groups.entries.sortedByDescending { it.key }.map { it.key to it.value.toList() }
        }

    /**
     * Gallery Browse (#3): images + video grouped by month key ("2026-10"),
     * newest month first, newest item first. Keep-safe items are excluded —
     * "kept" means "out of every cleaner surface".
     */
    suspend fun galleryByMonth(context: Context): List<Pair<String, List<MediaItem>>> =
        withContext(Dispatchers.IO) {
            val all = mutableListOf<MediaItem>()
            for (k in listOf("images", "video")) {
                all += query(
                    context,
                    mediaUri(k),
                    selection = "${MediaStore.MediaColumns.SIZE} > 0"
                )
            }
            all.filter { !isProtected(context, it) }
                .distinctBy { it.uri }
                .groupBy { monthKeyOf(it.dateAddedSec) }
                .entries
                .sortedByDescending { it.key }
                .map { (key, list) -> key to list.sortedByDescending { it.dateAddedSec } }
        }

    /**
     * Re-hydrate items by MediaStore id (any type) — used to restore a saved
     * Suggest session after the app was killed. Order follows [ids].
     */
    suspend fun itemsByIds(context: Context, ids: List<Long>): List<MediaItem> =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext emptyList()
            val selection = "${MediaStore.MediaColumns._ID} IN (${ids.joinToString(",") { "?" }})"
            val byOrder = ids.withIndex().associate { (pos, id) -> id to pos }
            query(
                context,
                mediaUri("files"),
                selection = selection,
                args = ids.map { it.toString() }.toTypedArray()
            ).sortedBy { byOrder[it.id] ?: Int.MAX_VALUE }
        }

    // ── misc ─────────────────────────────────────────────────────────

    fun bucketOf(item: MediaItem): String {
        val p = item.path.lowercase()
        return when {
            p.contains("voice notes") || p.contains("voicenotes") -> "Voice Notes"
            p.contains("status") || p.contains("stories") -> "Statuses"
            p.contains("video") || isVideo(item) -> "Videos"
            p.contains("image") || isImage(item) -> "Images"
            else -> "Other"
        }
    }

    fun monthKeyOf(dateAddedSec: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = dateAddedSec * 1000L
        val y = cal.get(java.util.Calendar.YEAR)
        val m = cal.get(java.util.Calendar.MONTH) + 1
        return "%04d-%02d".format(y, m)
    }

    private fun md5Of(context: Context, uri: Uri): String? {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val stream = context.contentResolver.openInputStream(uri)
                ?: return null
            stream.use { input ->
                val buf = ByteArray(64 * 1024)
                var r = input.read(buf)
                while (r >= 0) {
                    digest.update(buf, 0, r)
                    r = input.read(buf)
                }
            }
            digest.digest().joinToString("") { String.format("%02x", it) }
        } catch (_: Exception) {
            null
        }
    }
}
