import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        // Composition root of the shared Kotlin code. Also registers the BGTaskScheduler handler,
        // which Apple requires to happen before the app finishes launching.
        KoinIosKt.startKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
