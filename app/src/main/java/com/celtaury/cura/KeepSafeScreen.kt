package com.celtaury.cura

import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
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
import com.celtaury.cura.core.KeepSafe
import com.celtaury.cura.core.humanBytes

private data class ProtectedEntry(val uri: Uri, val name: String, val sizeBytes: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeepSafeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme

    var entries by remember { mutableStateOf<List<ProtectedEntry>>(emptyList()) }

    LaunchedEffect(Unit) {
        entries = KeepSafe.all(context).mapNotNull { str ->
            runCatching {
                val uri = Uri.parse(str)
                resolveEntry(context, uri)
            }.getOrNull()
        }
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Keep-safe", fontWeight = FontWeight.Bold)
                        Text("Protected from every cleaner", fontSize = 12.sp,
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cs.errorContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null,
                        tint = cs.error)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Swipe right on any file in Large Files — or keep a discovery on the home screen — and it lands here. Cura will never suggest it for cleaning.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (entries.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("🛡️", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Nothing protected yet", color = cs.onSurfaceVariant)
                    Text(
                        "Swipe right on a file to keep it safe forever",
                        fontSize = 12.sp, color = cs.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    "${entries.size} protected · ${humanBytes(entries.sumOf { it.sizeBytes })}",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleSmall
                )
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    items(entries, key = { it.uri.toString() }) { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.name, maxLines = 1, fontWeight = FontWeight.Medium)
                                Text(humanBytes(entry.sizeBytes), fontSize = 12.sp,
                                    color = cs.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                KeepSafe.remove(context, entry.uri)
                                entries = entries.filterNot { it.uri == entry.uri }
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove protection")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun resolveEntry(context: android.content.Context, uri: Uri): ProtectedEntry? {
    return try {
        context.contentResolver.query(
            uri,
            arrayOf(
                android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                android.provider.MediaStore.MediaColumns.SIZE
            ),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                val nameIdx = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.SIZE)
                ProtectedEntry(
                    uri = uri,
                    name = c.getString(nameIdx) ?: uri.lastPathSegment ?: "Protected file",
                    sizeBytes = c.getLong(sizeIdx)
                )
            } else null
        } ?: ProtectedEntry(uri, uri.lastPathSegment ?: "Protected file", 0L)
    } catch (_: Exception) {
        ProtectedEntry(uri, uri.lastPathSegment ?: "Protected file", 0L)
    }
}
