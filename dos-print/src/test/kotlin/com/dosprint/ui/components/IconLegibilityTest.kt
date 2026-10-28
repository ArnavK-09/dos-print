package com.dosprint.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * Guards the failure that made the entire icon set invisible on device.
 *
 * The icons were once filled shapes whose subpaths all wound the same way. Under the `NonZero` fill
 * rule that unions them, so a glyph built from an outer outline plus inner detail collapsed into one
 * solid 24x24 block: technically drawn, but with no readable symbol.
 *
 * The icons are now strokes described as data, so this test can rasterise exactly what is rendered
 * and measure how much of the viewport is really inked. An icon that is empty, or one whose strokes
 * merge into a solid block, both fail here.
 */
class IconLegibilityTest {

    @Test
    fun `every icon inks a readable amount of the viewport`() {
        allIcons().forEach { (name, shapes) ->
            val coverage = coverageOf(shapes)
            assertTrue(
                "$name inks ${pct(coverage)} of the viewport, which reads as blank",
                coverage >= MIN_COVERAGE,
            )
            assertTrue(
                "$name inks ${pct(coverage)} of the viewport, which reads as a solid block",
                coverage <= MAX_COVERAGE,
            )
        }
    }

    @Test
    fun `no icon collapses into a filled block`() {
        allIcons().forEach { (name, shapes) ->
            val length = shapes.sumOf { polylineLength(it.points).toDouble() }.toFloat()
            val inkArea = coverageOf(shapes) * VIEWPORT_SIZE * VIEWPORT_SIZE
            val paintedArea = length * ICON_STROKE_WIDTH
            val density = if (paintedArea <= 0f) 0f else inkArea / paintedArea
            assertTrue(
                "$name has stroke density ${"%.2f".format(density)}, so its shapes merge into a blob",
                density <= MAX_STROKE_DENSITY,
            )
        }
    }

    @Test
    fun `every icon stays inside the viewport`() {
        allIcons().forEach { (name, shapes) ->
            shapes.flatMap { it.points }.forEach { (x, y) ->
                assertTrue(
                    "$name escapes the viewport at $x,$y",
                    x in -MARGIN..(VIEWPORT_SIZE + MARGIN) && y in -MARGIN..(VIEWPORT_SIZE + MARGIN),
                )
            }
        }
    }

    @Test
    fun `every icon has drawable geometry`() {
        allIcons().forEach { (name, shapes) ->
            assertTrue("$name declares no shapes", shapes.isNotEmpty())
            assertTrue("$name has no line segments at all", shapes.all { it.points.size >= 2 })
        }
    }

    @Test
    fun `no two icons render identically`() {
        val duplicates = allIcons()
            .groupBy({ signatureOf(it.second) }, { it.first })
            .filterValues { it.size > 1 }
        assertTrue("these icons render identically: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `the studio canvas icon differs from the layout icon`() {
        val canvas = signatureOf(studioCanvas())
        val layout = signatureOf(studioLayout())
        assertTrue("Canvas and Layout render identically", canvas != layout)
    }

    @Test
    fun `undo and redo are mirror images rather than duplicates`() {
        val undo = studioUndo().first().points
        val redo = studioRedo().first().points
        val mirroredUndo = undo.map { (x, y) -> (VIEWPORT_SIZE - x) to y }
        assertTrue(
            "Redo should mirror Undo about the vertical centre line",
            mirroredUndo.map { "${it.first * 4},${it.second * 4}" } ==
                redo.map { "${it.first * 4},${it.second * 4}" },
        )
    }

    @Test
    fun `stroke width is thinner than the icon so shapes never fuse`() {
        assertTrue(ICON_STROKE_WIDTH < MIN_SHAPE_GAP)
    }

    @Test
    fun `every shape spans a real distance`() {
        allIcons().forEach { (name, shapes) ->
            shapes.forEach { shape ->
                assertTrue(
                    "$name contains a zero-length shape",
                    polylineLength(shape.points) >= MIN_LENGTH,
                )
            }
        }
    }

    private fun studioCanvas() = shapesOf(DosPrintIcons.Canvas)
    private fun studioLayout() = shapesOf(DosPrintIcons.Layout)
    private fun studioUndo() = shapesOf(DosPrintIcons.Undo)
    private fun studioRedo() = shapesOf(DosPrintIcons.Redo)

    /**
     * Renders the shapes the way the device does: every polyline thickened by the stroke width.
     * Coverage is the fraction of viewport pixels within half a stroke width of any segment.
     */
    private fun coverageOf(shapes: List<IconShape>): Float {
        val segments = shapes.flatMap { shape ->
            shape.points.zipWithNext().map { (from, to) ->
                floatArrayOf(from.first, from.second, to.first, to.second)
            }
        }
        if (segments.isEmpty()) {
            return 0f
        }
        var inked = 0
        var column = 0
        while (column < RESOLUTION) {
            val px = (column + 0.5f) / RESOLUTION * VIEWPORT_SIZE
            var row = 0
            while (row < RESOLUTION) {
                val py = (row + 0.5f) / RESOLUTION * VIEWPORT_SIZE
                val near = segments.any { segment ->
                    segmentDistance(
                        px,
                        py,
                        segment[0],
                        segment[1],
                        segment[2],
                        segment[3],
                    ) <= ICON_STROKE_WIDTH / 2f
                }
                if (near) {
                    inked++
                }
                row++
            }
            column++
        }
        return inked.toFloat() / (RESOLUTION * RESOLUTION)
    }

    private fun polylineLength(points: List<Pair<Float, Float>>): Float =
        points.zipWithNext()
            .sumOf { (from, to) -> hypot(to.first - from.first, to.second - from.second).toDouble() }
            .toFloat()

    private fun signatureOf(shapes: List<IconShape>): String = shapes
        .map { shape -> shape.points.joinToString(";") { "${it.first * 4},${it.second * 4}" } }
        .sorted()
        .joinToString("|")

    private fun pct(value: Float) = "${(value * 100).toInt()}%"

    private fun shapesOf(icon: androidx.compose.ui.graphics.vector.ImageVector): List<IconShape> =
        iconCatalogue().first { it.first == icon.name }.second

    private fun allIcons(): List<Pair<String, List<IconShape>>> = iconCatalogue()
}

private const val VIEWPORT_SIZE = 24f
private const val RESOLUTION = 48
private const val MIN_COVERAGE = 0.015f
private const val MAX_COVERAGE = 0.45f
private const val MAX_STROKE_DENSITY = 1.3f
private const val MIN_SHAPE_GAP = 2.4f
private const val MARGIN = 1.5f
private const val MIN_LENGTH = 1f
