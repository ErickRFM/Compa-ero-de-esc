import SwiftUI
import CompaneroShared

@MainActor
final class AppModel: ObservableObject {
    @Published var apiURL: String
    @Published private(set) var configured = false
    @Published private(set) var busy = false
    @Published private(set) var message = "Configura la API para comprobar la conexión."
    @Published private(set) var userName = ""
    @Published private(set) var userID = ""
    @Published private(set) var health = ""
    @Published private(set) var readiness = ""

    private var bridge: IosApiBridge?
    private var request: RequestHandle?
    private var gate = OperationGate()
    private let allowLocalHTTP: Bool

    init() {
        apiURL = Bundle.main.object(forInfoDictionaryKey: "CompaneroAPIBaseURL") as? String ?? ""
        allowLocalHTTP = (Bundle.main.object(forInfoDictionaryKey: "CompaneroAllowLocalHTTP") as? String) == "YES"
        if !apiURL.isEmpty { configure() }
    }

    var signedIn: Bool { !userID.isEmpty }

    func configure() {
        cancelAndClearSession()
        bridge?.close()
        bridge = nil
        configured = false
        health = ""
        readiness = ""
        do {
            bridge = try IosApiBridge(baseUrl: apiURL.trimmingCharacters(in: .whitespacesAndNewlines),
                                     allowLocalHttp: allowLocalHTTP)
            configured = true
            message = "API configurada. Comprueba la conexión."
        } catch {
            message = "URL inválida. Usa HTTPS o localhost HTTP en Debug, sin credenciales ni parámetros."
        }
    }

    func probe() {
        guard let bridge, configured else { return }
        let generation = begin()
        request = bridge.checkConnection { [weak self] result in
            Task { @MainActor [weak self] in
                guard let self, self.gate.accepts(generation) else { return }
                self.finish(result.message)
                self.health = result.healthStatus
                self.readiness = result.readinessStatus
            }
        }
    }

    func login(username: String, password: String) {
        guard let bridge, configured, !username.trimmingCharacters(in: .whitespaces).isEmpty, !password.isEmpty else {
            message = "Configura la API e introduce tus credenciales."
            return
        }
        let generation = begin()
        request = bridge.login(username: username, password: password) { [weak self] result in
            Task { @MainActor [weak self] in
                guard let self, self.gate.accepts(generation) else { return }
                self.finish(result.message)
                if result.success {
                    self.userName = result.userName
                    self.userID = result.userId
                }
            }
        }
    }

    func logout() {
        cancelAndClearSession()
        guard let bridge else { return }
        let generation = begin()
        request = bridge.logout { [weak self] result in
            Task { @MainActor [weak self] in
                guard let self, self.gate.accepts(generation) else { return }
                self.finish(result.message)
            }
        }
    }

    private func begin() -> UInt64 {
        request?.cancel()
        busy = true
        message = "Conectando…"
        return gate.begin()
    }

    private func finish(_ value: String) {
        busy = false
        request = nil
        message = value
    }

    private func cancelAndClearSession() {
        gate.invalidate()
        request?.cancel()
        request = nil
        busy = false
        userName = ""
        userID = ""
    }
}
