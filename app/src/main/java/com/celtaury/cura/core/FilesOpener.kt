package com.celtaury.cura.core

import android.app.DownloadManager
import android.content.Context
import android.content.Intent

/**
 * Opens the phone's Files app — the place where the system Trash lives
 * (Library / Menu → Trash). 1.0.3 (user report: "go to files don't actually
 * work"): the old call was a bare ACTION_VIEW pinned to
 * com.google.android.documentsui with no data, which most launchers refuse —
 * a silent no-op. This walks a ladder and returns whether ANYTHING opened:
 *
 *   1. Files (com.google.android.documentsui) FilesActivity, explicit
 *   2. the AOSP DocumentsUI FilesActivity, explicit
 *   3. Files by Google (com.google.android.apps.nbu.files) launcher intent
 *   4. the Downloads viewer (ACTION_VIEW_DOWNLOADS) — last resort, at least
 *      it is a file surface the user recognizes
 *
 * Callers fall back to the "look for Trash" hint toast when this returns
 * false. Returns true on the first successful startActivity.
 */
fun openSystemFilesApp(context: Context): Boolean {
    val pm = context.packageManager
    val attempts = listOf(
        Intent(Intent.ACTION_VIEW).setClassName(
            "com.google.android.documentsui",
            "com.google.android.documentsui.files.FilesActivity"
        ),
        Intent(Intent.ACTION_VIEW).setClassName(
            "com.android.documentsui",
            "com.android.documentsui.files.FilesActivity"
        ),
        pm.getLaunchIntentForPackage("com.google.android.apps.nbu.files"),
        Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
    )
    attempts.forEach { intent ->
        if (intent != null && runCatching { context.startActivity(intent) }.isSuccess) {
            return true
        }
    }
    return false
}
