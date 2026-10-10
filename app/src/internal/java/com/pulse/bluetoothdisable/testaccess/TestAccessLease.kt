package com.pulse.bluetoothdisable.testaccess

data class TestConfig(val allowLauncherWithoutDeviceOwner: Boolean, val validForSeconds: Int)

/** Process-local lease measured against elapsedRealtime, never the editable wall clock. */
class TestAccessLease {
    private var issuedAt = -1L
    private var expiresAt = -1L

    fun apply(config: TestConfig, requestStartedAt: Long, now: Long): Boolean {
        clear()
        if (!config.allowLauncherWithoutDeviceOwner || config.validForSeconds !in 1..900 ||
            requestStartedAt < 0 || now < requestStartedAt
        ) return false
        val duration = config.validForSeconds * 1_000L
        if (requestStartedAt > Long.MAX_VALUE - duration) return false
        issuedAt = requestStartedAt
        // Count network time toward the lease, rather than extending it on arrival.
        expiresAt = requestStartedAt + duration
        return isValid(now)
    }

    fun isValid(now: Long): Boolean = issuedAt >= 0 && now >= issuedAt && now < expiresAt

    fun remainingMillis(now: Long): Long = if (isValid(now)) expiresAt - now else 0

    fun clear() {
        issuedAt = -1
        expiresAt = -1
    }
}
