package com.dosprint.ui

/**
 * What is being printed.
 *
 * The two flows share one screen and one set of print settings, because they genuinely share
 * paper, orientation, copies, colour, quality, duplex and the printer itself. Only the page
 * composition options differ: documents are pages, photos are laid out onto a sheet by a grid.
 */
enum class PrintMode(val label: String) {
    DOCUMENTS("Documents"),
    IMAGES("Photos"),
    ;

    val isImages: Boolean get() = this == IMAGES

    fun toggled(): PrintMode = if (isImages) DOCUMENTS else IMAGES
}
