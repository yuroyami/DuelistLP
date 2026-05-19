package app.ui.rps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.model.PlayerSlot
import app.model.RpsOutcome
import app.model.RpsPick
import app.model.resolveRps
import app.ui.components.StrokedText
import app.ui.theme.DuelColors
import app.ui.theme.DuelTheme
import app.ui.theme.lpDigitStyle
import kotlinx.coroutines.delay

// Picking → Revealing (~250 ms hold so glyphs swap without a hard cut)
//   → Result (winner picks turn order; tie auto-restarts after 800 ms)
private enum class Phase { Picking, Revealing, Result }

/**
 * RPS first-player picker. Each side scrolls a horizontal marquee of glyphs
 * at extreme speed; tap anywhere to lock a uniformly-random pick. Both sides
 * picked → reveal → result. Ties auto-rematch with [rounds] incremented.
 *
 * After a non-tie result, the WINNER picks the turn order (go first or
 * second). Once chosen, BEGIN DUEL appears and [onResolved] fires with the
 * chosen first-player slot.
 */
@Composable
fun RpsScreen(
    player1: String,
    player2: String,
    onResolved: (firstPlayer: PlayerSlot, p1Pick: RpsPick, p2Pick: RpsPick, rounds: Int) -> Unit,
) {
    var p1Pick by remember { mutableStateOf<RpsPick?>(null) }
    var p2Pick by remember { mutableStateOf<RpsPick?>(null) }
    var phase by remember { mutableStateOf(Phase.Picking) }
    var rounds by remember { mutableStateOf(1) }
    var result by remember { mutableStateOf<RpsOutcome?>(null) }
    // Set when the RPS winner picks who actually goes first. Until set, the
    // halves show "winner is choosing"; once set, banners + BEGIN DUEL appear.
    var firstPlayer by remember { mutableStateOf<PlayerSlot?>(null) }

    LaunchedEffect(p1Pick, p2Pick) {
        if (p1Pick != null && p2Pick != null && phase == Phase.Picking) {
            phase = Phase.Revealing
            delay(REVEAL_FLASH_MS)
            val outcome = resolveRps(p1Pick!!, p2Pick!!)
            result = outcome
            phase = Phase.Result
            if (outcome == RpsOutcome.TIE) {
                delay(TIE_RESET_MS)
                p1Pick = null
                p2Pick = null
                result = null
                firstPlayer = null
                rounds += 1
                phase = Phase.Picking
            }
        }
    }

    val winnerSlot: PlayerSlot? = when (result) {
        RpsOutcome.P1 -> PlayerSlot.P1
        RpsOutcome.P2 -> PlayerSlot.P2
        else -> null
    }
    val winnerName = when (winnerSlot) {
        PlayerSlot.P1 -> player1
        PlayerSlot.P2 -> player2
        null -> ""
    }
    val choicePending = phase == Phase.Result && winnerSlot != null && firstPlayer == null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DuelColors.ArenaGradient)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            RpsHalf(
                playerName = player2,
                otherPicked = p1Pick != null,
                myPick = p2Pick,
                showReveal = phase != Phase.Picking,
                resultBanner = bannerFor(result, firstPlayer, isP1Side = false),
                showWaiting = choicePending && winnerSlot != PlayerSlot.P2,
                rotated = true,
                onPick = { if (phase == Phase.Picking && p2Pick == null) p2Pick = it },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            CenterDivider(phase = phase, rounds = rounds, choosingName = if (choicePending) winnerName else null)

            RpsHalf(
                playerName = player1,
                otherPicked = p2Pick != null,
                myPick = p1Pick,
                showReveal = phase != Phase.Picking,
                resultBanner = bannerFor(result, firstPlayer, isP1Side = true),
                showWaiting = choicePending && winnerSlot != PlayerSlot.P1,
                rotated = false,
                onPick = { if (phase == Phase.Picking && p1Pick == null) p1Pick = it },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }

        // Winner's turn-order choice — only one player decides, so we render
        // a single overlay positioned on their half and rotated for them.
        if (choicePending) {
            TurnOrderChoiceOverlay(
                winnerSlot = winnerSlot,
                winnerName = winnerName,
                onChoice = { theyGoFirst ->
                    firstPlayer = if (theyGoFirst) winnerSlot else winnerSlot.opposite()
                },
            )
        }

        // BEGIN DUEL only once turn order is locked.
        if (phase == Phase.Result && result != null && result != RpsOutcome.TIE && firstPlayer != null) {
            val d = DuelTheme.dimens
            Box(
                modifier = Modifier.fillMaxSize().padding(d.s24),
                contentAlignment = Alignment.Center,
            ) {
                Button(
                    onClick = {
                        onResolved(firstPlayer!!, p1Pick!!, p2Pick!!, rounds)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DuelColors.DuelGold,
                        contentColor = Color.Black,
                    ),
                    modifier = Modifier.height(d.touchLg),
                    shape = RoundedCornerShape(d.radiusLg),
                ) {
                    Text("BEGIN DUEL", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

private fun PlayerSlot.opposite(): PlayerSlot =
    if (this == PlayerSlot.P1) PlayerSlot.P2 else PlayerSlot.P1

/**
 * Banner shown in the bottom slot of each RpsHalf. While the winner is still
 * deciding, both sides see "WINNER!"/"DEFEAT". Once a turn order is locked,
 * it switches to FIRST/SECOND.
 */
private fun bannerFor(result: RpsOutcome?, firstPlayer: PlayerSlot?, isP1Side: Boolean): String? = when (result) {
    null -> null
    RpsOutcome.TIE -> "TIE"
    RpsOutcome.P1, RpsOutcome.P2 -> {
        if (firstPlayer == null) {
            val won = (result == RpsOutcome.P1) == isP1Side
            if (won) "WINNER" else "DEFEAT"
        } else {
            val goesFirst = (firstPlayer == PlayerSlot.P1) == isP1Side
            if (goesFirst) "FIRST" else "SECOND"
        }
    }
}

@Composable
private fun RpsHalf(
    playerName: String,
    otherPicked: Boolean,
    myPick: RpsPick?,
    showReveal: Boolean,
    resultBanner: String?,
    showWaiting: Boolean,
    rotated: Boolean,
    onPick: (RpsPick) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sideTint = if (rotated) Color(0xFF1A1140) else Color(0xFF120A33)
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    if (rotated) listOf(sideTint, Color.Transparent)
                    else listOf(Color.Transparent, sideTint)
                )
            )
            .then(if (rotated) Modifier.rotate(180f) else Modifier),
    ) {
        // Scale the reveal glyph + locked-card to fit this half on any screen.
        val halfH = maxHeight
        val revealSp = (halfH.value * 0.42f).toInt().coerceIn(72, 144)
        val markerSp = (halfH.value * 0.30f).toInt().coerceIn(56, 96)
        val cardWidthDp = minOf(maxWidth.value * 0.35f, 140f).coerceAtLeast(80f).dp
        val cardHeightDp = (cardWidthDp.value * 1.15f).dp
        val marqueeH = (halfH.value * 0.40f).toInt().coerceIn(72, 130).dp

        val d = DuelTheme.dimens
        // Vertical-only padding here so the shuffle marquee can extend to the
        // screen's left/right edges. Other children are center-aligned and
        // short, so they don't need horizontal insets.
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = d.s16),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = playerName,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = DuelColors.DuelGoldGlow,
            )

            if (showReveal && myPick != null) {
                AnimatedVisibility(
                    visible = true,
                    enter = scaleIn(tween(300)) + fadeIn(tween(220)),
                    exit = scaleOut() + fadeOut(),
                ) {
                    Text(text = myPick.glyph(), fontSize = revealSp.sp)
                }
            } else if (myPick != null) {
                // Locked pick — face down. The player's own pick is hidden until reveal.
                LockedCard(width = cardWidthDp, height = cardHeightDp, markerSp = markerSp)
            } else {
                // Active picker: a horizontal shuffle marquee. Tap anywhere to pick.
                ShuffleMarquee(
                    onPick = onPick,
                    height = marqueeH,
                    modifier = Modifier.fillMaxWidth().height(marqueeH),
                )
            }

            Box(modifier = Modifier.height(d.touchLg), contentAlignment = Alignment.Center) {
                when {
                    showWaiting -> Text(
                        "Opponent is choosing turn order…",
                        color = DuelColors.DuelGoldGlow.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    resultBanner != null -> StrokedText(
                        text = resultBanner,
                        style = lpDigitStyle(28),
                        fillColor = DuelColors.LpYellow,
                        strokeColor = DuelColors.LpStroke,
                        strokeWidth = d.borderEmphasized,
                    )
                    myPick != null && !otherPicked -> Text(
                        "Waiting for opponent…",
                        color = DuelColors.DuelGoldGlow.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                    myPick == null -> Text(
                        "Tap to pick",
                        color = DuelColors.DuelGoldGlow.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    else -> Spacer(Modifier)
                }
            }
        }
    }
}

/**
 * Full-screen overlay shown to the RPS winner so they can pick their turn
 * order. Rendered ONLY on the winner's half (rotated for them) so the loser
 * can't tap by accident — and so the winner reads it upright across the table.
 *
 * Two big cards: GO FIRST (gold, default-emphasis) and GO SECOND (cool tone).
 * A subtle pulsing glow draws the eye; tap either to commit.
 */
@Composable
private fun TurnOrderChoiceOverlay(
    winnerSlot: PlayerSlot,
    winnerName: String,
    onChoice: (theyGoFirst: Boolean) -> Unit,
) {
    val pulseTransition = rememberInfiniteTransition(label = "choicePulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.65f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Reverse),
        label = "choicePulseAlpha",
    )

    // Position the choice card on the winner's HALF (top half for P2, bottom
    // half for P1) and rotate so the winner reads it upright.
    val alignment = if (winnerSlot == PlayerSlot.P2) Alignment.TopCenter else Alignment.BottomCenter

    val d = DuelTheme.dimens
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = alignment,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = d.s16, vertical = d.s24 + d.s4)
                .then(if (winnerSlot == PlayerSlot.P2) Modifier.rotate(180f) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(d.s14),
            ) {
                StrokedText(
                    text = "$winnerName WON",
                    style = lpDigitStyle(30),
                    fillColor = DuelColors.LpYellow,
                    strokeColor = DuelColors.LpStroke,
                    strokeWidth = d.borderEmphasized,
                )
                Text(
                    text = "Choose your turn order",
                    color = DuelColors.DuelGoldGlow.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(d.s12),
                ) {
                    ChoiceCard(
                        label = "GO FIRST",
                        subtitle = "▶  draw, then act",
                        accent = DuelColors.DuelGold,
                        glow = DuelColors.DuelGoldGlow,
                        pulse = pulse,
                        emphasized = true,
                        onClick = { onChoice(true) },
                        modifier = Modifier.weight(1f),
                    )
                    ChoiceCard(
                        label = "GO SECOND",
                        subtitle = "wait, then react  ◀",
                        accent = Color(0xFF5B8BE0),
                        glow = Color(0xFFA8C6FF),
                        pulse = pulse,
                        emphasized = false,
                        onClick = { onChoice(false) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceCard(
    label: String,
    subtitle: String,
    accent: Color,
    glow: Color,
    pulse: Float,
    emphasized: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = DuelTheme.dimens
    Box(
        modifier = modifier
            .height(d.touchLg * 2)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        accent.copy(alpha = if (emphasized) 0.50f else 0.30f),
                        accent.copy(alpha = 0.18f),
                        Color(0xFF0B0620),
                    ),
                ),
                shape = RoundedCornerShape(d.radiusLg),
            )
            .border(
                width = if (emphasized) d.borderBold else d.borderEmphasized,
                color = if (emphasized) glow.copy(alpha = pulse) else glow.copy(alpha = 0.75f),
                shape = RoundedCornerShape(d.radiusLg),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                color = glow,
                fontSize = d.textIcon,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (d.trackExtraWide.value * 1.2f).sp,
            )
            Spacer(Modifier.height(d.s6))
            Text(
                text = subtitle,
                color = glow.copy(alpha = 0.70f),
                fontSize = d.textTiny,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = d.trackNormal,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Horizontal marquee of ✊✋✌ scrolling at 220 ms/cycle — too fast for the eye
 * to anchor on any one glyph. Tap anywhere → uniformly-random pick. The blur
 * is the whole point: it makes the pick feel committed-then-revealed rather
 * than chosen.
 */
@Composable
private fun ShuffleMarquee(
    onPick: (RpsPick) -> Unit,
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val items = remember { listOf(RpsPick.ROCK, RpsPick.PAPER, RpsPick.SCISSORS) }
    val infinite = rememberInfiniteTransition(label = "rps_shuffle")
    val cycleDp = ITEM_WIDTH_DP * items.size
    val itemFontSp = (height.value * 0.5f).toInt().coerceIn(32, 64)
    val offsetDp by infinite.animateFloat(
        initialValue = 0f,
        targetValue = -cycleDp.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SHUFFLE_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rps_offset",
    )

    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures { onPick(items.random()) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Render enough copies that the visible window always sees content
        // even at offset = -(cycleDp). Repeat = 30 covers any phone width.
        Row(
            modifier = Modifier.offset(x = offsetDp.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(MARQUEE_REPEATS) { i ->
                Box(
                    modifier = Modifier.size(width = ITEM_WIDTH_DP.dp, height = height),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(items[i % 3].glyph(), fontSize = itemFontSp.sp)
                }
            }
        }

        // Subtle gold accents on each side so the strip reads as a "reel"
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            DuelColors.DuelPurpleDeep.copy(alpha = 0.7f),
                            Color.Transparent,
                            Color.Transparent,
                            DuelColors.DuelPurpleDeep.copy(alpha = 0.7f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun LockedCard(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, markerSp: Int) {
    val d = DuelTheme.dimens
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .background(DuelColors.DuelPurple, RoundedCornerShape(d.radiusLg)),
        contentAlignment = Alignment.Center,
    ) {
        Text("?", color = DuelColors.DuelGoldGlow, fontSize = markerSp.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CenterDivider(phase: Phase, rounds: Int, choosingName: String?) {
    val d = DuelTheme.dimens
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(d.opponentPeekHeight)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, DuelColors.DuelGold, Color.Transparent))),
        contentAlignment = Alignment.Center,
    ) {
        when {
            choosingName != null -> Text(
                text = "$choosingName · CHOOSING TURN ORDER",
                color = Color.Black,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            phase == Phase.Picking -> Text(
                text = if (rounds == 1) "ROCK · PAPER · SCISSORS" else "ROUND $rounds",
                color = Color.Black,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            else -> Box(modifier = Modifier.alpha(0.6f)) {
                Text("REVEAL", color = Color.Black, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private fun RpsPick.glyph(): String = when (this) {
    RpsPick.ROCK -> "✊"
    RpsPick.PAPER -> "✋"
    RpsPick.SCISSORS -> "✌"
}

private const val SHUFFLE_PERIOD_MS = 220       // One full 3-glyph cycle.
private const val ITEM_WIDTH_DP = 96
private const val MARQUEE_REPEATS = 30           // Wide enough to cover any phone width.
private const val REVEAL_FLASH_MS = 250L         // Picker glyph → reveal glyph swap hold.
private const val TIE_RESET_MS = 800L            // Pause to register the tie before re-spinning.
