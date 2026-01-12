package org.pulse.tracking

fun hashHex(bytes: ByteArray): String {
    var hash = 0xcbf29ce484222325uL
    val prime = 0x100000001b3uL
    for (byte in bytes) {
        hash = hash xor (byte.toULong() and 0xffu)
        hash *= prime
    }
    return hash.toString(16).padStart(16, '0')
}
