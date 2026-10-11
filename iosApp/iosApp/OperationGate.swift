import Foundation

/// Rejects callbacks from cancelled requests, logout or a replaced API.
struct OperationGate {
    private(set) var generation: UInt64 = 0
    mutating func begin() -> UInt64 {
        generation &+= 1
        return generation
    }
    mutating func invalidate() { generation &+= 1 }
    func accepts(_ value: UInt64) -> Bool { value == generation }
}
