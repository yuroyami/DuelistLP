package app.audio

import androidx.compose.runtime.staticCompositionLocalOf

val LocalOst = staticCompositionLocalOf<OstController> {
    error("OstController not provided — wrap UI in App()")
}

val LocalLpSfx = staticCompositionLocalOf<LpSfxController> {
    error("LpSfxController not provided — wrap UI in App()")
}
