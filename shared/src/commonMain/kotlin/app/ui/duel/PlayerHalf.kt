package app.ui.duel

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.model.DuelPhase
import app.ui.components.AnimatedLifePoints
import app.ui.theme.DuelColors
import app.ui.theme.DuelTheme
import app.ui.theme.lpDigitStyle

/**
 * One staged action in the buffer. [value] is always > 0; sign and target at
 * resolution come from [mode] (DamageOpponent / HealOpponent / HealSelf /
 * DamageSelf).
 */
data class StagedAction(
    val mode: LpActionMode,
    val value: Int,
)

/**
 * Active player's full half. Top-to-bottom:
 *   Name row + FIRST badge (with opponent LP read on the trailing edge) →
 *   Phase tracker bar → buffer slot (conditional) → LP card (single centred
 *   value) → Next Phase + End Turn row → action panels (2×2) →
 *   bottom row: BufferModeToggle + OneShotWheelButton.
 *
 * All spacing, sizing, and text scales come from [DuelTheme.dimens] so the
 * layout adapts to phones and tablets without hand-tuning.
 *
 * The whole half is rotated 180° as a unit when [rotated] is true (P2's side).
 */
@Composable
fun PlayerHalf(
    playerName: String,
    selfLp: Int,
    opponentName: String,
    opponentLp: Int,
    opponentIsInfinite: Boolean,
    showFirstBadge: Boolean,
    rotated: Boolean,
    onStageAction: (StagedAction) -> Unit,
    onTapForValue: (LpActionMode) -> Unit,
    bufferedActions: List<StagedAction>,
    bufferEnabled: Boolean,
    onBufferModeChange: (Boolean) -> Unit,
    onCommitBuffer: () -> Unit,
    onUndoBuffer: () -> Unit,
    onClearBuffer: () -> Unit,
    onWheelStateChange: (LpWheelState?) -> Unit,
    onEndTurn: () -> Unit,
    endTurnEnabled: Boolean,
    onOpenOneShotWheel: () -> Unit,
    currentPhase: DuelPhase,
    onAdvancePhase: () -> Unit,
    turnNumber: Int,
    isInfinite: Boolean,
    actionsEnabled: Boolean,
    wheelMax: Int,
    animationDurationMs: Int,
    animationStepMagnitude: Int,
    pendingFloatingDelta: Int?,
    onFloatingDeltaComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = DuelTheme.dimens
    Box(
        modifier = modifier.then(if (rotated) Modifier.rotate(180f) else Modifier),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = d.s14, vertical = d.s8),
            verticalArrangement = Arrangement.spacedBy(d.s6),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top row: player name + FIRST badge | opponent name + LP value.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(d.s8),
                ) {
                    Text(
                        text = playerName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = DuelColors.DuelGoldGlow,
                        modifier = Modifier.padding(start = d.s4),
                    )
                    if (showFirstBadge) {
                        Box(
                            modifier = Modifier
                                .background(DuelColors.DuelGold, RoundedCornerShape(d.radiusPill))
                                .padding(horizontal = d.s10, vertical = d.s2),
                        ) {
                            Text(
                                "FIRST",
                                color = Color.Black,
                                fontSize = d.textTiny,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                OpponentLpReadout(
                    opponentName = opponentName,
                    opponentLp = opponentLp,
                    isInfinite = opponentIsInfinite,
                    animationDurationMs = animationDurationMs,
                )
            }

            // Phase tracker.
            PhaseTracker(currentPhase = currentPhase, modifier = Modifier.fillMaxWidth())

            // Buffer slot (conditional).
            if (bufferEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(d.bufferStripHeight),
                ) {
                    if (bufferedActions.isNotEmpty()) {
                        BufferStrip(
                            actions = bufferedActions,
                            enabled = actionsEnabled && !isInfinite,
                            onCommit = onCommitBuffer,
                            onUndo = onUndoBuffer,
                            onClear = onClearBuffer,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            // LP card — smaller weight than before so opp row + phase tracker fit.
            Box(modifier = Modifier.fillMaxWidth().weight(1.2f, fill = true)) {
                LpBox(
                    value = selfLp,
                    isInfinite = isInfinite,
                    durationMs = animationDurationMs,
                    stepMagnitude = animationStepMagnitude,
                    modifier = Modifier.fillMaxSize(),
                )
                pendingFloatingDelta?.let { delta ->
                    LpFloatingDelta(
                        delta = delta,
                        onComplete = onFloatingDeltaComplete,
                    )
                }
            }

            // Phase advance + end-turn row.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(d.endTurnRowHeight),
                horizontalArrangement = Arrangement.spacedBy(d.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NextPhaseButton(
                    phase = currentPhase,
                    enabled = currentPhase != DuelPhase.EndPhase,
                    onClick = onAdvancePhase,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                SwipeToEndTurn(
                    playerName = playerName,
                    enabled = endTurnEnabled,
                    onEndTurn = onEndTurn,
                    modifier = Modifier.weight(1.2f).fillMaxHeight(),
                )
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f, fill = true)) {
                LpActionRegion(
                    enabled = actionsEnabled && !isInfinite,
                    maxValue = wheelMax,
                    onStage = { mode, value -> onStageAction(StagedAction(mode, value)) },
                    onTapForValue = onTapForValue,
                    onWheelStateChange = onWheelStateChange,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(d.bottomRowHeight),
                horizontalArrangement = Arrangement.spacedBy(d.s8),
            ) {
                BufferModeToggle(
                    enabled = bufferEnabled,
                    turnNumber = turnNumber,
                    onChange = onBufferModeChange,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                OneShotWheelButton(
                    onClick = onOpenOneShotWheel,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(d.bottomRowHeight),
                )
            }
        }
    }
}

/**
 * Compact "OPP — NAME — 6500" readout shown to the right of the active
 * player's name. Lets the active player monitor their foe's LP without
 * lifting their head to read the rotated opponent peek across the table.
 */
@Composable
private fun OpponentLpReadout(
    opponentName: String,
    opponentLp: Int,
    isInfinite: Boolean,
    animationDurationMs: Int,
) {
    val d = DuelTheme.dimens
    Row(
        modifier = Modifier
            .height(d.touchSm.coerceAtMost(d.s32 + d.s8))
            .background(Color(0xFF120A33), RoundedCornerShape(d.radiusMd))
            .border(d.borderHairline, DuelColors.Crimson.copy(alpha = 0.65f), RoundedCornerShape(d.radiusMd))
            .padding(horizontal = d.s10),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(d.s8),
    ) {
        Text(
            text = "OPP",
            color = DuelColors.Crimson.copy(alpha = 0.85f),
            fontWeight = FontWeight.ExtraBold,
            fontSize = d.textMicro,
            letterSpacing = d.trackWide,
        )
        Text(
            text = opponentName,
            color = DuelColors.DuelGoldGlow.copy(alpha = 0.85f),
            fontWeight = FontWeight.SemiBold,
            fontSize = d.textTiny,
            maxLines = 1,
        )
        if (isInfinite) {
            Text(
                text = "∞",
                color = DuelColors.LpYellow,
                fontWeight = FontWeight.ExtraBold,
                fontSize = d.textReadout,
            )
        } else {
            AnimatedLifePoints(
                value = opponentLp,
                style = lpDigitStyle((d.textReadout.value).toInt().coerceAtLeast(14)),
                strokeWidth = d.borderHairline,
                durationMs = animationDurationMs,
            )
        }
    }
}

/**
 * "Next Phase ▶" button — disabled on End Phase (player must use End Turn).
 * Tints itself with the COMING phase's color for a forward-looking feel.
 */
@Composable
private fun NextPhaseButton(
    phase: DuelPhase,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = DuelTheme.dimens
    val nextPhase = phase.next()
    val accent = if (nextPhase != null) phaseAccent(nextPhase) else DuelColors.DuelGold.copy(alpha = 0.4f)
    val glow = if (nextPhase != null) phaseGlow(nextPhase) else DuelColors.DuelGoldGlow.copy(alpha = 0.4f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(d.radiusMd))
            .background(
                if (enabled) accent.copy(alpha = 0.32f) else Color(0xFF1A1140).copy(alpha = 0.5f),
            )
            .border(
                width = if (enabled) d.borderEmphasized else d.borderHairline,
                color = if (enabled) glow else DuelColors.DuelGold.copy(alpha = 0.25f),
                shape = RoundedCornerShape(d.radiusMd),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (enabled) "NEXT PHASE ▶" else "END OF TURN",
                color = if (enabled) glow else DuelColors.DuelGoldGlow.copy(alpha = 0.45f),
                fontWeight = FontWeight.ExtraBold,
                fontSize = d.textSmall,
                letterSpacing = d.trackNormal,
                maxLines = 1,
            )
            Text(
                text = if (enabled) (nextPhase?.shortCode ?: "") else "swipe ▶",
                color = if (enabled) glow.copy(alpha = 0.75f) else DuelColors.DuelGoldGlow.copy(alpha = 0.35f),
                fontWeight = FontWeight.SemiBold,
                fontSize = d.textMicro,
                letterSpacing = d.trackNormal,
                maxLines = 1,
            )
        }
    }
}

// Pill that swaps between AUTO COMMIT and QUEUE MODE.
@Composable
private fun BufferModeToggle(
    enabled: Boolean,
    turnNumber: Int,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = DuelTheme.dimens
    val border = if (enabled) DuelColors.DuelGoldGlow else DuelColors.DuelGold.copy(alpha = 0.6f)
    val bg = if (enabled) DuelColors.DuelGold.copy(alpha = 0.30f) else Color(0xFF1A1140)
    val label = if (enabled) "QUEUE MODE" else "AUTO COMMIT"
    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(d.radiusMd))
            .border(
                if (enabled) d.borderEmphasized else d.borderHairline,
                border,
                RoundedCornerShape(d.radiusMd),
            )
            .clickable { onChange(!enabled) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                color = DuelColors.DuelGoldGlow,
                fontWeight = FontWeight.ExtraBold,
                fontSize = d.textCompact,
                letterSpacing = d.trackWide,
                maxLines = 1,
            )
            Text(
                text = "Turn $turnNumber",
                color = DuelColors.DuelGoldGlow.copy(alpha = 0.55f),
                fontSize = d.textMicro,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun OneShotWheelButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = DuelTheme.dimens
    Box(
        modifier = modifier
            .background(Color(0xFF1A1140), RoundedCornerShape(d.radiusMd))
            .border(d.borderHairline, DuelColors.DuelGold.copy(alpha = 0.7f), RoundedCornerShape(d.radiusMd))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "✦",
            color = DuelColors.DuelGoldGlow,
            fontWeight = FontWeight.Bold,
            fontSize = d.textIcon,
        )
    }
}

/**
 * Swipe-to-confirm End Turn slider, sized smaller / centered horizontally
 * inside its row slot to keep the touch target clear of the screen edges
 * (predictive-back gesture zone).
 *
 * Direction-agnostic across the rotated P2 half: Compose's pointer system
 * applies the inverse layer transform to drag deltas, so "drag rightward
 * from the player's perspective" produces a positive delta in both halves.
 */
@Composable
private fun SwipeToEndTurn(
    playerName: String,
    enabled: Boolean,
    onEndTurn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = DuelTheme.dimens
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val trackWidthPx = with(density) { maxWidth.toPx() }
        val thumbWidthPx = with(density) { d.swipeThumbWidth.toPx() }
        val insetPx = with(density) { d.swipeThumbInset.toPx() }
        val maxThumbX = (trackWidthPx - thumbWidthPx - 2 * insetPx).coerceAtLeast(0f)

        var thumbX by remember { mutableFloatStateOf(0f) }
        val progress = if (maxThumbX > 0f) (thumbX / maxThumbX).coerceIn(0f, 1f) else 0f

        val draggable = rememberDraggableState { delta ->
            thumbX = (thumbX + delta).coerceIn(0f, maxThumbX)
        }

        val border = if (enabled) DuelColors.DuelGoldGlow else DuelColors.DuelGold.copy(alpha = 0.25f)
        val trackBg = Color(0xFF1A1140)

        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(d.radiusPill))
                .background(trackBg)
                .border(
                    if (enabled) d.borderEmphasized else d.borderHairline,
                    border,
                    RoundedCornerShape(d.radiusPill),
                )
                .draggable(
                    state = draggable,
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    onDragStopped = {
                        if (thumbX >= maxThumbX * SWIPE_THRESHOLD) {
                            animate(
                                initialValue = thumbX,
                                targetValue = maxThumbX,
                                animationSpec = tween(120),
                            ) { v, _ -> thumbX = v }
                            onEndTurn()
                        } else {
                            animate(
                                initialValue = thumbX,
                                targetValue = 0f,
                                animationSpec = tween(220, easing = EaseOutCubic),
                            ) { v, _ -> thumbX = v }
                        }
                    },
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(DuelColors.DuelGold.copy(alpha = 0.30f)),
            )
            Text(
                text = "Slide to end $playerName's turn ▶",
                color = if (enabled) {
                    DuelColors.DuelGoldGlow.copy(alpha = (1f - progress * 1.5f).coerceAtLeast(0f))
                } else {
                    DuelColors.DuelGoldGlow.copy(alpha = 0.35f)
                },
                fontWeight = FontWeight.SemiBold,
                fontSize = d.textCompact,
                letterSpacing = d.trackTight,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((insetPx + thumbX).toInt(), 0) }
                    .size(width = d.swipeThumbWidth, height = d.endTurnRowHeight - d.swipeThumbInset * 2)
                    .background(
                        if (enabled) DuelColors.DuelGold else DuelColors.DuelGold.copy(alpha = 0.35f),
                        RoundedCornerShape(d.radiusPill),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "▶",
                    color = if (enabled) Color.Black else Color(0xFF8A85A8),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = d.textIcon,
                )
            }
        }
    }
}

@Composable
private fun BufferStrip(
    actions: List<StagedAction>,
    enabled: Boolean,
    onCommit: () -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = DuelTheme.dimens
    Column(
        modifier = modifier
            .background(Color(0xFF120A33), RoundedCornerShape(d.radiusMd))
            .border(d.borderHairline, DuelColors.DuelGold.copy(alpha = 0.5f), RoundedCornerShape(d.radiusMd))
            .padding(horizontal = d.s8, vertical = d.s6),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(d.s6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEach { action -> ActionChip(action) }
            Box(modifier = Modifier.weight(1f))
            Text(
                text = "${actions.size}",
                color = DuelColors.DuelGoldGlow,
                fontWeight = FontWeight.Bold,
                fontSize = d.textBody,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(d.bufferActionRowHeight),
            horizontalArrangement = Arrangement.spacedBy(d.s6),
        ) {
            BufferButton(
                label = "Undo",
                tint = DuelColors.DuelGoldGlow,
                onClick = onUndo,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            BufferButton(
                label = "Clear",
                tint = DuelColors.Crimson,
                onClick = onClear,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            BufferButton(
                label = "Commit ▶",
                tint = DuelColors.DuelGold,
                onClick = onCommit,
                enabled = enabled,
                modifier = Modifier.weight(1.5f),
                emphasized = true,
            )
        }
    }
}

@Composable
private fun ActionChip(action: StagedAction) {
    val d = DuelTheme.dimens
    val accent = when (action.mode) {
        LpActionMode.DamageOpponent -> DuelColors.Crimson
        LpActionMode.HealOpponent -> Color(0xFF14B8C3)
        LpActionMode.HealSelf -> DuelColors.EmeraldHeal
        LpActionMode.DamageSelf -> DuelColors.BloodGlow
    }
    val isHeal = action.mode == LpActionMode.HealSelf || action.mode == LpActionMode.HealOpponent
    val sign = if (isHeal) "+" else "−"
    val label = "$sign${action.value}"
    Box(
        modifier = Modifier
            .height(d.s24 + d.s4)
            .background(accent.copy(alpha = 0.25f), RoundedCornerShape(d.radiusXs))
            .border(d.borderHairline, accent, RoundedCornerShape(d.radiusXs))
            .padding(horizontal = d.s8),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = d.textSmall,
        )
    }
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(d.s4))
}

@Composable
private fun BufferButton(
    label: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val d = DuelTheme.dimens
    val border = if (enabled) tint else tint.copy(alpha = 0.3f)
    val bg = if (emphasized) tint.copy(alpha = 0.18f) else Color(0xFF1A1140)
    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(d.radiusSm))
            .border(
                if (emphasized) d.borderEmphasized else d.borderHairline,
                border,
                RoundedCornerShape(d.radiusSm),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = d.s4, vertical = 0.dp),
        ) {
            Text(
                text = label,
                color = if (enabled) Color.White else Color(0xFF8A85A8),
                fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = d.textCompact,
            )
        }
    }
}

private const val SWIPE_THRESHOLD = 0.9f
