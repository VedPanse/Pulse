package org.pulse.core

import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSDate
import platform.Foundation.NSOperationQueue
import platform.Foundation.timeIntervalSince1970

class IosYawSource {
    private val motionManager = CMMotionManager()
    private var onYaw: ((YawSample) -> Unit)? = null

    fun start(onYaw: (YawSample) -> Unit) {
        this.onYaw = onYaw
        if (!motionManager.deviceMotionAvailable) return
        motionManager.deviceMotionUpdateInterval = 1.0 / 30.0
        motionManager.startDeviceMotionUpdatesToQueue(NSOperationQueue.mainQueue) { motion, _ ->
            val sample = motion ?: return@startDeviceMotionUpdatesToQueue
            val nowMs = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
            onYaw(YawSample(timestampMs = nowMs, yawRad = sample.attitude.yaw))
        }
    }

    fun stop() {
        motionManager.stopDeviceMotionUpdates()
        onYaw = null
    }
}
