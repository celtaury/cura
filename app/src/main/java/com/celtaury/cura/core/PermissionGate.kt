package com.celtaury.cura.core

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.stringResource
import com.celtaury.cura.R

/**
 * One permission gate for every cleaning screen.
 * - Android 13+: granular media permissions (+ partial photo access handled)
 * - Android 12-: READ_EXTERNAL_STORAGE
 * Auto-asks on entry; shows a friendly intro with a retry button if refused.
 */

private val partialPerm = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

/** The exact permission set Cura needs. Shared by every gate, banner and onboarding. */
fun mediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

/** True when Cura can read media (full OR the Android-14 partial selection). */
fun hasMediaAccess(context: android.content.Context): Boolean {
    val visualSelected = ContextCompat.checkSelfPermission(
        context, partialPerm
    ) == PackageManager.PERMISSION_GRANTED
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
            == PackageManager.PERMISSION_GRANTED || visualSelected) &&
            (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO)
                == PackageManager.PERMISSION_GRANTED || visualSelected)
    } else {
        (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED)
    }
}

/**
 * True when access came only from Android 14's "Select photos" flow — Cura
 * sees the picked few, everything else is hidden. Batch C coaches this state
 * toward a full allow instead of silently working with a partial library.
 */
fun isPartialMediaAccess(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
    val selected = ContextCompat.checkSelfPermission(context, partialPerm) ==
        PackageManager.PERMISSION_GRANTED
    val images = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_MEDIA_IMAGES
    ) == PackageManager.PERMISSION_GRANTED
    val videos = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_MEDIA_VIDEO
    ) == PackageManager.PERMISSION_GRANTED
    return selected && !(images && videos)
}

@Composable
fun PermissionGate(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(false) }
    var askedOnce by remember { mutableStateOf(false) }
    // Android 14+ partial access: we keep working with the picked few, but
    // a small card keeps offering "Allow all" (Batch C coaching).
    var partial by remember { mutableStateOf(false) }
    var coachDismissed by remember { mutableStateOf(false) }

    val perms = mediaPermissions()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        granted = hasMediaAccess(context)
        partial = granted && isPartialMediaAccess(context)
    }

    LaunchedEffect(Unit) {
        granted = hasMediaAccess(context)
        partial = granted && isPartialMediaAccess(context)
        if (!granted) {
            askedOnce = true
            launcher.launch(perms)
        }
    }

    if (granted) {
        Column(Modifier.fillMaxWidth()) {
            if (partial && !coachDismissed) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            stringResource(R.string.perm_partial_title),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.perm_partial_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { launcher.launch(perms) },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.perm_allow_all)) }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { coachDismissed = true }) {
                                Text(stringResource(R.string.perm_later))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 90.dp, start = 24.dp, end = 24.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { launcher.launch(perms) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    if (askedOnce) stringResource(R.string.perm_try_again) else stringResource(R.string.perm_grant),
                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
