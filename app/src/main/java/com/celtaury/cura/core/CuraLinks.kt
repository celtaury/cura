package com.celtaury.cura.core

/**
 * Outbound links in one honest place (Batch B — transparent earning).
 *
 * DONATION_URL is intentionally the ONLY place the support link lives.
 * Keep it "" until the real page exists: the Support card in Settings
 * detects the blank and shows "coming soon" instead of a dead button.
 *
 * When ready, paste the full URL, e.g.:
 *   const val DONATION_URL = "https://trakteer.id/your-page"
 */
object CuraLinks {
    // ← paste your Trakteer / Saweria / Ko-fi / GitHub Sponsors URL here
    const val DONATION_URL: String = "https://linktr.ee/celtaury"
}
