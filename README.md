<h1 align="center">DuelistLP</h1>

<p align="center">A Yu-Gi-Oh-style life-points tracker for two-player table duels.</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0+-a4c639?logo=android&logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/iOS-14.0+-555?logo=apple&logoColor=white" alt="iOS 14.0+">
  <img src="https://img.shields.io/badge/Kotlin_Multiplatform-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin Multiplatform">
  <img src="https://img.shields.io/badge/Compose_Multiplatform-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose Multiplatform">
</p>

---

Drop the phone flat on the table between you and your opponent. Set starting LP, name both duelists, blind-pick who goes first with a blurred Rock-Paper-Scissors shuffle, then start whittling each other down. Every life-points card renders the number twice in the same canvas — once upright, once rotated 180° — so both sides of the table read it head-on, like the mirror lettering on an ambulance hood. Every LP change rolls digit-by-digit on a slot-machine reel and triggers a looping SFX that resolves the instant the points settle.

## Features

- **Split-screen duel field** — top half rotated 180° so two players sitting across a table both see their controls right-side-up.
- **Mirror-printed LP** — each LP card renders the value upright AND inverted inside the same `yugi_lp_bg.webp` canvas. Either side of the table reads it head-on.
- **Animated digit rolls** — every digit lives in its own clipped slot and slides up on gain / down on loss. Italic Heuristica with a black stroke and yellow fill, exactly the anime look.
- **Long-press accelerating ± buttons** — tap once for one step, hold to ramp from 180 ms cadence down to a 35 ms floor for fast burns.
- **Five increment steps** — `10 / 100 / 500 / 1000 / Custom` (numeric dialog) per player, switchable mid-match.
- **Self / Opponent target toggle** — each player can adjust their own LP or the opponent's; controls stay confined to that player's half of the screen, defaulting to Self.
- **Random-pick RPS shuffler** — to decide who goes first, a horizontal ✊✋✌ strip scrolls past at 220 ms per cycle. Tap anywhere to lock in a uniformly random pick — the blur makes the choice truly blind. Ties auto-replay with a round counter.
- **Dynamic OST** — six anime tracks switch in real time based on game state:
  - Setup screen → main theme
  - First-pick screen → RPS theme
  - Both LP ≥ half-starting → duel theme
  - One player below half-starting → losing theme
  - Both below half-starting → tournament theme
  - A player crosses back **up** through half-starting (a comeback) → winning theme
  - Victory → winning theme
  - Threshold tracks starting LP automatically — half of 8000 is 4000, half of 16000 is 8000, etc.
- **Two-clip LP SFX** — `life-points-change-loop.mp3` ticks while LP is changing; once it has been stable for 320 ms, `life-points-settle.mp3` plays once for the resolution chord.
- **Persistent match history** — every duel saves to local DataStore with a full event log: every LP change (delta + before + after + timestamp), the RPS resolution + round count, the victory marker. Browse past matches; tap one to see every swing chronologically.
- **Auto-fit typography** — LP digits, victory banners, RPS reveal glyphs and the Setup title all measure their container with `BoxWithConstraints` and pick a font size from it. iPhone XS and iPhone Pro Max both fit cleanly without a single hardcoded sp value.

## Platform Support

| Target      | Status                  |
|:------------|:------------------------|
| **Android** | 8.0 (API 26) and above  |
| **iOS**     | 14.0 and above          |

Built from a single Compose Multiplatform shared module. Audio runs on Media3 ExoPlayer (Android) and `AVPlayer` (iOS) behind a one-method `AudioEngine` `expect`/`actual` interface.

## Building

**Requirements**

- JDK 21 (auto-provisioned by the Gradle toolchain)
- Android Studio with Kotlin 2.3+, Gradle 9
- Xcode 16+ for iOS
- `kmp-ssot` plugin available via `mavenLocal` (until it's live on the Gradle Plugin Portal)

**Android**

```bash
./gradlew :androidApp:installDebug
```

**iOS**

```bash
open iosApp/iosApp.xcodeproj
```

The Xcode build phase invokes `:shared:embedAndSignAppleFrameworkForXcode` automatically.

## Identity SSOT

App name, version, and bundle ID live in **one** place — the `kmpSsot { … }` block in the root `build.gradle.kts`:

```kotlin
kmpSsot {
    appName      = "DuelistLP"
    versionName  = "0.1.0"
    bundleIdBase = "com.yuroyami.duelistlp"
    javaVersion  = 21
}
```

The plugin propagates those values to `AndroidManifest.xml` (`${appName}` placeholder), `applicationId`, version codes, the iOS `pbxproj` (`MARKETING_VERSION`, `CURRENT_PROJECT_VERSION`, `PRODUCT_BUNDLE_IDENTIFIER`) and `Configuration/Config.xcconfig`. Renaming the app is a one-line change.

## Layout

```
shared/src/commonMain/kotlin/app/
├── App.kt                       — root composable + navigation
├── audio/                       — AudioEngine (expect), OstController, LpSfxController, DuelOstPicker
├── model/                       — Match, MatchEvent, DuelState, RpsPick
├── nav/Screen.kt                — sealed nav graph
├── persistence/DuelStore.kt     — DataStore wrapper (singleton via expect/actual)
├── ui/components/               — StrokedText, AnimatedLifePoints, RepeatingButton
├── ui/duel/                     — DuelScreen, PlayerHalf, LpBox, VictoryOverlay
├── ui/history/                  — HistoryScreen, MatchDetailScreen
├── ui/rps/RpsScreen.kt          — shuffle marquee + reveal
├── ui/setup/SetupScreen.kt      — landing screen
└── ui/theme/                    — DuelTheme, Heuristica typography
shared/src/commonMain/composeResources/
├── drawable/yugi_lp_bg.webp
├── font/heuristica_*.otf
├── files/ost/yugioh-*.mp3       — six background tracks
└── files/sfx/life-points-*.mp3  — change-loop + settle
```

## Feedback

[Open an issue](https://github.com/yuroyami/yugioh-lp-tracker/issues/new) or [start a discussion](https://github.com/yuroyami/yugioh-lp-tracker/discussions).

---

<p align="center"><sub>Personal project. Yu-Gi-Oh and all related properties are trademarks of Konami; this app is a fan-made life-points tracker, not affiliated.</sub></p>
