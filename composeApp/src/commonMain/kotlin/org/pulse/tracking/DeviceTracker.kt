package org.pulse.tracking

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.pulse.signal.ConfidenceLevel

class DeviceTracker {
    private val tracks = mutableMapOf<String, DeviceTrack>()
    private val lock = SimpleLock()

    private val _dotsFlow = MutableStateFlow<List<UiDot>>(emptyList())
    val dotsFlow: StateFlow<List<UiDot>> = _dotsFlow

    private var lastDots: List<UiDot> = emptyList()
    private var viewportWidth = 0f
    private var viewportHeight = 0f
    private var lastTickMs: Long = 0

    fun setViewport(widthPx: Float, heightPx: Float) {
        lock.withLock {
            if (widthPx == viewportWidth && heightPx == viewportHeight) return@withLock
            viewportWidth = widthPx
            viewportHeight = heightPx
            emitDotsLocked()
        }
    }

    fun onScan(event: BleScanEvent) {
        lock.withLock {
            val track = tracks[event.deviceKey] ?: DeviceTrack(
                key = event.deviceKey,
                lastSeenMs = event.timestampMs,
                seenCount = 0,
                rssiEma = event.rssi.toDouble(),
                rssiVar = 0.0,
                confidence = 0.0,
                phoneScore = 0.0,
                txPower = event.txPower,
            ).also { tracks[event.deviceKey] = it }

            track.lastSeenMs = event.timestampMs
            track.seenCount += 1
            val alpha = 0.2
            val nextEma = track.rssiEma * (1 - alpha) + event.rssi * alpha
            val delta = event.rssi - nextEma
            track.rssiVar = track.rssiVar * 0.9 + 0.1 * (delta * delta)
            track.rssiEma = nextEma
            track.txPower = event.txPower ?: track.txPower
            track.confidence = min(1.0, track.confidence + 0.06)
            track.phoneScore = computePhoneScore(track, event.manufacturerId)

            emitDotsLocked()
        }
    }

    fun tick(nowMs: Long) {
        lock.withLock {
            val deltaMs = if (lastTickMs == 0L) 0L else nowMs - lastTickMs
            lastTickMs = nowMs
            val iterator = tracks.values.iterator()
            while (iterator.hasNext()) {
                val track = iterator.next()
                val dt = nowMs - track.lastSeenMs
                if (dt > 1500 && deltaMs > 0) {
                    val decay = exp(-deltaMs.toDouble() / 6000.0)
                    track.confidence *= decay
                }
                if (dt > 20_000 || track.confidence < 0.10) {
                    iterator.remove()
                }
            }
            emitDotsLocked()
        }
    }

    fun getDotsSnapshot(): List<UiDot> = lastDots

    fun getSummarySnapshot(): TrackerSummary {
        return lock.withLock {
            val trackable = tracks.values.filter { isTrackable(it) }
            val avgConfidence = if (trackable.isEmpty()) 0.0 else trackable.map { it.confidence }.average()
            val confidenceLevel = when {
                avgConfidence >= 0.66 -> ConfidenceLevel.High
                avgConfidence >= 0.33 -> ConfidenceLevel.Medium
                else -> ConfidenceLevel.Low
            }
            val stationaryCount = trackable.count { stabilityScore(it) >= 0.7 }
            TrackerSummary(
                totalDevices = trackable.size,
                confidenceLevel = confidenceLevel,
                stationaryCount = stationaryCount,
            )
        }
    }

    fun getDebugSnapshot(nowMs: Long): DebugSnapshot {
        return lock.withLock {
            val values = tracks.values.toList()
            val trackable = values.filter { isTrackable(it) }
            val top = values.sortedByDescending { it.confidence }.take(5).map { track ->
                DebugDevice(
                    keyPrefix = track.key.take(6),
                    rssiEma = track.rssiEma,
                    phoneScore = track.phoneScore,
                    confidence = track.confidence,
                    lastSeenDeltaMs = max(0, nowMs - track.lastSeenMs),
                )
            }
            DebugSnapshot(
                totalTracks = values.size,
                trackableCount = trackable.size,
                topDevices = top,
            )
        }
    }

    private fun emitDotsLocked() {
        if (viewportWidth <= 0f || viewportHeight <= 0f) {
            lastDots = emptyList()
            _dotsFlow.value = lastDots
            return
        }
        val centerX = viewportWidth / 2f
        val centerY = viewportHeight / 2f
        val minDim = min(viewportWidth, viewportHeight)
        val dots = tracks.values.filter { isTrackable(it) }.map { track ->
            val range = estimateRange(track)
            val baseRadius = mapRangeToRadius(range, minDim)
            val angle = hashAngle(track.key, track.confidence)
            val rawX = (centerX + kotlin.math.cos(angle) * baseRadius).toFloat()
            val rawY = (centerY + kotlin.math.sin(angle) * baseRadius).toFloat()
            val x = rawX.coerceIn(0f, viewportWidth)
            val y = rawY.coerceIn(0f, viewportHeight)
            UiDot(
                key = track.key,
                confidence = track.confidence,
                phoneScore = track.phoneScore,
                rssiEma = track.rssiEma,
                rangeMeters = range,
                screenX = x,
                screenY = y,
            )
        }
        lastDots = dots
        _dotsFlow.value = dots
    }

    private fun isTrackable(track: DeviceTrack): Boolean {
        return track.confidence >= 0.35 && track.phoneScore >= 0.55
    }

    private fun computePhoneScore(track: DeviceTrack, manufacturerId: Int?): Double {
        val persistenceScore = min(1.0, track.seenCount / 12.0)
        val stabilityScore = stabilityScore(track)
        val manufacturerHint = if (manufacturerId != null && manufacturerId in phoneManufacturerIds) 0.15 else 0.0
        val score = 0.55 * persistenceScore + 0.35 * stabilityScore + 0.10 * manufacturerHint
        return score.coerceIn(0.0, 1.0)
    }

    private fun stabilityScore(track: DeviceTrack): Double {
        val std = sqrt(track.rssiVar)
        return (1.0 - min(1.0, std / 18.0)).coerceIn(0.0, 1.0)
    }

    private fun estimateRange(track: DeviceTrack): Double {
        val tx = (track.txPower ?: -59).toDouble()
        val n = 2.0
        val distance = 10.0.pow((tx - track.rssiEma) / (10.0 * n))
        return distance.coerceIn(0.3, 30.0)
    }

    private fun mapRangeToRadius(range: Double, minDim: Float): Double {
        val clamped = range.coerceIn(0.3, 30.0)
        val t = (clamped - 0.3) / (30.0 - 0.3)
        val radiusNorm = 0.1 + 0.35 * t
        return radiusNorm * minDim
    }

    private fun hashAngle(key: String, confidence: Double): Double {
        val hash = stableHash(key)
        val jitterSeed = stableHash("$key:jitter")
        val baseAngle = (hash % 3600) / 3600.0 * (2 * PI)
        val jitter = ((jitterSeed % 1000) / 1000.0 - 0.5) * 0.4 * (1.0 - confidence)
        return baseAngle + jitter
    }

    private fun stableHash(text: String): Int {
        return text.fold(0) { acc, char -> acc * 31 + char.code }
    }

    private companion object {
        val phoneManufacturerIds = setOf(0x004C, 0x0075, 0x00E0)
    }
}
