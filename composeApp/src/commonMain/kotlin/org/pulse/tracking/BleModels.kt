package org.pulse.tracking

data class BleScanEvent(
    val platform: String,
    val timestampMs: Long,
    val rssi: Int,
    val txPower: Int?,
    val manufacturerId: Int?,
    val serviceUuids: List<String>,
    val rawAdvHash: String?,
    val deviceKey: String,
)

data class DeviceTrack(
    val key: String,
    var lastSeenMs: Long,
    var seenCount: Int,
    var rssiEma: Double,
    var rssiVar: Double,
    var confidence: Double,
    var phoneScore: Double,
    var txPower: Int?,
)

data class UiDot(
    val key: String,
    val confidence: Double,
    val phoneScore: Double,
    val rssiEma: Double,
    val rangeMeters: Double,
    val screenX: Float,
    val screenY: Float,
)

data class DebugDevice(
    val keyPrefix: String,
    val rssiEma: Double,
    val phoneScore: Double,
    val confidence: Double,
    val lastSeenDeltaMs: Long,
)

data class DebugSnapshot(
    val totalTracks: Int,
    val trackableCount: Int,
    val topDevices: List<DebugDevice>,
)

data class TrackerSummary(
    val totalDevices: Int,
    val confidenceLevel: org.pulse.signal.ConfidenceLevel,
    val stationaryCount: Int,
)
