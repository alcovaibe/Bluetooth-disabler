package com.pulse.bluetoothdisable.cover.gallery

/** Access-ordered cache bounded by decoded allocation size, rather than photo count. */
internal class GalleryMemoryCache<K, V>(
    private val maxBytes: Long,
    private val sizeOf: (V) -> Long,
) {
    private val entries = LinkedHashMap<K, V>(16, 0.75f, true)
    private var bytes = 0L

    init { require(maxBytes > 0) }

    operator fun get(key: K): V? = entries[key]

    fun put(key: K, value: V) {
        entries.remove(key)?.let { bytes -= sizeOf(it) }
        val size = sizeOf(value)
        require(size >= 0)
        if (size > maxBytes) return
        entries[key] = value
        bytes += size
        val oldest = entries.iterator()
        while (bytes > maxBytes && oldest.hasNext()) {
            bytes -= sizeOf(oldest.next().value)
            oldest.remove()
        }
    }

    fun retainKeys(keep: (K) -> Boolean) {
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!keep(entry.key)) {
                bytes -= sizeOf(entry.value)
                iterator.remove()
            }
        }
    }

    fun clear() {
        entries.clear()
        bytes = 0
    }
}
