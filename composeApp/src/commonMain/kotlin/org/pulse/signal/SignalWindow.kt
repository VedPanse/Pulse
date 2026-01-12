package org.pulse.signal

class SignalWindow(
    private val windowMillis: Long,
) {
    private val samples = mutableListOf<SignalSample>()

    fun addSample(sample: SignalSample) {
        samples.add(sample)
    }

    fun prune(nowMillis: Long) {
        val cutoff = nowMillis - windowMillis
        var index = 0
        while (index < samples.size && samples[index].timestampMillis < cutoff) {
            index++
        }
        if (index > 0) {
            samples.subList(0, index).clear()
        }
    }

    fun snapshot(): List<SignalSample> = samples.toList()

    fun isEmpty(): Boolean = samples.isEmpty()
}
