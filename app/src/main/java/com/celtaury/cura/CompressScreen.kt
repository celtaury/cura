package com.celtaury.cura

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.celtaury.cura.core.SavedStats
import com.celtaury.cura.core.Scanner
import com.celtaury.cura.core.humanBytes
import com.celtaury.cura.core.rememberDeleteController
import com.celtaury.cura.ui.theme.AccentOrange
import com.celtaury.cura.ui.theme.AccentOrangeContainer
import com.celtaury.cura.ui.theme.AccentOrangeContainerDark
import com.celtaury.cura.ui.theme.OnAccentOrange
import com.celtaury.cura.ui.theme.OnAccentOrangeDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun CompressScreen(onBack: () -> Unit) {
    PermissionGate(
        title = "Photo Compress",
        description = "Shrink oversized photos to reclaim gigabytes. Your originals stay untouched — compressed copies land in Pictures/Cura Compressed."
    ) {
        CompressContent(onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompressContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val dark = isSystemInDarkTheme()
    val orangeContainer = if (dark) AccentOrangeContainerDark else AccentOrangeContainer
    val onOrange = if (dark) OnAccentOrangeDark else OnAccentOrange

    var photos by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var compressing by remember { mutableStateOf(false) }
    var progressDone by remember { mutableStateOf(0) }
    var progressTotal by remember { mutableStateOf(0) }
    var resultSaved by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        photos = Scanner.largeFiles(context, minBytes = 1L * 1024 * 1024)
            .filter { it.mime.startsWith("image/") }
            .take(200)
        selected = photos.map { it.uri.toString() }.toSet()
        loading = false
    }

    val selectedItems = photos.filter { it.uri.toString() in selected }
    val selectedBytes = selectedItems.sumOf { it.sizeBytes }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Photo Compress", fontWeight = FontWeight.Bold)
                        Text("Originals stay safe · copies go to Cura Compressed",
                            fontSize = 12.sp, color = cs.onSurfaceVariant)
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
            if (!loading && photos.isNotEmpty() && !compressing) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = orangeContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${selectedItems.size} photos", fontWeight = FontWeight.SemiBold)
                            Text("${humanBytes(selectedBytes)} of photos in",
                                fontSize = 12.sp, color = onOrange)
                        }
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                    Toast.makeText(
                                        context,
                                        "Saving compressed photos needs Android 10+",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@Button
                                }
                                compressing = true
                                progressDone = 0
                                progressTotal = selectedItems.size
                                val items = selectedItems
                                scope.launch {
                                    var savedTotal = 0L
                                    withContext(Dispatchers.IO) {
                                        items.forEachIndexed { i, item ->
                                            val saved = compressImage(context, item)
                                            if (saved != null && saved > 0) savedTotal += saved
                                            progressDone = i + 1
                                        }
                                    }
                                    compressing = false
                                    SavedStats.addClean(context, savedTotal)
                                    resultSaved = savedTotal
                                }
                            },
                            enabled = selectedItems.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                        ) { Text("Compress ⚡", color = Color.White) }
                    }
                }
                if (compressing) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        LinearProgressIndicator(
                            progress = {
                                if (progressTotal == 0) 0f
                                else progressDone.toFloat() / progressTotal
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = AccentOrange,
                            trackColor = cs.surfaceVariant
                        )
                        Text(
                            "Compressing $progressDone of $progressTotal…",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
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
                CircularProgressIndicator(color = AccentOrange)
                Spacer(Modifier.height(12.dp))
                Text("Finding oversized photos…", color = cs.onSurfaceVariant)
            }
        } else if (photos.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("🪶", fontSize = 48.sp)
                Text("Nothing oversized found", color = cs.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                item {
                    Text(
                        "Photos 1 MB and bigger, biggest first",
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(photos, key = { it.uri.toString() }) { item ->
                    val checked = item.uri.toString() in selected
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                val k = item.uri.toString()
                                selected = if (k in selected) selected - k else selected + k
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.displayName, maxLines = 1, fontWeight = FontWeight.Medium)
                            Text(humanBytes(item.sizeBytes), fontSize = 12.sp,
                                color = cs.onSurfaceVariant)
                        }
                        Checkbox(checked = checked, onCheckedChange = {
                            val k = item.uri.toString()
                            selected = if (k in selected) selected - k else selected + k
                        })
                    }
                }
                item { Spacer(Modifier.height(150.dp)) }
            }
        }
    }

    resultSaved?.let { saved ->
        AlertDialog(
            onDismissRequest = { resultSaved = null },
            title = { Text("Done! 🪶") },
            text = {
                Text(
                    "Compressed copies saved in Pictures/Cura Compressed.\n" +
                        "Reclaimed: ${humanBytes(saved)}\n\n" +
                        "Originals are untouched — review them in Large Files whenever you're ready."
                )
            },
            confirmButton = {
                TextButton(onClick = { resultSaved = null }) { Text("Nice") }
            }
        )
    }
}

/** Decode → downscale → fix rotation → JPEG re-encode → save via MediaStore. Returns bytes saved. */
private fun compressImage(context: android.content.Context, item: MediaItem): Long? {
    return try {
        val resolver = context.contentResolver

        // 1. bounds
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(item.uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // 2. downscale sample so the long edge ≈ 1920px
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1920) sample *= 2
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(item.uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null

        // 3. EXIF rotation
        val degrees = rotationDegrees(context, item.uri)
        val rotated = if (degrees != 0f) {
            val m = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        } else bitmap

        // 4. re-encode
        val baos = ByteArrayOutputStream()
        rotated.compress(Bitmap.CompressFormat.JPEG, 78, baos)
        val newSize = baos.size().toLong()
        if (newSize >= item.sizeBytes * 0.95) {
            // compression wouldn't help this one — skip
            rotated.recycle()
            return null
        }

        // 5. insert into MediaStore (Android 10+)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "CURA_${item.displayName}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Cura Compressed")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val newUri = resolver.insert(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), values
        )
        if (newUri == null) {
            rotated.recycle()
            return null
        }
        resolver.openOutputStream(newUri)?.use { rotated.compress(Bitmap.CompressFormat.JPEG, 78, it) }
        val clear = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        resolver.update(newUri, clear, null, null)
        rotated.recycle()

        item.sizeBytes - newSize
    } catch (_: Exception) {
        null
    }
}

private fun rotationDegrees(context: android.content.Context, uri: android.net.Uri): Float = try {
    val ex = context.contentResolver.openInputStream(uri)?.use {
        androidx.exifinterface.media.ExifInterface(it)
    } ?: return 0f
    when (ex.getAttributeInt(
        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
    )) {
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
} catch (_: Exception) {
    0f
}
