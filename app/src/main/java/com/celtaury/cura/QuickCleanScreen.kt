package com.celtaury.cura

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celtaury.cura.core.JunkCategory
import com.celtaury.cura.core.MediaItem
import com.celtaury.cura.core.Scanner
import com.celtaury.cura.core.humanBytes
import com.celtaury.cura.ui.deck.DeckSource
import com.celtaury.cura.ui.theme.InterBlack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Quick Clean, UX v2: scan → category cards → tap one → the shared Swipe Deck.
 * No checkbox review anymore — judging happens one card at a time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCleanScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme

    var phase by remember { mutableStateOf("scanning") } // scanning / categories
    var scanStatus by remember { mutableStateOf("Warming up…") }
    var categories by remember { mutableStateOf<List<JunkCategory>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val found = mutableListOf<JunkCategory>()

            scanStatus = "Hunting empty files…"
            val empties = Scanner.emptyFiles(context)
            if (empties.isNotEmpty()) found += JunkCategory("empty", "Empty files", "Zero bytes, pure clutter", empties)

            scanStatus = "Spotting twins…"
            val dupes = Scanner.duplicateImages(context)
            if (dupes.isNotEmpty()) found += JunkCategory("dupes", "Duplicate photos", "Same picture, many copies", dupes)

            scanStatus = "Digging up installers…"
            val apks = Scanner.apkFiles(context)
            if (apks.isNotEmpty()) found += JunkCategory("apks", "APK installers", "Forgotten installer files", apks)

            scanStatus = "Leafing through screenshots…"
            val shots = Scanner.oldScreenshots(context, minAgeDays = 30)
            if (shots.isNotEmpty()) found += JunkCategory("shots", "Old screenshots", "Untouched for 30+ days", shots)

            // One file can qualify for several pickers (e.g. an old screenshot that
            // is also a duplicate). Keep it in the FIRST category only — the same
            // URI twice in one deck would fight over the same decision.
            val seen = HashSet<String>()
            categories = found.map { cat ->
                cat.copy(items = cat.items.filter { seen.add(it.uri.toString()) })
            }.filter { it.items.isNotEmpty() }
        }
        phase = "categories"
    }

    val routeFor = mapOf(
        "empty" to "deck_empty",
        "dupes" to "deck_dupes",
        "apks" to "deck_apks",
        "shots" to "deck_shots"
    )

    fun openDeck(route: String, items: List<MediaItem>) {
        DeckSource.set(route, items)
        onNavigate(route)
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Quick Clean", fontWeight = FontWeight.Bold, fontFamily = InterBlack)
                        Text("Smart picks, judged by you", fontSize = 12.sp,
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
        Crossfade(targetState = phase, label = "quickclean_phase") { p ->
            when (p) {
                "scanning" -> Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = cs.primary,
                        trackColor = cs.surfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))
                    Crossfade(targetState = scanStatus, label = "scan_status") { s ->
                        Text(s, style = MaterialTheme.typography.titleMedium,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("🛡️ Keep-safe files are never touched",
                        fontSize = 12.sp, color = cs.onSurfaceVariant)
                }

                else -> {
                    if (categories.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("🌿", fontSize = 52.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("You're already clean!",
                                style = MaterialTheme.typography.headlineSmall)
                            Text("Nothing junky found this time.",
                                color = cs.onSurfaceVariant)
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = onBack, shape = MaterialTheme.shapes.large) {
                                Text("Back home")
                            }
                        }
                    } else {
                        val totalItems = categories.sumOf { it.items.size }
                        val totalBytes = categories.sumOf { it.totalBytes }
                        Column(
                            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
                        ) {
                            Spacer(Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = cs.tertiaryContainer
                                )
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(
                                        "Found $totalItems things to reconsider",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = cs.onTertiaryContainer
                                    )
                                    Text(
                                        "${humanBytes(totalBytes)} of clutter · judge them card by card",
                                        fontSize = 12.sp,
                                        color = cs.onTertiaryContainer
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(categories, key = { it.key }) { cat ->
                                    val route = routeFor[cat.key] ?: return@items
                                    Card(
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = cs.surface
                                        ),
                                        border = BorderStroke(
                                            1.dp, cs.outlineVariant.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable { openDeck(route, cat.items) }
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .background(
                                                        cs.secondaryContainer,
                                                        RoundedCornerShape(14.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    when (cat.key) {
                                                        "empty" -> "🕳️"
                                                        "dupes" -> "👯"
                                                        "apks" -> "📦"
                                                        else -> "📸"
                                                    },
                                                    fontSize = 20.sp
                                                )
                                            }
                                            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                                Text(cat.title, fontWeight = FontWeight.Bold)
                                                Text(
                                                    cat.subtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = cs.onSurfaceVariant
                                                )
                                                Text(
                                                    "${cat.items.size} files · ${humanBytes(cat.totalBytes)}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            Icon(
                                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = cs.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            Button(
                                onClick = {
                                    val all = categories.flatMap { it.items }
                                        .distinctBy { it.uri.toString() }
                                    openDeck("deck_qc_all", all)
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Text("Review all $totalItems in one deck",
                                    fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}
