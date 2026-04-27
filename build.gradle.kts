plugins {
    id("io.github.yuroyami.kmpssot") version "1.0.4"

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
    // No logo propagation here: launcher icons currently live in the platform
    // trees as static files. Provide both `appLogoXml` and `appLogoPng` to
    // make the plugin own them.
}
