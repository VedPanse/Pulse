package org.pulse

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Build
import org.pulse.core.BleScanEvent
import org.pulse.core.CompassTracker
import org.pulse.tracking.hashHex

class AndroidBleScanner(
    private val tracker: CompassTracker,
) {
    private var scanner: BluetoothLeScanner? = null

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val now = System.currentTimeMillis()
            val record = result.scanRecord
            val manufacturerData = record?.manufacturerSpecificData
            val manufacturerId = manufacturerData?.let { data ->
                if (data.size() > 0) data.keyAt(0) else null
            }
            val serviceUuids = record?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
            val txPower = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (result.txPower != ScanResult.TX_POWER_NOT_PRESENT) result.txPower else null
            } else {
                null
            }
            val address = try {
                device.address
            } catch (_: SecurityException) {
                null
            }
            val deviceKey = address?.takeIf { it.isNotBlank() }
                ?: record?.bytes?.let { bytes -> hashHex(bytes) }
                ?: hashHex("${manufacturerId ?: ""}|${serviceUuids.joinToString(",")}".encodeToByteArray())
            tracker.onScan(
                BleScanEvent(
                    key = deviceKey,
                    timestampMs = now,
                    rssi = result.rssi,
                    txPower = txPower,
                    manufacturerId = manufacturerId,
                    serviceUuids = serviceUuids,
                ),
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
