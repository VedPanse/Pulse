package org.pulse.signal

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

class SignalEngine(
    private val windowMillis: Long = 20_000,
    private val decayHalfLifeMillis: Long = 18_000,
    private val minSamplesForPresence: Int = 3,
) {
    private val sources = mutableMapOf<String, SourceState>()
    private val previousClusterScores = mutableMapOf<String, Float>()

    fun addSample(sourceId: String, rssi: Int, timestampMillis: Long, sourceType: SourceType) {
        val state = sources.getOrPut(sourceId) { SourceState(sourceId, windowMillis) }
        state.window.addSample(SignalSample(rssi, timestampMillis, sourceType))
        state.lastSeenMillis = timestampMillis
        updatePresence(state)
    }

    fun tick(nowMillis: Long) {
        val iterator = sources.values.iterator()
        while (iterator.hasNext()) {
            val state = iterator.next()
            state.window.prune(nowMillis)
            val elapsed = nowMillis - state.lastSeenMillis
            if (elapsed > 0) {
                val decayFactor = exp(-elapsed.toDouble() / decayHalfLifeMillis.toDouble()).toFloat()
                state.presenceScore *= decayFactor
            }
            if (state.presenceScore < 0.02f && elapsed > windowMillis) {
                iterator.remove()
            }
        }
    }

    fun getClustersSnapshot(nowMillis: Long): List<ClusterSnapshot> {
        tick(nowMillis)
        val candidates = sources.values.mapNotNull { it.toSnapshot() }
            .filter { nowMillis - it.lastSeenMillis <= windowMillis * 2 && it.presenceScore > 0.05f }
        if (candidates.isEmpty()) {
            return emptyList()
        }
        val sorted = candidates.sortedByDescending { it.averageRssi }
        val clusters = mutableListOf<ClusterBuilder>()
        val rssiThreshold = 8f
        for (source in sorted) {
            val target = clusters.firstOrNull { builder ->
                abs(builder.averageRssi - source.averageRssi) <= rssiThreshold &&
                    abs(builder.lastSeenMillis - source.lastSeenMillis) <= windowMillis
            }
            if (target != null) {
                target.add(source)
            } else {
                clusters.add(ClusterBuilder(source))
            }
        }
        return clusters.map { builder ->
            val snapshot = builder.toClusterSnapshot(previousClusterScores)
            previousClusterScores[snapshot.clusterId] = snapshot.aggregatedPresenceScore
            snapshot
        }.sortedByDescending { it.aggregatedPresenceScore }
    }

    fun getClustersSnapshotArray(nowMillis: Long): Array<ClusterSnapshot> {
        return getClustersSnapshot(nowMillis).toTypedArray()
    }

    private fun updatePresence(state: SourceState) {
        val samples = state.window.snapshot()
        if (samples.isEmpty()) {
            return
        }
        val avg = samples.map { it.rssi }.average().toFloat()
        val variance = samples.map { (it.rssi - avg) * (it.rssi - avg) }.average().toFloat()
        state.averageRssi = avg
        state.variance = variance
        state.motionState = when {
            variance < 12f -> MotionState.Stationary
            variance > 28f -> MotionState.Moving
            else -> MotionState.Uncertain
        }

        val persistence = ((samples.size - minSamplesForPresence).toFloat() / 10f).coerceIn(0f, 1f)
        val stability = (1f - (variance / 80f)).coerceIn(0f, 1f)
        val target = (0.7f * persistence + 0.3f * stability).coerceIn(0f, 1f)
        state.presenceScore = lerp(state.presenceScore, target, 0.15f)
    }

    private fun lerp(current: Float, target: Float, alpha: Float): Float {
        val clampedAlpha = alpha.coerceIn(0f, 1f)
        return current + (target - current) * clampedAlpha
    }

    private class SourceState(val sourceId: String, windowMillis: Long) {
        val window = SignalWindow(windowMillis)
        var presenceScore: Float = 0f
        var lastSeenMillis: Long = 0L
        var averageRssi: Float = 0f
        var variance: Float = 0f
        var motionState: MotionState = MotionState.Uncertain

        fun toSnapshot(): SignalSourceSnapshot? {
            if (window.isEmpty()) {
                return null
            }
            return SignalSourceSnapshot(
                sourceId = sourceId,
                presenceScore = presenceScore.coerceIn(0f, 1f),
                averageRssi = averageRssi,
                motionState = motionState,
                lastSeenMillis = lastSeenMillis,
            )
        }
    }

    private class ClusterBuilder(initial: SignalSourceSnapshot) {
        private val sources = mutableListOf(initial)
        var averageRssi: Float = initial.averageRssi
            private set
        var lastSeenMillis: Long = initial.lastSeenMillis
            private set

        fun add(source: SignalSourceSnapshot) {
            sources.add(source)
            val avg = sources.map { it.averageRssi }.average().toFloat()
            averageRssi = avg
            lastSeenMillis = max(lastSeenMillis, source.lastSeenMillis)
        }

        fun toClusterSnapshot(previousScores: Map<String, Float>): ClusterSnapshot {
            val aggregated = aggregatePresence(sources)
            val stabilityScore = sources.map { motionScore(it.motionState) }.average().toFloat()
            val deviceCount = sources.count { it.presenceScore > 0.2f }.coerceAtLeast(1)
            val clusterId = stableClusterId(sources.map { it.sourceId })
            val previous = previousScores[clusterId] ?: aggregated
            val trend = when {
                aggregated - previous > 0.05f -> Trend.Strengthening
                previous - aggregated > 0.05f -> Trend.Weakening
                else -> Trend.Stable
            }
            val confidence = when {
                aggregated >= 0.66f -> ConfidenceLevel.High
                aggregated >= 0.33f -> ConfidenceLevel.Medium
                else -> ConfidenceLevel.Low
            }
            return ClusterSnapshot(
                clusterId = clusterId,
                aggregatedPresenceScore = aggregated,
                estimatedDeviceCount = deviceCount,
                stabilityScore = stabilityScore,
                trend = trend,
                confidence = confidence,
                sources = sources.toList(),
            )
        }

        private fun aggregatePresence(sources: List<SignalSourceSnapshot>): Float {
            var product = 1f
            for (source in sources) {
                product *= (1f - source.presenceScore.coerceIn(0f, 1f))
            }
            return (1f - product).coerceIn(0f, 1f)
        }

        private fun motionScore(state: MotionState): Float {
            return when (state) {
                MotionState.Stationary -> 1f
                MotionState.Moving -> 0f
                MotionState.Uncertain -> 0.5f
            }
        }

        private fun stableClusterId(sourceIds: List<String>): String {
            val sorted = sourceIds.sorted()
            val raw = sorted.joinToString("|")
            val hash = raw.fold(0) { acc, char -> acc * 31 + char.code }
            return "c${abs(hash)}"
        }
    }
}
