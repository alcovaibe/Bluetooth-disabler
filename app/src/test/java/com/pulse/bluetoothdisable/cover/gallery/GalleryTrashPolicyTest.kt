package com.pulse.bluetoothdisable.cover.gallery

import org.junit.Assert.*
import org.junit.Test

class GalleryTrashPolicyTest {
    @Test fun retainsForTwentyNineFullDaysAndExpiresAtTheBoundary() {
        val deleted = 1_000_000_000L
        val expiry = deleted + 29L * 24 * 60 * 60 * 1000
        assertEquals(expiry, GalleryTrashPolicy.expiresAt(deleted))
        assertFalse(GalleryTrashPolicy.isExpired(deleted, expiry - 1))
        assertTrue(GalleryTrashPolicy.isExpired(deleted, expiry))
        assertTrue(GalleryTrashPolicy.isExpired(deleted, expiry + 1))
        assertFalse(GalleryTrashPolicy.isExpired(null, Long.MAX_VALUE))
    }

    @Test fun countdownRoundsUpAndFutureDatesDoNotExpireEarly() {
        val deleted = 1_000_000_000L
        assertEquals(29, GalleryTrashPolicy.remainingDays(deleted, deleted))
        assertEquals(29, GalleryTrashPolicy.remainingDays(deleted, deleted - 1000))
        assertEquals(1, GalleryTrashPolicy.remainingDays(deleted, GalleryTrashPolicy.expiresAt(deleted) - 1))
        assertEquals(0, GalleryTrashPolicy.remainingDays(deleted, GalleryTrashPolicy.expiresAt(deleted)))
        assertFalse(GalleryTrashPolicy.isExpired(deleted, deleted - 1000))
        assertEquals(Long.MAX_VALUE, GalleryTrashPolicy.expiresAt(Long.MAX_VALUE))
    }
}
