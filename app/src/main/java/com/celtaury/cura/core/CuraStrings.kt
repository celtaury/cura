package com.celtaury.cura.core

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate

/**
 * App-wide access to translated leaf strings for call sites that have no
 * Context (Format.kt's relative-day words). Initialized once from
 * CuraApplication. Batch D1.
 *
 * D1.5: when the user overrides the in-app language (per-app locales),
 * resolve through a cached configuration context. Reason: on API < 33 the
 * application context keeps the SYSTEM locale — only AppCompatActivity
 * activities get the override — so without this, relative-day words would
 * stay in the phone's language while the rest of the app switches.
 */
object CuraStrings {
    private var app: Context? = null
    private var localized: Pair<Context, String>? = null

    fun init(context: Context) {
        app = context.applicationContext
        localized = null
    }

    private fun ctx(): Context? {
        val base = app ?: return null
        val wanted = AppCompatDelegate.getApplicationLocales()
        if (wanted.isEmpty) return base
        val tag = wanted.toLanguageTags()
        localized?.let { (c, t) -> if (t == tag) return c }
        val loc = wanted.get(0) ?: return base
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(loc)
        val c = base.createConfigurationContext(cfg)
        localized = c to tag
        return c
    }

    fun s(@StringRes res: Int, vararg args: Any?): String =
        ctx()?.getString(res, *args) ?: res.toString()

    fun q(@PluralsRes res: Int, count: Int, vararg args: Any?): String =
        ctx()?.resources?.getQuantityString(res, count, *args) ?: count.toString()
}
