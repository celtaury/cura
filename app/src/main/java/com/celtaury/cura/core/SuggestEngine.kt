package com.celtaury.cura.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.celtaury.cura.R

/**
 * The Suggest deck's dealer (overhaul round 1).
 *
 * One scored pool, one job: deal ~[SESSION_SIZE] cards the engine can best
 * justify deleting, each led by a human reason ("Dusty screenshot — saved 8
 * months ago"), never by a category. Categories stay in Quick Clean; this
 * screen is the friendly nudge, not the janitor.
 *
 * Ranking: duplicates argue for themselves, then the Batch E
 * photo-mind verdicts (burst extras, blurry shots, plain screenshots,
 * scanned documents), then dusty screenshots, forgotten installers, old
 * media. A family contributes at most [MAX_PER_FAMILY] cards so the deck
 * feels varied. "Not today" skips sink a card by one point per skip;
 * judged files never return (SuggestPrefs).
 *
 * Tile handoff: the dashboard seeds [Session] with the exact item it showed —
 * the deck then opens ON that card, so the tile finally tells the truth.
 */
object SuggestEngine {

    const val SESSION_SIZE = 10
    private const val MAX_PER_FAMILY = 4

    data class Suggestion(
        val item: MediaItem,
        val tag: String,      // "Old screenshot" · "Duplicate" · "Stray installer" · "Old photo" · v2: "Burst extra" · "Blurry photo" · "Plain screenshot" · "Scanned document"
        val reason: String,   // the line that leads the card
        val score: Int,
        /** Batch M: non-empty on a burst GROUP card — the full burst (cover
         *  included). One card judges (or hand-picks) every member at once. */
        val group: List<MediaItem> = emptyList()
    )

    /** In-memory handoff so a tile tap opens the deck ON the promised card. */
    object Session {
        private var startItem: MediaItem? = null

        fun seed(item: MediaItem) {
            startItem = item
        }

        fun consume(): MediaItem? {
            val value = startItem
            startItem = null
            return value
        }
    }

    /** The reason line for a card — single source of truth (build + restore). */
    fun reasonFor(context: Context, item: MediaItem, tag: String): String = when (tag) {
        "Duplicate" ->
            context.getString(R.string.eng_duplicate)
        "Burst extra" ->
            context.getString(R.string.eng_burst_extra)
        "Blurry photo" ->
            context.getString(R.string.eng_blurry)
        "Plain screenshot" ->
            context.getString(R.string.eng_boring)
        "Scanned document" ->
            context.getString(R.string.eng_doc)
        "Old screenshot" ->
            context.getString(R.string.eng_dusty, relativeDays(item.dateAddedSec))
        "Stray installer" ->
            context.getString(R.string.eng_installer)
        else ->
            context.getString(R.string.eng_untouched, relativeDays(item.dateAddedSec))
    }

    /** Fallback tag for a seeded tile item that missed every scan pool. */
    fun familyTagOf(item: MediaItem): String = when (SuggestionEngine.familyOf(item)) {
        "shots" -> "Old screenshot"
        "old" -> "Old photo"
        "social" -> "Old photo"
        else -> "Old photo"
    }

    /**
     * Deal a fresh session. [startItem] (if present) leads the deck — it is
     * the card the tile showed; breaking that promise would recreate the
     * exact bug this overhaul exists to fix.
     */
    suspend fun buildSession(
        context: Context,
        startItem: MediaItem? = null,
        size: Int = SESSION_SIZE
    ): List<Suggestion> = withContext(Dispatchers.IO) {
        val judged = SuggestPrefs.judged(context)

        fun viable(item: MediaItem): Boolean {
            val uri = item.uri.toString()
            return uri !in judged && !KeepSafe.contains(context, item.uri)
        }

        // one pool, each source arguing with its own reason.
        // Batch K: the "Empty file" family is gone — zero-byte files turned
        // out to be impossible to actually delete through MediaStore on many
        // devices (the system dialog silently failed), so the whole feature
        // was retired instead of shipping a dead end.
        val pool = mutableListOf<Suggestion>()

        Scanner.duplicateImages(context).filter(::viable).forEach {
            pool += Suggestion(it, "Duplicate", reasonFor(context, it, "Duplicate"), 5)
        }
        // Batch E — the photo mind's four verdicts. Insertion order matters:
        // the pool is sorted by score with a STABLE sort, so equal-score ties
        // resolve to whichever family was added first. "Plain screenshot" is
        // added before "Old screenshot" for exactly that reason.
        val mind = PhotoMind.scan(context)
        // Batch M: ONE card per burst, not one per extra — the card's photo
        // is the group's most-deletable extra, and the full group (cover
        // included) rides along so the deck can judge or hand-pick every
        // frame as a single decision. Fallback keeps the flat extras if the
        // engine ever returns groups without members.
        if (mind.burstGroupsFull.isEmpty()) {
            mind.burstExtras.filter(::viable).forEach {
                pool += Suggestion(it, "Burst extra", reasonFor(context, it, "Burst extra"), 5)
            }
        } else {
            val extraUris = mind.burstExtras.mapTo(HashSet()) { it.uri.toString() }
            for (g in mind.burstGroupsFull.sortedByDescending { it.size }) {
                val rep = g.firstOrNull { it.uri.toString() in extraUris && viable(it) }
                    ?: continue
                pool += Suggestion(rep, "Burst extra", reasonFor(context, rep, "Burst extra"), 5, g)
            }
        }
        mind.blurry.filter(::viable).forEach {
            pool += Suggestion(it, "Blurry photo", reasonFor(context, it, "Blurry photo"), 5)
        }
        mind.boringShots.filter(::viable).forEach {
            pool += Suggestion(it, "Plain screenshot", reasonFor(context, it, "Plain screenshot"), 4)
        }
        Scanner.oldScreenshots(context, minAgeDays = 30).filter(::viable).forEach {
            pool += Suggestion(it, "Old screenshot", reasonFor(context, it, "Old screenshot"), 4)
        }
        Scanner.apkFiles(context).filter(::viable).forEach {
            pool += Suggestion(it, "Stray installer", reasonFor(context, it, "Stray installer"), 4)
        }
        mind.documents.filter(::viable).forEach {
            pool += Suggestion(it, "Scanned document", reasonFor(context, it, "Scanned document"), 3)
        }
        Scanner.oldMedia(context, minAgeDays = 60).filter(::viable).forEach {
            pool += Suggestion(it, "Old photo", reasonFor(context, it, "Old photo"), 3)
        }

        // dedupe by uri — a file that lands in two scans keeps the
        // sharper reason (higher score wins)
        val byUri = LinkedHashMap<String, Suggestion>()
        for (s in pool.sortedByDescending { it.score }) {
            byUri.putIfAbsent(s.item.uri.toString(), s)
        }

        // skipped cards sink — one point of score per skip
        val skips = SuggestPrefs.skipCounts(context)
        val ranked = byUri.values
            .map { s -> s.copy(score = s.score - (skips[s.item.uri.toString()] ?: 0)) }
            .sortedByDescending { it.score }

        // diversity cap, then the best [size]
        val perFamily = HashMap<String, Int>()
        val picked = mutableListOf<Suggestion>()
        for (s in ranked) {
            if ((perFamily[s.tag] ?: 0) >= MAX_PER_FAMILY) continue
            perFamily.merge(s.tag, 1, Int::plus)
            picked += s
            if (picked.size >= size) break
        }

        // the tile's promise leads the deck
        if (startItem != null) {
            val seeded = picked.firstOrNull { it.item.uri == startItem.uri }
                ?: Suggestion(
                    item = startItem,
                    tag = familyTagOf(startItem),
                    reason = reasonFor(context, startItem, familyTagOf(startItem)),
                    score = 0
                )
            listOf(seeded) + picked.filter { it.item.uri != startItem.uri }
        } else {
            picked
        }
    }
}
