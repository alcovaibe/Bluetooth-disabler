package com.pulse.bluetoothdisable.testaccess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestAccessLeaseTest {
    @Test fun `no lease is granted before a successful response or after process restart`() {
        assertFalse(TestAccessLease().isValid(0))
        val lease = TestAccessLease()
        assertTrue(lease.apply(TestConfig(true, 900), 1_000, 2_000))
        assertFalse(TestAccessLease().isValid(2_000))
    }

    @Test fun `network time consumes lease and access expires at exact deadline`() {
        val lease = TestAccessLease()
        assertTrue(lease.apply(TestConfig(true, 15), 1_000, 5_000))
        assertEquals(11_000L, lease.remainingMillis(5_000))
        assertTrue(lease.isValid(15_999))
        assertFalse(lease.isValid(16_000))
        assertEquals(0L, lease.remainingMillis(16_000))
    }

    @Test fun `disabled config and token removal immediately revoke existing lease`() {
        val lease = TestAccessLease()
        lease.apply(TestConfig(true, 900), 0, 0)
        assertFalse(lease.apply(TestConfig(false, 0), 10, 10))
        assertFalse(lease.isValid(11))
        lease.apply(TestConfig(true, 900), 20, 20)
        lease.clear()
        assertFalse(lease.isValid(21))
    }

    @Test fun `invalid leases and delayed responses never grant access`() {
        for (ttl in listOf(-1, 0, MAX_TEST_ACCESS_SECONDS + 1, Int.MAX_VALUE)) {
            assertFalse(TestAccessLease().apply(TestConfig(true, ttl), 0, 0))
        }
        assertFalse(TestAccessLease().apply(TestConfig(true, 1), 0, 1_000))
        assertFalse(TestAccessLease().apply(TestConfig(true, 900), 100, 99))
        assertFalse(TestAccessLease().apply(TestConfig(true, 900), Long.MAX_VALUE, Long.MAX_VALUE))
    }

    @Test fun `fourteen day lease counts network time and expires at the exact deadline`() {
        val lease = TestAccessLease()
        val duration = 1_209_600_000L
        assertEquals(1_209_600, MAX_TEST_ACCESS_SECONDS)
        assertTrue(lease.apply(TestConfig(true, MAX_TEST_ACCESS_SECONDS), 1_000, 6_000))
        assertEquals(duration - 5_000, lease.remainingMillis(6_000))
        assertTrue(lease.isValid(1_000 + duration - 1))
        assertFalse(lease.isValid(1_000 + duration))
        assertEquals(0L, lease.remainingMillis(1_000 + duration))
        assertFalse(TestAccessLease().apply(
            TestConfig(true, MAX_TEST_ACCESS_SECONDS), Long.MAX_VALUE - duration + 1,
            Long.MAX_VALUE - duration + 1,
        ))
    }

    @Test fun `read-only validity checks do not extend an offline lease`() {
        val lease = TestAccessLease()
        lease.apply(TestConfig(true, 900), 0, 0)
        assertTrue(lease.isValid(899_999))
        assertFalse(lease.isValid(900_000))
        assertFalse(lease.isValid(-1))
    }
}
