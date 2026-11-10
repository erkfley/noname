package com.erkfley.noname

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings

/** Data satu aplikasi yang tampil di launcher. */
data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Bitmap
)

/** Membaca daftar semua aplikasi yang punya launcher activity, urut abjad. */
fun loadApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val self = context.packageName

    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo != null && it.activityInfo.packageName != self }
        .map { ri ->
            val drawable = ri.loadIcon(pm) ?: pm.getDefaultActivityIcon()
            AppInfo(
                label = ri.loadLabel(pm)?.toString() ?: ri.activityInfo.packageName,
                packageName = ri.activityInfo.packageName,
                icon = drawable.toBitmap(128)
            )
        }
        .sortedBy { it.label.lowercase() }
}

/** Menggambar Drawable menjadi Bitmap persegi (untuk ikon aplikasi). */
fun Drawable.toBitmap(size: Int): Bitmap = toBitmap(size, size)

/** Menggambar Drawable menjadi Bitmap dengan ukuran bebas (untuk wallpaper). */
fun Drawable.toBitmap(width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, width, height)
    draw(canvas)
    return bitmap
}

/** Mengambil wallpaper saat ini sebagai Bitmap (tanpa butuh permission). */
fun getWallpaperBitmap(context: Context, maxSize: Int = 1600): Bitmap? = try {
    val drawable: Drawable = WallpaperManager.getInstance(context).drawable ?: return null
    val dw = drawable.intrinsicWidth.coerceAtLeast(1)
    val dh = drawable.intrinsicHeight.coerceAtLeast(1)
    val scale = maxSize.toFloat() / maxOf(dw, dh).coerceAtLeast(1)
    val w = (dw * scale).toInt().coerceIn(1, maxSize)
    val h = (dh * scale).toInt().coerceIn(1, maxSize)
    drawable.toBitmap(w, h)
} catch (e: Exception) {
    null
}

/** Membuka aplikasi lain. */
fun openApp(context: Context, packageName: String) {
    val pm = context.packageManager
    val intent = pm.getLaunchIntentForPackage(packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

/** Membuka halaman info aplikasi di Pengaturan. */
fun openAppInfo(context: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    intent.data = Uri.parse("package:$packageName")
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

/** Meminta sistem meng-uninstall aplikasi. */
fun uninstallApp(context: Context, packageName: String) {
    val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
