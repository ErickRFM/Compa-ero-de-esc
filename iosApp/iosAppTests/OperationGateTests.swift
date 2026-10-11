import XCTest
@testable import iosApp

final class OperationGateTests: XCTestCase {
    func testReplacedRequestIsRejected() {
        var gate = OperationGate()
        let first = gate.begin()
        let second = gate.begin()
        XCTAssertFalse(gate.accepts(first))
        XCTAssertTrue(gate.accepts(second))
    }
    func testEndpointChangeOrLogoutRejectsPendingCallback() {
        var gate = OperationGate()
        let pending = gate.begin()
        gate.invalidate()
        XCTAssertFalse(gate.accepts(pending))
    }
    @MainActor
    func testInvalidApiConfigurationDoesNotCrashAcrossKotlinBoundary() {
        let model = AppModel()
        model.apiURL = "not-an-absolute-url"
        model.configure()
        XCTAssertFalse(model.configured)
        XCTAssertFalse(model.signedIn)
        XCTAssertFalse(model.busy)
        XCTAssertTrue(model.message.contains("URL inválida"))
    }
}
