# DuelistLP

A Yu-Gi-Oh!–themed Life Point tracker for Android and iOS, built as a Kotlin Multiplatform app with Compose Multiplatform UI. Two players, one device — track LP, settle who goes first with rock-paper-scissors, and let the soundtrack escalate as the duel tightens.

The app is fully offline. No accounts, no network, no analytics, no permissions beyond what audio playback needs.

---

## Features

- **Dual-sided LP tracker.** Two `PlayerHalf` panels — the second is rotated 180° so opponents seated across from each other both read their own side right-side-up.
- **Fast LP adjustments.** ±100, ±500, ±1000 quick buttons plus a custom-amount dialog. Long-press accelerates: ticks start at ~180 ms and ramp down to ~35 ms, so swinging from 8000 → 0 takes a couple of seconds.
- **Target toggle.** Each side can either modify its own LP or push damage onto the opponent — useful when one player is doing the math for both.
- **Rock-paper-scissors first-player picker.** Animated reveal, automatic re-rounds on ties. The result is recorded as a `FirstPlayerDecided` event in the match log.
- **Adaptive soundtrack.** Six looping OST tracks. The active track is chosen by `DuelOstPicker` against an LP threshold (half of starting LP):
  - both players above threshold → standard duel theme
  - one player below → losing theme
  - both below → tournament theme
  - LP recovery crossing back up → winning theme
- **LP-change SFX.** A looping tick plays while LP is moving; a settle chord resolves once the value stabilizes.
- **Match history.** Up to 200 matches persisted locally. Each match stores the full event timeline (every LP delta, the RPS rounds, the victory event) so you can replay it from the detail screen.
- **Configurable starting LP.** Presets for 2000 / 4000 / 8000 / 16000 plus custom input (minimum 100).
- **No light theme.** It's a Yu-Gi-Oh! app — purple/gold dark arena only.

---

## Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.3.21 |
| UI | Compose Multiplatform 1.11.0-beta03 (Material3 1.11.0-alpha07) |
| Build | Gradle + AGP 9.1.1, JDK 21 (with `desugar_jdk_libs 2.1.5` for older Android APIs) |
| Persistence | AndroidX DataStore Preferences 1.3.0-alpha08 |
| Serialization | kotlinx-serialization-json 1.11.0 |
| Concurrency | kotlinx-coroutines 1.10.2 |
| Time | kotlinx-datetime 0.7.1 |
| Audio (Android) | Media3 ExoPlayer 1.10.0 |
| Audio (iOS) | AVFoundation (`AVPlayer`) |
| Android targets | compileSdk 36, minSdk 26, targetSdk 36 |
| iOS targets | iosArm64 + iosSimulatorArm64, dynamic framework |

Project metadata (`appName`, `versionName`, `bundleIdBase`, `javaVersion`) is owned by the `io.github.yuroyami.kmpssot` Gradle plugin in the root `build.gradle.kts` — change it there, not in per-platform configs.

---

## Repository layout

```
.
├── shared/                          Kotlin Multiplatform module (UI + logic)
│   └── src/
│       ├── commonMain/              Compose UI, models, audio controllers, store
│       │   ├── kotlin/              ~2.7k LOC — App, screens, components, audio
│       │   └── composeResources/    fonts, OST mp3s, SFX mp3s, background webp
│       ├── androidMain/             AudioEngine.android.kt (ExoPlayer),
│       │                            DuelStore.android.kt (DataStore on filesDir)
│       └── iosMain/                 AudioEngine.ios.kt (AVPlayer),
│                                    DuelStore.ios.kt (DataStore in Documents),
│                                    MainViewController.kt (UIViewController bridge)
├── androidApp/                      Single-Activity wrapper
│   └── src/main/                    AppActivity.kt, AndroidManifest.xml, theme/icons
├── iosApp/                          SwiftUI wrapper hosting Compose
│   └── iosApp/                      iOSApp.swift, KmpScreen.swift, Assets
├── build.gradle.kts                 Root — kmp-ssot config (appName, bundleId, version)
├── settings.gradle.kts              Modules: :androidApp, :shared
├── gradle/libs.versions.toml        Central version catalog
└── local.properties                 SDK paths (gitignored)
```

Launcher icons are *generated* on every Gradle sync from the SSOT logo and are gitignored — see `.gitignore` for the regeneration entrypoint (`./gradlew :shared:generateAppIcons`).

---

## Architecture

**Navigation** is intentionally minimal: `App.kt` holds a single `mutableStateOf(Screen)` and switches between `Setup`, `Rps`, `Duel`, `History`, and `MatchDetail`. There's no nav library.

**State flow:**

1. `SetupScreen` collects player names + starting LP and persists them via `DuelStore.saveSettings(...)`.
2. `RpsScreen` runs the picking → revealing → result state machine and emits the first-player decision.
3. `DuelScreen` constructs an in-memory `DuelState` and mutates it as `MatchEvent`s arrive (`LpChange`, `Victory`). When a player hits 0 LP it writes a finalized `Match` to `DuelStore`.
4. `HistoryScreen` and `MatchDetailScreen` read the persisted match list as a reactive `Flow`.

**Models** (commonMain):

```kotlin
sealed class MatchEvent {
    data class LpChange(timestamp, target: PlayerSlot, delta, before, after)
    data class FirstPlayerDecided(timestamp, firstPlayer, p1Pick, p2Pick, rounds)
    data class Victory(timestamp, winner: PlayerSlot)
}
data class DuelState(matchId, startedAt, player1, player2, startingLp,
                     firstPlayer, p1Lp, p2Lp, events, winner)
data class Match(/* same fields as DuelState plus endedAt + final LPs */)
```

`Match` is `@Serializable` and stored as a JSON string in DataStore under `matches_json`. The store keeps the most recent 200; older matches are dropped.

**Audio** is driven by two controllers exposed through `CompositionLocal`:

- `LocalOst` → `OstController` — swaps the looping background track. Track changes are mutex-guarded so rapid LP swings don't race the player.
- `LocalLpSfx` → `LpSfxController` — runs the LP-change tick loop and fires the settle chord once the LP value has been stable for the debounce window.

Both sit on top of an `expect class AudioEngine` with two implementations:

- **Android** writes resource bytes into `cacheDir/audio/` and feeds the `file://` URI to a Media3 `ExoPlayer`. Initialized via `AudioEngineContext.init(context)` in `AppActivity.onCreate`.
- **iOS** writes bytes into `~/Library/Caches/audio/` via `NSFileManager` and plays them through `AVPlayer`, looping by observing `AVPlayerItemDidPlayToEndTime`. Sets `AVAudioSessionCategoryPlayback` so audio survives mixing/backgrounding.

**Persistence** mirrors the audio split: `DuelStoreFactory` is `expect`/`actual`. Android initializes against `context.filesDir`; iOS resolves the Documents directory through `NSFileManager`. Both produce a DataStore at `duelistlp.preferences_pb` storing `starting_lp`, `player1`, `player2`, and `matches_json`.

---

## Running it

### Prerequisites

- JDK 21
- Android SDK with `compileSdk = 36` available (Android Studio Ladybug+ recommended)
- For iOS: macOS with Xcode 15+, an iOS 14+ simulator or device

### Android

```bash
./gradlew :androidApp:installDebug
```

Or open the project in Android Studio and run the `androidApp` configuration. Debug builds get the `.debug` application-id suffix so they install alongside release.

### iOS

The shared module produces a dynamic framework consumed by the Xcode project.

1. Build the framework once so Xcode can link it:
   ```bash
   ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
   ```
2. Open `iosApp/iosApp.xcodeproj` in Xcode.
3. Pick a simulator (or your device) and run.

`iOSApp.swift` disables the idle timer (`UIApplication.shared.isIdleTimerDisabled = true`) so the screen doesn't sleep mid-duel.

---

## Theme

Defined in `DuelColors.kt`:

| Token | Hex | Used for |
|---|---|---|
| DuelGold | `#E8B852` | primary accents, buttons |
| DuelGoldGlow | `#FFE07C` | highlights |
| LpYellow | `#FFE34C` | LP digits |
| LpStroke | `#1B1206` | LP digit outline |
| DuelPurple | `#2A1F62` | mid-stop background |
| DuelPurpleDeep | `#120A33` | bottom-stop background |
| Crimson | `#D7263D` | damage / negative deltas |
| EmeraldHeal | `#14C38E` | recovery / positive deltas |

Typography is the Heuristica family (Regular, Italic, Bold) bundled in `composeResources/font/`. LP digits use the italic cut with `StrokedText` for the inked-card-game look.

---

## Status

Single-developer project at `versionName = 0.1.0`. Core feature set is complete and exercised end-to-end on both platforms. There are no automated tests yet and no TODOs left in source.
