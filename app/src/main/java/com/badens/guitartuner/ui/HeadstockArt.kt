package com.badens.guitartuner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import com.badens.guitartuner.tuning.GuitarString
import com.badens.guitartuner.ui.theme.TunerColors
import kotlin.math.pow

/**
 * Layout of the tuner art in virtual units. The whole drawing is [WIDTH] x [HEIGHT] units and is
 * scaled uniformly to fit the screen; the composables placed over it use the same coordinates.
 */
internal object Headstock {
    const val WIDTH = 360f
    const val HEIGHT = 560f
    const val CENTER_X = 180f

    /** The cents track the bubble slides along: ±[TRACK_CENTS] maps to ±[TRACK_HALF_WIDTH]. */
    const val TRACK_Y = 48f
    const val TRACK_HALF_WIDTH = 120f
    const val TRACK_CENTS = 50f

    const val BUBBLE_RADIUS = 34f
    const val BUTTON_RADIUS = 26f
    const val CROWN_Y = 168f
    const val NUT_Y = 482f

    /** Where the bubble sits for an offset; clamps at the track's ends. */
    fun bubbleX(cents: Double): Float =
        CENTER_X + cents.toFloat().coerceIn(-TRACK_CENTS, TRACK_CENTS) / TRACK_CENTS * TRACK_HALF_WIDTH

    /** One string's tuning post, tuner key, button and path down to the nut. */
    class Spot(
        val postX: Float,
        val y: Float,
        val nutX: Float,
        val leftSide: Boolean,
        val gauge: Float,
        val wound: Boolean,
    ) {
        val keyX: Float get() = if (leftSide) 80f else 280f
        val buttonX: Float get() = if (leftSide) 32f else 328f
    }

    /** 3+3 layout as on the reference: D A E down the left, G B E down the right. */
    val spots: Map<GuitarString, Spot> = linkedMapOf(
        GuitarString.D to Spot(136f, 240f, 174.8f, leftSide = true, gauge = 1.7f, wound = true),
        GuitarString.A to Spot(136f, 312f, 164.4f, leftSide = true, gauge = 2.0f, wound = true),
        GuitarString.LOW_E to Spot(136f, 384f, 154.0f, leftSide = true, gauge = 2.4f, wound = true),
        GuitarString.G to Spot(224f, 240f, 185.2f, leftSide = false, gauge = 1.4f, wound = false),
        GuitarString.B to Spot(224f, 312f, 195.6f, leftSide = false, gauge = 1.1f, wound = false),
        GuitarString.HIGH_E to Spot(224f, 384f, 206.0f, leftSide = false, gauge = 0.9f, wound = false),
    )
}

/**
 * Draws the cents track, the stem from the bubble, and the headstock with its pegs and strings.
 * The bubble and string buttons are separate composables layered on top.
 *
 * The art is [Headstock.WIDTH] units wide at [unit] per unit, centred horizontally and
 * top-aligned; the neck continues to the bottom of the canvas, however tall it is.
 *
 * @param arrow 1 draws a chevron right of the bubble pointing right (tune up), -1 one on the
 *   left pointing left (tune down), 0 none.
 */
@Composable
internal fun HeadstockArt(
    activeString: GuitarString?,
    highlight: Color,
    bubbleX: () -> Float,
    arrow: Int,
    unit: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.clipToBounds()) {
        val k = unit.toPx()
        val left = (size.width - Headstock.WIDTH * k) / 2f
        val bottom = size.height / k
        val x = bubbleX()
        translate(left = left) {
            scale(k, pivot = Offset.Zero) {
                drawTrack()
                drawStem(x, highlight)
                if (arrow != 0) drawArrow(x, arrow)
                drawKeys()
                drawBody(bottom)
                drawStrings(activeString, highlight, bottom)
                drawPosts(activeString, highlight)
            }
        }
    }
}

private val Wood = Brush.verticalGradient(
    0f to Color(0xFF8E4B27),
    0.6f to Color(0xFF6E3519),
    1f to Color(0xFF552812),
    startY = Headstock.CROWN_Y,
    endY = Headstock.NUT_Y,
)
private val EdgeShade = Brush.horizontalGradient(
    0f to Color.Black.copy(alpha = 0.38f),
    0.22f to Color.Transparent,
    0.78f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.38f),
    startX = 104f,
    endX = 256f,
)
private val Binding = Color(0xFFEFE3C8)
private val Fretboard = Color(0xFF2A1A11)
private val NutColor = Color(0xFFEDE4D0)
private val PlainString = Color(0xFFD5D7D9)
private val WoundString = Color(0xFFCDBB94)
private val MetalEdge = Color(0xFF4F5357)
private val StemColor = Color(0xFF8E9194)

/** Nut-to-bridge length in units; sets fret spacing (fret n sits at L * (1 - 2^(-n/12))). */
private const val SCALE_LENGTH = 1070f

private fun fretY(n: Int): Float =
    Headstock.NUT_Y + SCALE_LENGTH * (1f - 2f.pow(-n / 12f))

private fun headstockPath(canvasBottom: Float): Path = Path().apply {
    val bottom = canvasBottom + 10f
    moveTo(148f, bottom)
    lineTo(148f, 486f)
    cubicTo(148f, 462f, 112f, 452f, 108f, 420f) // flare out from the neck
    cubicTo(104f, 360f, 106f, 280f, 104f, 216f) // left side
    cubicTo(103f, 200f, 110f, 192f, 124f, 190f) // top-left corner
    cubicTo(146f, 188f, 158f, 172f, 180f, Headstock.CROWN_Y) // up to the crown
    cubicTo(202f, 172f, 214f, 188f, 236f, 190f)
    cubicTo(250f, 192f, 257f, 200f, 256f, 216f) // top-right corner
    cubicTo(254f, 280f, 256f, 360f, 252f, 420f) // right side
    cubicTo(248f, 452f, 212f, 462f, 212f, 486f) // flare into the neck
    lineTo(212f, bottom)
    close()
}

private fun DrawScope.drawTrack() {
    val y = Headstock.TRACK_Y
    val zoneStart = Headstock.bubbleX(-5.0)
    drawRoundRect(
        color = TunerColors.InTune.copy(alpha = 0.16f),
        topLeft = Offset(zoneStart, y - 12f),
        size = Size(Headstock.bubbleX(5.0) - zoneStart, 24f),
        cornerRadius = CornerRadius(4f),
    )
    drawLine(
        color = TunerColors.TextMuted,
        start = Offset(Headstock.bubbleX(-50.0), y),
        end = Offset(Headstock.bubbleX(50.0), y),
        strokeWidth = 1.5f,
    )
    for (cents in -50..50 step 10) {
        val x = Headstock.bubbleX(cents.toDouble())
        val half = when {
            cents == 0 -> 14f
            cents % 50 == 0 -> 10f
            else -> 6f
        }
        drawLine(
            color = if (cents == 0) TunerColors.TextSecondary else TunerColors.TextMuted,
            start = Offset(x, y - half),
            end = Offset(x, y + half),
            strokeWidth = 1.5f,
        )
    }
}

private fun DrawScope.drawStem(bubbleX: Float, color: Color) {
    val bubbleBottom = Headstock.TRACK_Y + Headstock.BUBBLE_RADIUS
    drawLine(
        color = StemColor,
        start = Offset(bubbleX, bubbleBottom + 6f),
        end = Offset(Headstock.CENTER_X, Headstock.CROWN_Y + 2f),
        strokeWidth = 1.5f,
    )
    val pointer = Path().apply {
        moveTo(bubbleX - 6f, bubbleBottom - 2f)
        lineTo(bubbleX + 6f, bubbleBottom - 2f)
        lineTo(bubbleX, bubbleBottom + 7f)
        close()
    }
    drawPath(pointer, color)
}

private fun DrawScope.drawArrow(bubbleX: Float, direction: Int) {
    val x = bubbleX + direction * (Headstock.BUBBLE_RADIUS + 16f)
    val y = Headstock.TRACK_Y
    val chevron = Path().apply {
        moveTo(x - 5f * direction, y - 8f)
        lineTo(x + 3f * direction, y)
        lineTo(x - 5f * direction, y + 8f)
    }
    drawPath(
        chevron,
        TunerColors.Off,
        style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun DrawScope.drawKeys() {
    for (spot in Headstock.spots.values) {
        val y = spot.y
        val shaftStart = if (spot.leftSide) spot.keyX + 12f else 248f
        val shaftEnd = if (spot.leftSide) 112f else spot.keyX - 12f
        drawRect(Color(0xFF9EA2A6), Offset(shaftStart, y - 3.5f), Size(shaftEnd - shaftStart, 7f))
        val topLeft = Offset(spot.keyX - 15f, y - 12f)
        val keySize = Size(30f, 24f)
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFFF8F8F8),
                0.35f to Color(0xFFC3C6C9),
                0.7f to Color(0xFF6E7276),
                1f to Color(0xFFD6D8DA),
                startY = y - 12f,
                endY = y + 12f,
            ),
            topLeft = topLeft,
            size = keySize,
            cornerRadius = CornerRadius(11f),
        )
        drawRoundRect(MetalEdge, topLeft, keySize, CornerRadius(11f), style = Stroke(1f))
    }
}

private fun DrawScope.drawBody(canvasBottom: Float) {
    val body = headstockPath(canvasBottom)
    drawPath(body, Wood)
    drawPath(body, EdgeShade)
    drawRect(Fretboard, Offset(150f, Headstock.NUT_Y), Size(60f, canvasBottom - Headstock.NUT_Y + 10f))
    var fret = 1
    while (fretY(fret - 1) < canvasBottom && fret <= 24) {
        if (fret in MARKER_FRETS) {
            val y = (fretY(fret - 1) + fretY(fret)) / 2f
            if (fret == 12) {
                drawCircle(FretMarker, 3.5f, Offset(Headstock.CENTER_X - 12f, y))
                drawCircle(FretMarker, 3.5f, Offset(Headstock.CENTER_X + 12f, y))
            } else {
                drawCircle(FretMarker, 3.5f, Offset(Headstock.CENTER_X, y))
            }
        }
        drawLine(FretWire, Offset(150f, fretY(fret)), Offset(210f, fretY(fret)), strokeWidth = 2.5f)
        fret++
    }
    drawPath(body, Binding, style = Stroke(width = 3f))
    drawRect(NutColor, Offset(147f, Headstock.NUT_Y - 6f), Size(66f, 8f))
}

private val MARKER_FRETS = setOf(3, 5, 7, 9, 12, 15, 17, 19, 21)
private val FretWire = Color(0xFFBFB6A5)
private val FretMarker = Color(0xFFE6DFCF)

private fun DrawScope.drawStrings(activeString: GuitarString?, highlight: Color, canvasBottom: Float) {
    for ((string, spot) in Headstock.spots) {
        val active = string == activeString
        val color = when {
            active -> highlight
            spot.wound -> WoundString
            else -> PlainString
        }
        val post = Offset(spot.postX, spot.y)
        val nut = Offset(spot.nutX, Headstock.NUT_Y)
        val end = Offset(spot.nutX, canvasBottom + 10f)
        if (active) {
            drawLine(highlight.copy(alpha = 0.28f), post, nut, strokeWidth = spot.gauge + 6f, cap = StrokeCap.Round)
            drawLine(highlight.copy(alpha = 0.28f), nut, end, strokeWidth = spot.gauge + 6f)
        }
        val width = if (active) spot.gauge + 0.8f else spot.gauge
        drawLine(color, post, nut, strokeWidth = width, cap = StrokeCap.Round)
        drawLine(color, nut, end, strokeWidth = width)
    }
}

private fun DrawScope.drawPosts(activeString: GuitarString?, highlight: Color) {
    for ((string, spot) in Headstock.spots) {
        val center = Offset(spot.postX, spot.y)
        if (string == activeString) {
            drawCircle(highlight, radius = 13f, center = center, style = Stroke(2f))
        }
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White, Color(0xFFC9CCCF), Color(0xFF7C8085)),
                center = center + Offset(-2.5f, -2.5f),
                radius = 11f,
            ),
            radius = 9f,
            center = center,
        )
        drawCircle(MetalEdge, radius = 9f, center = center, style = Stroke(1f))
        drawCircle(Color(0xFF3A3C3E), radius = 2.5f, center = center)
    }
}
