package com.celtaury.cura

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CleanableFile(
    val id: Long,
    val displayName: String,
    val sizeBytes: Long,
    val uri: Uri
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeFilesScreen(onBackClick: () -> Unit) {
    var hasPermission by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as Activity

    var files by remember { mutableStateOf<List<CleanableFile>>(emptyList()) }
    var selectedFileIds by remember { mutableStateOf(setOf<Long>()) }
    var isScanning by remember { mutableStateOf(false) }

    var previewUri by remember { mutableStateOf<Uri?>(null) }

    val coroutineScope = rememberCoroutineScope()

    // Calculate total size of ALL found files (for the Hero Card)
    val totalSizeMB = files.sumOf { it.sizeBytes } / (1024 * 1024)
    // Calculate size of SELECTED files
    val selectedSizeMB = files.filter { selectedFileIds.contains(it.id) }.sumOf { it.sizeBytes } / (1024 * 1024)

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedFileIds = emptySet()
            isScanning = true
            coroutineScope.launch {
                files = getAllFiles(context)
                isScanning = false
            }
        }
    }

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        hasPermission = permissionsMap.values.all { it }
        if (hasPermission) {
            isScanning = true
            coroutineScope.launch {
                files = getAllFiles(context)
                isScanning = false
            }
        }
    }

    LaunchedEffect(Unit) {
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        hasPermission = allGranted
        if (hasPermission) {
            isScanning = true
            files = getAllFiles(context)
            isScanning = false
        }
    }

    val requestBulkDelete = {
        val urisToDelete = files.filter { selectedFileIds.contains(it.id) }.map { it.uri }
        if (urisToDelete.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, urisToDelete)
                deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            } else {
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        urisToDelete.forEach { uri ->
                            context.contentResolver.delete(uri, null, null)
                        }
                    }
                    isScanning = true
                    files = getAllFiles(context)
                    isScanning = false
                    selectedFileIds = emptySet()
                }
            }
        }
    }

    // THE LAYOUT
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Cura", fontWeight = FontWeight.Bold)
                        Text("Storage Cleaner", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (selectedFileIds.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${selectedFileIds.size} files selected", fontWeight = FontWeight.Medium)
                            Text("$selectedSizeMB MB", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Button(onClick = requestBulkDelete) {
                            Text("Clean")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            if (!hasPermission) {
                Spacer(modifier = Modifier.height(100.dp))
                Text(
                    "Welcome to Cura",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "We need access to your storage to find large files and help you clean them.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { permissionLauncher.launch(permissions) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text("Grant Access", fontSize = 16.sp)
                }
            } else if (isScanning) {
                Spacer(modifier = Modifier.height(100.dp))
                CircularProgressIndicator(modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Analyzing storage...", modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                // THE HERO CARD (The "Proven Model" element, but honest)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Large Files Found", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "$totalSizeMB MB",
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            "Tap files below to select them for cleaning.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                if (files.isEmpty()) {
                    Spacer(modifier = Modifier.height(50.dp))
                    Text("No large files found. You're clean!", modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    Text("Review Files", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))

                    // THE CARD WRAPS THE LAZY COLUMN DIRECTLY
                    Card(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(files) { file ->
                                val isSelected = selectedFileIds.contains(file.id)
                                val sizeInMB = file.sizeBytes / (1024 * 1024)
                                val isVideo = file.displayName.endsWith(".mp4") || file.displayName.endsWith(".mov") || file.displayName.endsWith(".mkv")

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isSelected) selectedFileIds = selectedFileIds - file.id
                                            else selectedFileIds = selectedFileIds + file.id
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // THE THUMBNAIL (Using Coil)
                                    AsyncImage(
                                        model = file.uri,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )

                                    // Little video play icon overlay if it's a video
                                    if (isVideo) {
                                        Box(modifier = Modifier
                                            .size(56.dp)
                                            .clickable {
                                                var itemVideoUri = file.uri
                                                previewUri = itemVideoUri
                                            }, contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.PlayCircle,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.8f),
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.displayName,
                                            maxLines = 1,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "$sizeInMB MB",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            if (isSelected) selectedFileIds = selectedFileIds - file.id
                                            else selectedFileIds = selectedFileIds + file.id
                                        }
                                    )
                                }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                            // Space for the bottom bar so the last item isn't hidden
                            item { Spacer(modifier = Modifier.height(100.dp)) }
                        }
                    }
                }
            }
        }
    }
    // SHOW THE VIDEO PREVIEW SHEET IF A URI IS SELECTED
    previewUri?.let { uri ->
        VideoPreviewSheet(videoUri = uri) {
            previewUri = null // This closes the sheet
        }
    }
}

// VideoPreviewSheet.kt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPreviewSheet(
    videoUri: Uri,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // 1. Remember the ExoPlayer instance
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
        }
    }

    // 2. Release the player when the sheet closes (CRITICAL for memory)
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Preview", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))

            // 3. The PlayerView
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true // Shows play/pause/seek bar
                    }
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f)
            )

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close Preview")
            }
        }
    }
}

suspend fun getAllFiles(context: Context): List<CleanableFile> {
    return withContext(Dispatchers.IO) {
        val fileCollection = mutableListOf<CleanableFile>()
        val collections = mutableListOf<Uri>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collections.add(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL))
            collections.add(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL))
        } else {
            collections.add(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            collections.add(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        }

        for (contentUri in collections) {
            val projections = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE
            )

            // CHANGE THIS BACK TO 50 WHEN YOU ARE READY TO SHIP!
            val selection = "${MediaStore.MediaColumns.SIZE} > ?"
            val selectionArgs = arrayOf((50 * 1024 * 1024).toString())
            val sortOrder = "${MediaStore.MediaColumns.SIZE} DESC"

            context.contentResolver.query(contentUri, projections, selection, selectionArgs, sortOrder)?.use { cursor ->
                if (cursor.count > 0) {
                    val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val displayNameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIndex)
                        val displayName = cursor.getString(displayNameIndex) ?: "Unknown"
                        val sizeBytes = cursor.getLong(sizeIndex)
                        val uri = ContentUris.withAppendedId(contentUri, id)
                        fileCollection.add(CleanableFile(id, displayName, sizeBytes, uri))
                    }
                }
            }
        }
        fileCollection
    }
}