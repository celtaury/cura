package com.celtaury.cura.core

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * App-wide access to translated leaf strings for call sites that have no
 * Context (Format.kt's relative-day words). Initialized once from
 * CuraApplication. Batch D1.
 */
object CuraStrings {
    private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    fun s(@StringRes res: Int, vararg args: Any?): String =
        app?.getString(res, *args) ?: res.toString()

    fun q(@PluralsRes res: Int, count: Int, vararg args: Any?): String =
        app?.resources?.getQuantityString(res, count, *args) ?: count.toString()
}
