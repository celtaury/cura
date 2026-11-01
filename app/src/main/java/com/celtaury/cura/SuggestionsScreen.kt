package com.celtaury.cura

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celtaury.cura.core.PermissionGate
import com.celtaury.cura.core.Scanner
import com.celtaury.cura.core.humanBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Suggestions, UX v2: the big hero tile lands here. Smart picks with live
 * counts — plus the roadmap rows (v1.5 blur, v2 similar) so the plan is visible.
 */
@Composable
fun SuggestionsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    PermissionGate(
        title = "Suggestions",
        description = "A daily shortlist of what deserves your attention. Counts update as you clean."
    ) {
        SuggestionsContent(onBack, onNavigate)
    }
}

private data class SuggestionCard(
    val emoji: String,
    val title: String,
    val subtitle: String,
    val route: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionsContent(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme

    var largeCount by remember { mutableStateOf(-1) }
    var largeBytes by remember { mutableStateOf(0L) }
    var shotsCount by remember { mutableStateOf(-1) }
    var shotsBytes by remember { mutableStateOf(0L) }
    var oldCount by remember { mutableStateOf(-1) }
    var oldBytes by remember { mutableStateOf(0L) }
    var monthCount by remember { mutableStateOf(-1) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val large = Scanner.largeFiles(context, minBytes = 30L * 1024 * 1024)
            largeCount = large.size
            largeBytes = large.sumOf { it.sizeBytes }
            val shots = Scanner.oldScreenshots(context, minAgeDays = 30)
            shotsCount = shots.size
            shotsBytes = shots.sumOf { it.sizeBytes }
            val old = Scanner.oldMedia(context, minAgeDays = 60)
            oldCount = old.size
            oldBytes = old.sumOf { it.sizeBytes }
            monthCount = Scanner.imagesByMonth(context).size
        }
    }

    val cards = listOf(
        SuggestionCard("🐘", "Large Files", "30 MB and up — the heavy hitters", "large_files"),
        SuggestionCard("📸", "Old Screenshots", "30+ days of dust", "screenshots"),
        SuggestionCard("🕰️", "Old Media", "Photos & videos untouched for 60+ days", "old_media"),
        SuggestionCard("🗂️", "Organize Images", "Sort your gallery month by month", "organize")
    )

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Suggestions", fontWeight = FontWeight.Bold)
                        Text("Your storage, sorted for today", fontSize = 12.sp,
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
            cards.forEach { card ->
                val (count, bytes) = when (card.route) {
                    "large_files" -> largeCount to largeBytes
                    "screenshots" -> shotsCount to shotsBytes
                    "old_media" -> oldCount to oldBytes
                    else -> monthCount to 0L
                }
                Card(
                    modifier = Modifier.fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clickable { onNavigate(card.route) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cs.surface),
                    border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(cs.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) { Text(card.emoji, fontSize = 22.sp) }
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(card.title, fontWeight = FontWeight.Bold)
                            Text(card.subtitle, fontSize = 12.sp, color = cs.onSurfaceVariant)
                            Text(
                                if (count < 0) "Counting…"
                                else if (card.route == "organize") "$count months of photos"
                                else "$count files · ${humanBytes(bytes)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.primary
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

            Spacer(Modifier.height(8.dp))
            Text("On the roadmap", style = MaterialTheme.typography.titleSmall,
                color = cs.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            RoadmapRow("🌫️", "Blurry images", "Bad-quality detector", "v1.5")
            RoadmapRow("👯", "Similar pictures", "Same scene, many shots — real similarity", "v2")
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun RoadmapRow(emoji: String, title: String, subtitle: String, version: String) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 20.sp)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
            Text(version, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cs.primary)
        }
    }
}
