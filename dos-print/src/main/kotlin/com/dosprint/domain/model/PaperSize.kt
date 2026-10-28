package com.dosprint.domain.model

import android.print.PrintAttributes
import androidx.compose.runtime.Immutable

@Immutable
data class PaperSize(
    val id: String,
    val label: String,
    val widthMils: Int,
    val heightMils: Int,
    val source: Source = Source.STANDARD,
) {
    enum class Source {
        STANDARD,
        PRINTER,
    }

    val widthPoints: Float get() = widthMils * MILLIS_PER_INCH.toFloat() / POINTS_PER_INCH

    val heightPoints: Float get() = heightMils * MILLIS_PER_INCH.toFloat() / POINTS_PER_INCH

    val widthInches: Float get() = widthMils.toFloat() / MILLIS_PER_INCH

    val heightInches: Float get() = heightMils.toFloat() / MILLIS_PER_INCH

    fun withOrientation(landscape: Boolean): PaperSize =
        if (landscape && heightMils > widthMils) {
            copy(id = "$id-landscape", widthMils = heightMils, heightMils = widthMils)
        } else {
            this
        }

    /**
     * The media size to submit to the print framework.
     *
     * A platform-recognised size is submitted as the platform's own constant, rotated with
     * `asLandscape`/`asPortrait`, so the printer and the print service both see a real paper id.
     * Only sizes the framework has never heard of get a synthesised id.
     */
    fun toPrintMediaSize(landscape: Boolean): PrintAttributes.MediaSize =
        PlatformMediaSizes.find(this, landscape) ?: PrintAttributes.MediaSize(
            customMediaId(landscape),
            label,
            if (landscape) heightMils else widthMils,
            if (landscape) widthMils else heightMils,
        )

    /** True when the print framework can name this size, so its paper id will be honoured. */
    val isPlatformPaper: Boolean get() = PlatformPaperCatalogue.isPlatformPaper(this)

    /** The id the print framework reports for this size, used to match live job attributes. */
    fun mediaIdFor(landscape: Boolean): String =
        PlatformMediaSizes.find(this, landscape)?.id ?: customMediaId(landscape)

    private fun customMediaId(landscape: Boolean): String =
        if (landscape) "$id-landscape" else id

    fun matches(other: PaperSize, toleranceMils: Int = SIZE_TOLERANCE_MILS): Boolean =
        kotlin.math.abs(widthMils - other.widthMils) <= toleranceMils &&
            kotlin.math.abs(heightMils - other.heightMils) <= toleranceMils

    fun matches(mediaSize: PrintAttributes.MediaSize, toleranceMils: Int = SIZE_TOLERANCE_MILS): Boolean =
        kotlin.math.abs(widthMils - mediaSize.widthMils) <= toleranceMils &&
            kotlin.math.abs(heightMils - mediaSize.heightMils) <= toleranceMils

    companion object {
        const val MILLIS_PER_INCH = 1000
        const val POINTS_PER_INCH = 72
        const val SIZE_TOLERANCE_MILS = 500

        /**
         * The catalogue.
         *
         * Standard sizes carry the **print framework's own id** (`iso_a4`, `na_letter`,
         * `na_tabloid`, ...) so the platform recognises them and the requested paper is honoured.
         * Sizes the platform has no name for keep a `photo_` or `env_` id and are submitted with a
         * synthesised id, which is honest about the fact that they cannot be matched by name.
         */
        val A3 = PaperSize("iso_a3", "A3", 11693, 16538)
        val A2 = PaperSize("iso_a2", "A2", 16538, 23386)
        val A4 = PaperSize("iso_a4", "A4", 8270, 11692)
        val A5 = PaperSize("iso_a5", "A5", 5791, 8270)
        val A6 = PaperSize("iso_a6", "A6", 4131, 5791)
        val B4 = PaperSize("iso_b4", "B4 (JIS)", 10319, 14572)
        val B5 = PaperSize("iso_b5", "B5 (JIS)", 7286, 10319)
        val B6 = PaperSize("iso_b6", "B6 (JIS)", 6142, 8661)
        val C4 = PaperSize("iso_c4", "C4", 9250, 13039)
        val C5 = PaperSize("iso_c5", "C5", 6500, 9197)
        val LETTER = PaperSize("na_letter", "Letter", 8500, 11000)
        val LEGAL = PaperSize("na_legal", "Legal", 8500, 14000)
        val TABLOID = PaperSize("na_tabloid", "Tabloid 11 x 17", 11000, 17000)
        val LEDGER = PaperSize("na_ledger", "Ledger 17 x 11", 17000, 11000)
        val EXECUTIVE = PaperSize("na_executive", "Executive", 7250, 9500)
        val STATEMENT = PaperSize("na_statement", "Statement", 5500, 8500)
        val FOLIO = PaperSize("na_folio", "Folio", 8500, 13000)
        val PAPER_10X14 = PaperSize("na_10x14", "10 x 14 in", 10000, 14000)
        val PHOTO_4X6 = PaperSize("na_4x6", "4 x 6 in photo", 4000, 6000)
        val PHOTO_5X7 = PaperSize("na_5x7", "5 x 7 in photo", 5000, 7000)
        val ENVELOPE_DL = PaperSize("na_fanfold_us_germany", "Envelope DL", 7874, 4318)

        val PHOTO_8X10 = PaperSize("photo_8x10", "8 x 10 in photo", 8000, 10000)
        val PHOTO_10X15 = PaperSize("photo_10x15", "10 x 15 cm photo", 3937, 5906)
        val PHOTO_2X2 = PaperSize("photo_2x2", "2 x 2 in photo", 2000, 2000)
        val HALF_LETTER = PaperSize("half_letter", "Half Letter", 5500, 8500)
        val ENVELOPE_C5 = PaperSize("env_c5", "Envelope C5", 7874, 11024)
        val ENVELOPE_C6 = PaperSize("env_c6", "Envelope C6", 4331, 6299)
        val ENVELOPE_B5 = PaperSize("env_b5", "Envelope B5", 9843, 7087)
        val ENVELOPE_MONARCH = PaperSize("env_monarch", "Envelope Monarch", 4250, 7000)
        val ENVELOPE_NUMBER_10 = PaperSize("env_number_10", "Envelope #10", 4250, 9500)
        val HAGAKI = PaperSize("jis_hagaki", "Hagaki", 3937, 5591)

        /** Sizes the platform names, in the order they are worth showing first. */
        val COMMON = listOf(
            A4,
            LETTER,
            LEGAL,
            A5,
            A3,
            A6,
            B5,
            B4,
            C4,
            EXECUTIVE,
            TABLOID,
            PHOTO_4X6,
            PHOTO_5X7,
            ENVELOPE_DL,
        )

        val BY_ID: Map<String, PaperSize> = buildMap {
            fun put(vararg sizes: PaperSize) = sizes.forEach { put(it.id, it) }
            fun putAll(sizes: List<PaperSize>) = sizes.forEach { put(it.id, it) }
            putAll(COMMON)
            put(A2, B6, C5, LEDGER, STATEMENT, FOLIO, PAPER_10X14, HALF_LETTER)
            put(PHOTO_8X10, PHOTO_10X15, PHOTO_2X2)
            put(ENVELOPE_C5, ENVELOPE_C6, ENVELOPE_B5, ENVELOPE_MONARCH, ENVELOPE_NUMBER_10, HAGAKI)
        }

        val BY_LABEL: Map<String, PaperSize> = buildMap {
            BY_ID.forEach { (id, size) ->
                put(normalizeKey(id), size)
                put(normalizeKey(size.label), size)
            }
        }

        val ALL: List<PaperSize> = BY_ID.values.toList()

        fun byId(id: String): PaperSize? = BY_ID[id.lowercase()]

        fun byLabel(label: String): PaperSize? = BY_LABEL[normalizeKey(label)]

        private fun normalizeKey(value: String): String = value
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")

        fun fromPrinter(id: String, label: String, widthMils: Int, heightMils: Int): PaperSize {
            val normalizedId = id.lowercase().replace('-', '_').replace(' ', '_')
            val existing = byId(normalizedId)
            if (existing != null && existing.matches(PaperSize(normalizedId, label, widthMils, heightMils))) {
                return existing
            }
            val fallbackLabel = buildString {
                append(widthMils / MILLIS_PER_INCH)
                append(" x ")
                append(heightMils / MILLIS_PER_INCH)
                append(" in")
            }
            return PaperSize(
                id = normalizedId,
                label = label.ifBlank { fallbackLabel },
                widthMils = widthMils,
                heightMils = heightMils,
                source = Source.PRINTER,
            )
        }
    }
}
