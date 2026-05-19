package app.ui.duel

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.resources.Res
import app.resources.blood_drop
import app.resources.healing_heart
import app.resources.sword
import app.ui.theme.DuelColors
import app.ui.theme.DuelTheme
import org.jetbrains.compose.resources.painterResource

/**
 * Four-panel combat surface arranged 2×2:
 *
 *    +-------------------+-------------------+
 *    | DAMAGE OPPONENT   | HEAL OPPONENT     |   (opponent-targeting row)
 *    +-------------------+-------------------+
 *    | HEAL SELF         | DAMAGE SELF       |   (self-targeting row)
 *    +-------------------+-------------------+
 *
 * Two gesture detectors share the surface:
 *   - Single tap → [onTapForValue] opens [ActionValueDialog] (numeric keypad).
 *     Mode picked by [modeForOffset] (which quadrant was tapped).
 *   - Long-press + vertical drag → opens the triangular gauge ([LpWheelState]
 *     emitted via [onWheelStateChange]). Drag up = bigger value. Released
 *     with value > 0 → [onStage]; released at 0 or cancelled → no commit.
 *
 * Sign and target of the resulting LP delta are determined later by
 * [LpActionMode] — this surface only emits non-negative magnitudes.
 *
 * The gauge OVERLAY itself is hosted at the [DuelScreen] level so it can
 * render on the OPPOSITE half from the pressing finger.
 */
@Composable
fun LpActionRegion(
    enabled: Boolean,
    maxValue: Int,
    onStage: (LpActionMode, Int) -> Unit,
    onTapForValue: (LpActionMode) -> Unit,
    onWheelStateChange: (LpWheelState?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var wheel by remember { mutableStateOf<LpWheelState?>(null) }
    var pressAnchor by remember { mutableStateOf(Offset.Zero) }
    val enabledRef = rememberUpdatedState(enabled)
    val maxValueRef = rememberUpdatedState(maxValue)
    val density = LocalDensity.current
    val sensitivityPx = with(density) { GAUGE_SENSITIVITY_DP.dp.toPx() }

    fun publish(state: LpWheelState?) {
        wheel = state
        onWheelStateChange(state)
    }

    fun computeValueFromDy(dy: Float): Int {
        // Negative dy = finger up = bigger value. All three modes use a
        // non-negative magnitude; sign is applied at resolution time.
        val signed = -dy
        val ratio = (signed / sensitivityPx).coerceIn(0f, 1f)
        val raw = (ratio * maxValueRef.value).toInt()
        return snapToStep(raw).coerceIn(0, maxValueRef.value)
    }

    Box(
        modifier = modifier
            // Tap detector: short release → keypad. Empty onLongPress so the
            // long-press timing belongs to the drag detector below.
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        if (!enabledRef.value) return@detectTapGestures
                        onTapForValue(
                            modeForOffset(
                                offset.x, offset.y,
                                this.size.width.toFloat(), this.size.height.toFloat(),
                            ),
                        )
                    },
                    onLongPress = { /* handled by detectDragGesturesAfterLongPress */ },
                )
            }
            // Long-press + drag: opens gauge, tracks magnitude, commits on release.
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        if (!enabledRef.value) return@detectDragGesturesAfterLongPress
                        val mode = modeForOffset(
                            offset.x, offset.y,
                            this.size.width.toFloat(), this.size.height.toFloat(),
                        )
                        pressAnchor = offset
                        publish(LpWheelState(mode = mode, value = 0))
                    },
                    onDrag = { change, _ ->
                        val current = wheel ?: return@detectDragGesturesAfterLongPress
                        val dy = change.position.y - pressAnchor.y
                        publish(current.copy(value = computeValueFromDy(dy)))
                    },
                    onDragEnd = {
                        val final = wheel
                        publish(null)
                        if (final != null && final.value > 0) {
                            onStage(final.mode, final.value)
                        }
                    },
                    onDragCancel = { publish(null) },
                )
            },
    ) {
        val activeMode = wheel?.mode
        Column(modifier = Modifier.fillMaxSize()) {
            // Top row: opponent-targeting actions.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                ActionPanel(
                    mode = LpActionMode.DamageOpponent,
                    isActive = activeMode == LpActionMode.DamageOpponent,
                    isDimmed = activeMode != null && activeMode != LpActionMode.DamageOpponent,
                    modifier = Modifier.fillMaxHeight().weight(1f),
                )
                ActionPanel(
                    mode = LpActionMode.HealOpponent,
                    isActive = activeMode == LpActionMode.HealOpponent,
                    isDimmed = activeMode != null && activeMode != LpActionMode.HealOpponent,
                    modifier = Modifier.fillMaxHeight().weight(1f),
                )
            }
            // Bottom row: self-targeting actions.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                ActionPanel(
                    mode = LpActionMode.HealSelf,
                    isActive = activeMode == LpActionMode.HealSelf,
                    isDimmed = activeMode != null && activeMode != LpActionMode.HealSelf,
                    modifier = Modifier.fillMaxHeight().weight(1f),
                )
                ActionPanel(
                    mode = LpActionMode.DamageSelf,
                    isActive = activeMode == LpActionMode.DamageSelf,
                    isDimmed = activeMode != null && activeMode != LpActionMode.DamageSelf,
                    modifier = Modifier.fillMaxHeight().weight(1f),
                )
            }
        }
    }
}

/**
 * One quadrant in the 2×2 action grid. Decorative — pointer events live in
 * the parent [LpActionRegion]'s shared gesture detector.
 *
 * Compact layout: icon on top, two-line label below (e.g. "DAMAGE\nOPPONENT")
 * because the panel cells are roughly half-width × half-height of the region.
 */
@Composable
private fun ActionPanel(
    mode: LpActionMode,
    isActive: Boolean,
    isDimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = panelAccent(mode)
    val accentDeep = panelAccentDeep(mode)
    val accentGlow = panelAccentGlow(mode)
    val (label, drawable) = when (mode) {
        LpActionMode.DamageOpponent -> "DAMAGE\nOPPONENT" to Res.drawable.sword
        LpActionMode.HealOpponent -> "HEAL\nOPPONENT" to Res.drawable.healing_heart
        LpActionMode.HealSelf -> "HEAL\nSELF" to Res.drawable.healing_heart
        LpActionMode.DamageSelf -> "DAMAGE\nSELF" to Res.drawable.blood_drop
    }

    val d = DuelTheme.dimens
    val targetAlpha = if (isDimmed) 0.35f else 1f
    val alpha by animateFloatAsState(targetValue = targetAlpha, animationSpec = tween(180))
    val targetScale = if (isActive) 1.04f else 1f
    val scale by animateFloatAsState(targetValue = targetScale, animationSpec = tween(180))

    Box(
        modifier = modifier
            .padding(d.s4 - 1.dp)
            .clip(RoundedCornerShape(d.radiusLg))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        accent.copy(alpha = if (isActive) 0.55f else 0.32f),
                        accentDeep.copy(alpha = 0.95f),
                        Color(0xFF0B0620),
                    ),
                ),
            )
            .border(
                width = if (isActive) d.borderBold else d.borderStandard,
                color = if (isActive) accentGlow else DuelColors.DuelGold.copy(alpha = 0.65f),
                shape = RoundedCornerShape(d.radiusLg),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = d.s6, horizontal = d.s4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(d.actionGlowSize)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(accentGlow.copy(alpha = 0.55f), Color.Transparent),
                                ),
                            ),
                    )
                }
                Image(
                    painter = painterResource(drawable),
                    contentDescription = label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(d.actionIconSize)
                        .scale(scale)
                        .clip(RoundedCornerShape(d.radiusSm)),
                    alpha = alpha,
                )
            }
            Text(
                text = label,
                color = if (isActive) accentGlow else DuelColors.DuelGoldGlow,
                fontSize = d.textTiny,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = d.textCompact,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                letterSpacing = d.trackTight,
                modifier = Modifier.padding(top = d.s4),
            )
        }
    }
}

/** Snapshot of an active gauge: which mode + currently-snapped magnitude. */
data class LpWheelState(
    val mode: LpActionMode,
    val value: Int,
)

internal fun panelAccent(mode: LpActionMode): Color = when (mode) {
    LpActionMode.DamageOpponent -> DuelColors.Crimson
    LpActionMode.HealOpponent -> Color(0xFF14B8C3)
    LpActionMode.HealSelf -> DuelColors.EmeraldHeal
    LpActionMode.DamageSelf -> DuelColors.BloodRed
}

internal fun panelAccentDeep(mode: LpActionMode): Color = when (mode) {
    LpActionMode.DamageOpponent -> Color(0xFF5A0A18)
    LpActionMode.HealOpponent -> Color(0xFF064D54)
    LpActionMode.HealSelf -> Color(0xFF064D38)
    LpActionMode.DamageSelf -> Color(0xFF3A0410)
}

internal fun panelAccentGlow(mode: LpActionMode): Color = when (mode) {
    LpActionMode.DamageOpponent -> Color(0xFFFF6A7C)
    LpActionMode.HealOpponent -> Color(0xFF6BE3F0)
    LpActionMode.HealSelf -> Color(0xFF6BF0BD)
    LpActionMode.DamageSelf -> DuelColors.BloodGlow
}

private fun snapToStep(raw: Int): Int {
    val step = SNAP_STEP
    val sign = if (raw < 0) -1 else 1
    val mag = kotlin.math.abs(raw)
    val snapped = ((mag + step / 2) / step) * step
    return sign * snapped
}

/**
 * Map a (x, y) touch offset to the mode for that quadrant in the 2×2 grid.
 * Top row = opponent-targeting; bottom row = self-targeting.
 */
private fun modeForOffset(x: Float, y: Float, width: Float, height: Float): LpActionMode {
    val isLeft = x < width / 2f
    val isTop = y < height / 2f
    return when {
        isTop && isLeft -> LpActionMode.DamageOpponent
        isTop -> LpActionMode.HealOpponent
        isLeft -> LpActionMode.HealSelf
        else -> LpActionMode.DamageSelf
    }
}

/** Vertical travel needed to swing 0 → maxValue. ~½ phone height. */
private const val GAUGE_SENSITIVITY_DP = 280

private const val SNAP_STEP = 50
