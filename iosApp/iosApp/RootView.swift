import SwiftUI

struct RootView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        TabView {
            NavigationStack { TodayView() }
                .tabItem { Label("Hoy", systemImage: "house.fill") }
            NavigationStack { AgendaView() }
                .tabItem { Label("Agenda", systemImage: "calendar") }
            NavigationStack { ConnectionView() }
                .tabItem { Label("Conexión", systemImage: "network") }
        }
        .background(V8.background)
    }
}

struct TodayView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Image(systemName: "graduationcap.fill").foregroundStyle(V8.crimson)
                    Text("COMPAÑERO DE CLASE").font(.caption.weight(.bold))
                }.accessibilityElement(children: .combine)
                Text(model.signedIn ? "Hola, " + model.userName : "Tu día, a la mano")
                    .font(.largeTitle.bold())
                Text("Compañero de Clase · V8").foregroundStyle(V8.secondary)
                if model.signedIn {
                    V8Card {
                        Label("Sesión iniciada", systemImage: "person.crop.circle.fill")
                        Text("La sesión se conserva mientras la app está abierta.").foregroundStyle(V8.secondary)
                        Button("Cerrar sesión") { model.logout() }.buttonStyle(V8Button())
                            .disabled(model.busy)
                    }
                } else {
                    LoginCard()
                }
                V8Card {
                    Label("Estamos construyendo tu experiencia iOS", systemImage: "sparkles")
                        .font(.headline)
                    Text("Clases, asistencia y avisos aún no están integrados. Esta versión permite iniciar sesión y comprobar la conexión con la API.")
                        .foregroundStyle(V8.secondary)
                }
                Text(model.message).font(.callout).accessibilityIdentifier("apiMessage")
                if model.busy { ProgressView().accessibilityLabel("Conectando con la API") }
            }.padding(20)
        }
        .background(V8.background)
        .foregroundStyle(V8.primary)
        .navigationTitle("Hoy")
        .toolbarBackground(V8.surface, for: .navigationBar)
    }
}

private struct LoginCard: View {
    @EnvironmentObject private var model: AppModel
    @State private var username = ""
    @State private var password = ""

    var body: some View {
        V8Card {
            Text("Inicia sesión").font(.title2.bold())
            TextField("Usuario", text: $username)
                .textContentType(.username).textInputAutocapitalization(.never).autocorrectionDisabled()
                .textFieldStyle(.roundedBorder).accessibilityIdentifier("username")
            SecureField("Contraseña", text: $password)
                .textContentType(.password).textFieldStyle(.roundedBorder)
                .accessibilityIdentifier("password")
            Button("Entrar") {
                model.login(username: username, password: password)
                password = ""
            }.buttonStyle(V8Button())
                .disabled(model.busy || !model.configured || username.isEmpty || password.isEmpty)
            if !model.configured {
                Text("Primero configura la API en Conexión.").font(.caption).foregroundStyle(V8.secondary)
            }
        }
    }
}

struct AgendaView: View {
    var body: some View {
        ScrollView {
            V8Card {
                Label("Tu agenda", systemImage: "calendar").font(.title2.bold())
                Text("La agenda académica todavía no está integrada en iOS.")
                    .foregroundStyle(V8.secondary)
                Text("No hay clases descargadas.").font(.callout)
            }.padding(20)
        }.background(V8.background).foregroundStyle(V8.primary).navigationTitle("Agenda")
    }
}
