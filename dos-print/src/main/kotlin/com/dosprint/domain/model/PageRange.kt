package com.dosprint.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class PageSegment(
    val start: Int,
    val end: Int,
) {
    init {
        require(start >= 1) { "Page numbers start at 1" }
        require(end >= start) { "Segment end must not precede its start" }
    }

    fun count(): Int = end - start + 1

    fun clampTo(limit: Int): PageSegment? {
        if (limit < 1 || start > limit) {
            return null
        }
        return PageSegment(start, end.coerceAtMost(limit))
    }

    fun overlaps(other: PageSegment): Boolean = start <= other.end && other.start <= end
}

sealed interface PageSelection {
    data object All : PageSelection

    data class Explicit(val segments: List<PageSegment>) : PageSelection {
        val isEmpty: Boolean get() = segments.isEmpty()
    }
}

@Immutable
data class PageRange(
    val selection: PageSelection,
) {
    val isAllPages: Boolean get() = selection is PageSelection.All

    fun segmentsFor(documentPages: Int): List<PageSegment> = when (val current = selection) {
        is PageSelection.All -> if (documentPages > 0) listOf(PageSegment(1, documentPages)) else emptyList()
        is PageSelection.Explicit -> current.segments.mapNotNull { it.clampTo(documentPages) }
    }

    fun selectedPageCount(documentPages: Int): Int = segmentsFor(documentPages).sumOf { it.count() }

    fun clampTo(documentPages: Int): PageRange = when (val current = selection) {
        is PageSelection.All -> this
        is PageSelection.Explicit -> {
            val clamped = current.segments.mapNotNull { it.clampTo(documentPages) }.sortedBy { it.start }
            PageRange(if (clamped.isEmpty()) PageSelection.Explicit(emptyList()) else PageSelection.Explicit(merge(clamped)))
        }
    }

    fun toDisplayString(): String = when (val current = selection) {
        is PageSelection.All -> "All"
        is PageSelection.Explicit -> current.segments.joinToString(", ") { segment ->
            if (segment.start == segment.end) "${segment.start}" else "${segment.start}-${segment.end}"
        }
    }

    fun firstPageOrNull(documentPages: Int): Int? = segmentsFor(documentPages).firstOrNull()?.start

    companion object {
        val All: PageRange = PageRange(PageSelection.All)

        const val MAX_PAGES_PER_JOB = 5000

        fun parse(input: String, documentPages: Int = 0): Result<PageRange> {
            val cleaned = input.replace("–", "-").replace("—", "-").replace(" ", "")
            if (cleaned.isBlank()) {
                return Result.failure(IllegalArgumentException("Empty page range"))
            }
            val segments = mutableListOf<PageSegment>()
            for (token in cleaned.split(',')) {
                if (token.isBlank()) {
                    continue
                }
                val segment = parseToken(token) ?: return Result.failure(IllegalArgumentException("Invalid token: $token"))
                segments += segment
            }
            if (segments.isEmpty()) {
                return Result.failure(IllegalArgumentException("No pages parsed"))
            }
            val merged = merge(segments)
            if (merged.sumOf { it.count() } > MAX_PAGES_PER_JOB) {
                return Result.failure(IllegalArgumentException("Too many pages requested"))
            }
            val bounded = if (documentPages > 0) {
                merged.mapNotNull { it.clampTo(documentPages) }
            } else {
                merged
            }
            return Result.success(PageRange(PageSelection.Explicit(bounded)))
        }

        fun of(vararg pages: Int): PageRange {
            val segments = pages.filter { it >= 1 }.map { PageSegment(it, it) }.sortedBy { it.start }
            return PageRange(PageSelection.Explicit(merge(segments)))
        }

        fun ofSegments(segments: List<PageSegment>): PageRange =
            PageRange(PageSelection.Explicit(merge(segments.sortedBy { it.start })))

        fun expandForPrint(range: PageRange, documentPages: Int): List<android.print.PageRange> =
            range.segmentsFor(documentPages).map { android.print.PageRange(it.start, it.end) }

        private fun parseToken(token: String): PageSegment? {
            val dashIndex = token.indexOf('-')
            if (dashIndex < 0) {
                val page = token.toIntOrNull()?.takeIf { it >= 1 } ?: return null
                return PageSegment(page, page)
            }
            val start = token.substring(0, dashIndex).toIntOrNull() ?: return null
            val end = token.substring(dashIndex + 1).toIntOrNull() ?: return null
            return if (start >= 1 && end >= start) PageSegment(start, end) else null
        }

        private fun merge(segments: List<PageSegment>): List<PageSegment> {
            if (segments.isEmpty()) {
                return emptyList()
            }
            val sorted = segments.sortedBy { it.start }
            val merged = mutableListOf(sorted.first())
            for (segment in sorted.drop(1)) {
                val last = merged.last()
                if (segment.start <= last.end + 1) {
                    merged[merged.lastIndex] = PageSegment(last.start, maxOf(last.end, segment.end))
                } else {
                    merged += segment
                }
            }
            return merged
        }
    }
}
