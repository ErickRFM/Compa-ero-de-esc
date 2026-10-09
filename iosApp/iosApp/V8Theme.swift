import SwiftUI

enum V8 {
    static let background = Color(hex: 0x08090C)
    static let surface = Color(hex: 0x15171C)
    static let card = Color(hex: 0x252730)
    static let crimson = Color(hex: 0xFF303F)
    static let primary = Color(hex: 0xF8F8FA)
    static let secondary = Color(hex: 0xABB0BE)
}

private extension Color {
    init(hex: UInt32) {
        self.init(.sRGB, red: Double((hex >> 16) & 255) / 255,
                  green: Double((hex >> 8) & 255) / 255,
                  blue: Double(hex & 255) / 255, opacity: 1)
    }
}

struct V8Card<Content: View>: View {
    let content: Content
    init(@ViewBuilder content: () -> Content) { self.content = content() }
    var body: some View {
        VStack(alignment: .leading, spacing: 12) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(20)
            .background(V8.card.opacity(0.85), in: RoundedRectangle(cornerRadius: 22))
            .overlay(RoundedRectangle(cornerRadius: 22).stroke(.white.opacity(0.10)))
    }
}

struct V8Button: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .frame(maxWidth: .infinity)
            .padding(16)
            .background(V8.crimson.opacity(configuration.isPressed ? 0.7 : 1), in: RoundedRectangle(cornerRadius: 16))
            .foregroundStyle(V8.primary)
    }
}
