package app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

/**
 * Anime-style "inked" text — black stroke beneath, fill on top. Adds symmetric
 * horizontal padding (~12% of font size) so the italic glyph's right-side
 * slant overhang doesn't shift the layout box's center off the visual center
 * — important for stacked rotated copies in [app.ui.duel.LpBox] to align.
 */
@Composable
fun StrokedText(
    text: String,
    style: TextStyle,
    fillColor: Color,
    strokeColor: Color,
    strokeWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val widthPx = with(density) { strokeWidth.toPx() }
    // Symmetric pad so the layout box's center matches the visual glyph
    // center even when the right side has italic slant overhang.
    val italicPad = with(density) { (style.fontSize.toPx() * 0.12f).toDp() }
    Box(
        modifier = modifier.padding(start = italicPad, end = italicPad),
    ) {
        Text(
            text = text,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            style = style.copy(
                color = strokeColor,
                drawStyle = Stroke(width = widthPx, miter = 4f, join = StrokeJoin.Round),
            ),
        )
        Text(
            text = text,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            style = style.copy(color = fillColor),
        )
    }
}
