import Foundation
import CoreBluetooth
import ComposeApp

private func kotlinInt(_ value: Int32?) -> KotlinInt? {
    guard let value = value else { return nil }
    return KotlinInt(int: value)
}

final class PulseViewModel: NSObject, ObservableObject, CBCentralManagerDelegate {
    @Published var dots: [UiDot] = []
    @Published var summary: TrackerSummary?
    @Published var debug: DebugSnapshot?

    private let tracker = DeviceTracker()
    private let trackerQueue = DispatchQueue(label: "pulse.tracker")
    private var central: CBCentralManager?
    private var timer: Timer?

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
        let deviceKey = peripheral.identifier.uuidString
        let manufacturerData = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data
        let manufacturerId: Int32?
        if let data = manufacturerData, data.count >= 2 {
            manufacturerId = Int32(Int(data[0]) | (Int(data[1]) << 8))
        } else {
            manufacturerId = nil
        }
        let serviceUuids = (advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID])?.map {
            $0.uuidString
        } ?? []
        let txPowerValue = (advertisementData[CBAdvertisementDataTxPowerLevelKey] as? NSNumber)?.intValue
        let txPower = txPowerValue == nil ? nil : Int32(txPowerValue ?? 0)
        let event = BleScanEvent(
            platform: "ios",
            timestampMs: now,
            rssi: Int32(RSSI.intValue),
            txPower: kotlinInt(txPower),
            manufacturerId: kotlinInt(manufacturerId),
            serviceUuids: serviceUuids,
            rawAdvHash: nil,
            deviceKey: deviceKey
        )
        trackerQueue.async { [tracker] in
            tracker.onScan(event: event)
        }
    }

    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 0.2, repeats: true) { [weak self] _ in
            self?.refresh()
        }
    }

    private func refresh() {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        trackerQueue.async { [tracker] in
            tracker.tick(nowMs: now)
            let dots = tracker.getDotsSnapshot()
            let summary = tracker.getSummarySnapshot()
            let debug = tracker.getDebugSnapshot(nowMs: now)
            DispatchQueue.main.async {
                self.dots = dots
                self.summary = summary
                self.debug = debug
            }
        }
    }

    func updateViewport(width: Float, height: Float) {
        trackerQueue.async { [tracker] in
            tracker.setViewport(widthPx: width, heightPx: height)
        }
    }

    deinit {
        timer?.invalidate()
        central?.stopScan()
    }
}
