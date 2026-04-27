import SwiftUI
import shared

@main
struct iOSApp: App {
    init() {
        UIApplication.shared.isIdleTimerDisabled = true
    }

    var body: some Scene {
        WindowGroup {
            KmpScreen().ignoresSafeArea(.all)
        }
    }
}
