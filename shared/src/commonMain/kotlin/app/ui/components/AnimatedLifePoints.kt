package app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import app.ui.theme.DuelColors
import kotlin.math.floor

/**
 * LP digits as a geared odometer.
 *
 * One float [Animatable] interpolates the LP value with [EaseOutCubic]. Each
 * digit slot displays the integer digit at `floor(current) / 10^p mod 10`,
 * so a resting value of 7800 cleanly shows a "7" in the thousands slot, not
 * "80% of the way from 7 to 8". A slot only animates a transition when its
 * digit value actually differs between `floor(current)` and `ceil(current)`
 * — i.e., at the natural odometer "carry" moments.
 *
 * Direction-symmetric: the visual at any given fractional value is identical
 * regardless of whether the animation is going up or down, so no direction
 * bookkeeping is needed.
 *
 * Speed effect: lower digits transition often, upper digits rarely. For a
 * 3000-LP swing in ~3 s, the ones digit blurs (a transition every ms), the
 * hundreds visibly tick down, and the thousands take a single deliberate
 * step. EaseOutCubic slows the whole thing at the end so the lower digits
 * resolve from blur → visible cycling → clean stop, like a slot machine
 * settling.
 *
 * Mid-animation re-targeting via [Animatable.animateTo] continues smoothly
 * from the current visual position — no snaps if a new LP change arrives
 * before the previous one finishes.
 *
 * Renders only the digits the value actually needs (no invisible padding
 * zeros), so the row's visual centre matches its layout centre. Layout will
 * reflow at power-of-10 crossings (4 → 5 digits at value 10000 etc.); the
 * caller decides how to absorb that — [app.ui.duel.LpBox] sizes its font
 * for the worst-case digit count so reflow stays inside the card.
 *
 * @param value target LP to animate toward
 * @param durationMs total animation length; clamped to a 120 ms minimum
 * @param stepMagnitude unused, kept for caller API stability
 */
@Composable
fun AnimatedLifePoints(
    value: Int,
    style: TextStyle,
    strokeWidth: Dp,
    modifier: Modifier = Modifier,
    fillColor: Color = DuelColors.LpYellow,
    strokeColor: Color = DuelColors.LpStroke,
    durationMs: Int = 360,
    @Suppress("UNUSED_PARAMETER") stepMagnitude: Int = 0,
) {
    // Updated only on animation completion so re-renders mid-flight don't
    // disturb in-progress motion.
    var landedValue by remember { mutableIntStateOf(value) }
    val animatedValue = remember { Animatable(value.toFloat()) }

    LaunchedEffect(value) {
        if (value == landedValue && animatedValue.value == value.toFloat()) return@LaunchedEffect
        animatedValue.animateTo(
            targetValue = value.toFloat(),
            animationSpec = tween(
                durationMillis = durationMs.coerceAtLeast(120),
                easing = EaseOutCubic,
            ),
        )
        landedValue = value
    }

    val current = animatedValue.value.coerceAtLeast(0f)
    // Decompose into the integer pair we're transitioning between (vBelow,
    // vAbove) plus the fractional progress through that one-unit step.
    val vBelow = floor(current).toInt()
    val vAbove = vBelow + 1
    val sub = current - vBelow

    // displayLen tracks the larger of landed-vs-target so layout doesn't
    // shrink mid-animation. After landing, it matches the new value.
    val displayLen = maxOf(digitCount(landedValue), digitCount(value), 1)

    Row(modifier = modifier) {
        // Slots ordered MSB → LSB. `p` is this slot's power-of-10 weight.
        for (i in 0 until displayLen) {
            val p = displayLen - 1 - i
            val divisor = pow10Int(p)
            val digitBelow = (vBelow / divisor) % 10
            val digitAbove = (vAbove / divisor) % 10

            DigitSlot(
                digitBelow = digitBelow,
                digitAbove = digitAbove,
                // Pin sub to 0 for static digits — otherwise the same glyph
                // would render twice with offsets, producing a half-and-half
                // visual cut through the centre.
                sub = if (digitBelow == digitAbove) 0f else sub,
                style = style,
                fillColor = fillColor,
                strokeColor = strokeColor,
                strokeWidth = strokeWidth,
            )
        }
    }
}

/**
 * One digit slot. Renders [digitBelow] and [digitAbove] in a clipped,
 * one-glyph-tall window, vertically offset so the slot scrolls smoothly
 * across the inter-integer transition.
 *
 * Layout: at sub=0, below sits centered (y=0) and above is parked one slot
 * height down (y=h, off-screen). As sub climbs to 1, both shift upward by
 * one slot height — below exits through the top, above slides into the
 * centered position. The next render samples the new floor and starts over.
 */
@Composable
private fun DigitSlot(
    digitBelow: Int,
    digitAbove: Int,
    sub: Float,
    style: TextStyle,
    fillColor: Color,
    strokeColor: Color,
    strokeWidth: Dp,
) {
    Layout(
        modifier = Modifier.clipToBounds(),
        content = {
            StrokedText(digitBelow.toString(), style, fillColor, strokeColor, strokeWidth)
            StrokedText(digitAbove.toString(), style, fillColor, strokeColor, strokeWidth)
        },
    ) { measurables, constraints ->
        val below = measurables[0].measure(constraints)
        val above = measurables[1].measure(constraints)
        val height = below.height
        val width = below.width
        layout(width, height) {
            below.place(0, (-sub * height).toInt())
            above.place(0, ((1f - sub) * height).toInt())
        }
    }
}

private fun digitCount(n: Int): Int = n.coerceAtLeast(0).toString().length

private fun pow10Int(n: Int): Int {
    var r = 1
    repeat(n) { r *= 10 }
    return r
}
