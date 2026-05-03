package app.audio

import androidx.compose.runtime.staticCompositionLocalOf

// CompositionLocals provided by App.kt. Static (not dynamic) because the
// controllers are app-singletons that don't change for the composition's
// lifetime — readers don't need to re-compose when "the" controller changes.

val LocalOst = staticCompositionLocalOf<OstController> {
    error("OstController not provided — wrap UI in App()")
}

val LocalLpSfx = staticCompositionLocalOf<LpSfxController> {
    error("LpSfxController not provided — wrap UI in App()")
}
