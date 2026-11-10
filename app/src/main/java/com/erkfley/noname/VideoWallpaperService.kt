package com.erkfley.noname

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.media3.common.MediaItem

/**
 * Foreground service (tipe mediaPlayback) yang menjaga proses launcher
 * dan player video tetap berjalan walaupun beranda tidak sedang tampil.
 * Wajib menampilkan notifikasi permanen sesuai aturan Android.
 */
class VideoWallpaperService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val player = VideoWallpaper.getOrCreatePlayer(this)

        // Setelah HP reboot / proses mati: siapkan ulang video dari penyimpanan.
        val uri = VideoWallpaper.getVideoUri(this)
        if (uri != null && player.mediaItemCount == 0) {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = true
        }

        // minSdk 36 >= API 29, jadi startForeground 3-argumen selalu tersedia;
        // tipe diambil dari foregroundServiceType di manifest.
        startForeground(
            VideoWallpaper.NOTIF_ID,
            VideoWallpaper.buildNotification(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }
}
