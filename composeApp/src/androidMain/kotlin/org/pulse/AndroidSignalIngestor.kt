package org.pulse

import android.content.Context
import org.pulse.core.CompassTracker

class AndroidSignalIngestor(
    context: Context,
    tracker: CompassTracker,
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
