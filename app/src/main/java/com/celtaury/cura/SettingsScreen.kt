package com.celtaury.cura

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celtaury.cura.core.CuraLinks
import com.celtaury.cura.core.InstantDeletePrefs
import com.celtaury.cura.core.ThemePrefs
import com.celtaury.cura.core.hasMediaAccess
import com.celtaury.cura.core.isPartialMediaAccess
import com.celtaury.cura.core.mediaPermissions
import com.celtaury.cura.ui.theme.InterBlack

/**
 * Settings, UX v2 (v1 scope): theme, permissions, trash helper.
 * The trash helper is written for the elderly test: short steps, big button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { ThemePrefs.load(context) }
    val themeMode = ThemePrefs.mode

    var mediaOk by remember { mutableStateOf(true) }
    var partialMedia by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        InstantDeletePrefs.load(context)
        mediaOk = hasMediaAccess(context)
        partialMedia = mediaOk && isPartialMediaAccess(context)
    }

    // Android 14+ partial access: offer the upgrade to "Allow all" in place.
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        mediaOk = hasMediaAccess(context)
        partialMedia = mediaOk && isPartialMediaAccess(context)
    }

    val instant = InstantDeletePrefs.enabled

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Settings", fontWeight = FontWeight.Bold, fontFamily = InterBlack)
                        Text("Small dials, big comfort", fontSize = 12.sp,
                            color = cs.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(6.dp))

            // ── Appearance ──
            SectionCard(title = "Appearance") {
                Text(
                    "Theme",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    listOf(
                        ThemePrefs.SYSTEM to "System",
                        ThemePrefs.LIGHT to "Light",
                        ThemePrefs.DARK to "Dark"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { ThemePrefs.setMode(context, mode) },
                            label = { Text(label) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }

            // ── Permissions ──
            SectionCard(title = "Permissions") {
                Text(
                    when {
                        mediaOk && !partialMedia ->
                            "✅ Photo access granted — Cura can see your media."
                        mediaOk ->
                            "🟡 Cura sees only the photos you picked. Allow all " +
                                "so nothing gets missed."
                        else ->
                            "⚠️ Photo access is off — Cura can't see anything yet."
                    },
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                if (mediaOk && partialMedia) {
                    Button(
                        onClick = { permLauncher.launch(mediaPermissions()) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = MaterialTheme.shapes.large
                    ) { Text("Allow all photos") }
                    Spacer(Modifier.height(6.dp))
                }
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                            )
                        }.onFailure {
                            Toast.makeText(context, "Couldn't open settings", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = MaterialTheme.shapes.large
                ) { Text("Open Android app settings") }
            }

            // ── Deletion style (Batch C): the confidence dial ──
            SectionCard(title = "Deletion style") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Instant delete",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Skip Cura's “are you sure?” sheet — one tap and " +
                                "it's out of sight. Android may still ask on its " +
                                "own dialog, and everything stays recoverable in " +
                                "Trash & recovery for ~30 days.",
                            fontSize = 12.sp,
                            color = cs.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = instant,
                        onCheckedChange = { InstantDeletePrefs.set(context, it) }
                    )
                }
            }

            // ── Trash & recovery (elderly-friendly, UX v2 decision 27) ──
            SectionCard(title = "Deleted something by accident?") {
                Text(
                    "Cura never destroys instantly. Cleaned files rest in your " +
                        "phone's Trash for about 30 days:\n\n" +
                        "1. Open your Files app\n" +
                        "2. Tap Library or Menu\n" +
                        "3. Tap Trash — restore what you miss",
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onNavigate("trash") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = MaterialTheme.shapes.large
                ) { Text("Show what's in the trash") }
                TextButton(
                    onClick = {
                        val ok = runCatching {
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.setPackage("com.google.android.documentsui")
                            context.startActivity(intent)
                        }.isSuccess
                        if (!ok) {
                            Toast.makeText(
                                context,
                                "Open your Files app, then look for “Trash”",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Or open the Files app instead") }
            }

            // ── Support (Batch B — transparent earning: no ads, no data selling) ──
            SectionCard(title = "Support Cura 💚") {
                Text(
                    "Cura is free, shows no ads, and never sells your data. " +
                        "If it saved you space — and a little stress — a small " +
                        "donation keeps it that way.",
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                if (CuraLinks.DONATION_URL.isBlank()) {
                    Text(
                        "Donation link coming soon — nothing to tap yet 🙂",
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant
                    )
                } else {
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(CuraLinks.DONATION_URL))
                                )
                            }.onFailure {
                                Toast.makeText(context, "Couldn't open the link", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = MaterialTheme.shapes.large
                    ) { Text("Support the project") }
                }
            }

            // ── Your data ──
            MenuRow(title = "Your impact 🌿", subtitle = "Saved space, sessions, storage") {
                onNavigate("more")
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "CURA v${BuildConfig.VERSION_NAME} · flow build — storage care, not storage stress.",
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = cs.onSurfaceVariant
            )
        }
    }
}
