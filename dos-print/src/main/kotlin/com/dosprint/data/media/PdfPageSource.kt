package com.dosprint.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.dosprint.core.AppLog
import com.dosprint.domain.model.ColorMode
import com.dosprint.domain.model.PageGeometry
import com.dosprint.domain.model.ScalingMode
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PdfPageSource(
    private val context: Context,
    private val uri: Uri,
) : PageSource {

    private val lock = ReentrantLock()
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val geometries = mutableListOf<PageGeometry>()

    override val pageCount: Int
        get() = lock.withLock { renderer?.pageCount ?: 0 }

    fun open(): Boolean = lock.withLock {
        if (renderer != null) {
            return true
        }
        val opened = try {
            context.contentResolver.openFileDescriptor(uri, "r")
        } catch (error: SecurityException) {
            AppLog.warn(AppLog.throwableTag("pdfOpen", error))
            null
        } catch (error: IOException) {
            AppLog.warn(AppLog.throwableTag("pdfOpen", error))
            null
        } catch (error: IllegalArgumentException) {
            AppLog.warn("Cannot open PDF: $uri", error)
            null
        }
        if (opened == null) {
            return false
        }
        try {
            val created = PdfRenderer(opened)
            descriptor = opened
            renderer = created
            repeat(created.pageCount) { index ->
                created.openPage(index).use { page ->
                    geometries += PageGeometry.fromPdfPage(page)
                }
            }
            true
        } catch (error: IllegalArgumentException) {
            AppLog.warn("Not a readable PDF document: $uri", error)
            runCatching { opened.close() }
            false
        } catch (error: SecurityException) {
            AppLog.warn(AppLog.throwableTag("pdfInit", error))
            runCatching { opened.close() }
            false
        } catch (error: OutOfMemoryError) {
            AppLog.error("Out of memory opening PDF", error)
            runCatching { opened.close() }
            false
        }
    }

    override fun renderPage(index: Int, targetWidthPx: Int, targetHeightPx: Int): Bitmap? {
        if (!open()) {
            return null
        }
        return lock.withLock {
            val activeRenderer = renderer ?: return@withLock null
            if (index !in 0 until activeRenderer.pageCount) {
                return@withLock null
            }
            val geometry = geometries.getOrNull(index) ?: PageGeometry(MONO_WIDTH_MILS, MONO_HEIGHT_MILS)
            val box = boxForGeometry(geometry, targetWidthPx)
            val bitmap = try {
                Bitmap.createBitmap(box.widthPx, box.heightPx, Bitmap.Config.ARGB_8888)
            } catch (error: OutOfMemoryError) {
                AppLog.error("Out of memory allocating PDF page", error)
                return@withLock null
            }
            bitmap.eraseColor(Color.WHITE)
            try {
                activeRenderer.openPage(index).use { page ->
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            } catch (error: IllegalStateException) {
                AppLog.warn(AppLog.throwableTag("pdfRender", error))
                bitmap.recycle()
                return@withLock null
            } catch (error: OutOfMemoryError) {
                AppLog.error("Out of memory rendering PDF page", error)
                bitmap.recycle()
                return@withLock null
            }
            bitmap
        }
    }

    override fun pageAspectRatio(index: Int): Float = lock.withLock {
        geometries.getOrNull(index)?.aspectRatio() ?: DEFAULT_ASPECT_RATIO
    }

    override fun close() {
        lock.withLock {
            val activeRenderer = renderer
            if (activeRenderer != null) {
                runCatching { activeRenderer.close() }
                    .onFailure { AppLog.warn(AppLog.throwableTag("pdfClose", it)) }
                renderer = null
            }
            val activeDescriptor = descriptor
            if (activeDescriptor != null) {
                runCatching { activeDescriptor.close() }
                    .onFailure { AppLog.warn(AppLog.throwableTag("descriptorClose", it)) }
                descriptor = null
            }
            geometries.clear()
        }
    }

    private fun boxForGeometry(geometry: PageGeometry, targetWidthPx: Int): RenderBox {
        val byWidth = targetWidthPx.coerceAtLeast(1)
        val ratio = geometry.aspectRatio()
        val byHeight = heightFor(byWidth, ratio).coerceIn(RATIO_HEIGHT_FLOOR, MAX_RENDER_HEIGHT_PX)
        return RenderBox(byWidth, byHeight).coerceToBudget()
    }

    private fun heightFor(widthPx: Int, aspectRatio: Float): Int {
        val hasRatio = aspectRatio > RATIO_POSITIVE_FLOOR
        val raw = if (hasRatio) (widthPx / aspectRatio).toInt() else widthPx
        return raw.coerceIn(1, MAX_RENDER_HEIGHT_PX)
    }

    private fun RenderBox.coerceToBudget(): RenderBox {
        val pixels = widthPx.toLong() * heightPx.toLong()
        if (pixels <= MAX_BITMAP_PIXELS) {
            return this
        }
        val scale = kotlin.math.sqrt(MAX_BITMAP_PIXELS.toDouble() / pixels).toFloat()
        return RenderBox(
            (widthPx * scale).toInt().coerceAtLeast(1),
            (heightPx * scale).toInt().coerceAtLeast(1),
        )
    }

    private companion object {
        const val DEFAULT_ASPECT_RATIO = 0.707f
        const val MONO_WIDTH_MILS = 8270
        const val MONO_HEIGHT_MILS = 11692
        const val MAX_RENDER_HEIGHT_PX = 6000
        const val RATIO_POSITIVE_FLOOR = 0f
        const val RATIO_HEIGHT_FLOOR = 1
        const val MAX_BITMAP_PIXELS = 16_000_000L
    }
}

internal object BitmapTransforms {
    fun desaturate(source: Bitmap, mode: ColorMode): Bitmap {
        if (mode == ColorMode.COLOR) {
            return source
        }
        val saturation = if (mode == ColorMode.MONOCHROME) 0f else GRAYSCALE_SATURATION
        val converted = try {
            Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        } catch (error: OutOfMemoryError) {
            AppLog.error("Out of memory converting color mode", error)
            return source
        }
        Canvas(converted).drawBitmap(
            source,
            0f,
            0f,
            Paint().apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(saturation) })
                isAntiAlias = true
                isFilterBitmap = true
            },
        )
        return converted
    }

    fun toGrayscaleIfRequested(source: Bitmap, mode: ColorMode): Bitmap {
        val converted = desaturate(source, mode)
        if (converted === source) {
            return source
        }
        source.recycle()
        return converted
    }

    private const val GRAYSCALE_SATURATION = 0.2f
}

internal object ContentGeometry {
    fun placement(
        sourceWidth: Float,
        sourceHeight: Float,
        target: RectF,
        scaling: ScalingMode,
        scalePercent: Int,
    ): RectF {
        val invalidSource = sourceWidth <= 0f || sourceHeight <= 0f
        val invalidTarget = target.width() <= 0f || target.height() <= 0f
        if (invalidSource || invalidTarget) {
            return RectF(target)
        }
        val fitScale = minOf(target.width() / sourceWidth, target.height() / sourceHeight)
        val fillScale = maxOf(target.width() / sourceWidth, target.height() / sourceHeight)
        val base = when (scaling) {
            ScalingMode.FIT_TO_PAGE -> fitScale
            ScalingMode.FILL_PAGE -> fillScale
            ScalingMode.ACTUAL_SIZE -> 1f
        }
        val effective = base * (scalePercent.coerceIn(1, MAX_SCALE_MULTIPLIER) / 100f)
        val width = sourceWidth * effective
        val height = sourceHeight * effective
        val left = target.centerX() - width / 2f
        val top = target.centerY() - height / 2f
        return RectF(left, top, left + width, top + height)
    }

    const val MAX_SCALE_MULTIPLIER = 400
}
