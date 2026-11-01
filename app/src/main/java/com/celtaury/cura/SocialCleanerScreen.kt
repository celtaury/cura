package com.celtaury.cura

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celtaury.cura.core.MediaItem
import com.celtaury.cura.core.PermissionGate
import com.celtaury.cura.core.Scanner
import com.celtaury.cura.core.humanBytes
import com.celtaury.cura.ui.deck.DeckSource

/**
 * Social Cleaner, UX v2: WhatsApp only in v1. Type tabs with counts; every tab
 * opens the shared Swipe Deck. Stickers are opt-in (sorted last). "Review all"
 * bundles Images+Videos+Docs+Audio into one deck (never stickers by default).
 */
@Composable
fun SocialCleanerScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    PermissionGate(
        title = "Social Cleaner",
        description = "WhatsApp loves to hoard memes and voice notes. See exactly what it's keeping, then judge card by card."
    ) {
        SocialCleanerContent(onBack, onNavigate)
    }
}

private data class SocialTab(
    val name: String,
    val emoji: String,
    val route: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SocialCleanerContent(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme

    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        items = Scanner.whatsappMedia(context)
        loading = false
    }

    val buckets = items.groupBy { Scanner.socialTypeOf(it) }
    val orderedTypes = listOf("Images", "Videos", "Docs", "Audio", "Stickers")
    val tabs = orderedTypes.map { type ->
        SocialTab(type, emojiFor(type), "deck_soc_" + type.lowercase())
    }

    fun openDeck(route: String, subset: List<MediaItem>) {
        DeckSource.set(route, subset)
        onNavigate(route)
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Social Cleaner", fontWeight = FontWeight.Bold)
                        Text("Media hoarded by chat apps", fontSize = 12.sp,
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
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (loading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = cs.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Auditing WhatsApp media…", color = cs.onSurfaceVariant)
                }
            } else if (items.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("💬✨", fontSize = 44.sp)
                    Text("Nothing here. Your chats are tidy!",
                        color = cs.onSurfaceVariant)
                }
            } else {
                Spacer(Modifier.height(6.dp))
                // Totals card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cs.tertiaryContainer)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(cs.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ChatBubble,
                                contentDescription = null,
                                tint = cs.tertiary
                            )
                        }
                        Spacer(Modifier.size(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("WhatsApp media found",
                                style = MaterialTheme.typography.titleSmall,
                                color = cs.onTertiaryContainer)
                            Text(
                                "${items.size} files · ${humanBytes(items.sumOf { it.sizeBytes })}",
                                style = MaterialTheme.typography.titleLarge,
                                color = cs.onTertiaryContainer
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                Text("Pick a pile to judge", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                // Type cards with counts — the v2 tabs
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tabs.forEach { tab ->
                        val subset = buckets[tab.name] ?: emptyList()
                        if (subset.isEmpty() && tab.name == "Stickers") {
                            return@forEach   // no stickers? don't even show the row
                        }
                        TabRow(
                            emoji = tab.emoji,
                            name = if (tab.name == "Stickers") "Stickers · opt-in" else tab.name,
                            count = subset.size,
                            sizeBytes = subset.sumOf { it.sizeBytes },
                            dimmed = subset.isEmpty(),
                            enabled = subset.isNotEmpty()
                        ) {
                            openDeck(tab.route, subset)
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Review all (stickers excluded by default — UX v2 decision 17)
                val reviewAll = items.filter { Scanner.socialTypeOf(it) != "Stickers" }
                if (reviewAll.isNotEmpty()) {
                    androidx.compose.material3.Button(
                        onClick = { openDeck("deck_soc_all", reviewAll) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(
                            "Review all ${reviewAll.size} in one deck",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun TabRow(
    emoji: String,
    name: String,
    count: Int,
    sizeBytes: Long,
    dimmed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (dimmed) cs.surfaceVariant else cs.surface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold)
                Text(
                    "$count files · ${humanBytes(sizeBytes)}",
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant
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

private fun emojiFor(type: String): String = when (type) {
    "Images" -> "🖼️"
    "Videos" -> "🎬"
    "Docs" -> "📄"
    "Audio" -> "🎙️"
    else -> "🌟"
}
