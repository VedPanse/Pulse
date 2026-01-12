package org.pulse.core

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.pulse.signal.ConfidenceLevel
import org.pulse.tracking.SimpleLock

class CompassTracker {
    private val tracks = mutableMapOf<String, DeviceTrack>()
    private val lock = SimpleLock()

    private val _dotsFlow = MutableStateFlow<List<UiDot>>(emptyList())
    val dotsFlow: StateFlow<List<UiDot>> = _dotsFlow

    private val _debugFlow = MutableStateFlow(
        DebugSnapshot(
            totalTracks = 0,
            trackableCount = 0,
            yawRad = 0.0,
            scanCount = 0,
            lastScanMs = 0,
            topDevices = emptyList(),
        )
    )
    val debugFlow: StateFlow<DebugSnapshot> = _debugFlow

    private var lastDots: List<UiDot> = emptyList()
    private var viewportWidth = 0f
    private var viewportHeight = 0f
    private var lastTickMs: Long = 0
    private var lastScanMs: Long = 0
    private var lastYawRad = 0.0
    private var lastYawMs: Long = 0
    private var scanCount: Int = 0

    fun setViewport(widthPx: Float, heightPx: Float) {
        lock.withLock {
            if (widthPx == viewportWidth && heightPx == viewportHeight) return@withLock
            viewportWidth = widthPx
            viewportHeight = heightPx
            emitDotsLocked()
        }
    }

    fun onYaw(sample: YawSample) {
        lock.withLock {
            lastYawRad = sample.yawRad
            lastYawMs = sample.timestampMs
            emitDotsLocked()
        }
    }

    fun onScan(event: BleScanEvent) {
        lock.withLock {
            lastScanMs = max(lastScanMs, event.timestampMs)
            scanCount += 1
            val track = tracks[event.key] ?: DeviceTrack(
                key = event.key,
                lastSeenMs = event.timestampMs,
                seenCount = 0,
                rssiEma = event.rssi.toDouble(),
                rssiVar = 0.0,
                confidence = 0.0,
                phoneScore = 0.0,
                azimuthRad = seedAzimuth(event.key),
                azimuthConfidence = 0.08,
                txPower = event.txPower,
            ).also { tracks[event.key] = it }

            if (track.seenCount == 0 && lastYawMs != 0L) {
                track.azimuthRad = lastYawRad
                track.azimuthConfidence = 0.18
            }

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

            updateAzimuthLocked(track)
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
                if (deltaMs > 0) {
                    track.azimuthConfidence *= 0.995
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
            buildDebugSnapshot(nowMs)
        }
    }

    private fun updateAzimuthLocked(track: DeviceTrack) {
        if (lastYawMs == 0L) return
        val strength = ((-55.0 - track.rssiEma) / 35.0).coerceIn(0.0, 1.0)
        val stability = stabilityScore(track)
        val pullStrength = (strength * stability).coerceIn(0.0, 1.0)
        if (pullStrength <= 0.0) return
        val pull = 0.05 + 0.10 * pullStrength
        track.azimuthRad = angleLerp(track.azimuthRad, lastYawRad, pull)
        track.azimuthConfidence = min(1.0, track.azimuthConfidence + 0.03 * pullStrength)
    }

    private fun emitDotsLocked() {
        if (viewportWidth <= 0f || viewportHeight <= 0f) {
            lastDots = emptyList()
            _dotsFlow.value = lastDots
            _debugFlow.value = buildDebugSnapshot(max(lastTickMs, max(lastScanMs, lastYawMs)))
            return
        }
        val centerX = viewportWidth / 2f
        val centerY = viewportHeight / 2f
        val fovRad = 70.0 / 180.0 * PI
        val margin = (viewportWidth * 0.05f).coerceAtLeast(14f)
        val dots = tracks.values.filter { isTrackable(it) }.map { track ->
            val range = estimateRange(track)
            val rel = wrapPi(track.azimuthRad - lastYawRad)
            val xOffset = (rel / (fovRad / 2.0)) * (viewportWidth * 0.45f)
            val x = (centerX + xOffset.toFloat()).coerceIn(margin, viewportWidth - margin)
            val t = ((range - 1.0) / (12.0 - 1.0)).coerceIn(0.0, 1.0)
            val yOffset = (-0.15 + 0.45 * t) * viewportHeight
            val y = (centerY + yOffset.toFloat()).coerceIn(margin, viewportHeight - margin)
            val sizeBase = lerp(10.0, 34.0, (1.0 - range / 12.0).coerceIn(0.0, 1.0))
            val size = sizeBase * lerp(0.7, 1.2, track.confidence.coerceIn(0.0, 1.0))
            val alpha = track.confidence.coerceIn(0.2, 1.0)
            UiDot(
                key = track.key,
                confidence = track.confidence,
                phoneScore = track.phoneScore,
                rssiEma = track.rssiEma,
                rangeMeters = range,
                screenX = x,
                screenY = y,
                sizePx = size.toFloat(),
                alpha = alpha.toFloat(),
            )
        }
        lastDots = dots
        _dotsFlow.value = dots
        _debugFlow.value = buildDebugSnapshot(max(lastTickMs, max(lastScanMs, lastYawMs)))
    }

    private fun buildDebugSnapshot(nowMs: Long): DebugSnapshot {
        val values = tracks.values.toList()
        val trackable = values.filter { isTrackable(it) }
        val top = values.sortedByDescending { it.confidence }.take(3).map { track ->
            DebugDevice(
                keyPrefix = track.key.take(6),
                rssiEma = track.rssiEma,
                phoneScore = track.phoneScore,
                confidence = track.confidence,
                azimuthConfidence = track.azimuthConfidence,
                lastSeenDeltaMs = max(0, nowMs - track.lastSeenMs),
            )
        }
        return DebugSnapshot(
            totalTracks = values.size,
            trackableCount = trackable.size,
            yawRad = lastYawRad,
            scanCount = scanCount,
            lastScanMs = lastScanMs,
            topDevices = top,
        )
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

    private fun seedAzimuth(key: String): Double {
        val hash = stableHash(key).toLong() and 0x7fffffff
        val angle = (hash % 3600L) / 3600.0 * (2 * PI)
        return wrapPi(angle)
    }

    private fun stableHash(text: String): Int {
        return text.fold(0) { acc, char -> acc * 31 + char.code }
    }

    private fun wrapPi(value: Double): Double {
        var x = value
        while (x <= -PI) x += 2 * PI
        while (x > PI) x -= 2 * PI
        return x
    }

    private fun angleDiff(target: Double, source: Double): Double {
        return wrapPi(target - source)
    }

    private fun angleLerp(source: Double, target: Double, t: Double): Double {
        return wrapPi(source + angleDiff(target, source) * t)
    }

    private fun lerp(a: Double, b: Double, t: Double): Double {
        return a + (b - a) * t
    }

    private companion object {
        val phoneManufacturerIds = setOf(0x004C, 0x0075, 0x00E0)
    }
}
