package io.github.rude56.poemstatus.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppIconCache {
    private const val ICON_SIZE_PX = 128
    private const val MAX_ENTRIES = 128
    private val cache = object : LruCache<String, ImageBitmap>(MAX_ENTRIES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = 1
    }
    private val missing = HashSet<String>()

    suspend fun load(context: Context, packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        if (packageName in missing) return null

        val icon = withContext(Dispatchers.IO) {
            runCatching { readIcon(context, packageName) }.getOrNull()
        }
        if (icon == null) {

            missing.add(packageName)
            return null
        }
        cache.put(packageName, icon)
        return icon
    }
    fun peek(packageName: String): ImageBitmap? = cache.get(packageName)
    private fun readIcon(context: Context, packageName: String): ImageBitmap? {
        val drawable: Drawable = context.packageManager.getApplicationIcon(packageName)
        return drawable.toSquareBitmap(ICON_SIZE_PX)?.asImageBitmap()
    }

    private fun Drawable.toSquareBitmap(sizePx: Int): Bitmap? {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, sizePx, sizePx)
        draw(canvas)
        return bitmap
    }

    fun clear() {
        cache.evictAll()
        missing.clear()
    }

    @Suppress("unused")
    private fun Drawable.isBitmapDrawable(): Boolean = this is BitmapDrawable
}
