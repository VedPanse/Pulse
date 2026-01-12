package org.pulse.tracking

expect class SimpleLock() {
    fun <T> withLock(block: () -> T): T
}
