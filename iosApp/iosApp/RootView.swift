import SwiftUI

struct RootView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        TabView {
            NavigationStack { TodayView() }
                .tabItem { Label("Hoy", systemImage: "house.fill") }
            NavigationStack { AgendaView() }
                .tabItem { Label("Agenda", systemImage: "calendar") }
            NavigationStack { ConnectionView() }
                .tabItem { Label("Conexión", systemImage: "network") }
        }
        .background(V8.background(for: scheme))
    }
}

struct TodayView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: V8Spacing.sm) {
                HStack {
                    Image(systemName: "graduationcap.fill").foregroundStyle(V8.crimson)
                        .accessibilityHidden(true)
                    Text("COMPAÑERO DE CLASE").font(.caption.weight(.bold))
                }
                .accessibilityElement(children: .combine)
                .accessibilityLabel("Compañero de Clase")

                Text(model.signedIn ? "Hola, " + model.userName : "Tu día, a la mano")
                    .font(.largeTitle.bold())
                    .accessibilityAddTraits(.isHeader)

                Text("Compañero de Clase · V8")
                    .foregroundStyle(V8.secondaryText(for: scheme))

                if model.signedIn {
                    V8Card {
                        Label("Sesión iniciada", systemImage: "person.crop.circle.fill")
                            .accessibilityLabel("Sesión iniciada")
                        Text("La sesión se conserva mientras la app está abierta.")
                            .foregroundStyle(V8.secondaryText(for: scheme))
                        Button("Cerrar sesión") { model.logout() }
                            .buttonStyle(V8Button())
                            .disabled(model.busy)
                            .accessibilityHint("Cierra tu sesión actual")
                    }
                } else {
                    LoginCard()
                }

                V8Card {
                    Label("Estamos construyendo tu experiencia iOS", systemImage: "sparkles")
                        .font(.headline)
                        .accessibilityAddTraits(.isHeader)
                    Text("Clases, asistencia y avisos aún no están integrados. Esta versión permite iniciar sesión y comprobar la conexión con la API.")
                        .foregroundStyle(V8.secondaryText(for: scheme))
                }

                if model.hasError {
                    V8ErrorBanner(
                        title: "No pudimos conectar",
                        message: model.message,
                        onRetry: { model.probe() }
                    )
                } else if !model.message.isEmpty {
                    Text(model.message).font(.callout)
                        .accessibilityIdentifier("apiMessage")
                }

                if model.busy {
                    ProgressView()
                        .accessibilityLabel("Conectando con la API")
                        .accessibilityHint("Espera mientras se completa la operación")
                }
            }
            .padding(V8Spacing.lg)
        }
        .background(V8.background(for: scheme))
        .foregroundStyle(V8.primaryText(for: scheme))
        .navigationTitle("Hoy")
        .toolbarBackground(V8.surface(for: scheme), for: .navigationBar)
    }
}

private struct LoginCard: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.colorScheme) private var scheme
    @State private var username = ""
    @State private var password = ""

    var body: some View {
        V8Card {
            Text("Inicia sesión").font(.title2.bold())
                .accessibilityAddTraits(.isHeader)
            TextField("Usuario", text: $username)
                .textContentType(.username).textInputAutocapitalization(.never).autocorrectionDisabled()
                .textFieldStyle(.roundedBorder)
                .accessibilityIdentifier("username")
                .accessibilityLabel("Campo de usuario")
                .accessibilityHint("Escribe tu nombre de usuario institucional")
            SecureField("Contraseña", text: $password)
                .textContentType(.password).textFieldStyle(.roundedBorder)
                .accessibilityIdentifier("password")
                .accessibilityLabel("Campo de contraseña")
                .accessibilityHint("Escribe tu contraseña")
            Button("Entrar") {
                model.login(username: username, password: password)
                password = ""
            }
            .buttonStyle(V8Button())
            .disabled(model.busy || !model.configured || username.isEmpty || password.isEmpty)
            .accessibilityHint("Inicia sesión con tus credenciales")
            if !model.configured {
                Text("Primero configura la API en Conexión.").font(.caption)
                    .foregroundStyle(V8.secondaryText(for: scheme))
            }
        }
    }
}

struct AgendaView: View {
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        ScrollView {
            V8Card {
                Label("Tu agenda", systemImage: "calendar")
                    .font(.title2.bold())
                    .accessibilityAddTraits(.isHeader)
                Text("La agenda académica todavía no está integrada en iOS.")
                    .foregroundStyle(V8.secondaryText(for: scheme))
                Text("No hay clases descargadas.").font(.callout)
                    .accessibilityLabel("Estado: sin clases descargadas")
            }
            .padding(V8Spacing.lg)
        }
        .background(V8.background(for: scheme))
        .foregroundStyle(V8.primaryText(for: scheme))
        .navigationTitle("Agenda")
    }
}
