package com.celtaury.cura.core

import java.util.Locale
import com.celtaury.cura.R

/** Human-readable byte sizes, locale-safe ("1.2 GB", "340 MB", "18 KB"). */
fun humanBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.1f GB", gb)
}

/** "2026-10" → "October 2026" (device locale); falls back to the raw key. */
fun monthLabelOf(monthKey: String): String = try {
    java.time.YearMonth.parse(monthKey)
        .format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))
} catch (_: Exception) {
    monthKey
}

/** "3 days ago" style relative label from a unix-seconds timestamp. */
fun relativeDays(dateAddedSec: Long, nowSec: Long = System.currentTimeMillis() / 1000): String {
    val days = ((nowSec - dateAddedSec) / 86400L).coerceAtLeast(0)
    return when {
        days == 0L -> CuraStrings.s(R.string.rel_today)
        days == 1L -> CuraStrings.s(R.string.rel_yesterday)
        days < 30L -> CuraStrings.q(R.plurals.rel_days_ago, days.toInt(), days.toInt())
        days < 365L -> CuraStrings.s(R.string.rel_mo_ago, days / 30)
        else -> CuraStrings.s(
            R.string.rel_yr_ago,
            String.format(Locale.US, "%.1f", days / 365.0)
        )
    }
}
