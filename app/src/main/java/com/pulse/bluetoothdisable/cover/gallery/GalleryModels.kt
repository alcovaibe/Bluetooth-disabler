package com.pulse.bluetoothdisable.cover.gallery

data class GalleryImage(
    val id: String,
    val encryptedFileName: String,
    val thumbnailFileName: String,
    val width: Int,
    val height: Int,
    val orientation: Int,
    val colorSpace: String?,
    val importedAt: Long,
    val updatedAt: Long,
    val favorite: Boolean,
    val sha256: String,
    val mimeType: String,
    val source: String,
    val shooting: Map<String, String>,
    val capturedAt: Long? = null,
    val albumIds: List<String> = emptyList(),
    val deletedAt: Long? = null,
    val durationMillis: Long? = null,
    val videoBytes: Long = 0,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}

data class GallerySnapshot(
    val images: List<GalleryImage>,
    val recentlyDeleted: List<GalleryImage>,
    val albums: List<GalleryAlbum>,
)

internal object GalleryTrashPolicy {
    const val RETENTION_MILLIS = 29L * 24 * 60 * 60 * 1000

    fun expiresAt(deletedAt: Long): Long =
        if (deletedAt > Long.MAX_VALUE - RETENTION_MILLIS) Long.MAX_VALUE else deletedAt + RETENTION_MILLIS

    fun isExpired(deletedAt: Long?, now: Long): Boolean =
        deletedAt != null && now >= expiresAt(deletedAt)

    fun remainingDays(deletedAt: Long, now: Long): Int {
        val remaining = (expiresAt(deletedAt) - now).coerceAtLeast(0)
        return (remaining / 86_400_000L + if (remaining % 86_400_000L > 0) 1 else 0).coerceAtMost(29).toInt()
    }
}

data class GalleryAlbum(
    val id: String,
    val name: String,
    val createdAt: Long,
)
