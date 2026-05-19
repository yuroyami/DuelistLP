package app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.audio.LocalLpSfx
import app.audio.LocalOst
import app.audio.LpSfxController
import app.audio.OstController
import app.audio.OstTracks
import app.nav.Screen
import app.persistence.DuelSettings
import app.persistence.DuelStoreFactory
import app.ui.duel.DuelScreen
import app.ui.history.HistoryScreen
import app.ui.history.MatchDetailScreen
import app.ui.rps.RpsScreen
import app.ui.setup.SetupScreen
import app.ui.theme.DuelTheme

/**
 * App root. Owns:
 *   - the [Screen] navigation state (single mutableStateOf — no nav library)
 *   - app-singleton [DuelStoreFactory] for persistence
 *   - app-singleton audio controllers ([OstController], [LpSfxController])
 *
 * Per-screen background music is set here for non-Duel screens; DuelScreen
 * drives its own music via [app.audio.OstEventScheduler].
 *
 * Subscribes to lifecycle events to pause/resume music when backgrounded.
 */
@Composable
fun App() {
    val store = remember { DuelStoreFactory.create() }
    val audioScope = rememberCoroutineScope()
    val ost = remember { OstController(audioScope) }
    val lpSfx = remember { LpSfxController(audioScope).also { it.preload() } }

    DisposableEffect(Unit) {
        onDispose {
            ost.release()
            lpSfx.release()
        }
    }

    // Pause OST on backgrounding, resume on foregrounding.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> ost.pauseForBackground()
                Lifecycle.Event.ON_RESUME -> ost.resumeFromBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var screen: Screen by remember { mutableStateOf(Screen.Setup) }

    // Music-enabled flag from persistence — gates the per-screen background
    // music below. DuelScreen reads the same flag for its event scheduler.
    val settings by store.settings.collectAsState(initial = DuelSettings(musicEnabled = true))
    val musicEnabled = settings.musicEnabled

    // Per-screen background music for non-Duel screens. DuelScreen owns its
    // own music routing through OstEventScheduler.
    LaunchedEffect(screen::class, musicEnabled) {
        if (!musicEnabled) {
            ost.stop()
            return@LaunchedEffect
        }
        when (screen) {
            is Screen.Setup, is Screen.History, is Screen.MatchDetail ->
                ost.play(OstTracks.Main)
            is Screen.Rps -> ost.play(OstTracks.Rps)
            is Screen.Duel -> { /* DuelScreen drives OST via OstEventScheduler */ }
        }
    }

    CompositionLocalProvider(
        LocalOst provides ost,
        LocalLpSfx provides lpSfx,
    ) {
        DuelTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                when (val s = screen) {
                    is Screen.Setup -> SetupScreen(
                        store = store,
                        onStartDuel = { p1, p2, lp ->
                            screen = Screen.Rps(p1, p2, lp)
                        },
                        onOpenHistory = { screen = Screen.History },
                    )

                    is Screen.Rps -> RpsScreen(
                        player1 = s.player1,
                        player2 = s.player2,
                        onResolved = { firstPlayer, p1Pick, p2Pick, rounds ->
                            screen = Screen.Duel(
                                player1 = s.player1,
                                player2 = s.player2,
                                startingLp = s.startingLp,
                                firstPlayer = firstPlayer,
                                rpsRounds = rounds,
                                rpsP1Pick = p1Pick,
                                rpsP2Pick = p2Pick,
                            )
                        },
                    )

                    is Screen.Duel -> DuelScreen(
                        player1 = s.player1,
                        player2 = s.player2,
                        startingLp = s.startingLp,
                        firstPlayer = s.firstPlayer,
                        rpsRounds = s.rpsRounds,
                        rpsP1Pick = s.rpsP1Pick,
                        rpsP2Pick = s.rpsP2Pick,
                        store = store,
                        onExit = { screen = Screen.Setup },
                        onRematch = { screen = Screen.Rps(s.player1, s.player2, s.startingLp) },
                    )

                    is Screen.History -> HistoryScreen(
                        store = store,
                        onBack = { screen = Screen.Setup },
                        onOpen = { match -> screen = Screen.MatchDetail(match) },
                    )

                    is Screen.MatchDetail -> MatchDetailScreen(
                        match = s.match,
                        onBack = { screen = Screen.History },
                    )
                }
            }
        }
    }
}
