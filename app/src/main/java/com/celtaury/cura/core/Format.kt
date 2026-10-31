package com.celtaury.cura.core

import java.util.Locale

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

/** "3 days ago" style relative label from a unix-seconds timestamp. */
fun relativeDays(dateAddedSec: Long, nowSec: Long = System.currentTimeMillis() / 1000): String {
    val days = ((nowSec - dateAddedSec) / 86400L).coerceAtLeast(0)
    return when {
        days == 0L -> "today"
        days == 1L -> "yesterday"
        days < 30L -> "$days days ago"
        days < 365L -> "${days / 30} mo ago"
        else -> "${"%.1f".format(Locale.US, days / 365.0)} yr ago"
    }
}
