package app.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Tap once → fires [onTick] once.
 * Hold → fires [onTick] repeatedly, ramping from a slow initial cadence
 * down to a fast floor cadence the longer the press is held.
 *
 * The composable is intentionally "headless"; the caller supplies any visual
 * (background, label, icon) via [content].
 */
@Composable
fun RepeatingButton(
    onTick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = LocalContentColor.current,
    content: @Composable () -> Unit,
) {
    val tickState = rememberUpdatedState(onTick)
    Box(
        modifier = modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    // Spawn the tick loop as a child job and cancel it on release.
                    // (detectTapGestures.onPress does NOT auto-cancel on release; if
                    // we just delay-loop here it runs forever after a single tap.)
                    coroutineScope {
                        val tickJob = launch {
                            tickState.value.invoke()
                            delay(LONG_PRESS_THRESHOLD_MS)
                            var interval = INITIAL_INTERVAL_MS
                            while (isActive) {
                                tickState.value.invoke()
                                delay(interval)
                                interval = (interval - INTERVAL_STEP_MS)
                                    .coerceAtLeast(MIN_INTERVAL_MS)
                            }
                        }
                        try {
                            tryAwaitRelease()
                        } finally {
                            tickJob.cancel()
                        }
                    }
                },
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() }
        }
    }
}

private const val LONG_PRESS_THRESHOLD_MS = 320L
private const val INITIAL_INTERVAL_MS = 180L
private const val INTERVAL_STEP_MS = 12L
private const val MIN_INTERVAL_MS = 35L
