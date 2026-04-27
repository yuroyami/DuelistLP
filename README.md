# KmpApp — a minimal, opinionated Kotlin Multiplatform template

One codebase. One composable. **Six supported binary targets:** Android APK/AAB,
iOS IPA, Windows `.exe`/`.msi`, macOS `.dmg`, Linux `.deb`/`.rpm`, and a static
Web bundle.

This template is a starting point — not a framework. It ships with exactly the
machinery you will reach for on day one of every Compose Multiplatform project,
and nothing you won't.

---

## Why this template over every other KMP starter

Most KMP starters fall into one of two camps:

1. **Demo-shaped.** They show off every library the author was excited about —
   Ktor, Koin, Voyager, DataStore, Media3, SQLDelight, Decompose, CocoaPods,
   custom logging — before you have even decided which of those you need. You
   spend your first day deleting code.
2. **Toy-shaped.** They're wired up for one platform well (usually Android) and
   the iOS/Desktop/Web targets are "stubs for later." You spend your first week
   figuring out why the framework doesn't link, why Webpack can't find the
   output, and why there's no `.dmg` task.

**This template refuses both.** It's a production-grade multi-target Gradle
build with a single `App()` composable, nothing between you and your product,
and a single source of truth for every piece of app identity.

### The concrete differences

| Concern | Typical KMP template | This template |
|---|---|---|
| App name, bundle ID, version | Scattered across 6+ files | **One `AppConfig.kt`** propagates to Gradle, Xcode `pbxproj`, `Config.xcconfig`, `AndroidManifest`, `index.html`, and a generated `AppConstants.kt` for runtime reads |
| Platform icons | Hand-drop 30+ PNGs into 9 folders | **One Kotlin file** (`LogoGenerator.kt`) draws the logo and emits every Android mipmap, iOS AppIconSet entry, `.ico`, `.icns`, Linux PNG, and PWA favicon — on every Gradle sync |
| Desktop distributions | "Run from IDE" only | `./gradlew :shared:packageDistributionForCurrentOS` → real `.dmg` / `.msi` / `.exe` / `.deb` / `.rpm` with the right icon, bundle ID, and upgrade UUID |
| Web bundle | JS plugin applied, output filename not synced | `./gradlew :shared:jsBrowserDistribution` → a static folder you can drop on any CDN, with `<title>`, script name, favicon, and PWA manifest all derived from `AppConfig` |
| Dependencies | 15+ libraries you'll rip out | Only Compose Multiplatform + kotlinx. Everything else is a choice you make when you need it |
| iOS integration | Manual pbxproj edits drift from Gradle | AppConfig's `propagateAll()` rewrites pbxproj and xcconfig on every sync; changing the app name in one place renames the Xcode product |
| Package hierarchy | `com.example.myapp.feature.home.ui.compose.screens` out of the box | `app.App()`. That's it. Add packages when you have reason to |

---

## Quick start

```bash
./gradlew :shared:generateAppIcons        # runs automatically on sync, but forces it once
./gradlew :androidApp:installDebug        # Android (needs a connected device/emulator)
./gradlew :shared:jsBrowserDevelopmentRun # Web, served at http://localhost:8080
./gradlew :shared:run                     # JVM desktop
```

For iOS, open `iosApp/iosApp.xcodeproj` in Xcode and press Run. A build phase
invokes `./gradlew :shared:embedAndSignAppleFrameworkForXcode` automatically.

### Producing release binaries

```bash
# Android
./gradlew :androidApp:assembleRelease       # APK
./gradlew :androidApp:bundleRelease         # AAB

# iOS — use Xcode's Archive action, or:
./gradlew :shared:assembleSharedReleaseXCFramework

# Desktop (current OS only)
./gradlew :shared:packageDistributionForCurrentOS
# Produces: .dmg on macOS, .msi + .exe on Windows, .deb + .rpm on Linux.
# Outputs under shared/build/compose/binaries/main/

# Web static bundle
./gradlew :shared:jsBrowserDistribution
# Outputs under shared/build/dist/js/productionExecutable/
```

---

## The single source of truth

Everything that makes this app *this* app lives in
[`buildSrc/src/main/kotlin/AppConfig.kt`](buildSrc/src/main/kotlin/AppConfig.kt):

```kotlin
object AppConfig {
    const val appName = "KmpApp"
    const val bundleId = "com.example.kmpapp"
    const val versionName = "0.1.0"
    // ...
    const val logoInitials = "K"
    const val logoBackgroundColor = 0xFF2962FFL.toInt()
    const val logoForegroundColor = 0xFFFFFFFFL.toInt()
}
```

On every Gradle sync, `AppConfig.propagateAll()` rewrites:

- `iosApp/iosApp.xcodeproj/project.pbxproj` — `MARKETING_VERSION`,
  `CURRENT_PROJECT_VERSION`, `PRODUCT_NAME`, `PRODUCT_BUNDLE_IDENTIFIER`,
  `INFOPLIST_KEY_CFBundleDisplayName`, and the `.app` path
- `iosApp/Configuration/Config.xcconfig` — `APP_NAME`, `BUNDLE_ID`
- `shared/src/jsMain/resources/index.html` — `<title>` and `<script src>`
- `androidApp/src/main/res/values/strings.xml` — `app_name`
- `shared/build/generated/kotlin/app/AppConstants.kt` — runtime-readable
  constants for the desktop window title and anywhere else you need the name

The Android build script reads the constants directly. The Xcode project reads
them through the xcconfig file and baked pbxproj values.

### To rename the app

Change `appName` and `bundleId` in `AppConfig.kt`, then run any Gradle task.
That's it. Every platform updates.

### To replace the logo

**Option A (no artwork).** Change `logoInitials` and the two color hexes in
`AppConfig.kt`. `LogoGenerator.kt` draws a new master 1024×1024 PNG in pure
Java2D and regenerates every platform asset from it.

**Option B (your own artwork).** Drop a 1024×1024 PNG at `logo/override.png`.
The generator detects it and uses it instead of the programmatic draw. The
file is gitignored by default so every contributor can have their own.

---

## What lives where

```
kmpTemplate/
├── buildSrc/src/main/kotlin/
│   ├── AppConfig.kt              ← SSOT for name, ID, version, logo config
│   └── LogoGenerator.kt          ← pure-Kotlin icon pipeline (Java2D + ICO/ICNS writers)
├── gradle/libs.versions.toml     ← exactly Compose MP + kotlinx + Android glue
├── settings.gradle.kts           ← rootProject.name = AppConfig.appName
├── build.gradle.kts              ← plugin declarations only
│
├── shared/
│   ├── build.gradle.kts          ← KMP targets + compose.desktop distributions + icon task
│   └── src/
│       ├── commonMain/kotlin/app/App.kt          ← the single root composable
│       ├── androidMain/kotlin/app/AppActivity.kt ← setContent { App() }
│       ├── iosMain/kotlin/app/MainViewController.kt ← ComposeUIViewController { App() }
│       ├── jvmMain/kotlin/app/Main.kt            ← Window { App() }
│       └── jsMain/
│           ├── kotlin/app/Main.kt                ← ComposeViewport { App() }
│           └── resources/index.html              ← loaded by the JS plugin
│
├── androidApp/
│   ├── build.gradle.kts          ← thin wrapper: reads everything from AppConfig
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/values/themes.xml
│       └── res/values/strings.xml (generated)
│
└── iosApp/
    ├── iosApp.xcodeproj/         ← Xcode project (values rewritten by AppConfig)
    ├── Configuration/Config.xcconfig (rewritten by AppConfig)
    └── iosApp/
        ├── iOSApp.swift          ← @main
        ├── KmpScreen.swift       ← UIViewControllerRepresentable → MainViewController()
        └── Info.plist
```

The Kotlin package is always `app`. There is no `com.yourcompany.yourapp.feature`
ceremony. If you want layers, add them when you have a second screen.

---

## The dependency policy

The rule: **only ship what every Compose Multiplatform app uses.**

### Included (always used)

- **Compose Multiplatform** — UI for every target. `compose.runtime`,
  `compose.foundation`, `compose.ui`, `compose.components.resources`,
  `compose.material3` (the only Compose artifact that ships separately).
- **kotlinx.coroutines** — structured concurrency. Compose depends on it anyway.
- **kotlinx.datetime** — every app eventually formats a date.
- **kotlinx.serialization** — Navigation, DataStore, Ktor, and any future state
  persistence assume it. Cheap to include, expensive to add later.
- **AndroidX Activity Compose** + **core library desugaring** — minimal glue
  required to host Compose on Android and support `java.time` below API 26.

### Deliberately excluded

- Ktor / any HTTP client — add when you need network
- Navigation3 / Voyager / Decompose — single-screen app, pick your nav lib
- Koin / Hilt / kotlin-inject — no DI until there's something to inject
- DataStore / SQLDelight / Room — no storage until there's something to store
- Kermit / any logging library — `println` is fine until it isn't
- Media3 / ExoPlayer — domain-specific

Every one of those is a two-line addition to `libs.versions.toml` when the time
comes. Adding later is cheap; rolling back a starter kit is expensive.

---

## Targets and build matrix

| Target | Source set | Entry point | Release artifact |
|---|---|---|---|
| Android (arm64/x86_64) | `androidMain` | `app.AppActivity` | APK / AAB |
| iOS (device + sim) | `iosMain` | `MainViewControllerKt.MainViewController()` | `shared.framework` embedded into `KmpApp.app` |
| JVM desktop (macOS) | `jvmMain` | `app.MainKt` | `.dmg` |
| JVM desktop (Windows) | `jvmMain` | `app.MainKt` | `.msi` + `.exe` |
| JVM desktop (Linux) | `jvmMain` | `app.MainKt` | `.deb` + `.rpm` |
| Web (browser) | `jsMain` | `app.MainKt` | static folder (JS + HTML + favicon + manifest) |

Min Android SDK is 26 (Android 8.0). iOS deployment target is 14.0. Desktop uses
the Java 21 toolchain. Web bundles to ES6 with IR-based incremental output.

---

## Upgrading Kotlin, Compose, or AGP

Every version is in `gradle/libs.versions.toml`. No version numbers hide in
build scripts. Bumping `kotlin`, `agp`, or `composeMultiplatform` and running
`./gradlew build` is the upgrade path.

---

## License

Add your own LICENSE file. This template is a starting point, not a product —
you own the code from the moment you clone it.
