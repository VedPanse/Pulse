package org.pulse.signal

enum class SourceType {
    BLE,
    WIFI,
}

data class SignalSample(
    val rssi: Int,
    val timestampMillis: Long,
    val sourceType: SourceType,
)

enum class MotionState {
    Stationary,
    Moving,
    Uncertain,
}

enum class Trend {
    Strengthening,
    Stable,
    Weakening,
}

enum class ConfidenceLevel {
    Low,
    Medium,
    High,
}

data class SignalSourceSnapshot(
    val sourceId: String,
    val presenceScore: Float,
    val averageRssi: Float,
    val motionState: MotionState,
    val lastSeenMillis: Long,
)

data class ClusterSnapshot(
    val clusterId: String,
    val aggregatedPresenceScore: Float,
    val estimatedDeviceCount: Int,
    val stabilityScore: Float,
    val trend: Trend,
    val confidence: ConfidenceLevel,
    val sources: List<SignalSourceSnapshot>,
)
