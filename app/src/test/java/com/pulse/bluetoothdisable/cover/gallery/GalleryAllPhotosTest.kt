package com.pulse.bluetoothdisable.cover.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryAllPhotosTest {
    @Test fun combinesAlbumAndUnassignedPhotosOnceAndExcludesDeletedMediaAndVideos() {
        val shared = photo("shared").copy(albumIds = listOf("album-a", "album-b"), favorite = true)
        val unassigned = photo("unassigned")
        val deleted = photo("deleted").copy(deletedAt = 10L)
        val video = photo("video").copy(mimeType = "video/mp4")
        assertEquals(
            listOf("shared", "unassigned"),
            galleryAllPhotos(listOf(shared, unassigned, shared, deleted, video)).map { it.id },
        )
        assertEquals(listOf(shared, unassigned), galleryAllPhotos(listOf(shared, unassigned)))
        assertEquals(emptyList<GalleryImage>(), galleryAllPhotos(listOf(deleted, video)))
    }

    private fun photo(id: String) = GalleryImage(
        id = id,
        encryptedFileName = "$id.enc",
        thumbnailFileName = "$id.thumb",
        width = 100,
        height = 100,
        orientation = 0,
        colorSpace = null,
        importedAt = 1L,
        updatedAt = 1L,
        favorite = false,
        sha256 = id,
        mimeType = "image/jpeg",
        source = "test",
        shooting = emptyMap(),
    )
}
