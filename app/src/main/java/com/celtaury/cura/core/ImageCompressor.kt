package com.celtaury.cura.core

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.provider.MediaStore
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * 1.0.1: the photo-compression engine, lifted out of CompressScreen.kt so the
 * DECK can compress a card in place ("compress button on all deck mode — more
 * granular control"). Same pipeline, one implementation: decode → exact
 * downscale → EXIF-rotate → best-effort re-encode → save one copy into
 * Pictures/Cura Compressed via MediaStore. CompressScreen keeps its preview
 * (compressPreview) on these same primitives, so the preview never lies.
 *
 * The encoded bytes are measured once and the very same bytes are written to
 * the new row (the old code encoded twice, so the saved-size estimate could
 * drift from reality).
 */
internal const val COMPRESS_TAG = "CuraCompress"

/** What one compression attempt ended up doing. "Already small" is only ever
 *  claimed when the pixels were readable and every quality step still failed
 *  to shrink the file — decode and save problems are Failed, never skipped. */
internal sealed interface CompressResult {
    /** Copy written to Pictures/Cura Compressed. savedBytes > 0. */
    data class Done(val savedBytes: Long, val newSize: Long, val qualityUsed: Int) : CompressResult

    /** Read fine, but even the fallback quality could not shrink it ≥5%. */
    data object AlreadySmall : CompressResult

    /** Could not read (stage=decode) or could not save (stage=insert/save). */
    data class Failed(val stage: String) : CompressResult
}

/** Decode → downscale → rotate → re-encode → save one copy into
 *  Pictures/Cura Compressed via MediaStore. Never touches the original. */
internal fun compressImage(
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
internal fun encodeBestEffort(bitmap: Bitmap, quality: Int, originalBytes: Long): Pair<ByteArray, Int>? {
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
internal fun decodeForCompress(
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
internal fun originalBytes(context: android.content.Context, item: MediaItem): Long {
    if (item.sizeBytes > 0L) return item.sizeBytes
    return try {
        context.contentResolver.openFileDescriptor(item.uri, "r")?.use { it.statSize } ?: 0L
    } catch (_: Exception) {
        0L
    }
}

internal fun rotationDegrees(context: android.content.Context, uri: android.net.Uri): Float = try {
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
