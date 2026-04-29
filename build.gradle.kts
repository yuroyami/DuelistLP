plugins {
    id("io.github.yuroyami.kmpssot") version "1.1.0"

    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.kotlin.android).apply(false)
    alias(libs.plugins.kSerialization).apply(false)

    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.compose.plugin).apply(false)

    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.kmp.library).apply(false)
}

kmpSsot {
    appName      = "DuelistLP"
    versionName  = "0.1.0"
    bundleIdBase = "com.yuroyami.duelistlp"
    javaVersion  = 21

    sharedModule     = "shared"
    androidAppModule = "androidApp"

    // Layered app logo — both PNGs are the full 108dp adaptive-icon canvas;
    // foreground content lives inside the inner ~61% safe zone.
    appLogoPngForeground = file("shared/src/commonMain/composeResources/drawable/yugilp-fg.png")
    appLogoPngBackground = file("shared/src/commonMain/composeResources/drawable/yugilp-bg.png")

    // Migration aid: removes drawable/ic_launcher.xml + values/ic_launcher_background.xml
    // left over from the pre-FG/BG plugin pipeline. Safe to leave on.
    cleanupLegacyLogoArtifacts = true
}
