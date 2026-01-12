package org.pulse

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
