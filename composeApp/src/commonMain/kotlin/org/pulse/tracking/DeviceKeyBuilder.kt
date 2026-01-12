package org.pulse.tracking

object DeviceKeyBuilder {
    fun fromAndroid(
        address: String?,
        manufacturerId: Int?,
        serviceUuids: List<String>,
        localName: String?,
        rawAdvHash: String?,
    ): String {
        val parts = listOf(
            "android",
            address.orEmpty(),
            manufacturerId?.toString().orEmpty(),
            serviceUuids.sorted().joinToString(","),
            localName.orEmpty(),
            rawAdvHash.orEmpty(),
        )
        return hashHex(parts.joinToString("|").encodeToByteArray())
    }
}
