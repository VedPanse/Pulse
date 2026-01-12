package org.pulse.tracking

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

actual class SimpleLock {
    private val lock = ReentrantLock()

    actual fun <T> withLock(block: () -> T): T {
        return lock.withLock(block)
    }
}
