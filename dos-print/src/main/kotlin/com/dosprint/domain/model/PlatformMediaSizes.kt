package com.dosprint.domain.model

import android.print.PrintAttributes

/**
 * The platform's paper catalogue, as plain data.
 *
 * Page selection appeared to do nothing because the app submitted a made-up media size id such as
 * `DosPrint-iso_a4`. `PrintAttributes.MediaSize` ids are not free-form: the print framework and
 * print services identify paper by matching an id against the platform catalogue, and an id that is
 * not in that catalogue falls back to whatever the printer happens to default to.
 *
 * The app's catalogue already uses the platform's own ids for standard sizes (`iso_a4`,
 * `na_letter`, `jis_b5`, ...), so the constant is just the id upper-cased. Keeping that rule as pure
 * data rather than reflection makes it checkable: `PlatformMediaSizesTest` can assert that the
 * catalogue lines up with the platform without an Android runtime.
 */
internal object PlatformPaperCatalogue {

    /** `iso_a4` becomes `ISO_A4`. */
    fun constantFor(paperId: String): String = paperId.uppercase()

    /** Every media size constant this project is willing to submit. */
    val constants: Set<String> = buildSet {
        addSeries("ISO", "A", 0..10)
        addSeries("ISO", "B", 0..10)
        addSeries("ISO", "C", 0..10)
        addSeries("ISO", "D", 0..10)
        addSeries("ISO", "E", 0..10)
        addSeries("ISO", "F", 0..10)
        addSeries("JIS", "B", 0..10)
        addSeries("JIS_B", "", 0..10)
        addSeries("JIS_B", "_", 0..10)
        listOf(
            "ANSI_A", "ANSI_B", "ANSI_C", "ANSI_D", "ANSI_E", "ANSI_F",
            "NA_LETTER", "NA_LEGAL", "NA_TABLOID", "NA_EXECUTIVE", "NA_STATEMENT", "NA_INVOICE",
            "NA_FANFOLD_US", "NA_FANFOLD_US_GERMANY", "NA_FANFOLD_FOLDER", "NA_FOLIO", "NA_LEDGER",
            "NA_4X6", "NA_5X7", "NA_10X14",
            "JIS_EXECUTIVE", "JIS_K10", "JIS_3K", "JIS_4K", "JIS_2K",
            "ROLLED_ANTIPAPER_10_INCH", "ROLLED_ANTIPAPER_11_INCH", "ROLLED_ANTIPAPER_12_INCH",
            "ROLLED_ANTIPAPER_15_INCH", "ROLLED_ANTIPAPER_20_INCH", "ROLLED_ANTIPAPER_30_INCH",
            "ROLLED_ANTIPAPER_36_INCH", "ROLLED_ANTIPAPER_50_INCH",
            "ROC_A0", "ROC_A1", "ROC_A2", "ROC_A3", "ROC_A4", "ROC_A5",
            "ROC_A6", "ROC_A7", "ROC_A8", "ROC_A9",
        ).forEach(::add)
    }

    private fun MutableSet<String>.addSeries(prefix: String, letter: String, range: IntRange) {
        range.forEach { index -> add(prefix + "_" + letter + index) }
    }

    fun isPlatformPaper(paper: PaperSize): Boolean = constantFor(paper.id) in constants

    /** Catalogue entries the platform can name, which is every common size. */
    fun platformPapers(papers: List<PaperSize>): List<PaperSize> = papers.filter(::isPlatformPaper)

    fun customPapers(papers: List<PaperSize>): List<PaperSize> = papers.filterNot(::isPlatformPaper)
}

/**
 * Turns a [PaperSize] into the media size handed to the print framework.
 *
 * A recognised size is submitted as the platform's own constant and rotated with
 * `asPortrait`/`asLandscape`, so the printer sees a real paper id. Only sizes the platform has never
 * heard of get a synthesised id.
 */
object PlatformMediaSizes {

    private val resolved = mutableMapOf<String, PrintAttributes.MediaSize?>()

    /**
     * The platform constant for [paper], or null when there is none.
     *
     * Reads the field by name rather than referencing it directly, because the platform catalogue
     * differs between releases and this project compiles against a stripped `android.jar` where a
     * missing field would not compile at all.
     */
    fun find(paper: PaperSize, landscape: Boolean): PrintAttributes.MediaSize? {
        val constant = PlatformPaperCatalogue.constantFor(paper.id)
        val media = resolved.getOrPut(constant) { readConstant(constant) } ?: return null
        return if (landscape) media.asLandscape() else media.asPortrait()
    }

    fun isPlatformPaper(paper: PaperSize): Boolean = PlatformPaperCatalogue.isPlatformPaper(paper)

    private fun readConstant(name: String): PrintAttributes.MediaSize? = runCatching {
        PrintAttributes.MediaSize::class.java.getField(name).get(null) as PrintAttributes.MediaSize
    }.getOrNull()
}
