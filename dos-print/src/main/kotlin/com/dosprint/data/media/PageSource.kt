package com.dosprint.data.media

import android.graphics.Bitmap
import android.graphics.Rect
import java.io.Closeable

interface PageSource : Closeable, AutoCloseable {
    val pageCount: Int

    fun renderPage(index: Int, targetWidthPx: Int, targetHeightPx: Int): Bitmap?

    fun pageAspectRatio(index: Int): Float

    override fun close() = Unit

    companion object {
        const val INVALID_INDEX = -1
    }
}

data class RenderBox(
    val widthPx: Int,
    val heightPx: Int,
) {
    val isValid: Boolean get() = widthPx > 0 && heightPx > 0

    fun scaleToFit(maxWidth: Int, maxHeight: Int): RenderBox {
        if (!isValid || maxWidth <= 0 || maxHeight <= 0) {
            return RenderBox(1, 1)
        }
        val scale = minOf(maxWidth.toFloat() / widthPx, maxHeight.toFloat() / heightPx)
        val width = (widthPx * scale).toInt().coerceAtLeast(1)
        val height = (heightPx * scale).toInt().coerceAtLeast(1)
        return RenderBox(width, height)
    }
}

data class SheetSlot(val bounds: Rect)
