package org.pulse

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import org.pulse.signal.SignalEngine
import org.pulse.signal.SourceType

class AndroidWifiScanner(
    private val context: Context,
    private val engine: SignalEngine,
    private val idGenerator: AndroidEphemeralId,
    private val scanIntervalMillis: Long = 10_000,
) {
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val handler = Handler(Looper.getMainLooper())
    private var isRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!hasWifiPermission()) {
                return
            }
            @SuppressLint("MissingPermission")
            val results: List<ScanResult> = try {
                wifiManager.scanResults ?: emptyList()
            } catch (_: SecurityException) {
                emptyList()
            }
            val now = System.currentTimeMillis()
            for (result in results) {
                val sourceId = idGenerator.idFor("wifi", result.BSSID ?: "unknown", now)
                engine.addSample(
                    sourceId = sourceId,
                    rssi = result.level,
                    timestampMillis = now,
                    sourceType = SourceType.WIFI,
                )
            }
        }
    }

    private val scanRunnable = object : Runnable {
        override fun run() {
            try {
                if (!hasWifiPermission()) {
                    handler.postDelayed(this, scanIntervalMillis)
                    return
                }
                wifiManager.startScan()
            } catch (_: SecurityException) {
                // Permissions are handled by the activity.
            }
            handler.postDelayed(this, scanIntervalMillis)
        }
    }

    fun start() {
        if (!isRegistered) {
            context.registerReceiver(
                receiver,
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            )
            isRegistered = true
        }
        handler.post(scanRunnable)
    }

    fun stop() {
        handler.removeCallbacks(scanRunnable)
        if (isRegistered) {
            context.unregisterReceiver(receiver)
            isRegistered = false
        }
    }

    private fun hasWifiPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            val fine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            fine || coarse
        }
    }
}
