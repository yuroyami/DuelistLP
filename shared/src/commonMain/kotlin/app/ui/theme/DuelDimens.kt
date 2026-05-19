package app.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Responsive design tokens for the whole app — spacing, sizing, typography
 * sizes, corner radii, border widths, and component-specific heights.
 *
 * Read via [DuelTheme.dimens] inside any composable wrapped by [DuelTheme].
 * All values scale with the viewport so the same composable looks tuned on a
 * tiny phone, a tablet, or a wall display.
 *
 * Touch-target heights ([touchSm], [touchMd], [touchLg]) are clamped to never
 * fall below Material's 44/48/56 dp minimums regardless of how small the
 * viewport is — accessibility wins over aesthetic shrinkage.
 */
@Immutable
data class DuelDimens(
    /** Linear scale factor; 1.0 at reference width (~375dp). Clamped 0.85–1.45. */
    val scale: Float,

    // ============ Spacing scale ============
    val s2: Dp,
    val s4: Dp,
    val s6: Dp,
    val s8: Dp,
    val s10: Dp,
    val s12: Dp,
    val s14: Dp,
    val s16: Dp,
    val s20: Dp,
    val s24: Dp,
    val s32: Dp,

    // ============ Touch targets ============
    /** Dense buttons, secondary controls. Never < 44 dp. */
    val touchSm: Dp,
    /** Material standard. Never < 48 dp. */
    val touchMd: Dp,
    /** Emphasized primary. Never < 56 dp. */
    val touchLg: Dp,
    /** Hero / swipe / large slider. */
    val touchXl: Dp,

    // ============ Corner radii ============
    val radiusXs: Dp,
    val radiusSm: Dp,
    val radiusMd: Dp,
    val radiusLg: Dp,
    /** Pill / circle — always 50% (independent of scale). */
    val radiusPill: Dp,

    // ============ Border widths ============
    val borderHairline: Dp,
    val borderStandard: Dp,
    val borderEmphasized: Dp,
    val borderBold: Dp,

    // ============ Component heights ============
    val opponentPeekHeight: Dp,
    val centerBarHeight: Dp,
    val phaseTrackerHeight: Dp,
    val bufferStripHeight: Dp,
    val bufferActionRowHeight: Dp,
    val endTurnRowHeight: Dp,
    val bottomRowHeight: Dp,
    val swipeThumbWidth: Dp,
    val swipeThumbInset: Dp,
    val actionIconSize: Dp,
    val actionGlowSize: Dp,

    // ============ Text size tokens ============
    /** 9sp scaled — micro badges, sublabels. */
    val textMicro: TextUnit,
    /** 11sp scaled — small chips, FIRST badge, gauge label. */
    val textTiny: TextUnit,
    /** 12sp scaled — button labels, chip text. */
    val textSmall: TextUnit,
    /** 13sp scaled — emphasized small labels. */
    val textCompact: TextUnit,
    /** 14sp scaled — body text, quit button. */
    val textBody: TextUnit,
    /** 16sp scaled — emphasized body, overlay button labels. */
    val textBodyLg: TextUnit,
    /** 18sp scaled — opponent LP readout (inline). */
    val textReadout: TextUnit,
    /** 22sp scaled — icon button glyphs. */
    val textIcon: TextUnit,
    /** 34sp scaled — peek LP digits. */
    val textPeekLp: TextUnit,
    /** 40sp scaled — overlay result text. */
    val textOverlayResult: TextUnit,

    // ============ Letter spacing ============
    val trackTight: TextUnit,
    val trackNormal: TextUnit,
    val trackWide: TextUnit,
    val trackExtraWide: TextUnit,
)

internal val LocalDuelDimens = compositionLocalOf<DuelDimens> {
    error("DuelDimens not provided — wrap content in DuelTheme.")
}

/**
 * Compute scaled dimensions from the available viewport.
 *
 * Reference width: 375 dp (≈ iPhone 12 mini, iPhone SE). Larger phones and
 * tablets scale up to 1.45×; tiny windows scale down to 0.85×. Min/max touch
 * targets are clamped separately so accessibility never breaks.
 */
internal fun computeDuelDimens(widthDp: Float, heightDp: Float): DuelDimens {
    val shortest = kotlin.math.min(widthDp, heightDp)
    val raw = shortest / 375f
    val scale = raw.coerceIn(0.85f, 1.45f)

    fun d(base: Float): Dp = (base * scale).dp
    fun s(base: Float): TextUnit = (base * scale).sp

    // Touch-target heights never fall below the Material minimums even if
    // the device is tiny.
    val touchSm = maxOf(d(44f), 44.dp)
    val touchMd = maxOf(d(48f), 48.dp)
    val touchLg = maxOf(d(56f), 56.dp)
    val touchXl = maxOf(d(64f), 60.dp)

    return DuelDimens(
        scale = scale,

        s2 = d(2f),
        s4 = d(4f),
        s6 = d(6f),
        s8 = d(8f),
        s10 = d(10f),
        s12 = d(12f),
        s14 = d(14f),
        s16 = d(16f),
        s20 = d(20f),
        s24 = d(24f),
        s32 = d(32f),

        touchSm = touchSm,
        touchMd = touchMd,
        touchLg = touchLg,
        touchXl = touchXl,

        radiusXs = d(4f),
        radiusSm = d(8f),
        radiusMd = d(12f),
        radiusLg = d(16f),
        radiusPill = 50.dp,

        // Borders don't scale linearly — they need to stay perceptible.
        borderHairline = 1.dp,
        borderStandard = 1.5.dp,
        borderEmphasized = 2.dp,
        borderBold = 2.5.dp,

        opponentPeekHeight = maxOf(d(72f), 64.dp),
        centerBarHeight = touchMd,
        phaseTrackerHeight = maxOf(d(34f), 30.dp),
        bufferStripHeight = maxOf(d(82f), 72.dp),
        bufferActionRowHeight = maxOf(d(36f), 36.dp),
        endTurnRowHeight = maxOf(d(60f), 56.dp),
        bottomRowHeight = maxOf(d(56f), 52.dp),
        swipeThumbWidth = d(64f),
        swipeThumbInset = d(5f),
        actionIconSize = maxOf(d(48f), 44.dp),
        actionGlowSize = d(68f),

        textMicro = s(9f),
        textTiny = s(11f),
        textSmall = s(12f),
        textCompact = s(13f),
        textBody = s(14f),
        textBodyLg = s(16f),
        textReadout = s(18f),
        textIcon = s(22f),
        textPeekLp = s(34f),
        textOverlayResult = s(38f),

        trackTight = s(0.5f),
        trackNormal = s(0.8f),
        trackWide = s(1.5f),
        trackExtraWide = s(2.5f),
    )
}

/**
 * Wraps [content] with a [DuelDimens] computed from the available viewport.
 * Single source of truth for all spacing/sizing across the app.
 */
@Composable
internal fun ProvideDuelDimens(content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier) {
        val widthDp = maxWidth.value
        val heightDp = maxHeight.value
        val dimens = remember(widthDp, heightDp) {
            computeDuelDimens(widthDp, heightDp)
        }
        CompositionLocalProvider(LocalDuelDimens provides dimens, content = content)
    }
}

/** Shortcut accessor: `DuelTheme.dimens.s12`. */
object DuelTheme {
    val dimens: DuelDimens
        @Composable
        @ReadOnlyComposable
        get() = LocalDuelDimens.current
}
