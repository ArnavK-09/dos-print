package com.dosprint.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.dosprint.core.AppLog
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class TextPageSource(
    private val context: Context,
    private val uri: Uri,
    private val renderMarkdown: Boolean,
) : PageSource {

    private val lock = ReentrantLock()
    private var text: CharSequence? = null
    private var cachedWidth: Int = 0
    private var cachedHeight: Int = 0
    private var cachedLayout: Layout? = null
    private var pageBreaks: List<Int> = emptyList()

    override val pageCount: Int
        get() {
            ensureLayout(DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT)
            return lock.withLock { pageBreaks.size }
        }

    override fun renderPage(index: Int, targetWidthPx: Int, targetHeightPx: Int): Bitmap? {
        if (index < 0 || targetWidthPx <= 0 || targetHeightPx <= 0) {
            return null
        }
        ensureLayout(targetWidthPx, targetHeightPx)
        val layout = lock.withLock { cachedLayout } ?: return null
        val breakLine = lock.withLock { pageBreaks.getOrNull(index) } ?: return null
        val bitmap = try {
            Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
        } catch (error: OutOfMemoryError) {
            AppLog.error("Out of memory allocating text page", error)
            return null
        }
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.translate(0f, -layout.getLineTop(breakLine).toFloat())
        layout.draw(canvas)
        return bitmap
    }

    override fun pageAspectRatio(index: Int): Float = DEFAULT_ASPECT_RATIO

    private fun ensureLayout(widthPx: Int, heightPx: Int) {
        lock.withLock {
            if (cachedLayout != null && cachedWidth == widthPx && cachedHeight == heightPx) {
                return
            }
            val content = text ?: loadText().also { text = it }
            val raw = content.toString()
            if (raw.isEmpty()) {
                return
            }
            val layout = buildLayout(raw, widthPx)
            cachedLayout = layout
            cachedWidth = widthPx
            cachedHeight = heightPx
            pageBreaks = computeBreaks(layout, heightPx)
        }
    }

    private fun loadText(): CharSequence {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: ""
        } catch (error: SecurityException) {
            AppLog.warn(AppLog.throwableTag("readText", error))
            ""
        } catch (error: IOException) {
            AppLog.warn(AppLog.throwableTag("readText", error))
            ""
        } catch (error: OutOfMemoryError) {
            AppLog.error("Out of memory reading text document", error)
            readTextTruncated()
        }
    }

    private fun readTextTruncated(): String = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val reader = stream.reader()
            val buffer = CharArray(MAX_FALLBACK_CHARACTERS)
            var total = 0
            while (total < buffer.size) {
                val read = reader.read(buffer, total, buffer.size - total)
                if (read <= 0) {
                    break
                }
                total += read
            }
            String(buffer, 0, total)
        } ?: ""
    } catch (error: Exception) {
        AppLog.warn(AppLog.throwableTag("readTextTruncated", error))
        ""
    }

    private fun buildLayout(content: String, widthPx: Int): Layout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = TEXT_SIZE_PX
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        val prepared = if (renderMarkdown) MarkdownText.strip(content) else content
        return StaticLayout.Builder.obtain(prepared, 0, prepared.length, paint, widthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, LINE_SPACING_MULTIPLIER)
            .setIncludePad(false)
            //noinspection WrongConstant
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()
    }

    private fun computeBreaks(layout: Layout, pageHeightPx: Int): List<Int> {
        if (pageHeightPx <= 0 || layout.lineCount == 0) {
            return listOf(0)
        }
        val breaks = mutableListOf(0)
        var line = 0
        while (line < layout.lineCount) {
            val top = layout.getLineTop(line)
            val bottom = layout.getLineBottom(line)
            if (bottom - top > pageHeightPx && line == breaks.last()) {
                return listOf(0)
            }
            if (bottom > pageHeightPx) {
                breaks += line
            }
            line++
        }
        return breaks
    }

    private companion object {
        const val DEFAULT_LAYOUT_WIDTH = 1240
        const val DEFAULT_LAYOUT_HEIGHT = 1754
        const val TEXT_SIZE_PX = 28f
        const val LINE_SPACING_MULTIPLIER = 1.15f
        const val MAX_FALLBACK_CHARACTERS = 400_000
        const val DEFAULT_ASPECT_RATIO = 0.707f
    }
}

object MarkdownText {
    fun strip(content: String): String {
        if (content.isEmpty()) {
            return content
        }
        val builder = StringBuilder(content.length)
        var inFence = false
        for (line in content.lineSequence()) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```")) {
                inFence = !inFence
                builder.append('\n')
                continue
            }
            if (inFence) {
                builder.append(line).append('\n')
                continue
            }
            val heading = trimmed.takeWhile { it == '#' }
            if (heading.isNotEmpty() && trimmed.length > heading.length && trimmed[heading.length] == ' ') {
                builder.append(trimmed.substring(heading.length + 1).uppercase()).append('\n')
                continue
            }
            builder.append(stripInline(line)).append('\n')
        }
        return builder.toString()
    }

    private fun stripInline(line: String): String {
        var result = line
        result = result.replace(IMAGE_PATTERN, "")
        result = result.replace(LINK_PATTERN, "$1")
        result = result.replace(EMPHASIS_PATTERN, "$1")
        result = result.replace(STRIKETHROUGH_PATTERN, "$1")
        result = result.replace(QUOTE_PREFIX, "  ")
        return result.trimEnd()
    }

    private val IMAGE_PATTERN = Regex("!\\[([^]]*)]\\([^)]*\\)")
    private val LINK_PATTERN = Regex("\\[([^]]*)]\\([^)]*\\)")
    private val EMPHASIS_PATTERN = Regex("(\\*\\*|__|\\*|_)(.+?)\\1")
    private val STRIKETHROUGH_PATTERN = Regex("~~(.+?)~~")
    private val QUOTE_PREFIX = Regex("^>\\s?")
}
