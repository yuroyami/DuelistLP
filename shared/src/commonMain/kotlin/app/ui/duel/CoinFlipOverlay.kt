package app.ui.duel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.audio.GameSfx
import app.audio.LocalLpSfx
import app.ui.theme.DuelColors
import app.ui.theme.DuelTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Full-screen overlay: flip a coin in 3D-feeling animation.
 *
 *  - `graphicsLayer { rotationY; cameraDistance }` produces real perspective
 *    flipping — at 90°/270° the disc collapses to an "edge" naturally.
 *  - Heads/tails switch based on sign of cos(rotY); the back face is wrapped
 *    in its own layer with rotationY=180 so it appears upright through the
 *    parent's back-projection.
 *  - Lift arc: ease-out cubic up, ease-in cubic down (mimics gravity).
 *  - Settle: scale bounce + glow halo + radial particle burst.
 *  - Both face designs are 180°-symmetric so opposing players can read it
 *    without rotating the overlay.
 */
@Composable
fun CoinFlipOverlay(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val lpSfx = LocalLpSfx.current
    var result by remember { mutableStateOf<CoinFace?>(null) }
    var spinning by remember { mutableStateOf(false) }

    val rotY = remember { Animatable(0f) }            // degrees
    val liftFrac = remember { Animatable(0f) }        // 0..1, scaled to dp at render time
    val landScale = remember { Animatable(1f) }
    val glow = remember { Animatable(0f) }
    val particle = remember { Animatable(0f) }

    fun forwardTargetDeg(current: Float, end: Float, spins: Int): Float {
        val norm = ((current % 360f) + 360f) % 360f
        val delta = ((end - norm + 360f) % 360f) + spins * 360f
        return current + delta
    }

    fun doFlip() {
        if (spinning) return
        spinning = true
        result = null
        lpSfx.playSfx(GameSfx.CoinToss)
        scope.launch {
            val target = if (Random.nextBoolean()) CoinFace.Heads else CoinFace.Tails
            val finalDeg = if (target == CoinFace.Heads) 0f else 180f
            val spins = 5 + Random.nextInt(3)
            val targetRotY = forwardTargetDeg(rotY.value, finalDeg, spins)

            glow.snapTo(0f)
            particle.snapTo(0f)
            landScale.snapTo(1f)

            val flipMs = 1700
            coroutineScope {
                launch {
                    rotY.animateTo(targetRotY, tween(flipMs, easing = LinearOutSlowInEasing))
                }
                launch {
                    liftFrac.animateTo(1f, tween(flipMs / 2, easing = EaseOutCubic))
                    liftFrac.animateTo(0f, tween(flipMs / 2, easing = EaseInCubic))
                }
            }

            result = target
            coroutineScope {
                launch {
                    landScale.animateTo(1.18f, tween(140, easing = EaseOutCubic))
                    landScale.animateTo(0.94f, tween(110))
                    landScale.animateTo(1.05f, tween(120))
                    landScale.animateTo(1f, tween(110))
                }
                launch { glow.animateTo(1f, tween(420)) }
                launch { particle.animateTo(1f, tween(720)) }
            }
            spinning = false
        }
    }

    LaunchedEffect(Unit) { doFlip() }

    val d = DuelTheme.dimens
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        val shortest = kotlin.math.min(maxWidth.value, maxHeight.value)
        val arenaSize = (shortest * 0.78f).coerceIn(220f, 420f).dp
        val coinSize = (shortest * 0.50f).coerceIn(140f, 280f).dp
        val shadowBox = (shortest * 0.58f).coerceIn(160f, 320f).dp
        val resultSp = (shortest * 0.10f).coerceIn(32f, 56f).sp
        val resultPad = (shortest * 0.17f).coerceIn(48f, 96f).dp

        result?.let { r ->
            val color = if (r == CoinFace.Heads) DuelColors.DuelGoldGlow else DuelColors.BloodGlow
            Text(
                text = r.name.uppercase(),
                color = color,
                fontSize = resultSp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (resultSp.value * 0.2f).sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = resultPad)
                    .rotate(180f),
            )
            Text(
                text = r.name.uppercase(),
                color = color,
                fontSize = resultSp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (resultSp.value * 0.2f).sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = resultPad),
            )
        }

        Box(
            modifier = Modifier
                .size(arenaSize)
                .pointerInput(spinning) {
                    detectTapGestures { if (!spinning) doFlip() }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (glow.value > 0f) {
                val haloColor = if (result == CoinFace.Tails) DuelColors.BloodGlow else DuelColors.DuelGold
                Canvas(modifier = Modifier.size(arenaSize)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(haloColor.copy(alpha = 0.55f * glow.value), Color.Transparent),
                            radius = size.minDimension / 2f,
                        ),
                        radius = size.minDimension / 2f,
                    )
                }
            }

            if (particle.value > 0f && result != null) {
                val pc = if (result == CoinFace.Heads) DuelColors.DuelGoldGlow else DuelColors.BloodGlow
                Canvas(modifier = Modifier.size(arenaSize)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxR = size.minDimension / 2f
                    val p = particle.value
                    for (i in 0 until 26) {
                        val angle = (i / 26f) * 2.0 * PI
                        val r0 = maxR * 0.30f
                        val r1 = maxR * (0.55f + 0.30f * (i % 4) / 4f)
                        val r = r0 + (r1 - r0) * p
                        val fade = (1f - p).coerceAtLeast(0f)
                        drawCircle(
                            color = pc.copy(alpha = 0.85f * fade),
                            radius = 4f * fade + 1.2f,
                            center = Offset(cx + (cos(angle) * r).toFloat(), cy + (sin(angle) * r).toFloat()),
                        )
                    }
                }
            }

            val cosVal = cos(rotY.value.toDouble() * PI / 180.0).toFloat()
            val showHeads = cosVal >= 0f
            Box(
                modifier = Modifier
                    .size(coinSize)
                    .graphicsLayer {
                        translationY = -liftFrac.value * coinSize.toPx() * 1.26f
                        rotationY = rotY.value
                        cameraDistance = 24f * density
                        scaleX = landScale.value
                        scaleY = landScale.value
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (showHeads) {
                    HeadsFace(modifier = Modifier.fillMaxSize())
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationY = 180f
                                cameraDistance = 24f * density
                            },
                    ) {
                        TailsFace(modifier = Modifier.fillMaxSize())
                    }
                }
            }

            val lf = liftFrac.value
            val shadowScale = 1f - lf * 0.55f
            val shadowAlpha = 0.45f * (1f - lf * 0.65f)
            Canvas(modifier = Modifier.size(shadowBox)) {
                drawOval(
                    color = Color.Black.copy(alpha = shadowAlpha),
                    topLeft = Offset(
                        size.width / 2f - (size.width * 0.55f * shadowScale) / 2f,
                        size.height * 0.86f,
                    ),
                    size = Size(size.width * 0.55f * shadowScale, size.height * 0.085f * shadowScale),
                )
            }
        }

        OverlayPillRow(
            spinning = spinning,
            actionLabel = "FLIP AGAIN",
            onAction = { doFlip() },
            onDismiss = onDismiss,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = d.s24),
        )
        OverlayPillRow(
            spinning = spinning,
            actionLabel = "FLIP AGAIN",
            onAction = { doFlip() },
            onDismiss = onDismiss,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = d.s24)
                .rotate(180f),
        )
    }
}

@Composable
private fun OverlayPillRow(
    spinning: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = DuelTheme.dimens
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(d.s10),
    ) {
        DonePill(onClick = onDismiss)
        FlipAgainPill(
            label = actionLabel,
            enabled = !spinning,
            onClick = onAction,
        )
    }
}

@Composable
private fun DonePill(onClick: () -> Unit) {
    val d = DuelTheme.dimens
    Box(
        modifier = Modifier
            .background(Color(0xFF1A1140), RoundedCornerShape(d.radiusMd))
            .border(d.borderHairline, DuelColors.Crimson.copy(alpha = 0.85f), RoundedCornerShape(d.radiusMd))
            .clickable(onClick = onClick)
            .padding(horizontal = d.s20 + d.s2, vertical = d.s12),
    ) {
        Text(
            text = "✕  DONE",
            color = DuelColors.Crimson,
            fontWeight = FontWeight.ExtraBold,
            fontSize = d.textBodyLg,
            letterSpacing = d.trackWide,
        )
    }
}

enum class CoinFace { Heads, Tails }

@Composable
private fun HeadsFace(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f

        // Gold disc body with off-center radial gradient (fake light source).
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFF4C4),
                    DuelColors.DuelGoldGlow,
                    DuelColors.DuelGold,
                    Color(0xFFB8893A),
                ),
                center = Offset(cx - r * 0.30f, cy - r * 0.30f),
                radius = r * 1.05f,
            ),
            radius = r,
            center = Offset(cx, cy),
        )
        // Outer rim (dark gold).
        drawCircle(
            color = Color(0xFF6B4F1A),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = r * 0.07f),
        )
        // Inner highlight ring.
        drawCircle(
            color = Color(0xFFFFE8A8).copy(alpha = 0.7f),
            radius = r * 0.90f,
            center = Offset(cx, cy),
            style = Stroke(width = r * 0.02f),
        )

        // 4-radial eye/cross — 180°-symmetric.
        val outerR = r * 0.78f
        val innerR = r * 0.30f
        val path = Path().apply {
            for (i in 0..7) {
                val a = i * (PI / 4)
                val rad = if (i % 2 == 0) outerR else innerR
                val x = cx + (cos(a) * rad).toFloat()
                val y = cy + (sin(a) * rad).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(
            path,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF8E6B22), Color(0xFF4A340D)),
                start = Offset(cx - r, cy - r),
                end = Offset(cx + r, cy + r),
            ),
        )
        drawPath(path, color = Color(0xFFFFE8A8).copy(alpha = 0.4f), style = Stroke(width = r * 0.015f))

        // Central iris.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF2D1F0A), Color(0xFF1A0F04)),
                center = Offset(cx, cy),
                radius = r * 0.22f,
            ),
            radius = r * 0.22f,
            center = Offset(cx, cy),
        )
        drawCircle(
            color = Color(0xFF8E6B22),
            radius = r * 0.22f,
            center = Offset(cx, cy),
            style = Stroke(width = r * 0.03f),
        )
        // Iris highlight.
        drawCircle(
            color = Color(0xFFFFE8A8).copy(alpha = 0.85f),
            radius = r * 0.06f,
            center = Offset(cx - r * 0.05f, cy - r * 0.05f),
        )

        // Diagonal specular sheen.
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.28f),
                    Color.Transparent,
                ),
                start = Offset(cx - r, cy - r * 1.2f),
                end = Offset(cx + r, cy + r * 1.2f),
            ),
            radius = r,
            center = Offset(cx, cy),
        )
    }
}

@Composable
private fun TailsFace(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f

        // Crimson disc body.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFA0B0),
                    DuelColors.BloodGlow,
                    DuelColors.Crimson,
                    Color(0xFF5C0410),
                ),
                center = Offset(cx - r * 0.30f, cy - r * 0.30f),
                radius = r * 1.05f,
            ),
            radius = r,
            center = Offset(cx, cy),
        )
        drawCircle(
            color = Color(0xFF3A030A),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = r * 0.07f),
        )
        drawCircle(
            color = Color(0xFFFFC8D8).copy(alpha = 0.45f),
            radius = r * 0.90f,
            center = Offset(cx, cy),
            style = Stroke(width = r * 0.02f),
        )

        // 8-point burst star — 180°-symmetric.
        val outerR = r * 0.80f
        val innerR = r * 0.32f
        val starPath = Path().apply {
            for (i in 0..15) {
                val a = i * (PI / 8) - PI / 2  // start pointing up
                val rad = if (i % 2 == 0) outerR else innerR
                val x = cx + (cos(a) * rad).toFloat()
                val y = cy + (sin(a) * rad).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(
            starPath,
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFE0E8), Color(0xFFB8121D), Color(0xFF4A040C)),
                center = Offset(cx - r * 0.1f, cy - r * 0.1f),
                radius = r,
            ),
        )
        drawPath(starPath, color = Color(0xFF3A030A), style = Stroke(width = r * 0.020f))

        // Central jewel.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFE0E8), Color(0xFF4A040C)),
                center = Offset(cx - r * 0.03f, cy - r * 0.03f),
                radius = r * 0.12f,
            ),
            radius = r * 0.12f,
            center = Offset(cx, cy),
        )
        drawCircle(
            color = Color(0xFFFFD8E0).copy(alpha = 0.85f),
            radius = r * 0.04f,
            center = Offset(cx - r * 0.025f, cy - r * 0.025f),
        )

        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.22f),
                    Color.Transparent,
                ),
                start = Offset(cx - r, cy - r * 1.2f),
                end = Offset(cx + r, cy + r * 1.2f),
            ),
            radius = r,
            center = Offset(cx, cy),
        )
    }
}

@Composable
private fun FlipAgainPill(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = DuelTheme.dimens
    Box(
        modifier = modifier
            .background(Color(0xFF1A1140), RoundedCornerShape(d.radiusMd))
            .border(d.borderHairline, DuelColors.DuelGold.copy(alpha = 0.7f), RoundedCornerShape(d.radiusMd))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = d.s24, vertical = d.s12),
    ) {
        Text(
            text = label,
            color = if (enabled) DuelColors.DuelGoldGlow else DuelColors.DuelGoldGlow.copy(alpha = 0.35f),
            fontWeight = FontWeight.ExtraBold,
            fontSize = d.textBodyLg,
            letterSpacing = d.trackWide,
        )
    }
}
