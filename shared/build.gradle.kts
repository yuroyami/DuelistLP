plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kSerialization)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "app"
        compileSdk = providers.gradleProperty("android.compileSdk").get().toInt()
        minSdk = providers.gradleProperty("android.minSdk").get().toInt()
        androidResources { enable = true }
    }

    listOf(
        iosSimulatorArm64(),
        iosArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "shared"
            isStatic = false
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        all {
            languageSettings {
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
                optIn("androidx.compose.material3.ExperimentalMaterial3Api")
                optIn("androidx.compose.ui.ExperimentalComposeUiApi")
                optIn("androidx.compose.animation.ExperimentalAnimationApi")
                optIn("androidx.compose.foundation.ExperimentalFoundationApi")
                optIn("kotlin.RequiresOptIn")
                optIn("kotlin.experimental.ExperimentalNativeApi")
                optIn("kotlin.time.ExperimentalTime")
                // Used only by iOS sources; harmless "unresolved marker" warning on Android.
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
                optIn("kotlinx.cinterop.BetaInteropApi")
            }
        }

        commonMain.dependencies {
            implementation(libs.bundles.compose.multiplatform)
            implementation(libs.bundles.kotlinx)
            implementation(libs.androidx.datastore.preferences.core)
        }

        androidMain.dependencies {
            implementation(libs.android.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.common)
        }
    }
}

/* ── Compose resources: pin generated package so imports are stable ──── */
compose.resources {
    publicResClass = true
    packageOfResClass = "app.resources"
    generateResClass = org.jetbrains.compose.resources.ResourcesExtension.ResourceClassGeneration.Always
}
