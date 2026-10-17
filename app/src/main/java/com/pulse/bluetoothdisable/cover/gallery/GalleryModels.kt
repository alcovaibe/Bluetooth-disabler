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
)

data class GalleryAlbum(
    val id: String,
    val name: String,
    val createdAt: Long,
)
