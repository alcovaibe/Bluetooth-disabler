package com.pulse.bluetoothdisable.cover.gallery

import org.junit.Assert.*
import org.junit.Test

class GalleryMemoryCacheTest {
    private fun cache(bytes: Long = 8) = GalleryMemoryCache<String, String>(bytes) { it.length.toLong() }

    @Test fun evictsByBytesAndKeepsRecentlyViewedPhoto() {
        val cache = cache()
        cache.put("a", "aaaa")
        cache.put("b", "bbbb")
        assertEquals("aaaa", cache["a"])
        cache.put("c", "cccc")
        assertNull(cache["b"])
        assertEquals("aaaa", cache["a"])
        assertEquals("cccc", cache["c"])
    }

    @Test fun replacingEntryDoesNotDoubleCountItsAllocation() {
        val cache = cache()
        cache.put("a", "aaaa")
        cache.put("a", "aa")
        cache.put("b", "bbbbbb")
        assertEquals("aa", cache["a"])
        assertEquals("bbbbbb", cache["b"])
    }

    @Test fun oversizedPreviewCannotEvictAllOtherPhotos() {
        val cache = cache()
        cache.put("a", "aaaa")
        cache.put("large", "012345678")
        assertNull(cache["large"])
        assertEquals("aaaa", cache["a"])
    }

    @Test fun oldRevisionsAndDeletedPhotosAreRemoved() {
        val cache = cache()
        cache.put("photo:old", "old")
        cache.put("deleted:old", "gone")
        cache.retainKeys { it == "photo:new" }
        assertNull(cache["photo:old"])
        assertNull(cache["deleted:old"])
        cache.put("photo:new", "updated")
        assertEquals("updated", cache["photo:new"])
        cache.clear()
        assertNull(cache["photo:new"])
    }
}
