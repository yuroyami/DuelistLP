package app.ui.duel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ui.components.StrokedText
import app.ui.theme.DuelColors
import app.ui.theme.DuelTheme

/**
 * Combat action being composed. All four carry a non-negative magnitude;
 * sign and target at LP resolution come from the mode.
 *   - [DamageOpponent] subtracts LP from the opponent (attack).
 *   - [HealOpponent]   adds LP to the opponent (card effects that buff the foe).
 *   - [HealSelf]       restores the actor's own LP.
 *   - [DamageSelf]     damages the actor's own LP (tribute, blood costs).
 */
enum class LpActionMode { DamageOpponent, HealOpponent, HealSelf, DamageSelf }

/**
 * Triangular gauge overlay. Geometries:
 *   - DAMAGE OPPONENT: right triangle, apex bottom-LEFT, marker → top-right.
 *   - HEAL OPPONENT:   isosceles centered at top (apex top-CENTER inverted)
 *     so the "give" feel mirrors the self-heal direction.
 *   - HEAL SELF:       right triangle (mirror), apex bottom-RIGHT, marker → top-left.
 *   - DAMAGE SELF:     isosceles, apex bottom-CENTER, horizontal level-line
 *     climbing from apex to top edge.
 *
 * The big readout sits at the TOP of the column so the rotated-180° version
 * (P2 pressing) puts the readout at the far screen edge, away from the
 * player's finger.
 */
@Composable
fun LpActionWheel(
    mode: LpActionMode,
    value: Int,
    maxValue: Int,
    modifier: Modifier = Modifier,
) {
    val accent = panelAccent(mode)
    val accentDeep = panelAccentDeep(mode)
    val accentGlow = panelAccentGlow(mode)

    val d = DuelTheme.dimens
    val open = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        open.animateTo(1f, tween(durationMillis = 220))
    }
    val openScale = open.value
    val t = (value.toFloat() / maxValue.coerceAtLeast(1)).coerceIn(0f, 1f)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Gauge readout + canvas both scale with the available space so the
        // overlay reads big on a tablet and still legible on small phones.
        val available = maxWidth.value
        val titleSp = (available * 0.045f).coerceIn(13f, 22f)
        val valueSp = (available * 0.18f).coerceIn(46f, 92f)
        val readoutH = (available * 0.28f).coerceIn(76f, 140f)
        val canvasH = (available * 0.62f).coerceIn(160f, 320f)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = d.s16, vertical = d.s12)
                .graphicsLayer {
                    scaleX = openScale
                    scaleY = openScale
                    alpha = openScale
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(d.s10),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(readoutH.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = modeLabel(mode),
                        style = TextStyle(
                            color = accentGlow,
                            fontSize = titleSp.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = d.trackExtraWide,
                        ),
                    )
                    StrokedText(
                        text = value.toString(),
                        style = TextStyle(
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Black,
                            fontSize = valueSp.sp,
                        ),
                        fillColor = accentGlow,
                        strokeColor = DuelColors.LpStroke,
                        strokeWidth = d.borderBold,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(canvasH.dp),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    if (w <= 0f || h <= 0f) return@Canvas
                    drawGauge(
                        mode = mode,
                        t = t,
                        w = w,
                        h = h,
                        accent = accent,
                        accentDeep = accentDeep,
                        accentGlow = accentGlow,
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGauge(
    mode: LpActionMode,
    t: Float,
    w: Float,
    h: Float,
    accent: Color,
    accentDeep: Color,
    accentGlow: Color,
) {
    when (mode) {
        LpActionMode.DamageOpponent -> drawCornerGauge(
            apex = Offset(0f, h),
            tip = Offset(w, 0f),
            rightAngle = Offset(0f, 0f),
            t = t, accent = accent, accentDeep = accentDeep, accentGlow = accentGlow,
        )
        LpActionMode.HealOpponent -> drawCenterGauge(
            apex = Offset(w / 2f, h),
            topLeft = Offset(0f, 0f),
            topRight = Offset(w, 0f),
            t = t, accent = accent, accentDeep = accentDeep, accentGlow = accentGlow,
        )
        LpActionMode.HealSelf -> drawCornerGauge(
            apex = Offset(w, h),
            tip = Offset(0f, 0f),
            rightAngle = Offset(w, 0f),
            t = t, accent = accent, accentDeep = accentDeep, accentGlow = accentGlow,
        )
        LpActionMode.DamageSelf -> drawCenterGauge(
            apex = Offset(w / 2f, h),
            topLeft = Offset(0f, 0f),
            topRight = Offset(w, 0f),
            t = t, accent = accent, accentDeep = accentDeep, accentGlow = accentGlow,
        )
    }
}

/**
 * Right-triangle gauge for ATTACK and HEAL. [apex] is value=0 (marker start),
 * [tip] is value=max (marker end), [rightAngle] is the third corner forming
 * the enclosing right angle. Marker walks the hypotenuse apex → tip.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCornerGauge(
    apex: Offset,
    tip: Offset,
    rightAngle: Offset,
    t: Float,
    accent: Color,
    accentDeep: Color,
    accentGlow: Color,
) {
    // Soft fill — the empty triangle interior gets a subtle gradient
    // washing along the hypotenuse axis.
    val fillPath = Path().apply {
        moveTo(apex.x, apex.y)
        lineTo(tip.x, tip.y)
        lineTo(rightAngle.x, rightAngle.y)
        close()
    }
    drawPath(
        path = fillPath,
        brush = Brush.linearGradient(
            colors = listOf(
                accentDeep.copy(alpha = 0.28f),
                accent.copy(alpha = 0.16f),
                Color(0xFF120A33).copy(alpha = 0.55f),
            ),
            start = apex,
            end = tip,
        ),
    )

    // Active fill from apex along the hypotenuse to the marker position.
    if (t > 0f) {
        val markerOnDiag = lerpOffset(apex, tip, t)
        val activePath = Path().apply {
            moveTo(apex.x, apex.y)
            lineTo(markerOnDiag.x, markerOnDiag.y)
            // Close along the right-angle leg so the wedge reads as filling
            // up from the apex side, not as a thin diagonal sliver.
            val along = Offset(
                rightAngle.x + (apex.x - rightAngle.x) * (1f - t),
                rightAngle.y + (apex.y - rightAngle.y) * (1f - t),
            )
            lineTo(along.x, along.y)
            close()
        }
        drawPath(
            path = activePath,
            brush = Brush.linearGradient(
                colors = listOf(accentDeep, accent, accentGlow),
                start = apex,
                end = markerOnDiag,
            ),
        )
    }

    // Quarter ticks on the hypotenuse.
    for (i in 0..4) {
        val tt = i / 4f
        val pos = lerpOffset(apex, tip, tt)
        val isEnd = i == 0 || i == 4
        drawCircle(
            color = if (isEnd) accentGlow else DuelColors.DuelGold.copy(alpha = 0.7f),
            radius = if (isEnd) 5f else 3f,
            center = pos,
        )
    }

    // Outline — solid hypotenuse (the gauge axis), dashed legs (frame).
    drawLine(
        color = DuelColors.DuelGold,
        start = apex, end = tip,
        strokeWidth = 3f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = DuelColors.DuelGold.copy(alpha = 0.55f),
        start = rightAngle, end = tip,
        strokeWidth = 1.5f,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
    )
    drawLine(
        color = DuelColors.DuelGold.copy(alpha = 0.55f),
        start = rightAngle, end = apex,
        strokeWidth = 1.5f,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
    )

    drawMarker(lerpOffset(apex, tip, t), accent = accent, accentGlow = accentGlow)
}

/**
 * Symmetric upward-pointing isosceles for SACRIFICE. Active fill grows from
 * apex outward and upward as t climbs. Marker is the horizontal level-line
 * at the current value height.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCenterGauge(
    apex: Offset,
    topLeft: Offset,
    topRight: Offset,
    t: Float,
    accent: Color,
    accentDeep: Color,
    accentGlow: Color,
) {
    val outline = Path().apply {
        moveTo(apex.x, apex.y)
        lineTo(topLeft.x, topLeft.y)
        lineTo(topRight.x, topRight.y)
        close()
    }
    drawPath(
        path = outline,
        brush = Brush.linearGradient(
            colors = listOf(
                accentDeep.copy(alpha = 0.28f),
                accent.copy(alpha = 0.16f),
                Color(0xFF120A33).copy(alpha = 0.55f),
            ),
            start = apex,
            end = Offset((topLeft.x + topRight.x) / 2f, topLeft.y),
        ),
    )

    if (t > 0f) {
        val leftMark = lerpOffset(apex, topLeft, t)
        val rightMark = lerpOffset(apex, topRight, t)
        val active = Path().apply {
            moveTo(apex.x, apex.y)
            lineTo(leftMark.x, leftMark.y)
            lineTo(rightMark.x, rightMark.y)
            close()
        }
        drawPath(
            path = active,
            brush = Brush.linearGradient(
                colors = listOf(accentDeep, accent, accentGlow),
                start = apex,
                end = Offset((leftMark.x + rightMark.x) / 2f, leftMark.y),
            ),
        )
        drawLine(
            color = accentGlow,
            start = leftMark,
            end = rightMark,
            strokeWidth = 3f,
            cap = StrokeCap.Round,
        )
    }

    // Quarter level-lines for SAC — short horizontal hashes on the
    // centerline at 25/50/75% to give a sense of scale.
    for (i in 1..3) {
        val tt = i / 4f
        val left = lerpOffset(apex, topLeft, tt)
        val right = lerpOffset(apex, topRight, tt)
        val mid = Offset((left.x + right.x) / 2f, left.y)
        drawLine(
            color = DuelColors.DuelGold.copy(alpha = 0.55f),
            start = Offset(mid.x - 8f, mid.y),
            end = Offset(mid.x + 8f, mid.y),
            strokeWidth = 1.5f,
            cap = StrokeCap.Round,
        )
    }

    // Solid leg outlines, dashed top edge (the "ceiling" of the gauge).
    drawLine(
        color = DuelColors.DuelGold,
        start = apex, end = topLeft,
        strokeWidth = 2.5f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = DuelColors.DuelGold,
        start = apex, end = topRight,
        strokeWidth = 2.5f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = DuelColors.DuelGold.copy(alpha = 0.55f),
        start = topLeft, end = topRight,
        strokeWidth = 1.5f,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
    )

    // Endpoints — a glowing dot at the apex and the level-line midpoint.
    drawCircle(
        color = accentGlow,
        radius = 5f,
        center = apex,
    )
    val markerCenter = Offset(
        x = (lerpOffset(apex, topLeft, t).x + lerpOffset(apex, topRight, t).x) / 2f,
        y = lerpOffset(apex, topLeft, t).y,
    )
    drawMarker(markerCenter, accent = accent, accentGlow = accentGlow)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMarker(
    pos: Offset,
    accent: Color,
    accentGlow: Color,
) {
    drawCircle(
        color = accentGlow.copy(alpha = 0.45f),
        radius = 18f,
        center = pos,
    )
    drawCircle(
        color = accent,
        radius = 10f,
        center = pos,
    )
    drawCircle(
        color = Color.White,
        radius = 4.5f,
        center = pos,
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.18f), Color.Transparent),
            center = pos,
            radius = 60f,
        ),
        radius = 60f,
        center = pos,
    )
}

private fun lerpOffset(a: Offset, b: Offset, t: Float): Offset =
    Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

private fun modeLabel(mode: LpActionMode): String = when (mode) {
    LpActionMode.DamageOpponent -> "DAMAGE OPPONENT"
    LpActionMode.HealOpponent -> "HEAL OPPONENT"
    LpActionMode.HealSelf -> "HEAL SELF"
    LpActionMode.DamageSelf -> "DAMAGE SELF"
}

