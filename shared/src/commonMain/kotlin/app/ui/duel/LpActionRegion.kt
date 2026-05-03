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
import androidx.compose.ui.unit.sp
import app.resources.Res
import app.resources.blood_drop
import app.resources.healing_heart
import app.resources.sword
import app.ui.theme.DuelColors
import org.jetbrains.compose.resources.painterResource

/**
 * Three-panel combat surface: ATTACK | SACRIFICE | HEAL, equal width.
 *
 * Two gesture detectors share the row:
 *   - Single tap → [onTapForValue] opens [ActionValueDialog] (numeric keypad).
 *     Mode picked by [modeForOffset] (which third was tapped).
 *   - Long-press + vertical drag → opens the triangular gauge ([LpWheelState]
 *     emitted via [onWheelStateChange]). Drag up = bigger value. Released
 *     with value > 0 → [onStage]; released at 0 or cancelled → no commit.
 *
 * Sign of the resulting LP delta is determined later by [LpActionMode] —
 * this surface only emits non-negative magnitudes.
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
                        onTapForValue(modeForOffset(offset.x, this.size.width.toFloat()))
                    },
                    onLongPress = { /* handled by detectDragGesturesAfterLongPress */ },
                )
            }
            // Long-press + drag: opens gauge, tracks magnitude, commits on release.
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        if (!enabledRef.value) return@detectDragGesturesAfterLongPress
                        val mode = modeForOffset(offset.x, this.size.width.toFloat())
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
        Row(modifier = Modifier.fillMaxSize()) {
            ActionPanel(
                mode = LpActionMode.Attack,
                isActive = activeMode == LpActionMode.Attack,
                isDimmed = activeMode != null && activeMode != LpActionMode.Attack,
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
            )
            ActionPanel(
                mode = LpActionMode.Sacrifice,
                isActive = activeMode == LpActionMode.Sacrifice,
                isDimmed = activeMode != null && activeMode != LpActionMode.Sacrifice,
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
            )
            ActionPanel(
                mode = LpActionMode.Heal,
                isActive = activeMode == LpActionMode.Heal,
                isDimmed = activeMode != null && activeMode != LpActionMode.Heal,
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
            )
        }
    }
}

/**
 * One ATTACK / SACRIFICE / HEAL panel. Decorative — pointer events live in
 * the parent [LpActionRegion]'s shared gesture detector.
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
    val (label, drawable, hint) = when (mode) {
        LpActionMode.Attack -> Triple("ATTACK", Res.drawable.sword, "tap · long-press")
        LpActionMode.Heal -> Triple("HEAL", Res.drawable.healing_heart, "tap · long-press")
        LpActionMode.Sacrifice -> Triple("SACRIFICE", Res.drawable.blood_drop, "tap · long-press")
    }

    val targetAlpha = if (isDimmed) 0.35f else 1f
    val alpha by animateFloatAsState(targetValue = targetAlpha, animationSpec = tween(180))
    val targetScale = if (isActive) 1.04f else 1f
    val scale by animateFloatAsState(targetValue = targetScale, animationSpec = tween(180))

    Box(
        modifier = modifier
            .padding(3.dp)
            .clip(RoundedCornerShape(14.dp))
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
                width = if (isActive) 2.5.dp else 1.5.dp,
                color = if (isActive) accentGlow else DuelColors.DuelGold.copy(alpha = 0.65f),
                shape = RoundedCornerShape(14.dp),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
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
                        .size(72.dp)
                        .scale(scale)
                        .clip(RoundedCornerShape(10.dp)),
                    alpha = alpha,
                )
            }
            Text(
                text = label,
                color = if (isActive) accentGlow else DuelColors.DuelGoldGlow,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = hint,
                color = DuelColors.DuelGoldGlow.copy(alpha = 0.55f),
                fontSize = 9.sp,
                modifier = Modifier.padding(top = 1.dp),
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
    LpActionMode.Attack -> DuelColors.Crimson
    LpActionMode.Heal -> DuelColors.EmeraldHeal
    LpActionMode.Sacrifice -> DuelColors.BloodRed
}

internal fun panelAccentDeep(mode: LpActionMode): Color = when (mode) {
    LpActionMode.Attack -> Color(0xFF5A0A18)
    LpActionMode.Heal -> Color(0xFF064D38)
    LpActionMode.Sacrifice -> Color(0xFF3A0410)
}

internal fun panelAccentGlow(mode: LpActionMode): Color = when (mode) {
    LpActionMode.Attack -> Color(0xFFFF6A7C)
    LpActionMode.Heal -> Color(0xFF6BF0BD)
    LpActionMode.Sacrifice -> DuelColors.BloodGlow
}

private fun snapToStep(raw: Int): Int {
    val step = SNAP_STEP
    val sign = if (raw < 0) -1 else 1
    val mag = kotlin.math.abs(raw)
    val snapped = ((mag + step / 2) / step) * step
    return sign * snapped
}

/** Map a horizontal touch x-offset to the action mode for that third of the row. */
private fun modeForOffset(x: Float, width: Float): LpActionMode {
    val third = width / 3f
    return when {
        x < third -> LpActionMode.Attack
        x < 2f * third -> LpActionMode.Sacrifice
        else -> LpActionMode.Heal
    }
}

/** Vertical travel needed to swing 0 → maxValue. ~½ phone height. */
private const val GAUGE_SENSITIVITY_DP = 280

private const val SNAP_STEP = 50
