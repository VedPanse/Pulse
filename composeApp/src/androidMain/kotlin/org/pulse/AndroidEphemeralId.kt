package org.pulse

import java.security.MessageDigest
import kotlin.math.floor

class AndroidEphemeralId(private val rotationMinutes: Int = 5) {
    fun idFor(prefix: String, rawId: String, nowMillis: Long): String {
        val bucket = floor(nowMillis.toDouble() / (rotationMinutes * 60_000)).toLong()
        val material = "$prefix|$rawId|$bucket"
        return sha256(material)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
