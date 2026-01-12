package org.pulse.core

data class BleScanEvent(
    val key: String,
    val timestampMs: Long,
    val rssi: Int,
    val txPower: Int? = null,
    val manufacturerId: Int? = null,
    val serviceUuids: List<String> = emptyList(),
)

data class YawSample(
    val timestampMs: Long,
    val yawRad: Double,
)

data class DeviceTrack(
    val key: String,
    var lastSeenMs: Long,
    var seenCount: Int,
    var rssiEma: Double,
    var rssiVar: Double,
    var confidence: Double,
    var phoneScore: Double,
    var azimuthRad: Double,
    var azimuthConfidence: Double,
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
    val sizePx: Float,
    val alpha: Float,
)

data class DebugDevice(
    val keyPrefix: String,
    val rssiEma: Double,
    val phoneScore: Double,
    val confidence: Double,
    val azimuthConfidence: Double,
    val lastSeenDeltaMs: Long,
)

data class DebugSnapshot(
    val totalTracks: Int,
    val trackableCount: Int,
    val yawRad: Double,
    val scanCount: Int,
    val lastScanMs: Long,
    val topDevices: List<DebugDevice>,
)

data class TrackerSummary(
    val totalDevices: Int,
    val confidenceLevel: org.pulse.signal.ConfidenceLevel,
    val stationaryCount: Int,
)
