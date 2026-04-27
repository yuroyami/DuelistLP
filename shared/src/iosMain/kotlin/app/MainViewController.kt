package app

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Entry point consumed from Swift (`MainViewControllerKt.MainViewController()`).
 * Returns a [UIViewController] hosting the shared Compose UI defined by [App].
 */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
