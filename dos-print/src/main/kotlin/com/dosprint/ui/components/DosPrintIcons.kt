package com.dosprint.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * The application icon set: **stroked outlines** on a 24x24 viewport, described as data.
 *
 * Stroked rather than filled, on purpose. The icons were once filled shapes whose subpaths all wound
 * the same way. Under the `NonZero` fill rule that unions them, so a glyph built from an outer
 * outline plus inner detail collapsed into one solid 24x24 block — technically drawn, but with no
 * readable symbol, which is exactly how they appeared on device. Outlines sidestep fill rules
 * entirely: whatever geometry is described is exactly what is drawn.
 *
 * The shapes are plain data rather than lambdas so [IconLegibilityTest] can flatten and rasterise
 * them on the JVM. A filled-blob regression cannot pass unnoticed, because the test measures the
 * same geometry that is rendered.
 */
internal sealed interface IconShape {
    val points: List<Pair<Float, Float>>
}

internal data class IconLine(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
) : IconShape {
    override val points: List<Pair<Float, Float>> = listOf(x1 to y1, x2 to y2)
}

internal data class IconRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val corner: Float,
) : IconShape {
    override val points: List<Pair<Float, Float>> = roundedRectPoints(x, y, width, height, corner)
}

internal data class IconCircle(
    val cx: Float,
    val cy: Float,
    val radius: Float,
) : IconShape {
    override val points: List<Pair<Float, Float>> =
        arcPoints(cx, cy, radius, 0f, 360f)
}

internal data class IconArc(
    val cx: Float,
    val cy: Float,
    val radius: Float,
    val startDegrees: Float,
    val sweepDegrees: Float,
) : IconShape {
    override val points: List<Pair<Float, Float>> =
        arcPoints(cx, cy, radius, startDegrees, sweepDegrees)
}

internal data class IconPolyline(
    override val points: List<Pair<Float, Float>>,
) : IconShape

object DosPrintIcons {

    val size40 = 40.dp

    val Printer: ImageVector by lazy {
        icon(
            "Printer",
            IconRect(3f, 9f, 18f, 7f, 2f),
            IconRect(6.5f, 3f, 11f, 6f, 1.5f),
            IconRect(6.5f, 16f, 11f, 5f, 1.5f),
            IconCircle(17.4f, 12.4f, 0.9f),
        )
    }

    val Minus: ImageVector by lazy { icon("Minus", IconLine(5f, 12f, 19f, 12f)) }

    val Plus: ImageVector by lazy {
        icon(
            "Plus",
            IconLine(12f, 5f, 12f, 19f),
            IconLine(5f, 12f, 19f, 12f),
        )
    }

    val Document: ImageVector by lazy {
        icon(
            "Document",
            IconRect(5.5f, 3f, 13f, 18f, 2f),
            IconPolyline(listOf(14.5f to 3f, 14.5f to 7f, 18.5f to 7f)),
            IconLine(8.8f, 11.5f, 15.2f, 11.5f),
            IconLine(8.8f, 15f, 15.2f, 15f),
        )
    }

    val Image: ImageVector by lazy {
        icon(
            "Image",
            IconRect(3f, 5f, 18f, 14f, 2.5f),
            IconCircle(8.6f, 10f, 1.6f),
            IconPolyline(
                listOf(
                    4.5f to 17.5f,
                    10f to 11.8f,
                    13.6f to 15.2f,
                    16.2f to 12.6f,
                    19.5f to 16.2f,
                ),
            ),
        )
    }

    val Text: ImageVector by lazy {
        icon(
            "Text",
            IconRect(3.5f, 4.5f, 17f, 15f, 2f),
            IconLine(7.5f, 9.5f, 16.5f, 9.5f),
            IconLine(12f, 9.5f, 12f, 16f),
        )
    }

    val ArrowUpward: ImageVector by lazy {
        icon(
            "ArrowUpward",
            IconLine(12f, 20f, 12f, 4.5f),
            IconPolyline(listOf(6f to 10.5f, 12f to 4.5f, 18f to 10.5f)),
        )
    }

    val ArrowDownward: ImageVector by lazy {
        icon(
            "ArrowDownward",
            IconLine(12f, 4f, 12f, 19.5f),
            IconPolyline(listOf(6f to 13.5f, 12f to 19.5f, 18f to 13.5f)),
        )
    }

    val AspectRatio: ImageVector by lazy {
        icon(
            "AspectRatio",
            IconRect(3f, 6f, 18f, 12f, 2f),
            IconLine(7f, 12f, 17f, 12f),
            IconPolyline(listOf(9.4f to 9.6f, 7f to 12f, 9.4f to 14.4f)),
            IconPolyline(listOf(14.6f to 9.6f, 17f to 12f, 14.6f to 14.4f)),
        )
    }

    val ContentCopy: ImageVector by lazy {
        icon(
            "ContentCopy",
            IconRect(8.5f, 3f, 12.5f, 12.5f, 2f),
            IconRect(3f, 8.5f, 12.5f, 12.5f, 2f),
        )
    }

    val Flip: ImageVector by lazy {
        icon(
            "Flip",
            IconLine(12f, 3f, 12f, 21f),
            IconPolyline(listOf(9f to 7.5f, 4.2f to 12f, 9f to 16.5f)),
            IconPolyline(listOf(15f to 7.5f, 19.8f to 12f, 15f to 16.5f)),
        )
    }

    val Lock: ImageVector by lazy {
        icon(
            "Lock",
            IconRect(4.5f, 10.5f, 15f, 10f, 2.5f),
            IconPolyline(
                listOf(8f to 10.5f, 8f to 7.5f) + arcPoints(12f, 7.5f, 4f, 180f, 180f).drop(1) +
                    listOf(16f to 10.5f),
            ),
        )
    }

    val LockOpen: ImageVector by lazy {
        icon(
            "LockOpen",
            IconRect(4.5f, 10.5f, 15f, 10f, 2.5f),
            IconPolyline(
                listOf(8f to 10.5f, 8f to 7.5f) + arcPoints(12f, 7.5f, 4f, 180f, 180f).drop(1),
            ),
        )
    }

    val RotateLeft: ImageVector by lazy {
        icon(
            "RotateLeft",
            IconArc(12f, 13f, 7f, 145f, 270f),
            IconPolyline(listOf(3.6f to 5.2f, 4.6f to 10.6f, 10f to 9.6f)),
        )
    }

    val RotateRight: ImageVector by lazy {
        icon(
            "RotateRight",
            IconArc(12f, 13f, 7f, 35f, -270f),
            IconPolyline(listOf(20.4f to 5.2f, 19.4f to 10.6f, 14f to 9.6f)),
        )
    }

    val ClosePanel: ImageVector by lazy {
        icon(
            "ClosePanel",
            IconRect(3f, 4f, 18f, 16f, 2f),
            IconLine(13f, 4f, 13f, 20f),
            IconPolyline(listOf(16.4f to 9.6f, 21f to 14f, 21f to 9.6f, 16.4f to 14f)),
        )
    }

    val AlignTop: ImageVector by lazy {
        icon(
            "AlignTop",
            IconRect(3.5f, 3.5f, 17f, 17f, 2f),
            IconLine(7f, 8f, 17f, 8f),
            IconLine(7f, 12f, 17f, 12f),
        )
    }

    val AlignMiddle: ImageVector by lazy {
        icon(
            "AlignMiddle",
            IconRect(3.5f, 3.5f, 17f, 17f, 2f),
            IconLine(7f, 9.4f, 17f, 9.4f),
            IconLine(7f, 12f, 17f, 12f),
            IconLine(7f, 14.6f, 17f, 14.6f),
        )
    }

    val AlignBottom: ImageVector by lazy {
        icon(
            "AlignBottom",
            IconRect(3.5f, 3.5f, 17f, 17f, 2f),
            IconLine(7f, 12f, 17f, 12f),
            IconLine(7f, 16f, 17f, 16f),
        )
    }

    val AlignLeft: ImageVector by lazy {
        icon(
            "AlignLeft",
            IconRect(3.5f, 3.5f, 17f, 17f, 2f),
            IconLine(8f, 7f, 8f, 17f),
            IconLine(12f, 7f, 12f, 17f),
        )
    }

    val AlignCenter: ImageVector by lazy {
        icon(
            "AlignCenter",
            IconRect(3.5f, 3.5f, 17f, 17f, 2f),
            IconLine(9.4f, 7f, 9.4f, 17f),
            IconLine(12f, 7f, 12f, 17f),
            IconLine(14.6f, 7f, 14.6f, 17f),
        )
    }

    val AlignRight: ImageVector by lazy {
        icon(
            "AlignRight",
            IconRect(3.5f, 3.5f, 17f, 17f, 2f),
            IconLine(12f, 7f, 12f, 17f),
            IconLine(16f, 7f, 16f, 17f),
        )
    }

    val Grid: ImageVector by lazy {
        icon(
            "Grid",
            IconRect(3f, 3f, 18f, 18f, 2f),
            IconLine(9f, 3f, 9f, 21f),
            IconLine(15f, 3f, 15f, 21f),
            IconLine(3f, 9f, 21f, 9f),
            IconLine(3f, 15f, 21f, 15f),
        )
    }

    val Layout: ImageVector by lazy {
        icon(
            "Layout",
            IconRect(3f, 4f, 18f, 16f, 2f),
            IconLine(3f, 8.5f, 21f, 8.5f),
            IconLine(10.5f, 8.5f, 10.5f, 20f),
        )
    }

    val Undo: ImageVector by lazy {
        icon(
            "Undo",
            IconPolyline(listOf(8.6f to 6.6f, 4.2f to 10.6f, 8.6f to 14.6f)),
            IconArc(12.8f, 15.6f, 6.9f, 170f, 190f),
        )
    }

    val Redo: ImageVector by lazy {
        icon(
            "Redo",
            IconPolyline(listOf(15.4f to 6.6f, 19.8f to 10.6f, 15.4f to 14.6f)),
            IconArc(11.2f, 15.6f, 6.9f, 10f, -190f),
        )
    }

    val Tune: ImageVector by lazy {
        icon(
            "Tune",
            IconLine(3f, 7f, 21f, 7f),
            IconLine(3f, 12f, 21f, 12f),
            IconLine(3f, 17f, 21f, 17f),
            IconCircle(8.5f, 7f, 2.6f),
            IconCircle(15.5f, 12f, 2.6f),
            IconCircle(7f, 17f, 2.6f),
        )
    }

    val Wifi: ImageVector by lazy {
        icon(
            "Wifi",
            IconArc(12f, 19.4f, 4.8f, 215f, 110f),
            IconArc(12f, 19.4f, 8.8f, 215f, 110f),
            IconArc(12f, 19.4f, 12.8f, 215f, 110f),
            IconCircle(12f, 19.4f, 1.1f),
        )
    }

    val Bluetooth: ImageVector by lazy {
        icon(
            "Bluetooth",
            IconPolyline(
                listOf(
                    7f to 7.6f,
                    17f to 16.4f,
                    12f to 20.4f,
                    12f to 3.6f,
                    17f to 7.6f,
                    7f to 16.4f,
                ),
            ),
        )
    }

    val Usb: ImageVector by lazy {
        icon(
            "Usb",
            IconLine(12f, 21f, 12f, 6.5f),
            IconPolyline(listOf(9.2f to 5.8f, 12f to 8.6f, 14.8f to 5.8f)),
            IconCircle(12f, 4.6f, 2.1f),
            IconPolyline(listOf(12f to 15.4f, 16f to 11.8f)),
            IconCircle(16.8f, 11f, 2.1f),
        )
    }

    val Cloud: ImageVector by lazy {
        icon(
            "Cloud",
            IconRect(5.2f, 9.6f, 13.6f, 9f, 4.4f),
            IconCircle(9.6f, 10.4f, 3.4f),
            IconCircle(15f, 11.8f, 2.6f),
        )
    }

    val Pdf: ImageVector by lazy {
        icon(
            "Pdf",
            IconRect(4.5f, 2.5f, 15f, 19f, 2f),
            IconPolyline(listOf(15f to 2.5f, 15f to 7.5f, 19.5f to 7.5f)),
            IconRect(7.6f, 12.4f, 8.8f, 6.4f, 1.5f),
        )
    }

    val Canvas: ImageVector by lazy {
        icon(
            "Canvas",
            IconRect(3f, 4f, 18f, 16f, 2f),
            IconLine(3f, 8.4f, 21f, 8.4f),
            IconRect(5.5f, 11.2f, 7f, 6.4f, 1f),
            IconRect(14f, 11.2f, 4.5f, 6.4f, 1f),
        )
    }
}

/**
 * Every declared icon, in a stable order, with the shapes it was built from.
 *
 * [allIcons] forces each lazily built vector, so this reflects exactly what production renders.
 * Tests measure this geometry; it is never restated by hand, so a change to a glyph cannot leave the
 * checks silently describing the old drawing.
 */
internal fun iconCatalogue(): List<Pair<String, List<IconShape>>> {
    val touch = listOf(
        DosPrintIcons.Printer, DosPrintIcons.Minus, DosPrintIcons.Plus, DosPrintIcons.Document, DosPrintIcons.Image, DosPrintIcons.Text, DosPrintIcons.ArrowUpward, DosPrintIcons.ArrowDownward, DosPrintIcons.AspectRatio,
        DosPrintIcons.ContentCopy, DosPrintIcons.Flip, DosPrintIcons.Lock, DosPrintIcons.LockOpen, DosPrintIcons.RotateLeft, DosPrintIcons.RotateRight, DosPrintIcons.ClosePanel, DosPrintIcons.AlignTop,
        DosPrintIcons.AlignMiddle, DosPrintIcons.AlignBottom, DosPrintIcons.AlignLeft, DosPrintIcons.AlignCenter, DosPrintIcons.AlignRight, DosPrintIcons.Grid, DosPrintIcons.Layout, DosPrintIcons.Undo, DosPrintIcons.Redo,
        DosPrintIcons.Tune, DosPrintIcons.Wifi, DosPrintIcons.Bluetooth, DosPrintIcons.Usb, DosPrintIcons.Cloud, DosPrintIcons.Pdf, DosPrintIcons.Canvas,
    )
    return touch.map { it.name to declarations.getValue(it.name) }
}

private val declarations = LinkedHashMap<String, List<IconShape>>()

/**
 * Builds an outlined vector from shape data.
 *
 * [fill] is deliberately null. Every shape emits its own path, so even a filled vector would show
 * seams where shapes overlap; outlining avoids the question entirely and guarantees a glyph can
 * never merge into a solid block.
 */
private fun icon(name: String, vararg shapes: IconShape): ImageVector {
    declarations[name] = shapes.toList()
    return ImageVector.Builder(
        name = name,
        defaultWidth = IconSize,
        defaultHeight = IconSize,
        viewportWidth = VIEWPORT_SIZE,
        viewportHeight = VIEWPORT_SIZE,
    ).apply {
        shapes.forEach { shape ->
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = ICON_STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                emitShape(shape)
            }
        }
    }.build()
}

private fun PathBuilder.emitShape(shape: IconShape) {
    val points = shape.points
    if (points.size < 2) {
        return
    }
    moveTo(points.first().first, points.first().second)
    points.drop(1).forEach { (x, y) -> lineTo(x, y) }
}

internal fun polar(cx: Float, cy: Float, radius: Float, degrees: Float): Pair<Float, Float> {
    val radians = Math.toRadians(degrees.toDouble())
    return (cx + radius * cos(radians).toFloat()) to (cy + radius * sin(radians).toFloat())
}

/**
 * Samples a circular arc in a y-down space where 0 degrees is right and positive angles run
 * clockwise. Describing curves by angle rather than hand-placed control points is what keeps them
 * from collapsing or escaping the viewport.
 */
internal fun arcPoints(
    cx: Float,
    cy: Float,
    radius: Float,
    startDegrees: Float,
    sweepDegrees: Float,
): List<Pair<Float, Float>> {
    val steps = maxOf(2, ceil(abs(sweepDegrees) / ARC_STEP_DEGREES).toInt())
    return (0..steps).map { step ->
        polar(cx, cy, radius, startDegrees + sweepDegrees * step / steps)
    }
}

internal fun roundedRectPoints(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    corner: Float,
): List<Pair<Float, Float>> {
    val radius = min(corner, min(width, height) / 2f).coerceAtLeast(0f)
    val right = x + width
    val bottom = y + height
    if (radius <= MIN_CORNER) {
        return listOf(
            x to y,
            right to y,
            right to bottom,
            x to bottom,
            x to y,
        )
    }
    val cornerSteps = maxOf(2, ceil(90f / ARC_STEP_DEGREES).toInt())
    fun cornerArc(cx: Float, cy: Float, fromDegrees: Float): List<Pair<Float, Float>> =
        (1..cornerSteps).map { step ->
            polar(cx, cy, radius, fromDegrees + 90f * step / cornerSteps)
        }
    return buildList {
        add(x + radius to y)
        add(right - radius to y)
        addAll(cornerArc(right - radius, y + radius, -90f))
        add(right to bottom - radius)
        addAll(cornerArc(right - radius, bottom - radius, 0f))
        add(x + radius to bottom)
        addAll(cornerArc(x + radius, bottom - radius, 90f))
        add(x to y + radius)
        addAll(cornerArc(x + radius, y + radius, 180f))
        add(x + radius to y)
    }
}

internal fun segmentDistance(
    px: Float,
    py: Float,
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float,
): Float {
    val dx = bx - ax
    val dy = by - ay
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared <= MIN_LENGTH_SQUARED) {
        return hypot(px - ax, py - ay)
    }
    val t = min(1f, maxOf(0f, ((px - ax) * dx + (py - ay) * dy) / lengthSquared))
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}

private val IconSize = 24.dp
internal const val VIEWPORT_SIZE = 24f
internal const val ICON_STROKE_WIDTH = 1.8f
private const val ARC_STEP_DEGREES = 12f
private const val MIN_CORNER = 0.01f
private const val MIN_LENGTH_SQUARED = 1e-6f
