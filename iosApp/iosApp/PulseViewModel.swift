import Foundation
import CoreBluetooth
import CryptoKit
import ComposeApp

final class PulseViewModel: NSObject, ObservableObject, CBCentralManagerDelegate {
    @Published var clusters: [ClusterViewData] = []

    private let engine = SignalEngine(
        windowMillis: 20_000,
        decayHalfLifeMillis: 18_000,
        minSamplesForPresence: 3
    )
    private var central: CBCentralManager?
    private var timer: Timer?
    private let idGenerator = IosEphemeralId()

    override init() {
        super.init()
        central = CBCentralManager(delegate: self, queue: DispatchQueue(label: "pulse.ble"))
        startTimer()
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            central.scanForPeripherals(
                withServices: nil,
                options: [CBCentralManagerScanOptionAllowDuplicatesKey: true]
            )
        }
    }

    func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let sourceId = idGenerator.idFor(prefix: "ble", rawId: peripheral.identifier.uuidString, nowMillis: now)
        engine.addSample(
            sourceId: sourceId,
            rssi: Int32(RSSI.intValue),
            timestampMillis: now,
            sourceType: SourceType.ble
        )
    }

    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            self?.refresh()
        }
    }

    private func refresh() {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        engine.tick(nowMillis: now)
        let snapshot = engine.getClustersSnapshotArray(nowMillis: now)
        clusters = ClusterViewData.from(snapshot: snapshot)
    }

    deinit {
        timer?.invalidate()
        central?.stopScan()
    }
}

struct ClusterViewData: Identifiable {
    let id: String
    let confidence: String
    let trend: String
    let deviceCount: Int
    let stability: String
    let score: Float

    static func from(snapshot: KotlinArray<ClusterSnapshot>) -> [ClusterViewData] {
        var results: [ClusterViewData] = []
        let size = Int(snapshot.size)
        for index in 0..<size {
            guard let item = snapshot.get(index: Int32(index)) else { continue }
            let stability: String
            if item.stabilityScore >= 0.7 {
                stability = "Stationary"
            } else if item.stabilityScore <= 0.3 {
                stability = "Moving"
            } else {
                stability = "Mixed"
            }
            let trend: String
            switch item.trend {
            case .strengthening:
                trend = "Strengthening"
            case .weakening:
                trend = "Weakening"
            default:
                trend = "Stable"
            }
            results.append(
                ClusterViewData(
                    id: item.clusterId,
                    confidence: "\(item.confidence)",
                    trend: trend,
                    deviceCount: Int(item.estimatedDeviceCount),
                    stability: stability,
                    score: item.aggregatedPresenceScore
                )
            )
        }
        return results.sorted { $0.score > $1.score }
    }
}

final class IosEphemeralId {
    private let rotationMinutes: Int = 5

    func idFor(prefix: String, rawId: String, nowMillis: Int64) -> String {
        let bucket = nowMillis / Int64(rotationMinutes * 60_000)
        let material = "\(prefix)|\(rawId)|\(bucket)"
        return sha256(material)
    }

    private func sha256(_ input: String) -> String {
        guard let data = input.data(using: .utf8) else { return input }
        if #available(iOS 13.0, *) {
            let digest = SHA256.hash(data: data)
            return digest.map { String(format: "%02x", $0) }.joined()
        }
        return input
    }
}
