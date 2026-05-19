package app.ui.duel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.audio.OstTrack
import app.ui.theme.DuelColors
import app.ui.theme.DuelTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One playable stinger entry for the [OneShotWheel]. The wheel toggles each
 * item via the OST controller's overlay engine — tap once to play, tap again
 * (while the same item is active) to stop.
 */
data class OneShotItem(
    val displayName: String,
    val track: OstTrack,
    val accent: Color,
)

/**
 * Radial picker for one-shot stingers. Tiles arrange in a circle starting at
 * the top, clockwise. Tap a tile to toggle its stinger; tap the dim scrim
 * outside the tiles to dismiss.
 *
 * Rotates 180° when [rotated] is true so the player who opened it reads it
 * upright across the table. The other player sees it inverted, but only one
 * player interacts at a time.
 *
 * 4 items by default: Tribute Summon · Fusion Summon · Special Summon · Exodia.
 */
@Composable
fun OneShotWheel(
    items: List<OneShotItem>,
    activeKey: String?,
    onToggle: (OneShotItem) -> Unit,
    onDismiss: () -> Unit,
    rotated: Boolean = false,
) {
    val d = DuelTheme.dimens
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        // Radius + tile size scale with the smaller screen dimension so the
        // wheel fits on tiny phones AND looks generous on tablets.
        val shortest = kotlin.math.min(maxWidth.value, maxHeight.value)
        val wheelRadius = (shortest * 0.30f).coerceIn(96f, 220f).dp
        val tileSize = (shortest * 0.21f).coerceIn(72f, 140f).dp

        Box(
            modifier = Modifier.graphicsLayer { if (rotated) rotationZ = 180f },
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            val radiusPx = with(density) { wheelRadius.toPx() }

            items.forEachIndexed { i, item ->
                val angle = (2 * PI * i / items.size) - PI / 2
                val x = (cos(angle) * radiusPx).toFloat()
                val y = (sin(angle) * radiusPx).toFloat()
                Tile(
                    item = item,
                    active = item.track.cacheKey == activeKey,
                    onClick = { onToggle(item) },
                    modifier = Modifier
                        .offset { IntOffset(x.toInt(), y.toInt()) }
                        .size(tileSize),
                )
            }

            Text(
                text = "ONE-SHOTS",
                color = DuelColors.DuelGoldGlow,
                fontWeight = FontWeight.ExtraBold,
                fontSize = d.textBody,
                letterSpacing = d.trackWide,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Tile(
    item: OneShotItem,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = DuelTheme.dimens
    val bg = if (active) item.accent else Color(0xFF1A1140)
    val border = if (active) DuelColors.DuelGoldGlow else item.accent.copy(alpha = 0.75f)
    val textColor = if (active) Color.Black else DuelColors.DuelGoldGlow
    Box(
        modifier = modifier
            .background(bg, CircleShape)
            .border(if (active) d.borderBold else d.borderStandard, border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = item.displayName,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = d.textTiny,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = d.s6),
        )
    }
}
