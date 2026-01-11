package org.pulse

import android.content.Context
import org.pulse.signal.SignalEngine

class AndroidSignalIngestor(
    context: Context,
    engine: SignalEngine,
    enableWifiScan: Boolean,
) {
    private val idGenerator = AndroidEphemeralId()
    private val bleScanner = AndroidBleScanner(engine, idGenerator)
    private val wifiScanner = if (enableWifiScan) {
        AndroidWifiScanner(context, engine, idGenerator)
    } else {
        null
    }

    fun start() {
        bleScanner.start()
        wifiScanner?.start()
    }

    fun stop() {
        wifiScanner?.stop()
        bleScanner.stop()
    }
}
