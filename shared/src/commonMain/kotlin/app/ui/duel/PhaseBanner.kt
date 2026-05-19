package app.ui.duel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.model.DuelPhase
import app.model.PlayerSlot
import app.ui.components.StrokedText
import app.ui.theme.DuelColors
import app.ui.theme.DuelTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-screen phase banner. Plays a multi-stage entrance animation each time
 * [phase] (keyed by [phaseChangeKey]) changes:
 *
 *   1. Scrim fades in (0 → 0.4 alpha, 120 ms).
 *   2. Light rays sweep out from the centre (scale 0 → 1.6, fade 0 → 0.55,
 *      350 ms with EaseOutCubic).
 *   3. Phase title slides in from the left, scales 0.4 → 1, alpha 0 → 1
 *      (EaseOutBack, 380 ms).
 *   4. Phase tagline fades in below (short delay + 180 ms).
 *   5. Hold ~700 ms.
 *   6. Title scales 1 → 1.12, alpha 1 → 0; scrim fades out (260 ms).
 *
 * Battle Phase gets the crimson treatment (red rays, bigger title); other
 * phases share the gold/blue/purple palette from [phaseAccent]/[phaseGlow].
 *
 * The banner content is rotated 180° when [activeSlot] is P2 so the upright
 * reading orientation matches the active player's seat.
 */
@Composable
fun PhaseBanner(
    phase: DuelPhase,
    activeSlot: PlayerSlot,
    phaseChangeKey: Int,
    modifier: Modifier = Modifier,
) {
    val scrim = remember { Animatable(0f) }
    val titleScale = remember { Animatable(0.4f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleX = remember { Animatable(-260f) }
    val taglineAlpha = remember { Animatable(0f) }
    val raysScale = remember { Animatable(0f) }
    val raysAlpha = remember { Animatable(0f) }
    val raysSpin = remember { Animatable(0f) }

    LaunchedEffect(phaseChangeKey) {
        // Reset
        scrim.snapTo(0f); titleScale.snapTo(0.4f); titleAlpha.snapTo(0f); titleX.snapTo(-260f)
        taglineAlpha.snapTo(0f); raysScale.snapTo(0f); raysAlpha.snapTo(0f); raysSpin.snapTo(0f)

        // Entrance
        scrim.animateTo(0.42f, tween(140))
    }
    LaunchedEffect(phaseChangeKey) {
        raysScale.animateTo(1.6f, tween(420, easing = EaseOutCubic))
    }
    LaunchedEffect(phaseChangeKey) {
        raysAlpha.animateTo(0.55f, tween(180))
        raysAlpha.animateTo(0f, tween(540, easing = EaseInCubic))
    }
    LaunchedEffect(phaseChangeKey) {
        raysSpin.animateTo(35f, tween(900, easing = EaseOutCubic))
    }
    LaunchedEffect(phaseChangeKey) {
        delay(40)
        // Title slides in from the left + scales up + fades in concurrently.
        titleX.animateTo(0f, tween(420, easing = EaseOutBack))
    }
    LaunchedEffect(phaseChangeKey) {
        delay(40)
        titleScale.animateTo(1f, tween(380, easing = EaseOutBack))
    }
    LaunchedEffect(phaseChangeKey) {
        delay(40)
        titleAlpha.animateTo(1f, tween(220))
    }
    LaunchedEffect(phaseChangeKey) {
        delay(260)
        taglineAlpha.animateTo(1f, tween(220))
    }
    LaunchedEffect(phaseChangeKey) {
        // Hold then exit.
        delay(1000)
        titleScale.animateTo(1.12f, tween(260, easing = EaseInCubic))
    }
    LaunchedEffect(phaseChangeKey) {
        delay(1000)
        titleAlpha.animateTo(0f, tween(260))
        taglineAlpha.animateTo(0f, tween(220))
        scrim.animateTo(0f, tween(280))
    }

    // Don't render anything once scrim has fully faded.
    if (scrim.value <= 0.001f) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrim.value)),
        contentAlignment = Alignment.Center,
    ) {
        val accent = phaseAccent(phase)
        val glow = phaseGlow(phase)
        val isBattle = phase == DuelPhase.BattlePhase

        // Rotated wrapper so the banner reads upright for the active player.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (activeSlot == PlayerSlot.P2) Modifier.rotate(180f) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            // Rays — radial gradient burst behind the title.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(raysAlpha.value),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = raysScale.value
                            scaleY = raysScale.value
                            rotationZ = raysSpin.value
                        },
                ) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxR = minOf(size.width, size.height) * 0.55f
                    // 16 spokes radiating out, alternating long/short.
                    for (i in 0 until 16) {
                        val angle = (i / 16.0) * PI * 2.0
                        val len = if (i % 2 == 0) maxR else maxR * 0.65f
                        val x2 = cx + (cos(angle) * len).toFloat()
                        val y2 = cy + (sin(angle) * len).toFloat()
                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(glow.copy(alpha = 0.85f), Color.Transparent),
                                start = Offset(cx, cy),
                                end = Offset(x2, y2),
                            ),
                            start = Offset(cx, cy),
                            end = Offset(x2, y2),
                            strokeWidth = if (i % 2 == 0) 12f else 6f,
                        )
                    }
                    // Inner glow disc
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(glow.copy(alpha = 0.5f), Color.Transparent),
                            center = Offset(cx, cy),
                            radius = maxR * 0.5f,
                        ),
                        radius = maxR * 0.5f,
                        center = Offset(cx, cy),
                    )
                }
            }

            val d = DuelTheme.dimens

            // Title card.
            Box(
                modifier = Modifier
                    .padding(horizontal = d.s24)
                    .graphicsLayer {
                        translationX = titleX.value
                        scaleX = titleScale.value
                        scaleY = titleScale.value
                        alpha = titleAlpha.value
                    },
                contentAlignment = Alignment.Center,
            ) {
                BannerTitle(phase = phase, accent = accent, glow = glow, big = isBattle)
            }

            // Tagline below the title.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = d.s32 * 4.5f),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .alpha(taglineAlpha.value)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(d.s24))
                        .border(d.borderHairline, glow.copy(alpha = 0.6f), RoundedCornerShape(d.s24))
                        .padding(horizontal = d.s16 + d.s2, vertical = d.s8),
                ) {
                    Text(
                        text = phaseTagline(phase),
                        color = glow,
                        fontSize = d.textTiny,
                        letterSpacing = d.trackExtraWide,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun BannerTitle(phase: DuelPhase, accent: Color, glow: Color, big: Boolean) {
    val baseSp = if (big) 58 else 50
    Box(contentAlignment = Alignment.Center) {
        // Backdrop bar.
        Box(
            modifier = Modifier
                .height((baseSp * 1.55f).dp)
                .fillMaxWidth(0.86f)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            accent.copy(alpha = 0f),
                            accent.copy(alpha = 0.5f),
                            accent.copy(alpha = 0.5f),
                            accent.copy(alpha = 0f),
                        ),
                    ),
                ),
        )
        // Top and bottom hairline rules.
        Box(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .height(2.dp)
                .background(glow.copy(alpha = 0.85f))
                .padding(top = (baseSp * 1.5f).dp),
        )
        StrokedText(
            text = phase.displayName,
            style = TextStyle(
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Black,
                fontSize = baseSp.sp,
                letterSpacing = 3.sp,
            ),
            fillColor = glow,
            strokeColor = DuelColors.LpStroke,
            strokeWidth = (baseSp / 22f).dp,
        )
    }
}

private fun phaseTagline(phase: DuelPhase): String = when (phase) {
    DuelPhase.DrawPhase -> "DRAW A CARD"
    DuelPhase.StandbyPhase -> "STANDBY EFFECTS"
    DuelPhase.MainPhase1 -> "SUMMON · SET · ACTIVATE"
    DuelPhase.BattlePhase -> "DECLARE YOUR ATTACKS"
    DuelPhase.MainPhase2 -> "POST-BATTLE PLAYS"
    DuelPhase.EndPhase -> "END OF TURN"
}
