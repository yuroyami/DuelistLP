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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
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
import app.ui.theme.lpDigitStyle
import kotlinx.coroutines.delay

private enum class Phase { Picking, Revealing, Result }

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
    var countdown by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<RpsOutcome?>(null) }

    LaunchedEffect(p1Pick, p2Pick) {
        if (p1Pick != null && p2Pick != null && phase == Phase.Picking) {
            phase = Phase.Revealing
            for (n in 3 downTo 1) {
                countdown = n
                delay(700)
            }
            countdown = 0
            delay(180)
            val outcome = resolveRps(p1Pick!!, p2Pick!!)
            result = outcome
            phase = Phase.Result
            if (outcome == RpsOutcome.TIE) {
                delay(1400)
                p1Pick = null
                p2Pick = null
                result = null
                rounds += 1
                phase = Phase.Picking
            }
        }
    }

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
                resultBanner = bannerFor(result, isP1Side = false),
                rotated = true,
                onPick = { if (phase == Phase.Picking) p2Pick = it },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            CenterDivider(countdown = countdown, phase = phase, rounds = rounds)

            RpsHalf(
                playerName = player1,
                otherPicked = p2Pick != null,
                myPick = p1Pick,
                showReveal = phase != Phase.Picking,
                resultBanner = bannerFor(result, isP1Side = true),
                rotated = false,
                onPick = { if (phase == Phase.Picking) p1Pick = it },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }

        if (phase == Phase.Result && result != null && result != RpsOutcome.TIE) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Button(
                    onClick = {
                        val firstPlayer = if (result == RpsOutcome.P1) PlayerSlot.P1 else PlayerSlot.P2
                        onResolved(firstPlayer, p1Pick!!, p2Pick!!, rounds)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DuelColors.DuelGold,
                        contentColor = Color.Black,
                    ),
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("BEGIN DUEL", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

private fun bannerFor(result: RpsOutcome?, isP1Side: Boolean): String? = when (result) {
    null -> null
    RpsOutcome.TIE -> "TIE"
    RpsOutcome.P1 -> if (isP1Side) "FIRST" else "SECOND"
    RpsOutcome.P2 -> if (isP1Side) "SECOND" else "FIRST"
}

@Composable
private fun RpsHalf(
    playerName: String,
    otherPicked: Boolean,
    myPick: RpsPick?,
    showReveal: Boolean,
    resultBanner: String?,
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

        // Vertical-only padding here so the shuffle marquee can extend to the
        // screen's left/right edges. Other children are center-aligned and
        // short, so they don't need horizontal insets.
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 16.dp),
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

            Box(modifier = Modifier.height(56.dp), contentAlignment = Alignment.Center) {
                when {
                    resultBanner != null -> StrokedText(
                        text = resultBanner,
                        style = lpDigitStyle(28),
                        fillColor = DuelColors.LpYellow,
                        strokeColor = DuelColors.LpStroke,
                        strokeWidth = 2.dp,
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
 * Horizontal marquee of rock/paper/scissors emoji scrolling at extreme speed.
 * Tapping anywhere on the strip locks in a uniformly-random pick — the visual
 * blur makes it impossible to predict what you'll get, which is the point.
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
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .background(DuelColors.DuelPurple, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("?", color = DuelColors.DuelGoldGlow, fontSize = markerSp.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CenterDivider(countdown: Int, phase: Phase, rounds: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, DuelColors.DuelGold, Color.Transparent))),
        contentAlignment = Alignment.Center,
    ) {
        when {
            countdown > 0 -> StrokedText(
                text = countdown.toString(),
                style = lpDigitStyle(56),
                fillColor = DuelColors.LpYellow,
                strokeColor = DuelColors.LpStroke,
                strokeWidth = 2.dp,
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

// One full cycle (3 items) scrolls past in 220 ms — fast enough that the eye
// can't fix on any single glyph.
private const val SHUFFLE_PERIOD_MS = 220
private const val ITEM_WIDTH_DP = 96
private const val MARQUEE_REPEATS = 30
