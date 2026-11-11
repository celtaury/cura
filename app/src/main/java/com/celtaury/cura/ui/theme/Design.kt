package com.celtaury.cura.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Cura design language — the single source for recurring measurements,
 * radii, motion feels and the swipe-glow palette.
 *
 * Batch F establishes the vocabulary. Adoption is deliberate and gradual:
 * every surface adopts these tokens when it gets touched, so a redesign
 * never has to guess a value twice and the app stops drifting between
 * 14/16/20dp paddings and 18/22/24/28dp corners by accident.
 *
 * Rules of the language:
 *  · spacing climbs the scale — never invent an in-between value
 *  · list rows use [radiusCard], standard cards [radiusCardLg],
 *    hero cards and sheets [radiusSheet], the deck card [radiusDeckCard]
 *  · primary controls are [controlHeight] tall
 *  · destructive accents come from the theme (error), not from here
 */
object CuraDesign {

    // ── spacing scale ─────────────────────────────────────
    val spaceXs = 4.dp      // inside-component breathing
    val spaceS = 8.dp       // related-element gap
    val spaceM = 12.dp      // card-internal gap
    val spaceL = 16.dp      // screen horizontal padding
    val spaceXl = 20.dp     // card padding
    val spaceXxl = 24.dp    // section gap
    val spaceXxxl = 28.dp   // hero padding

    // ── corner radii ─────────────────────────────────────
    val radiusChip = 12.dp      // small chips, thumbnails
    val radiusCard = 18.dp      // list rows
    val radiusCardLg = 22.dp    // standard cards
    val radiusSheet = 24.dp     // hero/feature cards, sheets
    val radiusDeckCard = 28.dp  // the swipe deck card

    // ── controls ─────────────────────────────────────────
    val controlHeight = 54.dp   // primary buttons
    val iconTile = 46.dp        // dashboard feature-card icon tile

    // ── motion ───────────────────────────────────────────
    const val glowFadeMs = 280  // swipe-glow fade-out on release

    // ── swipe glow ───────────────────────────────────────
    val glowSave = Color(0xFF2ECC71)    // green — thrown right
    val glowDelete = Color(0xFFFF4D4F)  // red — thrown left
    const val glowMaxAlpha = 0.60f      // ceiling at a full throw
}
