package com.erkfley.noname

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun NonameApp() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<AppInfo?>(null) }
    var dragAccum by remember { mutableStateOf(0f) }
    var showHomeMenu by remember { mutableStateOf(false) }
    val wallpaper = remember { getWallpaperBitmap(context) }

    // ===== Video wallpaper & pin beranda =====
    var videoUri by remember { mutableStateOf(VideoWallpaper.getVideoUri(context)) }
    var muted by remember { mutableStateOf(VideoWallpaper.isMuted(context)) }
    var pinned by remember { mutableStateOf(VideoWallpaper.getPinned(context)) }
    var pickAfterPermission by remember { mutableStateOf(false) }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Beberapa provider tidak memberi izin persisten; tetap coba putar.
            }
            VideoWallpaper.enable(context, uri)
            videoUri = VideoWallpaper.getVideoUri(context)
        }
    }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        if (pickAfterPermission) {
            pickAfterPermission = false
            videoPicker.launch(arrayOf("video/*"))
        }
    }

    fun pilihVideo() {
        val granted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            videoPicker.launch(arrayOf("video/*"))
        } else {
            pickAfterPermission = true
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Pastikan service jalan selama mode video aktif.
    LaunchedEffect(videoUri) {
        if (videoUri != null) VideoWallpaper.startService(context)
    }

    LaunchedEffect(Unit) { apps = loadApps(context) }

    // Muat ulang daftar aplikasi setiap kali launcher kembali tampil,
    // supaya hasil uninstall/install langsung terlihat.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) apps = loadApps(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = drawerOpen) {
        drawerOpen = false
        query = ""
    }

    // Beranda: kalau ada app yang di-pin, tampilkan itu saja.
    val homeApps = if (pinned.isEmpty()) apps else apps.filter { it.packageName in pinned }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // ===== LATAR: video (bila aktif) atau wallpaper gambar =====
        if (videoUri != null) {
            VideoBackground(modifier = Modifier.fillMaxSize())
        } else {
            wallpaper?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // ===== HOME: grid ikon =====
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            dragAccum += dragAmount
                        },
                        onDragEnd = {
                            if (dragAccum < -120f) drawerOpen = true
                            dragAccum = 0f
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { showHomeMenu = true }
                    )
                },
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp, top = 64.dp, bottom = 72.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(homeApps, key = { it.packageName }) { app ->
                HomeIcon(
                    app = app,
                    onClick = { openApp(context, app.packageName) },
                    onLongClick = { selected = app }
                )
            }
        }

        if (apps.isEmpty()) {
            Text(
                text = "Belum ada aplikasi.\nGeser ke atas untuk membuka drawer.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (homeApps.isEmpty()) {
            Text(
                text = "Aplikasi pin tidak ditemukan.\nLong-press aplikasi di drawer lalu \"Tambah ke Beranda\".",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // ===== DRAWER: swipe up dari home =====
        AnimatedVisibility(
            visible = drawerOpen,
            modifier = Modifier.fillMaxSize(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            DrawerContent(
                apps = apps.filter { it.label.contains(query, ignoreCase = true) },
                query = query,
                onQueryChange = { query = it },
                onOpen = { app ->
                    openApp(context, app.packageName)
                    drawerOpen = false
                    query = ""
                },
                onLongClick = { selected = it },
                onClose = {
                    drawerOpen = false
                    query = ""
                }
            )
        }

        // ===== MENU LONG-PRESS PADA APLIKASI =====
        selected?.let { app ->
            AlertDialog(
                onDismissRequest = { selected = null },
                title = { Text(app.label) },
                text = {
                    Column {
                        Text(
                            text = app.packageName,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = {
                            VideoWallpaper.togglePin(context, app.packageName)
                            pinned = VideoWallpaper.getPinned(context)
                            selected = null
                        }) {
                            Text(
                                if (app.packageName in pinned) "Hapus dari Beranda"
                                else "Tambah ke Beranda"
                            )
                        }
                        TextButton(onClick = {
                            selected = null
                            openAppInfo(context, app.packageName)
                        }) { Text("Info aplikasi") }
                        TextButton(onClick = {
                            selected = null
                            uninstallApp(context, app.packageName)
                        }) { Text("Hapus") }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selected = null }) { Text("Tutup") }
                }
            )
        }

        // ===== MENU LONG-PRESS AREA KOSONG BERANDA =====
        if (showHomeMenu) {
            AlertDialog(
                onDismissRequest = { showHomeMenu = false },
                title = { Text("Wallpaper & Pengaturan") },
                text = {
                    Column {
                        TextButton(onClick = {
                            showHomeMenu = false
                            pilihVideo()
                        }) {
                            Text(
                                if (videoUri == null) "Pilih Video Wallpaper"
                                else "Ganti Video Wallpaper"
                            )
                        }
                        TextButton(onClick = {
                            VideoWallpaper.setMuted(context, !muted)
                            muted = !muted
                        }) {
                            Text(if (muted) "Suara Video: Senyap (tap untuk menyalakan)"
                            else "Suara Video: Nyala (tap untuk membisukan)")
                        }
                        if (videoUri != null) {
                            TextButton(onClick = {
                                VideoWallpaper.disable(context)
                                videoUri = null
                                showHomeMenu = false
                            }) { Text("Kembali ke Wallpaper Gambar") }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHomeMenu = false }) { Text("Tutup") }
                }
            )
        }
    }
}

/**
 * Latar beranda berupa video. Player dimiliki singleton VideoWallpaper
 * (dijaga service), jadi tetap berjalan walau beranda tidak tampil.
 * Saat beranda terlihat -> permukaan video dipasang kembali;
 * saat beranda menghilang -> permukaan dilepas, pemutaran berlanjut.
 */
@Composable
fun VideoBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var textureView by remember { mutableStateOf<TextureView?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, textureView) {
        val tv = textureView
        val player = VideoWallpaper.getOrCreatePlayer(context)

        // Setelah reboot/proses mati: siapkan ulang video dari preferensi.
        if (player.mediaItemCount == 0) {
            val uri = VideoWallpaper.getVideoUri(context)
            if (uri != null) {
                val item = androidx.media3.common.MediaItem.fromUri(uri)
                player.setMediaItem(item)
                player.prepare()
                player.playWhenReady = true
            }
        }
        if (tv != null) player.setVideoTextureView(tv)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START ->
                    if (textureView != null) player.setVideoTextureView(textureView)
                Lifecycle.Event.ON_STOP -> player.clearVideoSurface()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                player.clearVideoSurface()
            } catch (e: Exception) {
                // abaikan
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).also {
                textureView = it
                VideoWallpaper.getOrCreatePlayer(ctx).setVideoTextureView(it)
            }
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeIcon(app: AppInfo, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = app.icon.asImageBitmap(),
            contentDescription = app.label,
            modifier = Modifier.size(54.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.label,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(
                shadow = Shadow(color = Color.Black.copy(alpha = 0.85f), blurRadius = 8f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawerContent(
    apps: List<AppInfo>,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: (AppInfo) -> Unit,
    onLongClick: (AppInfo) -> Unit,
    onClose: () -> Unit
) {
    var dragAccum by remember { mutableStateOf(0f) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.82f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            dragAccum += dragAmount
                        },
                        onDragEnd = {
                            if (dragAccum > 100f) onClose()
                            dragAccum = 0f
                        }
                    )
                }
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text("Cari aplikasi…", color = Color.White.copy(alpha = 0.6f))
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White
                )
            )
            Text(
                text = "Geser ke bawah / tombol back untuk menutup",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(8.dp))

            if (apps.isEmpty()) {
                Text(
                    text = "Tidak ada aplikasi yang cocok.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                lazyItems(apps, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onOpen(app) },
                                onLongClick = { onLongClick(app) }
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = app.icon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = app.label,
                            color = Color.White,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
