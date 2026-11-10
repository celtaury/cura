package com.celtaury.cura

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.asImageBitmap
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
import com.celtaury.cura.core.ThemePrefs
import com.celtaury.cura.core.humanBytes
import com.celtaury.cura.core.rememberConfirmableDelete
import com.celtaury.cura.ui.theme.AccentOrange
import com.celtaury.cura.ui.theme.AccentOrangeContainer
import com.celtaury.cura.ui.theme.AccentOrangeContainerDark
import com.celtaury.cura.ui.theme.OnAccentOrange
import com.celtaury.cura.ui.theme.OnAccentOrangeDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

private data class CompressPreset(
    val key: String,
    val labelRes: Int,
    val quality: Int,
    val maxEdge: Int
)

private val PRESETS = listOf(
    CompressPreset("high", R.string.comp_preset_high, 90, 1920),
    CompressPreset("balanced", R.string.comp_preset_balanced, 75, 1600),
    CompressPreset("small", R.string.comp_preset_small, 50, 1280)
)

/** Per-photo outcome of the last batch run, shown as a badge on the tile. */
private enum class ItemOutcome { SAVED, ALREADY_SMALL, FAILED }

/** What one compression attempt ended up doing. "Already small" is only ever
 *  claimed when the pixels were readable and every quality step still failed
 *  to shrink the file — decode and save problems are Failed, never skipped. */
private sealed interface CompressResult {
    /** Copy written to Pictures/Cura Compressed. savedBytes > 0. */
    data class Done(val savedBytes: Long, val newSize: Long, val qualityUsed: Int) : CompressResult

    /** Read fine, but even the fallback quality could not shrink it ≥5%. */
    data object AlreadySmall : CompressResult

    /** Could not read (stage=decode) or could not save (stage=insert/save). */
    data class Failed(val stage: String) : CompressResult
}

/** Preview outcome — mirrors the batch pipeline so the preview never lies. */
private sealed interface PreviewOutcome {
    data class Ready(val bitmap: Bitmap, val encodedBytes: Long, val qualityUsed: Int) : PreviewOutcome
    data object NoSavings : PreviewOutcome
    data object Unreadable : PreviewOutcome
}

private const val COMPRESS_TAG = "CuraCompress"

/** Decode → exact-downscale → EXIF-rotate → best-effort re-encode → save one
 *  copy into Pictures/Cura Compressed via MediaStore. The encoded bytes are
 *  measured once and the very same bytes are written to the new row (the old
 *  code encoded twice, so the saved-size estimate could drift from reality). */
private fun compressImage(
    context: android.content.Context,
    item: MediaItem,
    quality: Int,
    maxEdge: Int
): CompressResult {
    val original = originalBytes(context, item)
    val bitmap = decodeForCompress(context, item, maxEdge)
        ?: return CompressResult.Failed("decode")
    return try {
        val encoded = encodeBestEffort(bitmap, quality, original)
            ?: return CompressResult.AlreadySmall.also { bitmap.recycle() }

        val resolver = context.contentResolver
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
            Log.w(COMPRESS_TAG, "MediaStore insert failed: ${item.displayName}")
            bitmap.recycle()
            return CompressResult.Failed("insert")
        }

        val ok = try {
            val out = resolver.openOutputStream(newUri)
                ?: throw java.io.IOException("openOutputStream returned null")
            out.use { it.write(encoded.first) }
            resolver.update(
                newUri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null, null
            )
            true
        } catch (e: Exception) {
            Log.w(COMPRESS_TAG, "copy write failed: ${item.displayName}", e)
            runCatching { resolver.delete(newUri, null, null) } // drop the pending row
            false
        }
        bitmap.recycle()
        if (!ok) return CompressResult.Failed("save")

        // saved>0 is guaranteed by the ladder when original>0; if the size was
        // unknown (0) we bail BEFORE writing anything — no orphan rows.
        val saved = original - encoded.first.size
        if (saved <= 0L) {
            bitmap.recycle()
            return CompressResult.AlreadySmall
        }
        CompressResult.Done(saved, encoded.first.size.toLong(), encoded.second)
    } catch (e: Exception) {
        Log.w(COMPRESS_TAG, "compress failed: ${item.displayName}", e)
        bitmap.recycle()
        CompressResult.Failed("error")
    }
}

/** Try the preset quality, then step down a notch or two before giving up.
 *  Returns encoded bytes + the quality that finally cleared the ≥5% bar, or
 *  null when even the fallback could not meaningfully shrink the photo. */
private fun encodeBestEffort(bitmap: Bitmap, quality: Int, originalBytes: Long): Pair<ByteArray, Int>? {
    val ladder = linkedSetOf(quality, maxOf(40, quality - 20), maxOf(40, quality - 35))
    for (q in ladder) {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, q, baos)
        val bytes = baos.toByteArray()
        if (originalBytes <= 0L || bytes.size.toLong() < (originalBytes * 0.95).toLong()) {
            return bytes to q
        }
    }
    return null
}

/**
 * Decode a photo at ~maxEdge long edge with EXIF rotation applied.
 *
 * CRITICAL: with inJustDecodeBounds=true, BitmapFactory.decodeStream ALWAYS
 * returns null by design — it only fills in outWidth/outHeight. Because
 * `stream?.use { decode(...) }` evaluates to the LAMBDA's result, elvis- or
 * run-checking that chain (the original bug) made the null-check fire on
 * every single photo: decode failed 100% of the time and every image was
 * reported "already tiny" — later, honestly, "couldn't read". The stream-open
 * null-check below is therefore a plain `if (x == null)` on its own line,
 * and the decode call is a statement whose result is discarded (bounds) or
 * captured separately (full decode).
 */
private fun decodeForCompress(
    context: android.content.Context,
    item: MediaItem,
    maxEdge: Int
): Bitmap? {
    return try {
        val resolver = context.contentResolver

        // ── bounds pass: decodeStream returns null ON PURPOSE here ──
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(item.uri)
        if (boundsStream == null) {
            Log.w(COMPRESS_TAG, "stream open failed: ${item.displayName}")
            return null
        }
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(COMPRESS_TAG, "unreadable bounds ${bounds.outWidth}x${bounds.outHeight}: ${item.displayName}")
            return null
        }

        // Sample so the decoded long edge is below 2×maxEdge, then scale exactly.
        var sample = 1
        val srcEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (srcEdge > maxEdge) {
            while (srcEdge / (sample * 2) >= maxEdge) sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }

        // ── real decode pass: same discipline, result captured explicitly ──
        val imageStream = resolver.openInputStream(item.uri)
        if (imageStream == null) {
            Log.w(COMPRESS_TAG, "decode stream failed: ${item.displayName}")
            return null
        }
        val decoded = imageStream.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
        if (decoded == null) {
            Log.w(COMPRESS_TAG, "decode failed (corrupt or unsupported data): ${item.displayName}")
            return null
        }
        var current = decoded
        if (current.width <= 0 || current.height <= 0) {
            current.recycle()
            Log.w(COMPRESS_TAG, "decoded empty bitmap: ${item.displayName}")
            return null
        }

        val degrees = rotationDegrees(context, item.uri)
        if (degrees != 0f) {
            val m = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(current, 0, 0, current.width, current.height, m, true)
            if (rotated != current) current.recycle()
            current = rotated
        }

        // Exact downscale to maxEdge — inSampleSize alone can leave the edge up
        // to 2× the target, which wastes most of the possible savings.
        val longEdge = maxOf(current.width, current.height)
        if (longEdge > maxEdge) {
            val scale = maxEdge.toFloat() / longEdge
            val scaled = Bitmap.createScaledBitmap(
                current,
                (current.width * scale).toInt().coerceAtLeast(1),
                (current.height * scale).toInt().coerceAtLeast(1),
                true
            )
            if (scaled != current) current.recycle()
            current = scaled
        }
        current
    } catch (e: Exception) {
        Log.w(COMPRESS_TAG, "decode failed: ${item.displayName}", e)
        null
    }
}

/** MediaStore size, with a statSize fallback when the row reports 0. */
private fun originalBytes(context: android.content.Context, item: MediaItem): Long {
    if (item.sizeBytes > 0L) return item.sizeBytes
    return try {
        context.contentResolver.openFileDescriptor(item.uri, "r")?.use { it.statSize } ?: 0L
    } catch (_: Exception) {
        0L
    }
}

/** Decode → downscale → rotate → re-encode in memory, same pipeline as the
 *  batch (including the quality fallback), so the preview % matches reality. */
private fun compressPreview(
    context: android.content.Context,
    item: MediaItem,
    quality: Int,
    maxEdge: Int
): PreviewOutcome {
    val original = originalBytes(context, item)
    val bitmap = decodeForCompress(context, item, maxEdge) ?: return PreviewOutcome.Unreadable
    return try {
        val encoded = encodeBestEffort(bitmap, quality, original)
        if (encoded == null) {
            bitmap.recycle()
            PreviewOutcome.NoSavings
        } else {
            PreviewOutcome.Ready(bitmap, encoded.first.size.toLong(), encoded.second)
        }
    } catch (e: Exception) {
        Log.w(COMPRESS_TAG, "preview failed: ${item.displayName}", e)
        bitmap.recycle()
        PreviewOutcome.Unreadable
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

/**
 * Photo Compress, UX v3: photo-grid picker with per-photo outcome badges
 * (−NN% / already small / failed), a preview that runs the same pipeline as
 * the batch, a progress card that actually shows, and REPLACE flow —
 * compressed copies land in Pictures/Cura Compressed, then the originals are
 * removed (system trash keeps them ~30 days).
 */
@Composable
fun CompressScreen(onBack: () -> Unit) {
    PermissionGate(
        title = stringResource(R.string.dash_open_compress),
        description = stringResource(R.string.comp_desc)
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
    // Effective theme: follow the Settings override, not just the system
    // setting — same fix as the dashboard's decorative gradients.
    val dark = when (ThemePrefs.mode) {
        ThemePrefs.LIGHT -> false
        ThemePrefs.DARK -> true
        else -> isSystemInDarkTheme()
    }
    val orangeContainer = if (dark) AccentOrangeContainerDark else AccentOrangeContainer
    val onOrange = if (dark) OnAccentOrangeDark else OnAccentOrange

    var photos by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var preset by remember { mutableStateOf(PRESETS[1]) }

    // uri -> outcome + bytes saved, from the last batch run
    var outcomes by remember { mutableStateOf(mapOf<String, Pair<ItemOutcome, Long>>()) }

    var compressing by remember { mutableStateOf(false) }
    var progressDone by remember { mutableStateOf(0) }
    var progressTotal by remember { mutableStateOf(0) }

    // preview: sample photo + result of running the batch pipeline on it
    var previewBefore by remember { mutableStateOf<Pair<MediaItem, Long>?>(null) }
    var previewResult by remember { mutableStateOf<PreviewOutcome?>(null) }

    var pendingReplace by remember { mutableStateOf<Pair<List<android.net.Uri>, Long>?>(null) }
    var resultSaved by remember { mutableStateOf<Long?>(null) }

    suspend fun rescan() {
        loading = true
        val found = Scanner.largeFiles(context, minBytes = 1L * 1024 * 1024)
            .filter { it.mime.startsWith("image/") }
            .take(200)
        photos = found
        selected = found.map { it.uri.toString() }.toSet()
        outcomes = emptyMap()
        loading = false
    }

    LaunchedEffect(Unit) { rescan() }

    // Before/after preview whenever the sample photo or preset changes
    LaunchedEffect(selected.firstOrNull(), preset) {
        val first = photos.firstOrNull { it.uri.toString() == selected.firstOrNull() }
        if (first == null) {
            previewBefore = null
            previewResult = null
            return@LaunchedEffect
        }
        previewBefore = first to first.sizeBytes
        previewResult = null
        val result = withContext(Dispatchers.IO) {
            compressPreview(context, first, preset.quality, preset.maxEdge)
        }
        previewResult = result
    }

    val selectedItems = photos.filter { it.uri.toString() in selected }

    // Estimated batch savings from the preview sample (honest "~" number)
    val previewSavingsPct: Int? = previewBefore?.let { before ->
        val after = previewResult
        if (after is PreviewOutcome.Ready && before.second > 0) {
            (((before.second - after.encodedBytes) * 100f) / before.second)
                .toInt().coerceIn(0, 100)
        } else null
    }
    val estBytes = previewSavingsPct
        ?.let { pct -> selectedItems.sumOf { it.sizeBytes } * pct / 100L }
        ?: 0L

    // D1 hotfix: these strings are shown inside non-composable
    // callbacks (Toast in onClick / scope.launch / onCancelled) where
    // stringResource() is not allowed — resolve them here, at composition.
    val keptNote = stringResource(R.string.comp_kept_note)
    val needsQ = stringResource(R.string.comp_needs_q)
    val alreadyTiny = stringResource(R.string.comp_already_tiny)
    val allFailed = stringResource(R.string.comp_all_failed)

    val delete = rememberConfirmableDelete(
        onCleaned = { cleaned ->
            // `cleaned` = originals Android actually deleted. Denied ones are
            // still on disk: don't credit their bytes, and drop their SAVED
            // badges (outcomes is keyed by the original's uri string).
            val granted = cleaned.map { it.uri.toString() }.toSet()
            val saved = cleaned.sumOf { it.sizeBytes }
            SavedStats.addClean(context, saved)
            resultSaved = saved
            outcomes = outcomes.filter { (uri, outcome) ->
                outcome.first != ItemOutcome.SAVED || uri in granted
            }
            pendingReplace = null
            scope.launch { rescan() }
        },
        onCancelled = {
            // originals kept — the "saved" badges are no longer true, drop them
            outcomes = outcomes.filterValues { it.first != ItemOutcome.SAVED }
            Toast.makeText(context, keptNote, Toast.LENGTH_LONG).show()
            pendingReplace = null
        }
    )

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.dash_open_compress), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.comp_sub),
                            fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back_cd))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        bottomBar = {
            Column {
                if (compressing) {
                    // Progress card — lives OUTSIDE the !compressing guard now;
                    // the old code nested it inside, so it could never appear.
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = orangeContainer)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(
                                stringResource(R.string.comp_progress, progressDone, progressTotal),
                                fontWeight = FontWeight.SemiBold,
                                color = onOrange
                            )
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = {
                                    if (progressTotal == 0) 0f
                                    else progressDone.toFloat() / progressTotal
                                },
                                modifier = Modifier.fillMaxWidth(),
                                color = AccentOrange,
                                trackColor = onOrange.copy(alpha = 0.25f)
                            )
                        }
                    }
                } else if (!loading && photos.isNotEmpty()) {
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
                            Column(Modifier.weight(1f)) {
                                val totalBytes = selectedItems.sumOf { it.sizeBytes }
                                Text(
                                    pluralStringResource(R.plurals.comp_selected_n, selectedItems.size, selectedItems.size, humanBytes(totalBytes)),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    stringResource(R.string.comp_dest, stringResource(preset.labelRes)),
                                    fontSize = 12.sp, color = onOrange
                                )
                                if (previewSavingsPct != null && estBytes > 0L) {
                                    Text(
                                        stringResource(R.string.comp_est_saves, humanBytes(estBytes)),
                                        fontSize = 12.sp, color = onOrange
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                        Toast.makeText(context, needsQ, Toast.LENGTH_LONG).show()
                                        return@Button
                                    }
                                    compressing = true
                                    progressDone = 0
                                    progressTotal = selectedItems.size
                                    val items = selectedItems
                                    scope.launch {
                                        val savedPairs = mutableListOf<Pair<MediaItem, Long>>()
                                        val batch = mutableMapOf<String, Pair<ItemOutcome, Long>>()
                                        var failures = 0
                                        withContext(Dispatchers.IO) {
                                            items.forEachIndexed { i, item ->
                                                val key = item.uri.toString()
                                                when (val r = compressImage(
                                                    context, item, preset.quality, preset.maxEdge
                                                )) {
                                                    is CompressResult.Done -> {
                                                        batch[key] = ItemOutcome.SAVED to r.savedBytes
                                                        savedPairs.add(item to r.savedBytes)
                                                    }
                                                    CompressResult.AlreadySmall ->
                                                        batch[key] = ItemOutcome.ALREADY_SMALL to 0L
                                                    is CompressResult.Failed -> {
                                                        batch[key] = ItemOutcome.FAILED to 0L
                                                        failures++
                                                    }
                                                }
                                                progressDone = i + 1
                                            }
                                        }
                                        outcomes = outcomes + batch
                                        compressing = false
                                        when {
                                            savedPairs.isNotEmpty() -> {
                                                val total = savedPairs.sumOf { it.second }
                                                pendingReplace = savedPairs.map { it.first.uri } to total
                                                delete(savedPairs.map { it.first })
                                            }
                                            failures > 0 ->
                                                Toast.makeText(context, allFailed, Toast.LENGTH_LONG).show()
                                            else ->
                                                Toast.makeText(context, alreadyTiny, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = selectedItems.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                            ) { Text(stringResource(R.string.comp_action), color = Color.White) }
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
                CircularProgressIndicator(color = AccentOrange)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.comp_scanning), color = cs.onSurfaceVariant)
            }
        } else if (photos.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("🪶", fontSize = 48.sp)
                Text(stringResource(R.string.comp_none), color = cs.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                // ── Preset chips ──
                item(key = "presets") {
                    Text(
                        stringResource(R.string.comp_quality),
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PRESETS.forEach { p ->
                            FilterChip(
                                selected = preset.key == p.key,
                                onClick = { preset = p },
                                label = { Text(stringResource(p.labelRes)) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                // ── Before / after preview ──
                item(key = "preview") {
                    val before = previewBefore
                    val after = previewResult
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = orangeContainer),
                        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.3f))
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                stringResource(R.string.comp_preview_on),
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = onOrange
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(120.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(onOrange.copy(alpha = 0.15f))
                                ) {
                                    if (before != null) {
                                        AsyncImage(
                                            model = before.first.uri,
                                            contentDescription = stringResource(R.string.comp_before_cd),
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(120.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(onOrange.copy(alpha = 0.15f))
                                ) {
                                    if (after is PreviewOutcome.Ready) {
                                        Image(
                                            bitmap = after.bitmap.asImageBitmap(),
                                            contentDescription = stringResource(R.string.comp_after_cd),
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            when {
                                after is PreviewOutcome.Ready && before != null -> {
                                    val pct =
                                        (((before.second - after.encodedBytes) * 100f) / before.second)
                                            .toInt().coerceAtLeast(0)
                                    Column {
                                        Text(
                                            stringResource(R.string.comp_saves, humanBytes(before.second), humanBytes(after.encodedBytes), pct),
                                            fontWeight = FontWeight.Bold,
                                            color = onOrange
                                        )
                                        if (after.qualityUsed != preset.quality) {
                                            Text(
                                                stringResource(R.string.comp_quality_used, after.qualityUsed),
                                                fontSize = 12.sp, color = onOrange
                                            )
                                        }
                                    }
                                }
                                after is PreviewOutcome.NoSavings ->
                                    Text(stringResource(R.string.comp_preview_small), fontSize = 12.sp, color = onOrange)
                                after is PreviewOutcome.Unreadable ->
                                    Text(stringResource(R.string.comp_preview_fail), fontSize = 12.sp, color = onOrange)
                                else ->
                                    Text(stringResource(R.string.comp_previewing), fontSize = 12.sp, color = onOrange)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                // ── Select all / none ──
                item(key = "actions") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.comp_filter_sub),
                            fontSize = 12.sp,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(enabled = !compressing, onClick = {
                            selected = photos.map { it.uri.toString() }.toSet()
                        }) { Text(stringResource(R.string.common_all), fontSize = 12.sp) }
                        TextButton(enabled = !compressing, onClick = {
                            selected = emptySet()
                        }) { Text(stringResource(R.string.comp_none_btn), fontSize = 12.sp) }
                    }
                }
                // ── Photo grid, 3 tiles per row ──
                val cols = 3
                items(photos.chunked(cols), key = { row ->
                    row.joinToString("|") { it.uri.toString() }
                }) { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowItems.forEach { item ->
                            PhotoTile(
                                item = item,
                                checked = item.uri.toString() in selected,
                                outcome = outcomes[item.uri.toString()],
                                enabled = !compressing,
                                modifier = Modifier.weight(1f),
                                onToggle = {
                                    val k = item.uri.toString()
                                    selected = if (k in selected) selected - k else selected + k
                                }
                            )
                        }
                        repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                item(key = "footer") { Spacer(Modifier.height(150.dp)) }
            }
        }
    }

    resultSaved?.let { saved ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { resultSaved = null },
            title = { Text(stringResource(R.string.comp_done_title)) },
            text = {
                Text(
                    stringResource(R.string.comp_done_body, humanBytes(saved))
                )
            },
            confirmButton = {
                TextButton(onClick = { resultSaved = null }) { Text(stringResource(R.string.comp_nice)) }
            }
        )
    }
}

/** One grid tile: thumbnail, orange selection border + check dot, size scrim,
 *  and (after a batch run) an outcome badge — −NN% / already small / failed. */
@Composable
private fun PhotoTile(
    item: MediaItem,
    checked: Boolean,
    outcome: Pair<ItemOutcome, Long>?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onToggle)
            .border(
                width = if (checked) 3.dp else 0.dp,
                color = if (checked) AccentOrange else Color.Transparent,
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
                    .background(AccentOrange),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(humanBytes(item.sizeBytes), fontSize = 10.sp, color = Color.White)
        }
        val o = outcome
        if (o != null) {
            val badgeText = when (o.first) {
                ItemOutcome.SAVED ->
                    if (item.sizeBytes > 0L && o.second > 0L) {
                        stringResource(
                            R.string.comp_saved_chip,
                            (((item.sizeBytes - o.second) * 100L) / item.sizeBytes).toInt().coerceIn(1, 99)
                        )
                    } else null
                ItemOutcome.ALREADY_SMALL -> stringResource(R.string.comp_skip_chip)
                ItemOutcome.FAILED -> stringResource(R.string.comp_fail_chip)
            }
            if (badgeText != null) {
                val badgeColor = when (o.first) {
                    ItemOutcome.SAVED -> AccentOrange
                    ItemOutcome.ALREADY_SMALL -> Color.Black.copy(alpha = 0.6f)
                    ItemOutcome.FAILED -> cs.error
                }
                OutcomeBadge(badgeText, badgeColor, Modifier.align(Alignment.TopStart).padding(6.dp))
            }
        }
    }
}

@Composable
private fun OutcomeBadge(text: String, container: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
