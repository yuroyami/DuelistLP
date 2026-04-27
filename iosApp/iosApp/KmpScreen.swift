import SwiftUI
import UIKit
import shared

struct KmpScreen: View {
    var body: some View {
        KmpCompose().ignoresSafeArea(.all)
    }
}

struct KmpCompose: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        return MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
