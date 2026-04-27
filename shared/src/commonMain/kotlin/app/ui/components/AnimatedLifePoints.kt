package app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import app.ui.theme.DuelColors

/**
 * LP digits with a slot-machine vertical roll per digit. Each digit lives in
 * its own clipped slot, so the slide-in / slide-out always stays inside the
 * digit's own column — never bleeds into the LP box edge.
 *
 * Padded to [minDigits] so the value visibly retains its place when it shrinks
 * (e.g. 1000 → 999 keeps four columns).
 */
@Composable
fun AnimatedLifePoints(
    value: Int,
    style: TextStyle,
    strokeWidth: Dp,
    modifier: Modifier = Modifier,
    fillColor: Color = DuelColors.LpYellow,
    strokeColor: Color = DuelColors.LpStroke,
    minDigits: Int = 4,
) {
    var previous by remember { mutableStateOf(value) }
    val direction = when {
        value > previous -> 1   // increase: new digit slides in from BELOW
        value < previous -> -1  // decrease: new digit slides in from ABOVE
        else -> 0
    }
    SideEffect { previous = value }

    val raw = value.coerceAtLeast(0).toString()
    val digits = if (raw.length >= minDigits) raw else "0".repeat(minDigits - raw.length) + raw

    Row(modifier = modifier) {
        digits.forEachIndexed { idx, ch ->
            DigitSlot(
                digit = ch,
                style = style,
                fillColor = fillColor,
                strokeColor = strokeColor,
                strokeWidth = strokeWidth,
                direction = direction,
                slotKey = "lp_$idx",
            )
        }
    }
}

@Composable
private fun DigitSlot(
    digit: Char,
    style: TextStyle,
    fillColor: Color,
    strokeColor: Color,
    strokeWidth: Dp,
    direction: Int,
    slotKey: String,
) {
    AnimatedContent(
        targetState = digit,
        transitionSpec = {
            val d = if (direction == 0) 1 else direction
            // Enter from below (+) when increasing, from above (−) when decreasing.
            val enter = slideInVertically(animationSpec = tween(360)) { full -> full * d } +
                fadeIn(animationSpec = tween(220))
            val exit = slideOutVertically(animationSpec = tween(360)) { full -> -full * d } +
                fadeOut(animationSpec = tween(220))
            enter togetherWith exit
        },
        modifier = Modifier.clipToBounds(),
        label = slotKey,
    ) { ch ->
        StrokedText(
            text = ch.toString(),
            style = style,
            fillColor = fillColor,
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
        )
    }
}
