package io.github.nilsmorczinietz.nod.debug

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import kotlin.concurrent.thread

/**
 * Sets a vertical gradient as home and lock wallpaper, for clean store/README
 * screenshots on emulators whose shell cannot set wallpapers:
 *
 * ```
 * adb shell am broadcast -a io.github.nilsmorczinietz.nod.debug.WALLPAPER \
 *   -p io.github.nilsmorczinietz.nod --es top '#2D6E6E' --es bottom '#0B2F30'
 * ```
 */
class DebugWallpaperReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val top = intent.getStringExtra("top")?.toColorInt() ?: DEFAULT_TOP
        val bottom = intent.getStringExtra("bottom")?.toColorInt() ?: DEFAULT_BOTTOM
        val width = intent.getIntExtra("width", 1080)
        val height = intent.getIntExtra("height", 2400)
        val pending = goAsync()
        thread(name = "debug-wallpaper") {
            try {
                val bitmap = gradient(width, height, top, bottom)
                WallpaperManager.getInstance(context)
                    .setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                Log.i(TAG, "Wallpaper set (${width}x$height)")
            } catch (e: Exception) {
                Log.w(TAG, "Setting the wallpaper failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun gradient(width: Int, height: Int, top: Int, bottom: Int): Bitmap {
        val bitmap = createBitmap(width, height)
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        }
        Canvas(bitmap).drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }

    private companion object {
        const val TAG = "NodDebug"
        val DEFAULT_TOP = "#2D6E6E".toColorInt()
        val DEFAULT_BOTTOM = "#0B2F30".toColorInt()
    }
}
