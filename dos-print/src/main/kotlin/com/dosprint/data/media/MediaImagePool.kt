package com.dosprint.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import com.dosprint.core.AppLog
import com.dosprint.data.render.byteCountOrZero
import java.io.IOException
import java.io.InputStream
import kotlin.math.roundToInt

class MediaImagePool(
    private val context: Context,
    maxCacheBytes: Int = DEFAULT_CACHE_BYTES,
) {
    private val cache = object : LruCache<String, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCountOrZero().coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && !oldValue.isRecycled) {
                oldValue.recycle()
            }
        }
    }

    private val boundsCache = HashMap<String, SourceBounds>()
    private val orientationCache = HashMap<String, Int>()

    fun boundsFor(uri: String): SourceBounds? = synchronized(boundsCache) {
        boundsCache[uri] ?: readBounds(uri)?.also { boundsCache[uri] = it }
    }

    fun aspectRatio(uri: String): Float {
        val bounds = boundsFor(uri) ?: return 1f
        return bounds.aspect()
    }

    fun bitmapFor(uri: String, targetWidthPx: Int, targetHeightPx: Int): Bitmap? {
        if (uri.isBlank() || targetWidthPx <= 0 || targetHeightPx <= 0) {
            return null
        }
        val key = cacheKey(uri, targetWidthPx, targetHeightPx)
        cache.get(key)?.let { return it.takeIf { !it.isRecycled } }
        val decoded = decode(uri, targetWidthPx, targetHeightPx) ?: return null
        val trimmed = trimOversized(decoded, targetWidthPx, targetHeightPx)
        if (cache.get(key) == null) {
            cache.put(key, trimmed)
        }
        return trimmed
    }

    fun prefetch(uri: String, targetWidthPx: Int, targetHeightPx: Int): Bitmap? =
        bitmapFor(uri, targetWidthPx, targetHeightPx)

    fun trim(uri: String) {
        synchronized(boundsCache) {
            boundsCache.remove(uri)
            orientationCache.remove(uri)
        }
        cache.snapshot().keys
            .filter { it.startsWith(uri) }
            .forEach { cache.remove(it) }
    }

    fun clear() {
        cache.evictAll()
        synchronized(boundsCache) {
            boundsCache.clear()
            orientationCache.clear()
        }
    }

    private fun cacheKey(uri: String, widthPx: Int, heightPx: Int): String = "$uri@$widthPx x$heightPx"

    private fun decode(uri: String, targetWidthPx: Int, targetHeightPx: Int): Bitmap? {
        val bounds = boundsFor(uri) ?: return null
        if (bounds.width <= 0 || bounds.height <= 0) {
            return null
        }
        val orientation = orientationFor(uri)
        val rotated = orientation.rotates()
        val naturalWidth = if (rotated) bounds.height else bounds.width
        val naturalHeight = if (rotated) bounds.width else bounds.height
        val targetWidth = targetWidthPx.coerceAtLeast(1)
        val targetHeight = targetHeightPx.coerceAtLeast(1)
        val naturalAspect = naturalWidth.toFloat() / naturalHeight
        val targetAspect = targetWidth.toFloat() / targetHeight
        val decodeWidth = if (targetAspect > naturalAspect) {
            minOf(targetHeight * naturalAspect, naturalWidth.toFloat())
        } else {
            minOf(targetWidth.toFloat(), naturalWidth.toFloat())
        }.roundToInt().coerceAtLeast(1)
        val decodeHeight = (decodeWidth / naturalAspect).roundToInt().coerceAtLeast(1)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(naturalWidth, naturalHeight, decodeWidth, decodeHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val raw = openStream(uri)?.use { stream ->
            try {
                BitmapFactory.decodeStream(stream, null, options)
            } catch (error: OutOfMemoryError) {
                AppLog.error("Out of memory decoding $uri", error)
                null
            } catch (error: IOException) {
                AppLog.warn(AppLog.throwableTag("decode($uri)", error))
                null
            }
        } ?: return null
        return applyOrientation(raw, orientation) ?: raw
    }

    private fun trimOversized(source: Bitmap, targetWidthPx: Int, targetHeightPx: Int): Bitmap {
        val oversize = source.width > targetWidthPx * AllowableOversizeFactor ||
            source.height > targetHeightPx * AllowableOversizeFactor
        if (!oversize) {
            return source
        }
        return try {
            val scaled = Bitmap.createScaledBitmap(source, targetWidthPx, targetHeightPx, true)
            if (scaled !== source && !source.isRecycled) {
                source.recycle()
            }
            scaled
        } catch (error: OutOfMemoryError) {
            AppLog.error("Out of memory trimming $targetWidthPx x $targetHeightPx bitmap", error)
            source
        }
    }

    private fun readBounds(uri: String): SourceBounds? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        return if (options.outWidth > 0 && options.outHeight > 0) {
            SourceBounds(options.outWidth, options.outHeight)
        } else {
            null
        }
    }

    private fun orientationFor(uri: String): Int = synchronized(orientationCache) {
        orientationCache[uri] ?: readOrientation(uri).also { orientationCache[uri] = it }
    }

    private fun readOrientation(uri: String): Int = try {
        openStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (error: IOException) {
        AppLog.debug(AppLog.throwableTag("exifRead($uri)", error))
        ExifInterface.ORIENTATION_NORMAL
    } catch (error: SecurityException) {
        AppLog.warn(AppLog.throwableTag("exifRead($uri)", error))
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun applyOrientation(source: Bitmap, orientation: Int): Bitmap? {
        val matrix = orientation.matrix() ?: return null
        return try {
            val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            if (rotated !== source && !source.isRecycled) {
                source.recycle()
            }
            rotated
        } catch (error: OutOfMemoryError) {
            AppLog.error("Out of memory orienting image", error)
            source
        }
    }

    private fun openStream(uri: String): InputStream? = try {
        context.contentResolver.openInputStream(Uri.parse(uri))
    } catch (error: SecurityException) {
        AppLog.warn(AppLog.throwableTag("openImage($uri)", error))
        null
    } catch (error: IOException) {
        AppLog.warn(AppLog.throwableTag("openImage($uri)", error))
        null
    } catch (error: IllegalArgumentException) {
        AppLog.warn(AppLog.throwableTag("openImage($uri)", error))
        null
    }

    private fun sampleSizeFor(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        var currentWidth = width
        var currentHeight = height
        while (
            currentWidth / 2 >= targetWidth &&
            currentHeight / 2 >= targetHeight &&
            sample < MaxSampleSize
        ) {
            currentWidth /= 2
            currentHeight /= 2
            sample *= 2
        }
        return sample
    }

    private companion object {
        const val DEFAULT_CACHE_BYTES = 48 * 1024 * 1024
        const val MaxSampleSize = 32
        const val AllowableOversizeFactor = 2
    }
}

data class SourceBounds(val width: Int, val height: Int) {
    fun aspect(): Float = if (height <= 0) 1f else width.toFloat() / height
}

fun Int.rotates(): Boolean = this == ExifInterface.ORIENTATION_ROTATE_90 ||
    this == ExifInterface.ORIENTATION_ROTATE_270 ||
    this == ExifInterface.ORIENTATION_TRANSPOSE ||
    this == ExifInterface.ORIENTATION_TRANSVERSE

fun Int.matrix(): Matrix? = when (this) {
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { setScale(-1f, 1f) }
    ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { setRotate(180f) }
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply {
        setRotate(180f)
        postScale(-1f, 1f)
    }
    ExifInterface.ORIENTATION_TRANSPOSE -> Matrix().apply {
        setRotate(90f)
        postScale(-1f, 1f)
    }
    ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { setRotate(90f) }
    ExifInterface.ORIENTATION_TRANSVERSE -> Matrix().apply {
        setRotate(-90f)
        postScale(-1f, 1f)
    }
    ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { setRotate(-90f) }
    else -> null
}
