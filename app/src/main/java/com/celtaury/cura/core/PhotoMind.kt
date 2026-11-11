package com.celtaury.cura.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Batch E — the photo mind (SuggestionEngine v2).
 *
 * The old scanners only knew WHERE a file lives (folder names, file age).
 * This one also looks AT the pixels: one downsampled decode per image feeds
 * every verdict — blurry, boring, document-like — while burst shots are
 * detected for free from filename + timestamp patterns, no decode at all.
 *
 * Budget: the pixel pass is capped at [MAX_ANALYZE] images and [BUDGET_MS]
 * milliseconds, so a scan never wedges the UI on a huge gallery. Verdicts
 * are cached in-process (keyed by "id|size", so edited or replaced files
 * re-analyze), meaning Quick Clean warms the cache and the Suggest deck
 * reuses it — and vice versa — without re-decoding anything.
 *
 * One file, one surface: keep-safe items and chat-app folders (WhatsApp /
 * Telegram / Instagram — the Social Cleaner owns those) are never analyzed,
 * because [Scanner.imageInventory] filters them before we ever decode.
 */
object PhotoMind {

    // ── tunables — thresholds measured on [THUMB]-px thumbnails ─────
    private const val THUMB = 128
    private const val MIN_ANALYZE_BYTES = 64L * 1024        // icons are not photos
    private const val MAX_FILE_BYTES = 24L * 1024 * 1024    // giants read too slow
    private const val MAX_ANALYZE = 240                     // decode cap per pass
    private const val BUDGET_MS = 2500L                     // wall-clock cap per pass

    private const val BLURRY_LAP_VAR = 35.0                 // below = nothing in focus
    private const val BLURRY_MIN_BYTES = 300L * 1024        // small files are cheap junk
    private const val BORING_TOP_BUCKET = 0.80              // ≥80% one colour bucket
    private const val BORING_MIN_AGE_DAYS = 7               // don't nag about yesterday
    private const val DOC_MAX_SAT = 0.16                    // paper is colourless…
    private const val DOC_MIN_VAL = 0.55                    // …bright…
    private const val DOC_MIN_EDGE = 0.05                   // …with text-like strokes
    private const val EDGE_ABS = 24.0                       // |Laplacian| edge cutoff

    private const val BURST_MIN_GROUP = 3                   // fewer shots = a photo, not a burst
    private const val BURST_WINDOW_SEC = 15L                // burst frames share a moment

    /** Filename hints that scream "scanned paper" — free, no decode needed. */
    private val DOC_NAME_HINT = Regex("scan|receipt|kuitansi|dokumen|document|nota")

    /** `…_001` / `…-01` numeric tails (Samsung & friends burst naming). */
    private val BURST_TAIL = Regex("^(.+?)[-_](\\d{1,3})$")
    private val BURST_MARK = Regex("burst", RegexOption.IGNORE_CASE)

    private val CAMERA_MIMES = setOf("image/jpeg", "image/heic", "image/heif", "image/webp")

    /** What the pixels said about one image. Content-only — family rules decide. */
    private data class Verdict(
        val blurry: Boolean = false,
        val boring: Boolean = false,
        val document: Boolean = false
    )

    /** The four v2 families, ready for Quick Clean and the Suggest deck. */
    class PhotoFamilies(
        val burstExtras: List<MediaItem> = emptyList(),
        val blurry: List<MediaItem> = emptyList(),
        val boringShots: List<MediaItem> = emptyList(),
        val documents: List<MediaItem> = emptyList()
    )

    // verdict cache — "id|size" so edited/replaced files re-analyze
    private val cache = ConcurrentHashMap<String, Verdict>()

    /**
     * One full pass over the gallery. Burst grouping first (free), then the
     * shared pixel pass for everything the burst logic left unclaimed.
     */
    suspend fun scan(context: Context): PhotoFamilies = withContext(Dispatchers.IO) {
        val all = Scanner.imageInventory(context)

        // ── 1. burst groups — pure naming, zero decodes ─────────────
        val claimed = HashSet<String>()
        val burstExtras = mutableListOf<MediaItem>()
        for (group in burstGroups(all)) {
            val cover = group.firstOrNull { it.displayName.contains("cover", true) }
                ?: group.maxByOrNull { it.sizeBytes }
                ?: continue
            for (member in group) {
                if (member.uri == cover.uri) continue
                claimed += member.uri.toString()
                burstExtras += member
            }
        }

        // ── 2. shared pixel pass (oldest first — old junk leads) ────
        if (cache.size > 4096) cache.clear()   // crude but effective eviction
        val candidates = all.asSequence()
            .filter { it.uri.toString() !in claimed }
            .filter { it.sizeBytes >= MIN_ANALYZE_BYTES && it.sizeBytes <= MAX_FILE_BYTES }
            .sortedBy { it.dateAddedSec }
            .take(MAX_ANALYZE)
            .toList()

        val blurry = mutableListOf<MediaItem>()
        val boringShots = mutableListOf<MediaItem>()
        val documents = mutableListOf<MediaItem>()
        val deadline = System.currentTimeMillis() + BUDGET_MS

        for (item in candidates) {
            if (System.currentTimeMillis() > deadline) break
            val verdict = cache.getOrPut("${item.id}|${item.sizeBytes}") {
                analyze(context, item.uri) ?: Verdict()
            }
            val shot = Scanner.isScreenshotLike(item)
            if (verdict.blurry && !shot && looksLikeCameraPhoto(item) &&
                item.sizeBytes >= BLURRY_MIN_BYTES
            ) blurry += item
            if (verdict.boring && shot &&
                item.dateAddedSec <= System.currentTimeMillis() / 1000 - BORING_MIN_AGE_DAYS * 86400L
            ) boringShots += item
            if (verdict.document && !shot) documents += item
        }

        // ── 3. name-hint documents — free, never budget-bound ───────
        val docSeen = documents.mapTo(HashSet()) { it.uri.toString() }
        for (item in all) {
            if (Scanner.isScreenshotLike(item)) continue
            val uriKey = item.uri.toString()
            if (uriKey in claimed || uriKey in docSeen) continue
            if (item.sizeBytes < MIN_ANALYZE_BYTES) continue
            if (DOC_NAME_HINT.containsMatchIn(item.displayName.lowercase())) {
                docSeen += uriKey
                documents += item
            }
        }

        PhotoFamilies(burstExtras, blurry, boringShots, documents)
    }

    // ── burst grouping ───────────────────────────────────────────────

    /**
     * Public burst grouping for the gallery deck (Batch H): the same
     * detection the Suggest engine uses, exposed so the deck can judge a
     * whole burst with one swipe. Only still photos group — screenshots and
     * videos keep their individual cards.
     */
    fun burstGroupsOf(items: List<MediaItem>): List<List<MediaItem>> =
        burstGroups(items.filter { it.mime.startsWith("image/") })

    /**
     * Burst detection from names + timestamps alone:
     *  1. explicit burst naming — Google Camera writes "…_BURST004" (plus a
     *     "…_BURST_COVER" keeper frame);
     *  2. numeric-tail naming — ≥[BURST_MIN_GROUP] same-folder files sharing
     *     a base name with a short numeric tail, all within the window.
     * Screenshots stay out — they have their own families.
     */
    private fun burstGroups(all: List<MediaItem>): List<List<MediaItem>> {
        val photos = all.filter { !Scanner.isScreenshotLike(it) }
        val groups = mutableListOf<List<MediaItem>>()
        for ((_, files) in photos.groupBy { it.path.substringBeforeLast('/') }) {
            // 1. explicit BURST marks
            val marked = files.filter { BURST_MARK.containsMatchIn(it.displayName) }
            for ((_, run) in marked.groupBy { baseOfBurstName(it.displayName) }) {
                if (run.size >= BURST_MIN_GROUP) groups += run
            }
            // 2. numeric tails within one moment
            val tailed = files.filter { !BURST_MARK.containsMatchIn(it.displayName) }
                .mapNotNull { f ->
                    val stem = f.displayName.substringBeforeLast('.', f.displayName)
                    BURST_TAIL.matchEntire(stem)?.let { m -> m.groupValues[1] to f }
                }
                .groupBy({ it.first }, { it.second })
                .filterValues { g ->
                    g.size >= BURST_MIN_GROUP &&
                        g.maxOf { it.dateAddedSec } - g.minOf { it.dateAddedSec } <= BURST_WINDOW_SEC
                }
            for (run in tailed.values) groups += run
        }
        return groups
    }

    /** "IMG_20231024_123456789_BURST004.jpg" → "img_20231024_123456789_" */
    private fun baseOfBurstName(name: String): String {
        val stem = name.substringBeforeLast('.', name).lowercase()
        val cut = stem.indexOf("burst")
        return if (cut > 0) stem.substring(0, cut) else stem
    }

    // ── pixel pass ───────────────────────────────────────────────────

    private fun looksLikeCameraPhoto(item: MediaItem): Boolean {
        val n = item.displayName.lowercase()
        return item.mime in CAMERA_MIMES ||
            n.endsWith(".jpg") || n.endsWith(".jpeg") ||
            n.endsWith(".heic") || n.endsWith(".webp")
    }

    /**
     * Decode once at thumbnail size; every metric shares the same pixels:
     *  - Laplacian variance (4-neighbour) — the classic cheap sharpness score;
     *  - dominant 12-bit colour bucket share — plain frames are 80%+ one bucket;
     *  - mean saturation/value + text-like edge share — paper is colourless,
     *    bright, and full of small high-contrast strokes.
     */
    private fun analyze(context: Context, uri: Uri): Verdict? {
        val bmp = decodeThumb(context, uri) ?: return null
        val w = bmp.width
        val h = bmp.height
        if (w < 8 || h < 8) {
            bmp.recycle()
            return null
        }
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        bmp.recycle()

        val gray = DoubleArray(w * h)
        var satSum = 0.0
        var valSum = 0.0
        val buckets = IntArray(4096)
        for (i in px.indices) {
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            gray[i] = (r * 299 + g * 587 + b * 114) / 1000.0
            val mx = maxOf(r, g, b)
            val mn = minOf(r, g, b)
            valSum += mx / 255.0
            satSum += if (mx == 0) 0.0 else (mx - mn).toDouble() / mx
            buckets[((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)]++
        }
        val n = px.size
        val meanSat = satSum / n
        val meanVal = valSum / n
        var top = 0
        for (share in buckets) if (share > top) top = share
        val topShare = top.toDouble() / n

        var sum = 0.0
        var sumSq = 0.0
        var edges = 0
        var count = 0
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val i = row + x
                val lap = 4.0 * gray[i] - gray[i - 1] - gray[i + 1] - gray[i - w] - gray[i + w]
                sum += lap
                sumSq += lap * lap
                if (abs(lap) > EDGE_ABS) edges++
                count++
            }
        }
        val mean = sum / count
        val lapVar = sumSq / count - mean * mean
        val edgeShare = edges.toDouble() / count

        return Verdict(
            blurry = lapVar < BLURRY_LAP_VAR,
            boring = topShare >= BORING_TOP_BUCKET,
            document = meanSat < DOC_MAX_SAT && meanVal > DOC_MIN_VAL && edgeShare > DOC_MIN_EDGE
        )
    }

    /** Thumbnail decode via ContentResolver; any failure just means "no verdict". */
    private fun decodeThumb(context: Context, uri: Uri): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= THUMB &&
                bounds.outHeight / (sample * 2) >= THUMB
            ) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (_: SecurityException) {
            null
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
    }
}
