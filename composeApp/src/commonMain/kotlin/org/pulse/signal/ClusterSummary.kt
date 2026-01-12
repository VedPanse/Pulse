package org.pulse.signal

data class ClusterSummary(
    val totalDevices: Int,
    val confidence: ConfidenceLevel,
    val stationaryCount: Int,
)

fun computeClusterSummary(clusters: List<ClusterSnapshot>): ClusterSummary {
    val totalDevices = clusters.sumOf { it.estimatedDeviceCount }
    val confidence = when {
        clusters.any { it.confidence == ConfidenceLevel.High } -> ConfidenceLevel.High
        clusters.any { it.confidence == ConfidenceLevel.Medium } -> ConfidenceLevel.Medium
        else -> ConfidenceLevel.Low
    }
    val stationaryCount = clusters.count { it.stabilityScore >= 0.7f }
    return ClusterSummary(
        totalDevices = totalDevices,
        confidence = confidence,
        stationaryCount = stationaryCount,
    )
}

class ClusterSummaryCalculator {
    fun compute(clusters: List<ClusterSnapshot>): ClusterSummary {
        return computeClusterSummary(clusters)
    }
}
