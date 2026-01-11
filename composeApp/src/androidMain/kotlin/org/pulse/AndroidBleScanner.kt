package org.pulse

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import org.pulse.signal.SignalEngine
import org.pulse.signal.SourceType

class AndroidBleScanner(
    private val engine: SignalEngine,
    private val idGenerator: AndroidEphemeralId,
) {
    private var scanner: BluetoothLeScanner? = null

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val now = System.currentTimeMillis()
            val sourceId = idGenerator.idFor("ble", device.address, now)
            engine.addSample(
                sourceId = sourceId,
                rssi = result.rssi,
                timestampMillis = now,
                sourceType = SourceType.BLE,
            )
        }
    }

    fun start() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner = adapter.bluetoothLeScanner
        try {
            scanner?.startScan(null, settings, callback)
        } catch (_: SecurityException) {
            // Permissions are handled by the activity.
        }
    }

    fun stop() {
        try {
            scanner?.stopScan(callback)
        } catch (_: SecurityException) {
            // Ignore missing permissions on teardown.
        }
        scanner = null
    }
}
