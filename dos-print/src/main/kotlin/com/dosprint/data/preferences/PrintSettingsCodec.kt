package com.dosprint.data.preferences

import android.net.Uri
import com.dosprint.domain.model.ImageGridOrientation
import com.dosprint.domain.model.ImagePageSettings
import com.dosprint.domain.model.ImagesPerPage
import com.dosprint.domain.model.PageRange
import com.dosprint.domain.model.PaperSize
import com.dosprint.domain.model.PrintSettings

object PrintSettingsCodec {
    private const val SEPARATOR = "|"
    private const val VERSION_V1 = "v1"
    private const val VERSION_V2 = "v2"
    private const val VERSION_V3 = "v3"
    private const val VERSION_V4 = "v4"
    private const val VERSION_V5 = "v5"
    private const val VERSION_V6 = "v6"
    private const val VERSION_V7 = "v7"
    private const val VERSION = "v8"

    fun encode(settings: PrintSettings): String = listOf(
        VERSION,
        settings.paperSize.id,
        settings.landscape.toString(),
        settings.colorMode.name,
        settings.resolution.name,
        settings.copies.toString(),
        settings.pageRange.toDisplayString(),
        settings.scaling.name,
        settings.scalePercent.toString(),
        settings.duplex.name,
        settings.collate.toString(),
        settings.rotation.name,
        settings.marginPreset.name,
        settings.pagesPerSheet.toString(),
        settings.watermark.name,
        Uri.encode(settings.watermarkText),
        settings.borderless.toString(),
        settings.imagePageSettings.scaling.name,
        settings.imagePageSettings.gap.name,
        settings.imagePageSettings.position.name,
        settings.imagePageSettings.restrictToGrid.toString(),
        settings.imagePageSettings.compactGroup.toString(),
        settings.imagePageSettings.imagesPerPage.toString(),
        settings.imagePageSettings.columns.toString(),
        settings.imagePageSettings.rows.toString(),
        settings.imagePageSettings.gridOrientation.name,
        settings.imagePageSettings.rotation.name,
        settings.imagePageSettings.border.name,
        settings.imagePageSettings.photoSize.name,
    ).joinToString(SEPARATOR)

    fun decode(encoded: String): PrintSettings? {
        val parts = encoded.split(SEPARATOR)
        if (parts.isEmpty() || parts.first() !in setOf(VERSION_V1, VERSION_V2, VERSION_V3, VERSION_V4, VERSION_V5, VERSION_V6, VERSION_V7, VERSION)) {
            return null
        }
        if (parts.size < 17) {
            return null
        }
        val version = parts.first()
        val paperSize = PaperSize.byId(parts[1]) ?: PaperSize.A4
        val pageRange = PageRange.parse(parts[6]).getOrDefault(PageRange.All)
        val defaults = PrintSettings(paperSize = paperSize)
        return defaults.copy(
            landscape = parts[2].toBooleanSafe(defaults.landscape),
            colorMode = enumOrDefault(parts[3], defaults.colorMode),
            resolution = enumOrDefault(parts[4], defaults.resolution),
            copies = parts[5].toIntOrNull()?.coerceIn(PrintSettings.MIN_COPIES, PrintSettings.MAX_COPIES) ?: defaults.copies,
            pageRange = pageRange,
            scaling = enumOrDefault(parts[7], defaults.scaling),
            scalePercent = parts[8].toIntOrNull()?.coerceIn(PrintSettings.MIN_SCALE_PERCENT, PrintSettings.MAX_SCALE_PERCENT)
                ?: defaults.scalePercent,
            duplex = enumOrDefault(parts[9], defaults.duplex),
            collate = parts[10].toBooleanSafe(defaults.collate),
            rotation = enumOrDefault(parts[11], defaults.rotation),
            marginPreset = enumOrDefault(parts[12], defaults.marginPreset),
            pagesPerSheet = parts[13].toIntOrNull()?.coerceIn(1, 9) ?: defaults.pagesPerSheet,
            watermark = enumOrDefault(parts[14], defaults.watermark),
            watermarkText = if (version == VERSION_V1) parts[15] else Uri.decode(parts[15]),
            borderless = parts[16].toBooleanSafe(defaults.borderless),
            imagePageSettings = decodeImagePageSettings(version, parts, defaults.imagePageSettings),
        )
    }

    private fun decodeImagePageSettings(
        version: String,
        parts: List<String>,
        defaults: ImagePageSettings,
    ): ImagePageSettings = when {
        version in setOf(VERSION_V3, VERSION_V4, VERSION_V5, VERSION_V6, VERSION_V7, VERSION) && parts.size >= 25 -> {
            val imagesPerPage = parts[22].toIntOrNull()
                ?.coerceIn(ImagePageSettings.MIN_IMAGES_PER_PAGE, ImagePageSettings.MAX_IMAGES_PER_PAGE)
                ?: defaults.imagesPerPage
            val columns = parts[23].toIntOrNull()?.coerceIn(1, 6) ?: defaults.columns
            val rows = parts[24].toIntOrNull()?.coerceIn(1, 6) ?: defaults.rows
            defaults.copy(
                scaling = enumOrDefault(parts[17], defaults.scaling),
                gap = enumOrDefault(parts[18], defaults.gap),
                position = enumOrDefault(parts[19], defaults.position),
                restrictToGrid = parts[20].toBooleanSafe(defaults.restrictToGrid),
                compactGroup = parts[21].toBooleanSafe(defaults.compactGroup),
                imagesPerPage = imagesPerPage,
                perPage = if (imagesPerPage == 1) ImagesPerPage.SINGLE else ImagesPerPage.MULTIPLE,
                columns = columns,
                rows = rows,
                grid = defaults.grid.copy(columns = columns, rows = rows),
                gridOrientation = when {
                    version in setOf(VERSION_V5, VERSION_V6, VERSION_V7, VERSION) && parts.size >= 26 ->
                        enumOrDefault(parts[25], defaults.gridOrientation)
                    version == VERSION_V4 && parts.size >= 26 && parts[25] == "STACKED" ->
                        ImageGridOrientation.STACKED
                    else -> defaults.gridOrientation
                },
                rotation = if (version in setOf(VERSION_V6, VERSION_V7, VERSION) && parts.size >= 27) {
                    enumOrDefault(parts[26], defaults.rotation)
                } else {
                    enumOrDefault(parts[11], defaults.rotation)
                },
                border = if (version in setOf(VERSION_V7, VERSION) && parts.size >= 28) {
                    enumOrDefault(parts[27], defaults.border)
                } else {
                    defaults.border
                },
                photoSize = if (version == VERSION && parts.size >= 29) {
                    enumOrDefault(parts[28], defaults.photoSize)
                } else {
                    defaults.photoSize
                },
            )
        }
        version == VERSION_V2 && parts.size >= 25 -> {
            val columns = parts[23].toIntOrNull()?.coerceIn(1, 9) ?: defaults.columns
            val rows = parts[24].toIntOrNull()?.coerceIn(1, 9) ?: defaults.rows
            val imagesPerPage = if (parts[18] == "SINGLE") 1 else (columns * rows)
            defaults.copy(
                scaling = enumOrDefault(parts[17], defaults.scaling),
                gap = enumOrDefault(parts[19], defaults.gap),
                position = enumOrDefault(parts[20], defaults.position),
                restrictToGrid = parts[21].toBooleanSafe(defaults.restrictToGrid),
                compactGroup = parts[22].toBooleanSafe(defaults.compactGroup),
                imagesPerPage = imagesPerPage.coerceIn(1, ImagePageSettings.MAX_IMAGES_PER_PAGE),
                perPage = if (parts[18] == "SINGLE") ImagesPerPage.SINGLE else ImagesPerPage.MULTIPLE,
                columns = columns.coerceAtMost(6),
                rows = rows.coerceAtMost(6),
                grid = defaults.grid.copy(columns = columns.coerceAtMost(6), rows = rows.coerceAtMost(6)),
                rotation = enumOrDefault(parts[11], defaults.rotation),
            )
        }
        else -> defaults.copy(rotation = enumOrDefault(parts[11], defaults.rotation))
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String, fallback: T): T =
        runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)

    private fun String.toBooleanSafe(fallback: Boolean): Boolean =
        when (lowercase()) {
            "true" -> true
            "false" -> false
            else -> fallback
        }
}
