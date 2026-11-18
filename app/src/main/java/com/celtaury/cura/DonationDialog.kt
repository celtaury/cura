package com.celtaury.cura

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.celtaury.cura.core.CuraLinks
import com.celtaury.cura.core.DonationPrefs
import com.celtaury.cura.core.DonationPrompt

/**
 * Batch M: the one-time "Cura is free!" dialog. Shows once per install,
 * after the 2nd finished clean session (DonationPrefs); "Maybe later" and
 * "Donate" both dismiss it forever — the Support card in Settings stays
 * reachable. Donate opens CuraLinks.DONATION_URL (linktr.ee/celtaury).
 */
@Composable
fun DonationPopupDialog() {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme

    if (!DonationPrompt.pending) return

    AlertDialog(
        onDismissRequest = { DonationPrefs.markShown(context) },
        title = {
            Text(
                stringResource(R.string.don_title),
                fontWeight = FontWeight.Bold,
                color = cs.onSurface
            )
        },
        text = {
            Text(stringResource(R.string.don_body), color = cs.onSurfaceVariant)
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val url = CuraLinks.DONATION_URL
                    DonationPrefs.markShown(context)
                    if (url.isNotBlank()) {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            )
                        } catch (_: Exception) {
                            // no browser — the dismiss above already handled it
                        }
                    }
                }
            ) {
                Text(
                    stringResource(R.string.don_btn),
                    fontWeight = FontWeight.Bold,
                    color = cs.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { DonationPrefs.markShown(context) }) {
                Text(stringResource(R.string.don_later), color = cs.onSurfaceVariant)
            }
        }
    )
}
