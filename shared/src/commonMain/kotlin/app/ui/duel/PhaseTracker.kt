package app.ui.duel

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.model.DuelPhase
import app.ui.theme.DuelColors
import app.ui.theme.DuelTheme

/**
 * Slim horizontal phase track at the top of the active [PlayerHalf]. Three
 * visual layers stacked:
 *
 *   1. A faint baseline (full-width hairline)
 *   2. A bright "progress" segment from x=0 up to the centre of the current
 *      label (animated when phase changes)
 *   3. Six labels in a Row: shortcode + dot indicator. Current label is bright
 *      and slightly larger; past are muted; future are faint outlines.
 *
 * The whole tracker locks itself to a fixed 32dp height so it never expands
 * vertically inside a flexible parent (the previous bug: the labels' modifier
 * chain mixed `fillMaxHeight()` with a fixed inner `height(28.dp)`; the outer
 * modifier won and the pills swallowed the entire half).
 *
 * Battle Phase gets the crimson treatment via [phaseAccent]/[phaseGlow]; the
 * other five phases share the gold/blue/purple palette.
 */
@Composable
fun PhaseTracker(
    currentPhase: DuelPhase,
    modifier: Modifier = Modifier,
) {
    val ordered = DuelPhase.entries.toList()
    val currentIndex = ordered.indexOf(currentPhase)

    val pulseTransition = rememberInfiniteTransition(label = "phasePulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Reverse),
        label = "pulseAlpha",
    )

    // Animate the progress bar position so it slides between phases.
    val progressTarget = (currentIndex + 0.5f) / ordered.size
    val progress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(360, easing = EaseOutCubic),
        label = "phaseProgress",
    )

    val accent = phaseAccent(currentPhase)
    val glow = phaseGlow(currentPhase)
    val d = DuelTheme.dimens

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(d.phaseTrackerHeight),
    ) {
        // Layer 1: baseline rail.
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(d.s2)
                .align(Alignment.BottomCenter),
        ) {
            drawLine(
                color = DuelColors.DuelGold.copy(alpha = 0.30f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.5f,
            )
        }

        // Layer 2: progress fill — a thicker glowing line from the left to
        // the current label's centre. Drawn full width via Canvas so we can
        // compute the gradient + glow consistently.
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(d.s4)
                .align(Alignment.BottomCenter),
        ) {
            val w = size.width * progress
            // Soft halo behind the line.
            drawLine(
                color = glow.copy(alpha = 0.35f * pulseAlpha),
                start = Offset(0f, size.height / 2f),
                end = Offset(w, size.height / 2f),
                strokeWidth = 6f,
            )
            // Crisp bright line.
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(accent.copy(alpha = 0.85f), glow),
                    startX = 0f,
                    endX = w.coerceAtLeast(1f),
                ),
                start = Offset(0f, size.height / 2f),
                end = Offset(w, size.height / 2f),
                strokeWidth = 2.5f,
            )
            // Bright dot at the leading edge (the "playhead").
            drawCircle(
                color = glow,
                radius = 4f,
                center = Offset(w, size.height / 2f),
            )
            drawCircle(
                color = glow.copy(alpha = 0.35f * pulseAlpha),
                radius = 8f,
                center = Offset(w, size.height / 2f),
            )
        }

        // Layer 3: phase labels, equally weighted across the row.
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(d.s2),
        ) {
            ordered.forEachIndexed { i, phase ->
                PhaseLabel(
                    phase = phase,
                    isCurrent = i == currentIndex,
                    isPast = i < currentIndex,
                    pulseAlpha = if (i == currentIndex) pulseAlpha else 1f,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PhaseLabel(
    phase: DuelPhase,
    isCurrent: Boolean,
    isPast: Boolean,
    pulseAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val d = DuelTheme.dimens
    val accent = phaseAccent(phase)
    val glow = phaseGlow(phase)
    val textColor = when {
        isCurrent -> glow
        isPast -> accent.copy(alpha = 0.70f)
        else -> DuelColors.DuelGoldGlow.copy(alpha = 0.30f)
    }
    val fontSize = if (isCurrent) d.textSmall else d.textMicro
    val weight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold

    val scale by animateFloatAsState(
        targetValue = if (isCurrent) 1.08f else 1f,
        animationSpec = tween(220, easing = EaseOutCubic),
        label = "labelScale",
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = phase.shortCode,
                color = textColor,
                fontSize = fontSize,
                fontWeight = weight,
                letterSpacing = d.trackNormal,
                modifier = Modifier.clip(RoundedCornerShape(d.radiusPill)),
            )
            Spacer(Modifier.height(if (isCurrent) d.s2 + 1.dp else d.s4 + 1.dp))
            // Marker dot — sits just above the baseline.
            Canvas(modifier = Modifier.size(if (isCurrent) d.s8 - 1.dp else d.s6 - 1.dp)) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                when {
                    isCurrent -> {
                        drawCircle(color = glow.copy(alpha = 0.30f * pulseAlpha), radius = r * 1.8f * scale, center = c)
                        drawCircle(color = glow, radius = r * scale, center = c)
                        drawCircle(color = Color.White.copy(alpha = 0.6f), radius = r * 0.35f * scale, center = c)
                    }
                    isPast -> {
                        drawCircle(color = accent.copy(alpha = 0.85f), radius = r, center = c)
                    }
                    else -> {
                        drawCircle(
                            color = DuelColors.DuelGoldGlow.copy(alpha = 0.30f),
                            radius = r * 0.85f,
                            center = c,
                            style = Stroke(width = 1f),
                        )
                    }
                }
            }
        }
    }
}

internal fun phaseAccent(phase: DuelPhase): Color = when (phase) {
    DuelPhase.DrawPhase -> DuelColors.DuelGold
    DuelPhase.StandbyPhase -> Color(0xFFC9C2A6)
    DuelPhase.MainPhase1 -> Color(0xFF5B8BE0)
    DuelPhase.BattlePhase -> DuelColors.Crimson
    DuelPhase.MainPhase2 -> Color(0xFF5B8BE0)
    DuelPhase.EndPhase -> Color(0xFF8E6FD3)
}

internal fun phaseGlow(phase: DuelPhase): Color = when (phase) {
    DuelPhase.DrawPhase -> DuelColors.DuelGoldGlow
    DuelPhase.StandbyPhase -> Color(0xFFFFF6D6)
    DuelPhase.MainPhase1 -> Color(0xFFA8C6FF)
    DuelPhase.BattlePhase -> Color(0xFFFF6A7C)
    DuelPhase.MainPhase2 -> Color(0xFFA8C6FF)
    DuelPhase.EndPhase -> Color(0xFFD2B9FF)
}
