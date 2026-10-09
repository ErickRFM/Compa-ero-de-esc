import SwiftUI

struct ConnectionView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                V8Card {
                    Label("Conecta tu escuela", systemImage: "network").font(.title2.bold())
                    Text("URL de la API").font(.headline)
                    TextField("https://api.tu-escuela.edu/", text: $model.apiURL)
                        .keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                        .textFieldStyle(.roundedBorder).accessibilityIdentifier("apiURL")
                    Button("Guardar conexión") { model.configure() }.buttonStyle(V8Button())
                    Text("Cambiar la conexión cierra la sesión actual.").font(.caption).foregroundStyle(V8.secondary)
                }
                V8Card {
                    Text("Estado de conexión").font(.headline)
                    Text(model.message).accessibilityIdentifier("connectionStatus")
                    if !model.health.isEmpty { Text("Servicio: " + model.health) }
                    if !model.readiness.isEmpty { Text("Dependencias: " + model.readiness) }
                    if model.busy { ProgressView().accessibilityLabel("Comprobando la conexión") }
                    Button("Comprobar API") { model.probe() }.buttonStyle(V8Button())
                        .disabled(!model.configured || model.busy)
                }
                V8Card {
                    Text("Versión inicial para simulador").font(.headline)
                    Text("La conexión no garantiza que estén disponibles todos los servicios de tu escuela. La sesión requiere que la API tenga autenticación configurada.")
                        .foregroundStyle(V8.secondary)
                }
            }.padding(20)
        }.background(V8.background).foregroundStyle(V8.primary).navigationTitle("Conexión")
    }
}
