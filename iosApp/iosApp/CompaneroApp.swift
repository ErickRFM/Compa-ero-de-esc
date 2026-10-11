import SwiftUI

@main
struct CompaneroApp: App {
    @StateObject private var model = AppModel()
    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .preferredColorScheme(.dark)
                .tint(V8.crimson)
        }
    }
}
