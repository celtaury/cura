package com.celtaury.cura.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
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
    private const val BURST_GAP_SEC = 2L                    // consecutive burst frames sit THIS close together (Batch K)

    /** Filename hints that scream "scanned paper" — free, no decode needed. */
    private val DOC_NAME_HINT = Regex("scan|receipt|kuitansi|dokumen|document|nota")

    /** `…_001` / `…-01` numeric tails (Samsung & friends burst naming). */
    private val BURST_TAIL = Regex("^(.+?)[-_](\\d{1,3})$")
    private val BURST_MARK = Regex("burst", RegexOption.IGNORE_CASE)

    // Batch M — signal 4: look-alike chains. The same subject shot minutes
    // apart, in ANY folder: a 256-bit dHash per frame (cheap ~64px decode,
    // cached) fuses series that neither burst naming nor a ≤2 s camera-folder
    // window can catch. Chain rule (Batch N): every hop needs EXIF-accurate
    // times on both ends, ≤[LOOKALIKE_GAP_SEC] from the previous frame, and
    // ≤[LOOKALIKE_BITS] bits from BOTH the run's ANCHOR (first frame) and the
    // previous frame; [BURST_MIN_GROUP] frames make a burst,
    // [LOOKALIKE_MAX_GROUP] is the fuse.
    private const val LOOKALIKE_GAP_SEC = 300L           // minutes apart — one hop, not a whole evening
    private const val LOOKALIKE_MIN_BYTES = 50_000L      // icons/stickers stay out
    private const val LOOKALIKE_BITS = 28                // of 256 — ~11%, the classic dHash "same scene" band
    private const val LOOKALIKE_BUDGET_MS = 3500L        // decode wall-clock cap
    private const val LOOKALIKE_MAX_GROUP = 12           // Batch N fuse: a longer chain is a mood, not a burst — dissolve it

    /** dHash cache — "id|size" so edited files re-hash. Shared deck ⇄ scan. */
    private val hashCache = ConcurrentHashMap<String, LongArray>()

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
        val documents: List<MediaItem> = emptyList(),
        /** Batch M: every full burst group (cover included) — the Suggest
         *  deck collapses each into ONE group card instead of one per extra. */
        val burstGroupsFull: List<List<MediaItem>> = emptyList()
    )

    // verdict cache — "id|size" so edited/replaced files re-analyze
    private val cache = ConcurrentHashMap<String, Verdict>()

    /**
     * One full pass over the gallery. Burst grouping first (free), then the
     * shared pixel pass for everything the burst logic left unclaimed.
     */
    suspend fun scan(context: Context): PhotoFamilies = withContext(Dispatchers.IO) {
        val all = Scanner.imageInventory(context)

        // ── 1. burst groups — naming rules + Batch M look-alike chains ──
        val claimed = HashSet<String>()
        val burstExtras = mutableListOf<MediaItem>()
        val groups = burstGroups(all, context)
        val extrasSeen = HashSet<String>()
        for (group in groups) {
            val cover = group.firstOrNull { it.displayName.contains("cover", true) }
                ?: group.maxByOrNull { it.sizeBytes }
                ?: continue
            for (member in group) {
                if (member.uri == cover.uri) continue
                val key = member.uri.toString()
                // a look-alike chain can overlap a named burst — claim once
                if (key in extrasSeen) continue
                extrasSeen += key
                claimed += key
                burstExtras += member
            }
        }

        // ── 2. shared pixel pass (oldest first — old junk leads) ────
        if (cache.size > 4096) cache.clear()   // crude but effective eviction
        val candidates = all.asSequence()
            .filter { it.uri.toString() !in claimed }
            .filter { it.sizeBytes >= MIN_ANALYZE_BYTES && it.sizeBytes <= MAX_FILE_BYTES }
            .sortedBy { Scanner.captureSecOf(it) }
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
                Scanner.captureSecOf(item) <= System.currentTimeMillis() / 1000 - BORING_MIN_AGE_DAYS * 86400L
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

        PhotoFamilies(burstExtras, blurry, boringShots, documents, groups)
    }

    // ── burst grouping ───────────────────────────────────────────────

    /**
     * Public burst grouping for the gallery deck (Batch H): the same
     * detection the Suggest engine uses, exposed so the deck can judge a
     * whole burst with one swipe. Only still photos group — screenshots and
     * videos keep their individual cards. Batch K: overlapping detections
     * are resolved by first claim (largest group wins) so a file can never
     * sit in two bursts — that used to corrupt the group-judge index.
     *
     * Batch M: suspend + context — look-alike chains decode tiny thumbnails,
     * so this now runs on IO (the deck computes it inside its load effect)
     * and shares the dHash cache with the scan pass.
     */
    suspend fun burstGroupsOf(
        context: Context,
        items: List<MediaItem>
    ): List<List<MediaItem>> = withContext(Dispatchers.IO) {
        val seen = HashSet<String>()
        val out = mutableListOf<List<MediaItem>>()
        for (group in burstGroups(items.filter { it.mime.startsWith("image/") }, context)
            .sortedByDescending { it.size }
        ) {
            val fresh = group.filter { it.uri.toString() !in seen }
            if (fresh.size >= BURST_MIN_GROUP) {
                fresh.forEach { seen += it.uri.toString() }
                out += fresh
            }
        }
        out
    }

    /**
     * Burst detection, four signals (Batch K rewrote #3, Batch M added #4):
     *  1. explicit burst naming — Google Camera writes "…_BURST004" (plus a
     *     "…_BURST_COVER" keeper frame);
     *  2. numeric-tail naming — ≥[BURST_MIN_GROUP] same-folder files sharing
     *     a base name with a short numeric tail, all within the window.
     *  3. time-gap clustering — the reason the user kept seeing ungrouped
     *     bursts: modern cameras name frames however they like (Pixel
     *     "PXL_…jpg", Samsung date-time names, editors that rewrite names on
     *     export), so rules 1–2 only ever caught one vendor. Real burst
     *     frames share physics: same camera folder, consecutive capture
     *     times ≤[BURST_GAP_SEC] apart. ≥[BURST_MIN_GROUP] such frames in a
     *     chain = one burst. Restricted to camera folders (DCIM/Camera) so a
     *     batch of WhatsApp/Telegram downloads — which share one DATE_ADDED
     *     instant — can never fuse into a fake mega-burst. Batch N: only
     *     files with EXIF-accurate capture times chain — a noon filename
     *     stamp (remasters, exports) fakes gap-0 across a whole day.
     *  4. look-alike chains (Batch M, Batch N) — the user's actual bursts
     *     are series of the SAME subject, shot minutes apart, in any folder.
     *     Every frame gets a 256-bit dHash (cached, budget-capped); frames
     *     chain while each hop has EXIF-accurate times on both ends, sits
     *     ≤[LOOKALIKE_GAP_SEC] from the previous frame, and stays
     *     ≤[LOOKALIKE_BITS] bits from BOTH the run's ANCHOR and the previous
     *     frame. The anatomy of the old 180-photo "burst": no EXIF → noon
     *     stamps → gap-0 forever; a 9×9/64-bit hash that called every
     *     "bright subject, dark surround" the same scene; and no size cap.
     *     Now ≥[BURST_MIN_GROUP] frames group and anything past
     *     [LOOKALIKE_MAX_GROUP] dissolves — better to miss a series than to
     *     swallow the trip.
     * Screenshots stay out — they have their own families.
     */
    private fun burstGroups(all: List<MediaItem>, context: Context): List<List<MediaItem>> {
        val photos = all.filter { !Scanner.isScreenshotLike(it) }
        val groups = mutableListOf<List<MediaItem>>()
        for ((folder, files) in photos.groupBy { it.path.substringBeforeLast('/') }) {
            // 1. explicit BURST marks
            val marked = files.filter { BURST_MARK.containsMatchIn(it.displayName) }
            for ((_, run) in marked.groupBy { baseOfBurstName(it.displayName) }) {
                if (run.size >= BURST_MIN_GROUP) groups += run
            }
            // 2. numeric tails within one moment — the moment is CAPTURE
            // time (Batch I): a bulk-migrated library shares one DATE_ADDED
            // instant, which once fused separate shots into mega-bursts
            val tailed = files.filter { !BURST_MARK.containsMatchIn(it.displayName) }
                .mapNotNull { f ->
                    val stem = f.displayName.substringBeforeLast('.', f.displayName)
                    BURST_TAIL.matchEntire(stem)?.let { m -> m.groupValues[1] to f }
                }
                .groupBy({ it.first }, { it.second })
                .filterValues { g ->
                    g.size >= BURST_MIN_GROUP &&
                        g.maxOf { Scanner.captureSecOf(it) } - g.minOf { Scanner.captureSecOf(it) } <= BURST_WINDOW_SEC
                }
            for (run in tailed.values) groups += run
            // 3. time-gap clustering in camera folders only
            if (folder.lowercase().contains("dcim") || folder.lowercase().contains("camera")) {
                var run = mutableListOf<MediaItem>()
                fun flush() {
                    if (run.size >= BURST_MIN_GROUP) groups += run.toList()
                    run = mutableListOf()
                }
                for (f in files.sortedBy { Scanner.captureSecOf(it) }) {
                    // Batch N: no EXIF date-taken = fake gap-0 chains — a
                    // noon filename stamp makes a whole DAY gap-0, and
                    // DATE_ADDED can be one bulk-copy instant. Real camera
                    // bursts always carry EXIF, so nothing real is lost.
                    if (!Scanner.captureSecIsFine(f)) continue
                    if (run.isEmpty()) {
                        run += f
                        continue
                    }
                    val gap = Scanner.captureSecOf(f) - Scanner.captureSecOf(run.last())
                    if (gap in 0..BURST_GAP_SEC) {
                        run += f
                    } else {
                        flush()
                        run += f
                    }
                }
                flush()
            }
        }

        // ── 4. look-alike chains (Batch M) — any folder, minutes apart ──
        // Batch N hardening — the anatomy of the 180-photo mega-burst:
        //  a. fake capture times: no EXIF + a date-only filename stamps every
        //     file of that DAY at noon, so the ≤300 s gate never fired and the
        //     whole evening looked like one instant. Chaining now requires
        //     EXIF-accurate times on BOTH ends of every hop.
        //  b. the 9×9/64-bit dHash read "bright subject centred, dark
        //     surround" as the same scene. Now 16×16/256-bit, and a hop must
        //     stay within [LOOKALIKE_BITS] of the ANCHOR and the previous
        //     frame — composition-alikes don't chain.
        //  c. no size fuse: a chain ran until the folder ended. A run past
        //     [LOOKALIKE_MAX_GROUP] frames is a mood, not a burst — it
        //     dissolves, nothing groups or gets claimed. Better to miss a
        //     series than to swallow the trip.
        val hashDeadline = System.currentTimeMillis() + LOOKALIKE_BUDGET_MS
        for ((_, files) in photos.groupBy { it.path.substringBeforeLast('/') }) {
            val hashed = mutableListOf<Pair<MediaItem, LongArray>>()
            for (f in files.sortedBy { Scanner.captureSecOf(it) }) {
                if (System.currentTimeMillis() > hashDeadline) break
                if (!Scanner.captureSecIsFine(f)) continue
                if (f.sizeBytes < LOOKALIKE_MIN_BYTES) continue
                if (!f.mime.startsWith("image/")) continue
                val h = dHashOf(context, f) ?: continue
                hashed += f to h
            }
            var run = mutableListOf<Pair<MediaItem, LongArray>>()
            fun flushLook() {
                if (run.size in BURST_MIN_GROUP..LOOKALIKE_MAX_GROUP) {
                    groups += run.map { it.first }
                }
                run = mutableListOf()
            }
            for (pair in hashed) {
                if (run.isEmpty()) {
                    run += pair
                    continue
                }
                val gap = Scanner.captureSecOf(pair.first) - Scanner.captureSecOf(run.last().first)
                val okTime = gap in 0..LOOKALIKE_GAP_SEC
                val okLook = bitsDiff(pair.second, run.first().second) <= LOOKALIKE_BITS &&
                    bitsDiff(pair.second, run.last().second) <= LOOKALIKE_BITS
                if (okTime && okLook) {
                    run += pair
                } else {
                    flushLook()
                    run += pair
                }
            }
            flushLook()
        }
        return groups
    }

    /** "IMG_20231024_123456789_BURST004.jpg" → "img_20231024_123456789_" */
    private fun baseOfBurstName(name: String): String {
        val stem = name.substringBeforeLast('.', name).lowercase()
        val cut = stem.indexOf("burst")
        return if (cut > 0) stem.substring(0, cut) else stem
    }

    /**
     * 256-bit difference hash (dHash) of the shared ~128px decode — "do these
     * two photos show the same scene" with zero ML. Reads a 17×16 luminance
     * grid left-to-right; each bit says whether a pixel is brighter than its
     * right neighbour — 16 bits per row × 16 rows ride in four lanes.
     *
     * Batch N: was 9×9/64-bit — so coarse that any "bright subject centred,
     * dark surround" composition looked alike, which is how one folder fused
     * into a 180-photo burst. 16×16 keeps genuine same-scene series in and
     * pushes composition-alike strangers out. Cached per "id|size"; null =
     * undecodable file.
     */
    private fun dHashOf(context: Context, item: MediaItem): LongArray? {
        val key = "${item.id}|${item.sizeBytes}"
        hashCache[key]?.let { return it }
        return try {
            val bmp = decodeThumb(context, item.uri) ?: return null
            val scaled = Bitmap.createScaledBitmap(bmp, 17, 16, true)
            val lum = IntArray(16 * 17)
            for (y in 0 until 16) for (x in 0 until 17) {
                val p = scaled.getPixel(x, y)
                lum[y * 17 + x] = ((p shr 16 and 0xFF) * 299 +
                    (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
            }
            val hash = LongArray(4)
            for (y in 0 until 16) for (x in 0 until 16) {
                val idx = y * 16 + x
                hash[idx / 64] = (hash[idx / 64] shl 1) or
                    (if (lum[y * 17 + x] < lum[y * 17 + x + 1]) 1L else 0L)
            }
            if (scaled !== bmp) scaled.recycle()
            bmp.recycle()
            hashCache[key] = hash
            hash
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    /** Hamming distance across the four 64-bit dHash lanes. */
    private fun bitsDiff(a: LongArray, b: LongArray): Int {
        var n = 0
        for (i in a.indices) n += java.lang.Long.bitCount(a[i] xor b[i])
        return n
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
