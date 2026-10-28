package com.dosprint.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import com.dosprint.core.AppLog
import java.io.IOException
import java.io.InputStream

class ImagePageSource(
    private val context: Context,
    private val uri: Uri,
) : PageSource {

    override val pageCount: Int = 1

    override fun renderPage(index: Int, targetWidthPx: Int, targetHeightPx: Int): Bitmap? {
        if (index != 0 || targetWidthPx <= 0 || targetHeightPx <= 0) {
            return null
        }
        val bounds = decodeBounds() ?: return null
        val orientation = readOrientation()
        val rotated = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE
        val naturalWidth = if (rotated) bounds.height else bounds.width
        val naturalHeight = if (rotated) bounds.width else bounds.height
        if (naturalWidth <= 0 || naturalHeight <= 0) {
            return null
        }
        val target = RenderBox(targetWidthPx, targetHeightPx)
        val fitWidth = minOf(target.widthPx, naturalWidth).coerceAtLeast(1)
        val fitHeight = (fitWidth * naturalHeight.toFloat() / naturalWidth).toInt().coerceIn(1, MAX_RENDER_EDGE_PX)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(naturalWidth, naturalHeight, fitWidth, fitHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = openStream()?.use { stream ->
            try {
                BitmapFactory.decodeStream(stream, null, options)
            } catch (error: OutOfMemoryError) {
                AppLog.error("Out of memory decoding image", error)
                null
            }
        } ?: return null
        val oriented = applyOrientation(decoded, orientation)
        return oriented ?: decoded
    }

    override fun pageAspectRatio(index: Int): Float {
        val bounds = decodeBounds() ?: return DEFAULT_ASPECT_RATIO
        if (bounds.width <= 0 || bounds.height <= 0) {
            return DEFAULT_ASPECT_RATIO
        }
        val orientation = readOrientation()
        val rotated = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE
        return if (rotated) {
            bounds.height.toFloat() / bounds.width
        } else {
            bounds.width.toFloat() / bounds.height
        }
    }

    private fun decodeBounds(): Size? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream()?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        return if (options.outWidth > 0 && options.outHeight > 0) {
            Size(options.outWidth, options.outHeight)
        } else {
            null
        }
    }

    private fun openStream(): InputStream? = try {
        context.contentResolver.openInputStream(uri)
    } catch (error: SecurityException) {
        AppLog.warn(AppLog.throwableTag("openImage", error))
        null
    } catch (error: IOException) {
        AppLog.warn(AppLog.throwableTag("openImage", error))
        null
    } catch (error: IllegalArgumentException) {
        AppLog.warn("Cannot open image: $uri", error)
        null
    }

    private fun readOrientation(): Int = try {
        openStream()?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (error: IOException) {
        AppLog.debug(AppLog.throwableTag("exifRead", error))
        ExifInterface.ORIENTATION_NORMAL
    } catch (error: Exception) {
        AppLog.debug(AppLog.throwableTag("exifRead", error))
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun sampleSizeFor(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth / 2 >= targetWidth && currentHeight / 2 >= targetHeight && sample < MAX_SAMPLE_SIZE) {
            currentWidth /= 2
            currentHeight /= 2
            sample *= 2
        }
        return sample
    }

    private fun applyOrientation(source: Bitmap, orientation: Int): Bitmap? {
        val matrix = when (orientation) {
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
            else -> return null
        }
        return try {
            val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            if (rotated !== source) {
                source.recycle()
            }
            rotated
        } catch (error: OutOfMemoryError) {
            AppLog.error("Out of memory rotating image", error)
            source
        }
    }

    private companion object {
        const val DEFAULT_ASPECT_RATIO = 1f
        const val MAX_RENDER_EDGE_PX = 6000
        const val MAX_SAMPLE_SIZE = 16
    }
}
