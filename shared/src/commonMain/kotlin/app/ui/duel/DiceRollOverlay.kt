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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Full-screen overlay: roll a six-sided die in real 3D.
 *
 * The cube's 8 vertices are kept in unit-cube local space, rotated around
 * (X, Y, Z), and perspective-projected to 2D. Visible faces (normal·camera > 0)
 * are sorted back-to-front and filled with Lambert-shaded ivory; pips are
 * drawn as projected circles whose radius scales with depth perspective.
 *
 * Standard Western convention — opposite faces sum to 7:
 *   +Z=1, -Z=6, +X=2, -X=5, +Y=4, -Y=3.
 *
 * Roll = N full rotations on each axis (X: 4-5, Y: 3-4, Z: 2-3) ending at
 * the canonical orientation for the chosen face. After the animation we
 * `snapTo` exact canonical angles so the result face sits dead-on (no
 * accumulated float drift). Lift arc + bounce + glow + radial sparks.
 */
@Composable
fun DiceRollOverlay(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val lpSfx = LocalLpSfx.current
    var result by remember { mutableStateOf<Int?>(null) }
    var rolling by remember { mutableStateOf(false) }

    val rotX = remember { Animatable(0.45f) }   // radians; small initial tilt for 3D feel
    val rotY = remember { Animatable(-0.55f) }
    val rotZ = remember { Animatable(0.20f) }
    val liftFrac = remember { Animatable(0f) }
    val landScale = remember { Animatable(1f) }
    val glow = remember { Animatable(0f) }
    val particle = remember { Animatable(0f) }

    fun doRoll() {
        if (rolling) return
        rolling = true
        result = null
        lpSfx.playSfx(GameSfx.DiceThrow)
        scope.launch {
            val target = Random.nextInt(1, 7)
            val (endX, endY) = endRotForFace(target)
            val endZ = (Random.nextInt(4) * PI / 2).toFloat()

            val twoPi = (2 * PI).toFloat()
            fun forwardTarget(current: Float, end: Float, spins: Int): Float {
                val norm = ((current % twoPi) + twoPi) % twoPi
                val delta = ((end - norm + twoPi) % twoPi) + spins * twoPi
                return current + delta
            }
            val tx = forwardTarget(rotX.value, endX, 4 + Random.nextInt(2))
            val ty = forwardTarget(rotY.value, endY, 3 + Random.nextInt(2))
            val tz = forwardTarget(rotZ.value, endZ, 2 + Random.nextInt(2))

            glow.snapTo(0f)
            particle.snapTo(0f)
            landScale.snapTo(1f)

            val rollMs = 1700
            coroutineScope {
                launch { rotX.animateTo(tx, tween(rollMs, easing = LinearOutSlowInEasing)) }
                launch { rotY.animateTo(ty, tween(rollMs, easing = LinearOutSlowInEasing)) }
                launch { rotZ.animateTo(tz, tween(rollMs, easing = LinearOutSlowInEasing)) }
                launch {
                    liftFrac.animateTo(1f, tween(rollMs / 2, easing = EaseOutCubic))
                    liftFrac.animateTo(0f, tween(rollMs / 2, easing = EaseInCubic))
                }
            }

            // Canonical snap so the chosen face sits exactly facing camera.
            rotX.snapTo(endX)
            rotY.snapTo(endY)
            rotZ.snapTo(endZ)
            result = target

            coroutineScope {
                launch {
                    landScale.animateTo(1.15f, tween(140, easing = EaseOutCubic))
                    landScale.animateTo(0.94f, tween(110))
                    landScale.animateTo(1.05f, tween(120))
                    landScale.animateTo(1f, tween(110))
                }
                launch { glow.animateTo(1f, tween(420)) }
                launch { particle.animateTo(1f, tween(720)) }
            }
            rolling = false
        }
    }

    LaunchedEffect(Unit) { doRoll() }

    val d = DuelTheme.dimens
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        val shortest = kotlin.math.min(maxWidth.value, maxHeight.value)
        val arenaSize = (shortest * 0.82f).coerceIn(240f, 440f).dp
        val dieSize = (shortest * 0.56f).coerceIn(160f, 320f).dp
        val shadowBox = (shortest * 0.62f).coerceIn(170f, 340f).dp
        val resultSp = (shortest * 0.095f).coerceIn(30f, 54f).sp
        val resultPad = (shortest * 0.17f).coerceIn(48f, 96f).dp

        result?.let { r ->
            Text(
                text = "ROLLED  $r",
                color = DuelColors.DuelGoldGlow,
                fontSize = resultSp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (resultSp.value * 0.16f).sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = resultPad)
                    .rotate(180f),
            )
            Text(
                text = "ROLLED  $r",
                color = DuelColors.DuelGoldGlow,
                fontSize = resultSp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (resultSp.value * 0.16f).sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = resultPad),
            )
        }

        Box(
            modifier = Modifier
                .size(arenaSize)
                .pointerInput(rolling) {
                    detectTapGestures { if (!rolling) doRoll() }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (glow.value > 0f) {
                Canvas(modifier = Modifier.size(arenaSize)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                DuelColors.DuelGold.copy(alpha = 0.55f * glow.value),
                                Color.Transparent,
                            ),
                            radius = size.minDimension / 2f,
                        ),
                        radius = size.minDimension / 2f,
                    )
                }
            }

            if (particle.value > 0f) {
                Canvas(modifier = Modifier.size(arenaSize)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxR = size.minDimension / 2f
                    val p = particle.value
                    for (i in 0 until 22) {
                        val angle = (i / 22f) * 2.0 * PI + 0.13
                        val r0 = maxR * 0.30f
                        val r1 = maxR * (0.55f + 0.30f * (i % 3) / 3f)
                        val r = r0 + (r1 - r0) * p
                        val fade = (1f - p).coerceAtLeast(0f)
                        drawCircle(
                            color = DuelColors.DuelGoldGlow.copy(alpha = 0.85f * fade),
                            radius = 4f * fade + 1.2f,
                            center = Offset(cx + (cos(angle) * r).toFloat(), cy + (sin(angle) * r).toFloat()),
                        )
                    }
                }
            }

            Canvas(
                modifier = Modifier
                    .size(dieSize)
                    .graphicsLayer {
                        translationY = -liftFrac.value * dieSize.toPx()
                        scaleX = landScale.value
                        scaleY = landScale.value
                    },
            ) {
                drawDie3D(rotX.value, rotY.value, rotZ.value)
            }

            val lf = liftFrac.value
            val shadowScale = 1f - lf * 0.55f
            val shadowAlpha = 0.50f * (1f - lf * 0.65f)
            Canvas(modifier = Modifier.size(shadowBox)) {
                drawOval(
                    color = Color.Black.copy(alpha = shadowAlpha),
                    topLeft = Offset(
                        size.width / 2f - (size.width * 0.55f * shadowScale) / 2f,
                        size.height * 0.84f,
                    ),
                    size = Size(size.width * 0.55f * shadowScale, size.height * 0.085f * shadowScale),
                )
            }
        }

        OverlayPillRow(
            rolling = rolling,
            actionLabel = "ROLL AGAIN",
            onAction = { doRoll() },
            onDismiss = onDismiss,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = d.s24),
        )
        OverlayPillRow(
            rolling = rolling,
            actionLabel = "ROLL AGAIN",
            onAction = { doRoll() },
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
    rolling: Boolean,
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
        RollAgainPill(
            label = actionLabel,
            enabled = !rolling,
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

@Composable
private fun RollAgainPill(
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

// === 3D math ===========================================================

private data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z
    fun normalized(): Vec3 {
        val n = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-6f)
        return Vec3(x / n, y / n, z / n)
    }
}

private fun Vec3.rotated(rx: Float, ry: Float, rz: Float): Vec3 {
    val cx = cos(rx); val sx = sin(rx)
    var nx = x
    var ny = y * cx - z * sx
    var nz = y * sx + z * cx

    val cy = cos(ry); val sy = sin(ry)
    val ax = nx * cy + nz * sy
    val az = -nx * sy + nz * cy
    nx = ax; nz = az

    val cz = cos(rz); val sz = sin(rz)
    val bx = nx * cz - ny * sz
    val by = nx * sz + ny * cz
    nx = bx; ny = by

    return Vec3(nx, ny, nz)
}

private const val CAMERA_Z = 5f

private fun project(v: Vec3, scale: Float, cx: Float, cy: Float): Offset {
    val persp = CAMERA_Z / (CAMERA_Z - v.z)
    return Offset(cx + v.x * scale * persp, cy + v.y * scale * persp)
}

private val CUBE_VERTS = listOf(
    Vec3(-1f, -1f, -1f),  // 0
    Vec3(1f, -1f, -1f),   // 1
    Vec3(1f, 1f, -1f),    // 2
    Vec3(-1f, 1f, -1f),   // 3
    Vec3(-1f, -1f, 1f),   // 4
    Vec3(1f, -1f, 1f),    // 5
    Vec3(1f, 1f, 1f),     // 6
    Vec3(-1f, 1f, 1f),    // 7
)

private data class FaceDef(val value: Int, val indices: List<Int>, val pips: List<Vec3>)

// Each face's vertex indices are CCW when viewed from outside, so the cross
// product (v1-v0)×(v2-v0) gives the outward-pointing normal.
private val CUBE_FACES = listOf(
    FaceDef(value = 1, indices = listOf(4, 5, 6, 7), pips = listOf(Vec3(0f, 0f, 1f))),
    FaceDef(
        value = 6, indices = listOf(1, 0, 3, 2),
        pips = listOf(
            Vec3(-0.5f, -0.5f, -1f), Vec3(-0.5f, 0f, -1f), Vec3(-0.5f, 0.5f, -1f),
            Vec3(0.5f, -0.5f, -1f), Vec3(0.5f, 0f, -1f), Vec3(0.5f, 0.5f, -1f),
        ),
    ),
    FaceDef(
        value = 2, indices = listOf(5, 1, 2, 6),
        pips = listOf(Vec3(1f, -0.5f, 0.5f), Vec3(1f, 0.5f, -0.5f)),
    ),
    FaceDef(
        value = 5, indices = listOf(0, 4, 7, 3),
        pips = listOf(
            Vec3(-1f, -0.5f, -0.5f), Vec3(-1f, -0.5f, 0.5f),
            Vec3(-1f, 0f, 0f),
            Vec3(-1f, 0.5f, -0.5f), Vec3(-1f, 0.5f, 0.5f),
        ),
    ),
    FaceDef(
        value = 4, indices = listOf(7, 6, 2, 3),
        pips = listOf(
            Vec3(-0.5f, 1f, 0.5f), Vec3(0.5f, 1f, 0.5f),
            Vec3(-0.5f, 1f, -0.5f), Vec3(0.5f, 1f, -0.5f),
        ),
    ),
    FaceDef(
        value = 3, indices = listOf(0, 1, 5, 4),
        pips = listOf(
            Vec3(-0.5f, -1f, -0.5f),
            Vec3(0f, -1f, 0f),
            Vec3(0.5f, -1f, 0.5f),
        ),
    ),
)

// Maps a face value -> the (rotX, rotY) that places that face directly toward
// the camera (+Z). rotZ is free (spins the face around its own normal).
private fun endRotForFace(face: Int): Pair<Float, Float> = when (face) {
    1 -> Pair(0f, 0f)
    2 -> Pair(0f, -(PI / 2).toFloat())
    3 -> Pair(-(PI / 2).toFloat(), 0f)
    4 -> Pair((PI / 2).toFloat(), 0f)
    5 -> Pair(0f, (PI / 2).toFloat())
    6 -> Pair(PI.toFloat(), 0f)
    else -> Pair(0f, 0f)
}

// Stable directional light for Lambert shading. Slightly upper-left of camera.
private val LIGHT_DIR = Vec3(0.35f, -0.55f, 0.75f).normalized()

private data class DrawnFace(
    val polygon: List<Offset>,
    val normal: Vec3,
    val depth: Float,
    val pips: List<Vec3>,
    val faceCenter: Vec3,
)

private fun DrawScope.drawDie3D(rotX: Float, rotY: Float, rotZ: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val scale = size.minDimension * 0.32f

    val rv = CUBE_VERTS.map { it.rotated(rotX, rotY, rotZ) }
    val pv = rv.map { project(it, scale, cx, cy) }

    val drawn = CUBE_FACES.mapNotNull { face ->
        val pts3 = face.indices.map { rv[it] }
        val pts2 = face.indices.map { pv[it] }
        val e1 = pts3[1] - pts3[0]
        val e2 = pts3[2] - pts3[0]
        val normal = e1.cross(e2).normalized()
        // Camera looks down -Z; visible faces have positive normal.z.
        if (normal.z <= 0.02f) return@mapNotNull null
        val depth = (pts3[0].z + pts3[1].z + pts3[2].z + pts3[3].z) / 4f
        val faceCenter = Vec3(
            (pts3[0].x + pts3[1].x + pts3[2].x + pts3[3].x) / 4f,
            (pts3[0].y + pts3[1].y + pts3[2].y + pts3[3].y) / 4f,
            depth,
        )
        DrawnFace(pts2, normal, depth, face.pips.map { it.rotated(rotX, rotY, rotZ) }, faceCenter)
    }.sortedBy { it.depth }  // back-to-front (smaller z first since +Z is toward camera).

    // Cast shadow base plate behind each face for slight depth pop.
    for (df in drawn) {
        val intensity = (df.normal.dot(LIGHT_DIR).coerceAtLeast(0f) * 0.65f + 0.32f).coerceAtMost(1f)

        // Ivory body with a subtle inner gradient toward the lit corner.
        val poly = Path().apply {
            moveTo(df.polygon[0].x, df.polygon[0].y)
            for (i in 1 until df.polygon.size) lineTo(df.polygon[i].x, df.polygon[i].y)
            close()
        }
        val baseR = 0.97f
        val baseG = 0.93f
        val baseB = 0.82f
        val fillBase = Color(red = baseR * intensity, green = baseG * intensity, blue = baseB * intensity, alpha = 1f)
        drawPath(poly, color = fillBase)

        // Light gradient overlay (additive specular feel) — brighter near the
        // face center along the light direction.
        val polyMin = df.polygon.let { pts ->
            Offset(pts.minOf { it.x }, pts.minOf { it.y })
        }
        val polyMax = df.polygon.let { pts ->
            Offset(pts.maxOf { it.x }, pts.maxOf { it.y })
        }
        val gradStart = Offset(
            polyMin.x + (polyMax.x - polyMin.x) * 0.20f,
            polyMin.y + (polyMax.y - polyMin.y) * 0.18f,
        )
        val gradEnd = Offset(
            polyMin.x + (polyMax.x - polyMin.x) * 0.90f,
            polyMin.y + (polyMax.y - polyMin.y) * 0.92f,
        )
        drawPath(
            poly,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.20f * intensity),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.18f),
                ),
                start = gradStart,
                end = gradEnd,
            ),
        )

        // Gold edge accents.
        drawPath(
            poly,
            color = Color(0xFFB8893A).copy(alpha = 0.9f),
            style = Stroke(width = 2.8f),
        )

        // Pips — projected from 3D so they sit correctly even with perspective.
        for (pip in df.pips) {
            val pos = project(pip, scale, cx, cy)
            val persp = CAMERA_Z / (CAMERA_Z - pip.z)
            val pipR = scale * 0.15f * persp
            // Pip well (dark recess).
            drawCircle(
                color = Color(0xFF120A33),
                radius = pipR,
                center = pos,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF2B175C), Color(0xFF050214)),
                    center = Offset(pos.x - pipR * 0.30f, pos.y - pipR * 0.30f),
                    radius = pipR,
                ),
                radius = pipR * 0.88f,
                center = pos,
            )
            // Highlight glint.
            drawCircle(
                color = Color.White.copy(alpha = 0.45f * intensity),
                radius = pipR * 0.22f,
                center = Offset(pos.x - pipR * 0.32f, pos.y - pipR * 0.32f),
            )
        }
    }
}
