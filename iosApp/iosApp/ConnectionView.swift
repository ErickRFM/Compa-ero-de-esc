import SwiftUI

struct ConnectionView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: V8Spacing.sm) {
                V8Card {
                    Label("Conecta tu escuela", systemImage: "network")
                        .font(.title2.bold())
                        .accessibilityAddTraits(.isHeader)
                    Text("URL de la API").font(.headline)
                    TextField("https://api.tu-escuela.edu/", text: $model.apiURL)
                        .keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                        .textFieldStyle(.roundedBorder)
                        .accessibilityIdentifier("apiURL")
                        .accessibilityLabel("Campo de URL de la API")
                        .accessibilityHint("Escribe la dirección del servidor de tu escuela")
                    Button("Guardar conexión") { model.configure() }
                        .buttonStyle(V8Button())
                        .accessibilityHint("Guarda la URL y reinicia la conexión")
                    Text("Cambiar la conexión cierra la sesión actual.").font(.caption)
                        .foregroundStyle(V8.secondaryText(for: scheme))
                }

                V8Card {
                    Text("Estado de conexión").font(.headline)
                        .accessibilityAddTraits(.isHeader)

                    if model.hasError {
                        V8ErrorBanner(
                            title: "Error de conexión",
                            message: model.message,
                            onRetry: { model.probe() }
                        )
                    } else if !model.message.isEmpty {
                        Text(model.message)
                            .accessibilityIdentifier("connectionStatus")
                    }

                    if !model.health.isEmpty {
                        Text("Servicio: " + model.health)
                            .accessibilityLabel("Estado del servicio: \(model.health)")
                    }
                    if !model.readiness.isEmpty {
                        Text("Dependencias: " + model.readiness)
                            .accessibilityLabel("Dependencias: \(model.readiness)")
                    }
                    if model.busy {
                        ProgressView()
                            .accessibilityLabel("Comprobando la conexión")
                            .accessibilityHint("Espera mientras se verifica el estado")
                    }
                    Button("Comprobar API") { model.probe() }
                        .buttonStyle(V8Button())
                        .disabled(!model.configured || model.busy)
                        .accessibilityHint("Verifica que la API esté disponible")
                }

                V8Card {
                    Text("Versión inicial para simulador").font(.headline)
                        .accessibilityAddTraits(.isHeader)
                    Text("La conexión no garantiza que estén disponibles todos los servicios de tu escuela. La sesión requiere que la API tenga autenticación configurada.")
                        .foregroundStyle(V8.secondaryText(for: scheme))
                }
            }
            .padding(V8Spacing.lg)
        }
        .background(V8.background(for: scheme))
        .foregroundStyle(V8.primaryText(for: scheme))
        .navigationTitle("Conexión")
    }
}
