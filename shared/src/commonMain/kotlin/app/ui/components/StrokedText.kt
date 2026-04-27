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
 * Two-pass text: a black stroke layer beneath, the colored fill on top.
 * Looks like the Yu-Gi-Oh anime LP digits.
 *
 * Adds a small font-size-proportional right padding so italic glyph slant
 * (which extends past the typeface's measured advance) isn't clipped by
 * surrounding `clipToBounds()` containers.
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
    // ~20% of font size on the right for italic slant; ~5% on the left in case of
    // glyphs that extend slightly to the left of their layout box.
    val italicRightPad = with(density) { (style.fontSize.toPx() * 0.20f).toDp() }
    val italicLeftPad = with(density) { (style.fontSize.toPx() * 0.05f).toDp() }
    Box(
        modifier = modifier.padding(start = italicLeftPad, end = italicRightPad),
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
