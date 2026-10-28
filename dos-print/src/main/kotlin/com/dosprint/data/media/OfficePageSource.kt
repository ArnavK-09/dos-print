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
import com.dosprint.domain.model.PrintDocument
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipInputStream
import kotlin.concurrent.withLock

class OfficePageSource(
    private val context: Context,
    private val uri: Uri,
    private val document: PrintDocument,
) : PageSource {

    private val lock = ReentrantLock()
    private var text: CharSequence? = null
    private var layout: Layout? = null
    private var layoutWidth = 0
    private var layoutHeight = 0
    private var breaks: List<Int> = emptyList()

    override val pageCount: Int
        get() {
            ensureLayout(DEFAULT_WIDTH, DEFAULT_HEIGHT)
            return lock.withLock { breaks.size }
        }

    override fun renderPage(index: Int, targetWidthPx: Int, targetHeightPx: Int): Bitmap? {
        if (index < 0 || targetWidthPx <= 0 || targetHeightPx <= 0) {
            return null
        }
        ensureLayout(targetWidthPx, targetHeightPx)
        val currentLayout = lock.withLock { layout } ?: return null
        val breakLine = lock.withLock { breaks.getOrNull(index) } ?: return null
        val bitmap = try {
            Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
        } catch (error: OutOfMemoryError) {
            AppLog.error("Out of memory allocating office page", error)
            return null
        }
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.translate(0f, -currentLayout.getLineTop(breakLine).toFloat())
        currentLayout.draw(canvas)
        return bitmap
    }

    override fun pageAspectRatio(index: Int): Float = DEFAULT_ASPECT_RATIO

    private fun ensureLayout(widthPx: Int, heightPx: Int) {
        lock.withLock {
            if (layout != null && layoutWidth == widthPx && layoutHeight == heightPx) {
                return
            }
            val content = text ?: extract().also { text = it }
            if (content.isBlank()) {
                return
            }
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = TEXT_SIZE_PX
                typeface = Typeface.SANS_SERIF
            }
            val built = StaticLayout.Builder.obtain(content, 0, content.length, paint, widthPx)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, LINE_SPACING)
                .setIncludePad(false)
                //noinspection WrongConstant
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .build()
            layout = built
            layoutWidth = widthPx
            layoutHeight = heightPx
            breaks = computeBreaks(built, heightPx)
        }
    }

    private fun extract(): CharSequence {
        val extension = document.format.extension
        val extracted = when (extension) {
            "docx", "odt" -> extractFromZip(ZIP_TEXT_ENTRIES)
            "pptx", "odp" -> extractFromZip(SLIDE_TEXT_ENTRIES)
            "xlsx", "ods" -> extractFromZip(SPREADSHEET_TEXT_ENTRIES)
            "rtf" -> extractRtf()
            else -> extractLegacyBinary()
        }
        return if (extracted.isBlank()) {
            "$NO_TEXT_CONTENT\n\n${document.displayName}"
        } else {
            extracted
        }
    }

    private fun extractFromZip(patterns: List<String>): String {
        val collected = StringBuilder()
        openStream()?.use { stream ->
            ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                var entries = 0
                while (entry != null && entries < MAX_ZIP_ENTRIES) {
                    val name = entry.name
                    if (!entry.isDirectory && patterns.any { pattern -> matches(name, pattern) }) {
                        readEntry(zip)?.let { text ->
                            collected.append(text)
                            collected.append("\n\n")
                        }
                        entries++
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return collected.toString().trim()
    }

    private fun matches(name: String, pattern: String): Boolean {
        if (pattern.endsWith("*")) {
            val prefix = pattern.dropLast(1)
            return name.startsWith(prefix)
        }
        return name == pattern
    }

    private fun readEntry(zip: ZipInputStream): String? {
        val chunk = StringBuilder()
        val buffer = ByteArray(ENTRY_BUFFER_BYTES)
        var total = 0
        while (total < MAX_ENTRY_BYTES) {
            val read = try {
                zip.read(buffer)
            } catch (error: IOException) {
                AppLog.warn(AppLog.throwableTag("zipRead", error))
                break
            }
            if (read <= 0) {
                break
            }
            chunk.append(String(buffer, 0, read, Charsets.UTF_8))
            total += read
        }
        return XmlTextExtractor.extractText(chunk.toString())
    }

    private fun extractRtf(): String {
        val raw = openStream()?.use { stream ->
            stream.readBytesLimited(MAX_ENTRY_BYTES).toString(Charsets.ISO_8859_1)
        } ?: return ""
        val builder = StringBuilder()
        var index = 0
        while (index < raw.length) {
            when {
                raw.startsWith("\\par", index) -> {
                    builder.append('\n')
                    index += 4
                }
                raw.startsWith("\\line", index) -> {
                    builder.append('\n')
                    index += 5
                }
                raw[index] == '{' -> {
                    index++
                }
                raw[index] == '}' -> {
                    index++
                }
                raw.startsWith("\\'", index) -> {
                    index += 4
                }
                raw[index] == '\\' -> {
                    index++
                    while (index < raw.length && raw[index].isLetter()) {
                        index++
                    }
                    if (index < raw.length && raw[index].isDigit()) {
                        while (index < raw.length && raw[index].isDigit()) {
                            index++
                        }
                    }
                    if (index < raw.length && raw[index] == ' ') {
                        index++
                    }
                }
                raw[index] in '\r'..'\u009f' -> {
                    index++
                }
                else -> {
                    builder.append(raw[index])
                    index++
                }
            }
            if (builder.length > MAX_ENTRY_BYTES) {
                break
            }
        }
        return builder.toString().trim()
    }

    private fun extractLegacyBinary(): String {
        val bytes = openStream()?.use { it.readBytesLimited(MAX_ENTRY_BYTES) } ?: return ""
        val ascii = StringBuilder()
        val utf16 = StringBuilder()
        var index = 0
        while (index + 1 < bytes.size) {
            val byte = bytes[index].toInt() and 0xFF
            if (byte in PRINTABLE_ASCII_MIN..PRINTABLE_ASCII_MAX) {
                ascii.append(byte.toChar())
            } else if (byte == 0x0A || byte == 0x0D) {
                ascii.append('\n')
                ascii.append('\n')
            } else if (index + 5 < bytes.size) {
                val chunk = String(bytes, index, 6, Charsets.UTF_16LE)
                if (chunk.all(::isMeaningfulChar)) {
                    utf16.append(chunk)
                }
            }
            index++
            if (ascii.length + utf16.length > MAX_EXTRACTED_CHARACTERS) {
                break
            }
        }
        val result = if (ascii.length >= utf16.length) ascii.toString() else utf16.toString()
        return result.lineSequence()
            .map { it.trim() }
            .filter { it.length >= MIN_USEFUL_LINE_LENGTH }
            .joinToString("\n")
    }

    private fun isMeaningfulChar(character: Char): Boolean =
        character.isLetterOrDigit() || character == ' ' || character == '.' || character == ','

    private fun openStream(): InputStream? = try {
        context.contentResolver.openInputStream(uri)
    } catch (error: SecurityException) {
        AppLog.warn(AppLog.throwableTag("openOffice", error))
        null
    } catch (error: IOException) {
        AppLog.warn(AppLog.throwableTag("openOffice", error))
        null
    }

    private fun InputStream.readBytesLimited(limit: Int): ByteArray {
        val buffer = ByteArray(limit)
        var total = 0
        while (total < limit) {
            val read = try {
                read(buffer, total, limit - total)
            } catch (error: IOException) {
                AppLog.warn(AppLog.throwableTag("readLimited", error))
                break
            }
            if (read <= 0) {
                break
            }
            total += read
        }
        return buffer.copyOf(total)
    }

    private fun computeBreaks(currentLayout: Layout, pageHeightPx: Int): List<Int> {
        if (pageHeightPx <= 0 || currentLayout.lineCount == 0) {
            return listOf(0)
        }
        val result = mutableListOf(0)
        var line = 0
        while (line < currentLayout.lineCount) {
            val top = currentLayout.getLineTop(line)
            val bottom = currentLayout.getLineBottom(line)
            if (bottom - top > pageHeightPx) {
                return listOf(0)
            }
            if (bottom > pageHeightPx) {
                result += line
            }
            line++
        }
        return result
    }

    private companion object {
        const val DEFAULT_WIDTH = 1240
        const val DEFAULT_HEIGHT = 1754
        const val TEXT_SIZE_PX = 26f
        const val LINE_SPACING = 1.2f
        const val DEFAULT_ASPECT_RATIO = 0.707f
        const val ENTRY_BUFFER_BYTES = 16 * 1024
        const val MAX_ENTRY_BYTES = 4 * 1024 * 1024
        const val MAX_ZIP_ENTRIES = 400
        const val MAX_EXTRACTED_CHARACTERS = 400_000
        const val MIN_USEFUL_LINE_LENGTH = 3
        const val PRINTABLE_ASCII_MIN = 32
        const val PRINTABLE_ASCII_MAX = 126
        val ZIP_TEXT_ENTRIES = listOf("word/document.xml", "content.xml", "styles.xml")
        val SLIDE_TEXT_ENTRIES = listOf("ppt/slides/slide*.xml", "content.xml")
        val SPREADSHEET_TEXT_ENTRIES = listOf("xl/sharedStrings.xml", "content.xml")
        const val NO_TEXT_CONTENT = "No extractable text content was found in this document."
    }
}

private fun InputStream.readBytesLimited(limit: Int): ByteArray {
    val buffer = ByteArray(limit)
    var total = 0
    while (total < limit) {
        val read = read(buffer, total, limit - total)
        if (read <= 0) break
        total += read
    }
    return buffer.copyOf(total)
}

object XmlTextExtractor {
    private val TAG_PATTERN = Regex("<[^>]*>")
    private val ENTITY_PATTERN = Regex("&(amp|lt|gt|quot|apos|#\\d+|#x[0-9a-fA-F]+);")
    private val WHITESPACE_PATTERN = Regex("[ \\t]{2,}")

    fun extractText(xml: String): String {
        if (xml.isEmpty()) {
            return ""
        }
        val withoutScripts = xml.replace(SCRIPT_STYLE_PATTERN, "")
        val withBreaks = withoutScripts.replace(BLOCK_BREAK_PATTERN, "\n")
        val withoutTags = TAG_PATTERN.replace(withBreaks, "")
        val decoded = decodeEntities(withoutTags)
        return WHITESPACE_PATTERN.replace(decoded, " ")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()
    }

    private fun decodeEntities(input: String): String = ENTITY_PATTERN.replace(input) { match ->
        val body = match.groupValues[1]
        when {
            body == "amp" -> "&"
            body == "lt" -> "<"
            body == "gt" -> ">"
            body == "quot" -> "\""
            body == "apos" -> "'"
            body.startsWith("#x") -> body.drop(2).toIntOrNull(16)?.toChar()?.toString() ?: match.value
            body.startsWith("#") -> body.drop(1).toIntOrNull()?.toChar()?.toString() ?: match.value
            else -> match.value
        }
    }

    private val SCRIPT_STYLE_PATTERN = Regex("<(script|style)[^>]*>.*?</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val BLOCK_BREAK_PATTERN = Regex("</(p|div|tr|li|h[1-6]|w:p|a:p|text:p|sheetData)>|<br\\s*/?>", RegexOption.IGNORE_CASE)
}
