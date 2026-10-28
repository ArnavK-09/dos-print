package com.dosprint.domain.model

import androidx.compose.runtime.Immutable

/**
 * Photo printing options.
 *
 * The vocabulary matches the reference app's menus one for one — scaling mode, one image or a grid,
 * "restrict to grid", gap size, position, and the margin presets — so the choices users already know
 * are the choices offered here. Layout is always computed, never hand-placed: the reference app has
 * no image handles at all, and an automatic grid is what keeps the geometry exact for the printer.
 */
@Immutable
data class ImagePageSettings(
    val scaling: ImageScaling = ImageScaling.SCALE_TO_FIT,
    val rotation: PageRotation = PageRotation.NONE,
    val grid: LayoutGrid = LayoutGrid(3, 2),
    val gap: ImageGap = ImageGap.NORMAL,
    val position: PagePosition = PagePosition.CENTER,
    val restrictToGrid: Boolean = false,
    val compactGroup: Boolean = true,
    val imagesPerPage: Int = 6,
    val gridOrientation: ImageGridOrientation = ImageGridOrientation.AUTO,
    val columns: Int = 3,
    val rows: Int = 2,
    val border: PhotoBorder = PhotoBorder.NONE,
    val photoSize: PhotoSizePreset = PhotoSizePreset.AUTO,
    val perPage: ImagesPerPage = if (imagesPerPage <= 1) ImagesPerPage.SINGLE else ImagesPerPage.MULTIPLE,
) {
    val usesGrid: Boolean get() = perPage == ImagesPerPage.MULTIPLE && imagesPerPage > 1

    val slotsPerSheet: Int
        get() = if (perPage == ImagesPerPage.SINGLE) 1 else imagesPerPage.coerceIn(MIN_IMAGES_PER_PAGE, MAX_IMAGES_PER_PAGE)

    /**
     * True when the images should sit as one tight block rather than spread across every slot.
     *
     * With fewer images than slots a spread layout leaves a photo stranded in a corner, while a
     * compact group keeps the pictures together so the sheet reads as a set.
     */
    val packsImagesTogether: Boolean get() = compactGroup

    val effectiveGrid: LayoutGrid
        get() = layoutGridFor(DEFAULT_PAPER_ASPECT)

    fun layoutGridFor(
        pageAspect: Float,
        pageWidthInches: Float? = null,
        pageHeightInches: Float? = null,
        marginFraction: Float = LayoutGrid.DefaultMarginFraction,
        cellBorderFraction: Float = 0f,
    ): LayoutGrid {
        if (!usesGrid) {
            return LayoutGrid.single()
        }
        val dimensions = gridDimensionsFor(
            imagesPerPage.coerceIn(MIN_IMAGES_PER_PAGE, MAX_IMAGES_PER_PAGE),
            pageAspect,
            photoSize.aspectRatio,
            pageWidthInches,
            pageHeightInches,
            marginFraction,
            gap.fraction,
            compactGroup,
            cellBorderFraction,
        )
        return grid.copy(columns = dimensions.columns, rows = dimensions.rows).withGap(gap)
    }

    fun withImagesPerPage(count: Int, pageAspect: Float): ImagePageSettings {
        val safeCount = count.coerceIn(MIN_IMAGES_PER_PAGE, MAX_IMAGES_PER_PAGE)
        if (safeCount == 1) {
            return copy(
                imagesPerPage = 1,
                columns = 1,
                rows = 1,
                perPage = ImagesPerPage.SINGLE,
                grid = grid.copy(columns = 1, rows = 1),
            )
        }
        val dimensions = gridDimensionsFor(safeCount, pageAspect, photoSize.aspectRatio)
        return copy(
            imagesPerPage = safeCount,
            columns = dimensions.columns,
            rows = dimensions.rows,
            perPage = ImagesPerPage.MULTIPLE,
            grid = grid.copy(columns = dimensions.columns, rows = dimensions.rows),
        )
    }

    fun withPerPage(mode: ImagesPerPage): ImagePageSettings = when (mode) {
        ImagesPerPage.SINGLE -> copy(perPage = ImagesPerPage.SINGLE, imagesPerPage = 1, columns = 1, rows = 1)
        ImagesPerPage.MULTIPLE -> withImagesPerPage(
            imagesPerPage.coerceAtLeast(2),
            DEFAULT_PAPER_ASPECT,
        )
    }

    fun fitsPhotoCount(
        count: Int,
        pageAspect: Float,
        pageWidthInches: Float,
        pageHeightInches: Float,
        marginFraction: Float,
        cellBorderFraction: Float = 0f,
    ): Boolean {
        if (photoSize == PhotoSizePreset.AUTO) {
            return true
        }
        if (count !in MIN_IMAGES_PER_PAGE..MAX_IMAGES_PER_PAGE ||
            pageWidthInches <= 0f ||
            pageHeightInches <= 0f
        ) {
            return false
        }
        val margin = marginFraction.coerceIn(0f, 0.2f)
        if (count == 1) {
            return photoSize.widthInches <= pageWidthInches * (1f - 2f * margin) &&
                photoSize.heightInches <= pageHeightInches * (1f - 2f * margin)
        }
        return gridDimensionsFor(
            count = count,
            pageAspect = pageAspect,
            photoAspect = photoSize.aspectRatio,
            pageWidthInches = pageWidthInches,
            pageHeightInches = pageHeightInches,
            marginFraction = margin,
            gutterFraction = gap.fraction,
            compactGroup = compactGroup,
            cellBorderFraction = cellBorderFraction,
        ).fits
    }

    private fun gridDimensionsFor(
        count: Int,
        pageAspect: Float,
        photoAspect: Float,
        pageWidthInches: Float? = null,
        pageHeightInches: Float? = null,
        marginFraction: Float = LayoutGrid.DefaultMarginFraction,
        gutterFraction: Float = gap.fraction,
        compactGroup: Boolean = this.compactGroup,
        cellBorderFraction: Float = 0f,
    ): GridDimensions {
        val safeAspect = pageAspect.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_PAPER_ASPECT
        var bestOverall = GridDimensions(1, count, fits = false)
        var bestOverallScore = Float.MAX_VALUE
        var bestFitting: GridDimensions? = null
        var bestFittingScore = Float.MAX_VALUE
        val physicalSizeKnown = pageWidthInches != null && pageHeightInches != null
        val margin = marginFraction.coerceIn(0f, 0.2f)
        val usableWidth = pageWidthInches?.times(1f - 2f * margin)
        val usableHeight = pageHeightInches?.times(1f - 2f * margin)
        val gutterX = usableWidth?.times(gutterFraction)
        val gutterY = usableHeight?.times(gutterFraction)
        for (candidateColumns in MIN_GRID_SIDE..MAX_GRID_SIDE) {
            val candidateRows = (count + candidateColumns - 1) / candidateColumns
            if (candidateRows !in MIN_GRID_SIDE..MAX_GRID_SIDE) {
                continue
            }
            if (gridOrientation == ImageGridOrientation.SIDE_BY_SIDE && candidateColumns < candidateRows) {
                continue
            }
            if (gridOrientation == ImageGridOrientation.STACKED && candidateRows < candidateColumns) {
                continue
            }
            val occupiedSlots = candidateColumns * candidateRows
            val cellAspect = safeAspect * candidateRows / candidateColumns
            val score = kotlin.math.abs(cellAspect - photoAspect) +
                (occupiedSlots - count) * UNUSED_SLOT_PENALTY
            if (score < bestOverallScore) {
                bestOverallScore = score
                bestOverall = GridDimensions(candidateColumns, candidateRows, fits = false)
            }
            val fits = when {
                photoSize == PhotoSizePreset.AUTO || !physicalSizeKnown -> true
                compactGroup -> {
                    val packedWidth = candidateColumns * photoSize.widthInches +
                        (candidateColumns - 1) * gutterX!!
                    val packedHeight = candidateRows * photoSize.heightInches +
                        (candidateRows - 1) * gutterY!!
                    packedWidth <= usableWidth!! && packedHeight <= usableHeight!!
                }
                else -> {
                    val cellWidth = (usableWidth!! - gutterX!! * (candidateColumns - 1)) / candidateColumns -
                        pageWidthInches!! * cellBorderFraction * 2f
                    val cellHeight = (usableHeight!! - gutterY!! * (candidateRows - 1)) / candidateRows -
                        pageHeightInches!! * cellBorderFraction * 2f
                    photoSize.widthInches <= cellWidth && photoSize.heightInches <= cellHeight
                }
            }
            if (fits && score < bestFittingScore) {
                bestFittingScore = score
                bestFitting = GridDimensions(candidateColumns, candidateRows, fits = true)
            }
        }
        return bestFitting ?: bestOverall
    }

    private data class GridDimensions(val columns: Int, val rows: Int, val fits: Boolean)

    companion object {
        const val MIN_IMAGES_PER_PAGE = 1
        const val MAX_IMAGES_PER_PAGE = 36
        private const val MIN_GRID_SIDE = 1
        private const val MAX_GRID_SIDE = 6
        private const val UNUSED_SLOT_PENALTY = 0.025f
        private const val DEFAULT_PAPER_ASPECT = 0.707f
    }
}

enum class ImageGridOrientation {
    AUTO,
    SIDE_BY_SIDE,
    STACKED,
}

enum class PhotoBorder(val label: String, val widthPoints: Float) {
    NONE("None", 0f),
    THIN("Thin", 0.5f),
    MEDIUM("Medium", 1f),
    THICK("Thick", 2f),
}

enum class PhotoSizePreset(
    val label: String,
    val widthInches: Float,
    val heightInches: Float,
) {
    AUTO("Automatic", 0f, 0f),
    US_PASSPORT("U.S. passport · 2 × 2 in", 2f, 2f),
    CANADA_PASSPORT("Canada passport · 2 × 2.75 in", 2f, 2.75f),
    INDIA_UK_AUSTRALIA_PASSPORT("India / UK passport · Australia minimum · 1.38 × 1.77 in", 35f / 25.4f, 45f / 25.4f),
    WALLET_PORTRAIT("Wallet · 2.5 × 3.5 in", 2.5f, 3.5f),
    WALLET_LANDSCAPE("Wallet · 3.5 × 2.5 in", 3.5f, 2.5f),
    SQUARE_4("Square print · 4 × 4 in", 4f, 4f),
    PRINT_4X6("Photo print · 4 × 6 in", 4f, 6f),
    PRINT_6X4("Photo print · 6 × 4 in", 6f, 4f),
    PRINT_5X7("Photo print · 5 × 7 in", 5f, 7f),
    PRINT_7X5("Photo print · 7 × 5 in", 7f, 5f),
    SQUARE_8("Square print · 8 × 8 in", 8f, 8f),
    PRINT_8X10("Photo print · 8 × 10 in", 8f, 10f),
    PRINT_10X8("Photo print · 10 × 8 in", 10f, 8f),
    SQUARE_12("Square print · 12 × 12 in", 12f, 12f),
    PRINT_11X14("Photo print · 11 × 14 in", 11f, 14f),
    PRINT_14X11("Photo print · 14 × 11 in", 14f, 11f),
    PRINT_16X20("Photo print · 16 × 20 in", 16f, 20f),
    PRINT_20X16("Photo print · 20 × 16 in", 20f, 16f),
    PRINT_20X30("Photo print · 20 × 30 in", 20f, 30f),
    PRINT_30X20("Photo print · 30 × 20 in", 30f, 20f),
    ;

    val aspectRatio: Float
        get() = if (this == AUTO) PREFERRED_PHOTO_ASPECT else widthInches / heightInches

    companion object {
        private const val PREFERRED_PHOTO_ASPECT = 0.67f
    }
}

enum class ImagesPerPage {
    SINGLE,
    MULTIPLE,
}

/** How a photo fills its slot. */
enum class ImageScaling(val label: String) {
    CROP_TO_FIT("Crop to fit"),
    SCALE_TO_FIT("Scale to fit"),
    STRETCH_TO_FIT("Stretch to fit"),
    ;

    /** Cropping and stretching both fill the slot exactly; only scaling leaves whitespace. */
    val fillsSlot: Boolean get() = this != SCALE_TO_FIT
}

enum class ImageGap(val label: String, val fraction: Float) {
    NONE("No gaps", 0f),
    NARROW("Narrow gaps", 0.012f),
    NORMAL("Normal gaps", 0.026f),
    WIDE("Wide gaps", 0.05f),
}

/** Where a single image sits on the sheet. */
enum class PagePosition(val label: String, val column: Int, val row: Int) {
    TOP_LEFT("Top left", 0, 0),
    TOP_MIDDLE("Top middle", 1, 0),
    TOP_RIGHT("Top right", 2, 0),
    MIDDLE_LEFT("Middle left", 0, 1),
    CENTER("Center", 1, 1),
    MIDDLE_RIGHT("Middle right", 2, 1),
    BOTTOM_LEFT("Bottom left", 0, 2),
    BOTTOM_MIDDLE("Bottom middle", 1, 2),
    BOTTOM_RIGHT("Bottom right", 2, 2),
}

private fun LayoutGrid.withGap(gap: ImageGap): LayoutGrid =
    if (gap == ImageGap.NONE) copy(gutterFraction = 0f) else copy(gutterFraction = gap.fraction)
