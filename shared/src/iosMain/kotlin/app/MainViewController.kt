package app

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * iOS entry point. Consumed from Swift as `MainViewControllerKt.MainViewController()`
 * — see `iosApp/iosApp/KmpScreen.swift`. Returns a [UIViewController] hosting
 * the shared Compose UI defined by [App].
 */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
