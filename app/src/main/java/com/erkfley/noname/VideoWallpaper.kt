package com.erkfley.noname

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Pusat kendali video wallpaper + data pin beranda.
 * Player disimpan sebagai singleton supaya bisa terus berjalan
 * sekalipun layar beranda tidak sedang tampil (pilihan B),
 * dijaga hidup oleh VideoWallpaperService (foreground service).
 */
object VideoWallpaper {
    const val PREFS = "noname_settings"
    const val KEY_PINNED = "pinned_apps"
    const val NOTIF_ID = 42
    private const val KEY_VIDEO_URI = "video_uri"
    private const val KEY_VIDEO_MUTE = "video_mute"
    private const val CHANNEL_ID = "video_wallpaper"

    @Volatile
    private var player: ExoPlayer? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getVideoUri(context: Context): Uri? =
        prefs(context).getString(KEY_VIDEO_URI, null)?.let(Uri::parse)

    fun isMuted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VIDEO_MUTE, true)

    fun getPinned(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_PINNED, emptySet()) ?: emptySet()

    fun setPinned(context: Context, pinned: Set<String>) {
        prefs(context).edit().putStringSet(KEY_PINNED, pinned).apply()
    }

    fun togglePin(context: Context, packageName: String) {
        val current = getPinned(context).toMutableSet()
        if (!current.add(packageName)) current.remove(packageName)
        setPinned(context, current)
    }

    fun isPinned(context: Context, packageName: String): Boolean =
        packageName in getPinned(context)

    /** Ambil player yang sudah ada, atau buat baru (dipanggil di main thread). */
    fun getOrCreatePlayer(context: Context): ExoPlayer {
        player?.let { return it }
        val p = ExoPlayer.Builder(context.applicationContext).build()
        p.repeatMode = Player.REPEAT_MODE_ONE
        p.playWhenReady = true
        p.volume = if (isMuted(context)) 0f else 1f
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Fallback: video tidak didukung/gagal -> kembali ke wallpaper gambar.
                mainHandler.post {
                    Toast.makeText(
                        context,
                        "Video tidak dapat diputar. Kembali ke wallpaper gambar.",
                        Toast.LENGTH_LONG
                    ).show()
                    disable(context)
                }
            }
        })
        player = p
        return p
    }

    /** Mulai/mengganti video wallpaper dari file yang dipilih user. */
    fun enable(context: Context, uri: Uri) {
        prefs(context).edit().putString(KEY_VIDEO_URI, uri.toString()).apply()
        val p = getOrCreatePlayer(context)
        p.setMediaItem(MediaItem.fromUri(uri))
        p.prepare()
        p.volume = if (isMuted(context)) 0f else 1f
        p.playWhenReady = true
        startService(context)
    }

    fun setMuted(context: Context, muted: Boolean) {
        prefs(context).edit().putBoolean(KEY_VIDEO_MUTE, muted).apply()
        player?.volume = if (muted) 0f else 1f
    }

    /** Matikan mode video & kembali ke wallpaper gambar. */
    fun disable(context: Context) {
        prefs(context).edit().remove(KEY_VIDEO_URI).apply()
        try {
            player?.stop()
            player?.release()
        } catch (e: Exception) {
            // abaikan
        }
        player = null
        try {
            context.stopService(Intent(context, VideoWallpaperService::class.java))
        } catch (e: Exception) {
            // abaikan
        }
    }

    fun startService(context: Context) {
        try {
            context.startForegroundService(Intent(context, VideoWallpaperService::class.java))
        } catch (e: Exception) {
            // Di kondisi langka (mis. restriksi start dari latar), biarkan saja.
        }
    }

    fun buildNotification(context: Context): Notification {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Wallpaper Video",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_video)
            .setContentTitle("Noname — Wallpaper Video")
            .setContentText("Video wallpaper sedang berjalan di latar belakang")
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }
}
