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

/**
 * Membaca daftar semua aplikasi yang punya launcher activity, urut abjad.
 * Anti-crash: satu entri per package (dedup), ikon bermasalah diganti ikon
 * bawaan, dan error apa pun tidak boleh membuat launcher mati.
 */
fun loadApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val self = context.packageName
    val seen = HashMap<String, AppInfo>()

    try {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        for (ri in pm.queryIntentActivities(intent, 0)) {
            val info = ri.activityInfo ?: continue
            val pkg = info.packageName ?: continue
            if (pkg == self || seen.containsKey(pkg)) continue

            val app = try {
                val drawable = try {
                    ri.loadIcon(pm)
                } catch (e: Exception) {
                    null
                } ?: pm.getDefaultActivityIcon()
                val bitmap = try {
                    drawable.toBitmap(128)
                } catch (e: Exception) {
                    pm.getDefaultActivityIcon().toBitmap(128)
                }
                AppInfo(
                    label = ri.loadLabel(pm)?.toString()?.ifBlank { pkg } ?: pkg,
                    packageName = pkg,
                    icon = bitmap
                )
            } catch (e: Exception) {
                null
            }
            if (app != null) seen[pkg] = app
        }
    } catch (e: Exception) {
        // Abaikan: lebih baik daftar kurang lengkap daripada launcher crash.
    }

    return seen.values.sortedBy { it.label.lowercase() }
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
