package com.celtaury.cura.core

/**
 * Outbound links in one honest place (Batch B — transparent earning).
 *
 * DONATION_URL is intentionally the ONLY place the support link lives.
 * The Support card in Settings opens it, and the one-time donation popup
 * (Batch M) opens it too. Blank = both show "coming soon" / skip the button.
 *
 * Batch M: the real page is live — every donation keeps Cura ad-free.
 */
object CuraLinks {
    const val DONATION_URL: String = "https://linktr.ee/celtaury"
}
