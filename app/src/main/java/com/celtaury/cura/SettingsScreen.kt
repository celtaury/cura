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
import androidx.compose.ui.res.stringResource

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
                        Text(stringResource(R.string.set_title), fontWeight = FontWeight.Bold, fontFamily = InterBlack)
                        Text(stringResource(R.string.set_subtitle), fontSize = 12.sp,
                            color = cs.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back_cd))
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
                            stringResource(R.string.set_media_ok)
                        mediaOk ->
                            stringResource(R.string.set_media_partial)
                        else ->
                            stringResource(R.string.set_media_off)
                    },
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                if (mediaOk && partialMedia) {
                    Button(
                        onClick = { permLauncher.launch(mediaPermissions()) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = MaterialTheme.shapes.large
                    ) { Text(stringResource(R.string.set_allow_all)) }
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
                            Toast.makeText(context, context.getString(R.string.set_open_failed), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = MaterialTheme.shapes.large
                ) { Text(stringResource(R.string.set_open_android)) }
            }

            // ── Deletion style (Batch C): the confidence dial ──
            SectionCard(title = stringResource(R.string.set_deletion_style)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.set_instant_title),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.set_instant_body),
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
            SectionCard(title = stringResource(R.string.set_accident)) {
                Text(
                    stringResource(R.string.set_trash_teach),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onNavigate("trash") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = MaterialTheme.shapes.large
                ) { Text(stringResource(R.string.set_show_trash)) }
                // D1 hotfix: resolved at composition — used inside onClick below
                val filesHint = stringResource(R.string.common_files_hint)
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
                                filesHint,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.set_open_files_instead)) }
            }

            // ── Support (Batch B — transparent earning: no ads, no data selling) ──
            SectionCard(title = stringResource(R.string.set_support)) {
                Text(
                    stringResource(R.string.set_support_body),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                if (CuraLinks.DONATION_URL.isBlank()) {
                    Text(
                        stringResource(R.string.set_donation_soon),
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
                                Toast.makeText(context, context.getString(R.string.set_link_failed), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = MaterialTheme.shapes.large
                    ) { Text(stringResource(R.string.set_support_btn)) }
                }
            }

            // ── Your data ──
            MenuRow(title = stringResource(R.string.set_impact), subtitle = stringResource(R.string.set_impact_sub)) {
                onNavigate("more")
            }

            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.set_footer, BuildConfig.VERSION_NAME),
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
