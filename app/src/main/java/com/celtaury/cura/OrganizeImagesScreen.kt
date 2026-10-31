package com.celtaury.cura

import android.app.Activity
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.celtaury.cura.core.MediaItem
import com.celtaury.cura.core.PermissionGate
import com.celtaury.cura.core.Scanner
import com.celtaury.cura.core.humanBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OrganizeImagesScreen(onBack: () -> Unit) {
    PermissionGate(
        title = "Organize Images",
        description = "Your gallery, grouped by month. Pick photos and tidy them into their own Cura folders."
    ) {
        OrganizeImagesContent(onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrganizeImagesContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var months by remember { mutableStateOf<List<Pair<String, List<MediaItem>>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var moving by remember { mutableStateOf(false) }
    var pendingAfterWrite by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }

    suspend fun rescan() {
        loading = true
        months = Scanner.imagesByMonth(context)
        selected = emptySet()
        loading = false
    }

    LaunchedEffect(Unit) { rescan() }

    // 1) The actual move: update RELATIVE_PATH into Pictures/Cura/<yyyy-MM>
    val performMove: (List<MediaItem>) -> Unit = { items ->
        scope.launch {
            val moved = withContext(Dispatchers.IO) {
                var n = 0
                for (item in items) {
                    try {
                        val folder = Scanner.monthKeyOf(item.dateAddedSec)
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Cura/$folder")
                        }
                        if (context.contentResolver.update(item.uri, values, null, null) > 0) n++
                    } catch (_: Exception) {
                        // unreadable row — skip
                    }
                }
                n
            }
            moving = false
            Toast.makeText(context, "Moved $moved photos 📂", Toast.LENGTH_SHORT).show()
            rescan()
        }
    }

    // 2) Write-access result comes back here
    val writeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val ok = result.resultCode == Activity.RESULT_OK
        pendingAfterWrite?.invoke(ok)
        pendingAfterWrite = null
    }

    // 3) Ask Android for write access to exactly these photos, then move
    val startMove: (List<MediaItem>) -> Unit = { items ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            moving = true
            pendingAfterWrite = { ok ->
                if (ok) performMove(items) else {
                    moving = false
                    Toast.makeText(context, "Permission needed to move photos",
                        Toast.LENGTH_SHORT).show()
                }
            }
            runCatching {
                val pi = MediaStore.createWriteRequest(
                    context.contentResolver, items.map { it.uri }
                )
                writeLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            }.onFailure {
                moving = false
                pendingAfterWrite = null
                Toast.makeText(context, "Couldn't start the move", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Moving photos needs Android 11+", Toast.LENGTH_LONG).show()
        }
    }

    val allItems = months.flatMap { it.second }
    val selectedUris = allItems.filter { it.uri.toString() in selected }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Organize Images", fontWeight = FontWeight.Bold)
                        Text("Tap a photo to select it", fontSize = 12.sp,
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
        },
        bottomBar = {
            if (selectedUris.isNotEmpty() && !loading) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cs.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${selectedUris.size} photos selected",
                                fontWeight = FontWeight.SemiBold)
                            Text("Moves into Pictures/Cura/<month>",
                                fontSize = 12.sp, color = cs.onSurfaceVariant)
                        }
                        Button(
                            onClick = { startMove(selectedUris) },
                            enabled = !moving,
                            colors = ButtonDefaults.buttonColors(containerColor = cs.primary)
                        ) {
                            if (moving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = cs.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else Text("Move 📂")
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = cs.primary)
                Spacer(Modifier.height(12.dp))
                Text("Grouping your gallery…", color = cs.onSurfaceVariant)
            }
        } else if (months.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("🖼️", fontSize = 48.sp)
                Text("No photos found", color = cs.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                months.forEach { (key, items) ->
                    val label = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                        .format(Date(parseMonthKey(key)))
                    item(key = "h_$key") {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(label, fontWeight = FontWeight.Bold)
                                Text(
                                    "${items.size} photos · ${humanBytes(items.sumOf { it.sizeBytes })}",
                                    fontSize = 12.sp, color = cs.onSurfaceVariant
                                )
                            }
                            Text(
                                "Select all",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.primary,
                                modifier = Modifier.clickable {
                                    val keys = items.map { it.uri.toString() }.toSet()
                                    selected = if (selected.containsAll(keys)) selected - keys
                                    else selected + keys
                                }
                            )
                        }
                    }
                    item(key = "r_$key") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(items, key = { _, it -> it.uri.toString() }) { _, item ->
                                val checked = item.uri.toString() in selected
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            val k = item.uri.toString()
                                            selected = if (k in selected) selected - k
                                            else selected + k
                                        }
                                        .border(
                                            width = if (checked) 3.dp else 0.dp,
                                            color = if (checked) cs.primary else Color.Transparent,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                ) {
                                    AsyncImage(
                                        model = item.uri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (checked) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(20.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(cs.primary)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseMonthKey(key: String): Long = try {
    val parts = key.split("-")
    val cal = java.util.Calendar.getInstance()
    cal.set(parts[0].toInt(), parts[1].toInt() - 1, 1, 0, 0, 0)
    cal.timeInMillis
} catch (_: Exception) {
    0L
}
