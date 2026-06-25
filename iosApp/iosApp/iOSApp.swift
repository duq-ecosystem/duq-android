import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Стартуем Koin со всем графом shared (network/audio/viewModel/platform) до показа
        // UI — иначе koinViewModel()/koinInject() в Compose не зарезолвятся.
        Modules_iosKt.doInitKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
        }
    }
}
