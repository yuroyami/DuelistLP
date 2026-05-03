package app.ui.duel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ui.components.StrokedText
import app.ui.theme.DuelColors
import app.ui.theme.heuristicaItalicFamily
import androidx.compose.ui.text.TextStyle

/**
 * Floating "+N" / "−N" that pops over the LP box, hangs, then fades upward.
 * Signed delta: positive → green (HEAL), negative → red (ATTACK / SACRIFICE).
 *
 * Plays BEFORE the LP digit roll begins so the player reads the impact number
 * first. [onComplete] fires when the float finishes; the parent uses that to
 * gate the LP value flip + roll.
 *
 * Phase fractions of [FLOATING_DELTA_TOTAL_MS]:
 *   0–13%  punch in (0.4× → 1.30×)
 *   13–22% settle (1.30× → 1.05×)
 *   22–72% HANG (the long pause that gives the eye time to read)
 *   72–100% rise + fade
 */
@Composable
fun LpFloatingDelta(
    delta: Int,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isHeal = delta >= 0
    val color = if (isHeal) DuelColors.EmeraldHeal else DuelColors.Crimson
    val glow = if (isHeal) Color(0xFF8AF5C5) else Color(0xFFFF7A8C)
    val sign = if (isHeal) "+" else "−"
    val text = "$sign${kotlin.math.abs(delta)}"

    val phasePunchEnd = 0.13f      // 195 ms
    val phaseSettleEnd = 0.22f     // 130 ms
    val phaseHangEnd = 0.72f       // 750 ms hang
    // remainder = rise + fade (~420 ms)

    val progress = remember { Animatable(0f) }
    LaunchedEffect(delta) {
        progress.snapTo(0f)
        progress.animateTo(
            phasePunchEnd,
            animationSpec = tween((FLOATING_DELTA_TOTAL_MS * phasePunchEnd).toInt()),
        )
        progress.animateTo(
            phaseSettleEnd,
            animationSpec = tween((FLOATING_DELTA_TOTAL_MS * (phaseSettleEnd - phasePunchEnd)).toInt()),
        )
        progress.animateTo(
            phaseHangEnd,
            animationSpec = tween((FLOATING_DELTA_TOTAL_MS * (phaseHangEnd - phaseSettleEnd)).toInt()),
        )
        progress.animateTo(
            1f,
            animationSpec = tween((FLOATING_DELTA_TOTAL_MS * (1f - phaseHangEnd)).toInt()),
        )
        onComplete()
    }

    val p = progress.value
    val scale = when {
        p < phasePunchEnd -> 0.4f + (1.30f - 0.4f) * (p / phasePunchEnd)
        p < phaseSettleEnd -> 1.30f - (1.30f - 1.05f) * ((p - phasePunchEnd) / (phaseSettleEnd - phasePunchEnd))
        p < phaseHangEnd -> 1.05f
        else -> 1.05f + 0.08f * ((p - phaseHangEnd) / (1f - phaseHangEnd))
    }
    val translateYDp = when {
        p < phaseHangEnd -> 0f
        else -> -110f * ((p - phaseHangEnd) / (1f - phaseHangEnd))
    }
    val alpha = when {
        p < phaseHangEnd -> 1f
        else -> 1f - ((p - phaseHangEnd) / (1f - phaseHangEnd))
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .alpha(alpha)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = translateYDp * density
                },
        ) {
            // Glow halo (slightly larger faded layer behind the main text).
            StrokedText(
                text = text,
                style = floatingStyle(74),
                fillColor = glow.copy(alpha = 0.55f),
                strokeColor = glow.copy(alpha = 0.0f),
                strokeWidth = 0.dp,
            )
            StrokedText(
                text = text,
                style = floatingStyle(64),
                fillColor = color,
                strokeColor = Color.Black,
                strokeWidth = 3.dp,
            )
        }
    }
}

private fun floatingStyle(sizeSp: Int) = TextStyle(
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Black,
    fontSize = sizeSp.sp,
)

/** Total floating-delta duration. The LP roll starts after this completes. */
const val FLOATING_DELTA_TOTAL_MS: Long = 1500L
