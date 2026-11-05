import SwiftUI

// MARK: - Color tokens

enum V8 {
    // Dark palette (used when the system is in dark mode)
    static let darkBackground = Color(hex: 0x08090C)
    static let darkSurface = Color(hex: 0x15171C)
    static let darkCard = Color(hex: 0x252730)

    // Light palette (used when the system is in light mode)
    static let lightBackground = Color(hex: 0xF6F4F1)
    static let lightSurface = Color(hex: 0xFFFFFCF9)
    static let lightCard = Color(hex: 0xFFFCF9)

    // Brand accent — same in both modes
    static let crimson = Color(hex: 0xFF8A1538)
    static let crimsonLight = Color(hex: 0xFFB31328)

    // Semantic text colors resolved at runtime
    @Environment(\.colorScheme) static var resolvedScheme

    static func background(for scheme: ColorScheme) -> Color {
        scheme == .dark ? darkBackground : lightBackground
    }
    static func surface(for scheme: ColorScheme) -> Color {
        scheme == .dark ? darkSurface : lightSurface
    }
    static func card(for scheme: ColorScheme) -> Color {
        scheme == .dark ? darkCard : lightCard
    }
    static func primaryText(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(hex: 0xF8F8FA) : Color(hex: 0x211F1E)
    }
    static func secondaryText(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(hex: 0xABB0BE) : Color(hex: 0x514B49)
    }
    static func stroke(for scheme: ColorScheme) -> Color {
        scheme == .dark ? Color.white.opacity(0.10) : Color.black.opacity(0.08)
    }

    // Legacy static properties for backward compatibility (dark values)
    static let background = darkBackground
    static let surface = darkSurface
    static let card = darkCard
    static let primary = Color(hex: 0xF8F8FA)
    static let secondary = Color(hex: 0xABB0BE)
}

// MARK: - Design tokens

enum V8Spacing {
    static let xs: CGFloat = 8
    static let sm: CGFloat = 12
    static let md: CGFloat = 16
    static let lg: CGFloat = 20
    static let xl: CGFloat = 24
}

enum V8Radius {
    static let card: CGFloat = 22
    static let button: CGFloat = 16
    static let field: CGFloat = 12
}

private extension Color {
    init(hex: UInt32) {
        self.init(.sRGB, red: Double((hex >> 16) & 255) / 255,
                  green: Double((hex >> 8) & 255) / 255,
                  blue: Double(hex & 255) / 255, opacity: 1)
    }
}

// MARK: - Reusable components

struct V8Card<Content: View>: View {
    let content: Content
    init(@ViewBuilder content: () -> Content) { self.content = content() }
    var body: some View {
        VStack(alignment: .leading, spacing: V8Spacing.sm) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(V8Spacing.lg)
            .background(
                Group {
                    if V8.resolvedScheme == .dark {
                        V8.darkCard.opacity(0.85)
                    } else {
                        V8.lightCard
                    }
                },
                in: RoundedRectangle(cornerRadius: V8Radius.card)
            )
            .overlay(
                RoundedRectangle(cornerRadius: V8Radius.card)
                    .stroke(V8.stroke(for: V8.resolvedScheme))
            )
            .accessibilityElement(children: .contain)
    }
}

struct V8Button: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .frame(maxWidth: .infinity)
            .padding(V8Spacing.md)
            .background(
                V8.crimson.opacity(configuration.isPressed ? 0.7 : 1),
                in: RoundedRectangle(cornerRadius: V8Radius.button)
            )
            .foregroundStyle(.white)
            .accessibilityAddTraits(.isButton)
    }
}

// MARK: - Error banner

struct V8ErrorBanner: View {
    let title: String
    let message: String
    let onRetry: (() -> Void)?

    var body: some View {
        HStack(alignment: .top, spacing: V8Spacing.sm) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(V8.crimson)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.headline)
                Text(message).font(.subheadline)
                    .foregroundStyle(V8.secondaryText(for: V8.resolvedScheme))
                if let onRetry {
                    Button("Reintentar", action: onRetry)
                        .buttonStyle(V8Button())
                        .padding(.top, V8Spacing.xs)
                }
            }
        }
        .padding(V8Spacing.lg)
        .background(
            V8.crimson.opacity(0.12),
            in: RoundedRectangle(cornerRadius: V8Radius.card)
        )
        .overlay(
            RoundedRectangle(cornerRadius: V8Radius.card)
                .stroke(V8.crimson.opacity(0.3))
        )
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(title). \(message)")
        .accessibilityHint("Revisa la conexión o reintenta")
    }
}
