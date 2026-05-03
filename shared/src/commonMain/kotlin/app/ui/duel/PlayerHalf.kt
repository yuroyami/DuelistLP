package app.ui.duel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ui.theme.DuelColors
import kotlinx.coroutines.launch

/**
 * One staged action in the buffer. [value] is always > 0; sign at resolution
 * is determined by [mode] (ATTACK damages opponent, HEAL restores self,
 * SACRIFICE damages self).
 */
data class StagedAction(
    val mode: LpActionMode,
    val value: Int,
)

/**
 * Active player's full half. Top-to-bottom:
 *   name + FIRST badge → buffer slot (only when [bufferEnabled]) →
 *   LP card (LpBox) → End {Name}'s Turn button → action panels.
 *
 * Buffer slot only reserves space when [bufferEnabled] is true. In auto-commit
 * mode the buffer is always empty, so reserving the slot would just waste
 * vertical space.
 *
 * The whole half is rotated 180° as a unit when [rotated] is true (P2's side),
 * so all gesture coordinates and gauge orientation work in the half's local
 * frame — no per-child rotation needed downstream.
 *
 * End Turn lives between the LP card and the action region: each player ends
 * their own turn from inside their own half.
 */
@Composable
fun PlayerHalf(
    playerName: String,
    selfLp: Int,
    showFirstBadge: Boolean,
    rotated: Boolean,
    onStageAction: (StagedAction) -> Unit,
    onTapForValue: (LpActionMode) -> Unit,
    bufferedActions: List<StagedAction>,
    bufferEnabled: Boolean,
    onCommitBuffer: () -> Unit,
    onUndoBuffer: () -> Unit,
    onClearBuffer: () -> Unit,
    onWheelStateChange: (LpWheelState?) -> Unit,
    onEndTurn: () -> Unit,
    endTurnEnabled: Boolean,
    isInfinite: Boolean,
    actionsEnabled: Boolean,
    wheelMax: Int,
    animationDurationMs: Int,
    animationStepMagnitude: Int,
    pendingFloatingDelta: Int?,
    onFloatingDeltaComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.then(if (rotated) Modifier.rotate(180f) else Modifier),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = playerName,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = DuelColors.DuelGoldGlow,
                    modifier = Modifier.padding(start = 4.dp),
                )
                if (showFirstBadge) {
                    Box(
                        modifier = Modifier
                            .background(DuelColors.DuelGold, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text("FIRST", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Buffer slot. Only reserved when bufferEnabled — auto-commit
            // never queues, so the strip would always be empty there. Slot
            // height is fixed when reserved so the LP card doesn't shift as
            // entries come and go.
            if (bufferEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BUFFER_STRIP_SLOT_HEIGHT_DP.dp),
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

            // LP card gets ~60% of remaining vertical, action region ~40%.
            // End Turn slots in between as a fixed-height button.
            Box(modifier = Modifier.fillMaxWidth().weight(1.5f, fill = true)) {
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

            SwipeToEndTurn(
                playerName = playerName,
                enabled = endTurnEnabled,
                onEndTurn = onEndTurn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(END_TURN_HEIGHT_DP.dp),
            )

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
        }
    }
}

/**
 * Swipe-to-confirm End Turn slider. Drag the gold thumb across the track to
 * commit; release before the threshold ([SWIPE_THRESHOLD]) and the thumb
 * snaps back. Prevents the misclick risk of a tap button for an irreversible
 * turn-flip action.
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
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val trackWidthPx = with(density) { maxWidth.toPx() }
        val thumbWidthPx = with(density) { THUMB_WIDTH_DP.dp.toPx() }
        val insetPx = with(density) { THUMB_INSET_DP.dp.toPx() }
        val maxThumbX = (trackWidthPx - thumbWidthPx - 2 * insetPx).coerceAtLeast(0f)

        val thumbX = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val progress = if (maxThumbX > 0f) (thumbX.value / maxThumbX).coerceIn(0f, 1f) else 0f

        val draggable = rememberDraggableState { delta ->
            scope.launch {
                thumbX.snapTo((thumbX.value + delta).coerceIn(0f, maxThumbX))
            }
        }

        val border = if (enabled) DuelColors.DuelGoldGlow else DuelColors.DuelGold.copy(alpha = 0.25f)
        val trackBg = Color(0xFF1A1140)

        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(50))
                .background(trackBg)
                .border(if (enabled) 2.dp else 1.dp, border, RoundedCornerShape(50))
                .draggable(
                    state = draggable,
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    onDragStopped = {
                        if (thumbX.value >= maxThumbX * SWIPE_THRESHOLD) {
                            // Snap to fully-committed visual, then fire.
                            thumbX.animateTo(maxThumbX, tween(100))
                            onEndTurn()
                        } else {
                            // Snap back to start.
                            thumbX.animateTo(0f, tween(180, easing = EaseOutCubic))
                        }
                    },
                ),
        ) {
            // Progress fill drawn behind the thumb.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(DuelColors.DuelGold.copy(alpha = 0.30f)),
            )
            // Hint text fades as the thumb slides over it.
            Text(
                text = "Slide to end $playerName's turn ▶",
                color = if (enabled) {
                    DuelColors.DuelGoldGlow.copy(alpha = (1f - progress * 1.5f).coerceAtLeast(0f))
                } else {
                    DuelColors.DuelGoldGlow.copy(alpha = 0.35f)
                },
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center),
            )
            // Thumb. offset { ... } reads thumbX.value lazily so each
            // animation frame repositions without re-layout.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((insetPx + thumbX.value).toInt(), 0) }
                    .size(width = THUMB_WIDTH_DP.dp, height = (END_TURN_HEIGHT_DP - 2 * THUMB_INSET_DP).dp)
                    .background(
                        if (enabled) DuelColors.DuelGold else DuelColors.DuelGold.copy(alpha = 0.35f),
                        RoundedCornerShape(50),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "▶",
                    color = if (enabled) Color.Black else Color(0xFF8A85A8),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
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
    Column(
        modifier = modifier
            .background(Color(0xFF120A33), RoundedCornerShape(10.dp))
            .border(1.dp, DuelColors.DuelGold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEach { action -> ActionChip(action) }
            Box(modifier = Modifier.weight(1f))
            Text(
                text = "${actions.size}",
                color = DuelColors.DuelGoldGlow,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
    val accent = when (action.mode) {
        LpActionMode.Attack -> DuelColors.Crimson
        LpActionMode.Heal -> DuelColors.EmeraldHeal
        LpActionMode.Sacrifice -> DuelColors.BloodGlow
    }
    val sign = if (action.mode == LpActionMode.Heal) "+" else "−"
    val label = "$sign${action.value}"
    Box(
        modifier = Modifier
            .height(28.dp)
            .background(accent.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
            .border(1.dp, accent, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
    }
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(4.dp))
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
    val border = if (enabled) tint else tint.copy(alpha = 0.3f)
    val bg = if (emphasized) tint.copy(alpha = 0.18f) else Color(0xFF1A1140)
    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(8.dp))
            .border(if (emphasized) 2.dp else 1.dp, border, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        ) {
            Text(
                text = label,
                color = if (enabled) Color.White else Color(0xFF8A85A8),
                fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }
    }
}

// Sized to BufferStrip's natural content height (chips 28 + buttons 36 +
// spacing 6 + padding 12) so reservation == actual content.
private const val BUFFER_STRIP_SLOT_HEIGHT_DP = 82

private const val END_TURN_HEIGHT_DP = 48
private const val THUMB_WIDTH_DP = 64
private const val THUMB_INSET_DP = 4

// Thumb must reach this fraction of the track to commit. 90% leaves a small
// "almost there, keep going" zone before firing.
private const val SWIPE_THRESHOLD = 0.9f
