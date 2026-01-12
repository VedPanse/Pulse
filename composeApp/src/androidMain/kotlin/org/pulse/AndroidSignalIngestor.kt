package org.pulse

import android.content.Context
import org.pulse.tracking.DeviceTracker

class AndroidSignalIngestor(
    context: Context,
    tracker: DeviceTracker,
    enableWifiScan: Boolean,
) {
    private val bleScanner = AndroidBleScanner(tracker)
    private val wifiScanner = if (enableWifiScan) AndroidWifiScanner(context, tracker) else null

    fun start() {
        bleScanner.start()
        wifiScanner?.start()
    }

    fun stop() {
        wifiScanner?.stop()
        bleScanner.stop()
    }
}
