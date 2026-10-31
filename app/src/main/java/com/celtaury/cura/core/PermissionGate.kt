package com.celtaury.cura.core

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

/**
 * One permission gate for every cleaning screen.
 * - Android 13+: granular media permissions (+ partial photo access handled)
 * - Android 12-: READ_EXTERNAL_STORAGE
 * Auto-asks on entry; shows a friendly intro with a retry button if refused.
 */
@Composable
fun PermissionGate(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(false) }
    var askedOnce by remember { mutableStateOf(false) }

    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val partialPerm = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { map ->
        val visualSelected = map[partialPerm] == true
        val imagesOk = map[Manifest.permission.READ_MEDIA_IMAGES] == true || visualSelected
        val videoOk = map[Manifest.permission.READ_MEDIA_VIDEO] == true || visualSelected
        granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            imagesOk && videoOk
        } else {
            map[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
    }

    LaunchedEffect(Unit) {
        val visualSelected = ContextCompat.checkSelfPermission(
            context, partialPerm
        ) == PackageManager.PERMISSION_GRANTED
        val imagesOk = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED || visualSelected
        val videoOk = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_VIDEO
        ) == PackageManager.PERMISSION_GRANTED || visualSelected
        granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            imagesOk && videoOk
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
        if (!granted) {
            askedOnce = true
            launcher.launch(perms)
        }
    }

    if (granted) {
        content()
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
                    if (askedOnce) "Try Again" else "Grant Access",
                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
