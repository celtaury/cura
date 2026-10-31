package com.celtaury.cura

import android.content.Context
import android.os.Environment
import android.os.StatFs

data class StorageInfo(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val percentageUsed: Float
)

fun getDeviceStorageInfo(context: Context): StorageInfo {
    val path = Environment.getDataDirectory()
    val stat = StatFs(path.path)

    val blockSize = stat.blockSizeLong
    val totalBlocks = stat.blockCountLong
    val availableBlocks = stat.availableBlocksLong

    val totalBytes = totalBlocks * blockSize
    val freeBytes = availableBlocks * blockSize
    val usedBytes = totalBytes - freeBytes

    val percentageUsed = (usedBytes.toFloat() / totalBytes.toFloat())

    return StorageInfo(totalBytes, usedBytes, freeBytes, percentageUsed)
}