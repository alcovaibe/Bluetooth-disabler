package com.pulse.bluetoothdisable.cover.gallery

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.os.Build
import android.webkit.MimeTypeMap
import android.util.AtomicFile
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class GalleryRepository(context: Context, private val nowMillis: () -> Long = System::currentTimeMillis) {
    private val appContext = context.applicationContext
    private val root = File(appContext.noBackupFilesDir, "gallery_v1")
    private val imagesDir = File(root, "images")
    private val thumbsDir = File(root, "thumbs")
    private val indexFile = File(root, "index.enc")
    private val albumsFile = File(root, "albums.enc")

    init {
        check(imagesDir.mkdirs() || imagesDir.isDirectory) { "Unable to create gallery image directory" }
        check(thumbsDir.mkdirs() || thumbsDir.isDirectory) { "Unable to create gallery thumbnail directory" }
    }

    fun snapshot(): GallerySnapshot = synchronized(STORAGE_LOCK) {
        val items = purgeExpiredLocked(nowMillis())
        reconcileOrphans(items)
        GallerySnapshot(
            images = items.filter { it.deletedAt == null }.sortedByDescending { it.capturedAt ?: it.importedAt },
            recentlyDeleted = items.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt },
            albums = loadAlbums().sortedBy { it.name.lowercase(Locale.ROOT) },
        )
    }

    fun images(): List<GalleryImage> = snapshot().images
    fun recentlyDeleted(): List<GalleryImage> = snapshot().recentlyDeleted

    fun purgeExpiredTrash() = synchronized(STORAGE_LOCK) {
        purgeExpiredLocked(nowMillis())
        Unit
    }

    fun nextTrashExpiry(): Long? = synchronized(STORAGE_LOCK) {
        loadIndex().mapNotNull { it.deletedAt?.let(GalleryTrashPolicy::expiresAt) }.minOrNull()
    }

    private fun purgeExpiredLocked(now: Long): List<GalleryImage> {
        val current = loadIndex()
        val expired = current.filter { GalleryTrashPolicy.isExpired(it.deletedAt, now) }
        if (expired.isEmpty()) return current
        val kept = current.filterNot { GalleryTrashPolicy.isExpired(it.deletedAt, now) }
        saveIndex(kept) // Commit first: interrupted cleanup is repaired by orphan reconciliation.
        expired.forEach(::deleteFiles)
        return kept
    }

    private fun deleteFiles(item: GalleryImage) {
        File(imagesDir, item.encryptedFileName).delete()
        File(thumbsDir, item.thumbnailFileName).delete()
    }

    private fun scheduleCleanup(items: List<GalleryImage>) {
        GalleryTrashCleanupService.schedule(appContext,
            items.mapNotNull { it.deletedAt?.let(GalleryTrashPolicy::expiresAt) }.minOrNull())
    }

    fun moveToTrash(id: String): Boolean = synchronized(STORAGE_LOCK) {
        val current = purgeExpiredLocked(nowMillis()).toMutableList()
        val index = current.indexOfFirst { it.id == id && it.deletedAt == null }
        if (index < 0) return@synchronized false
        val now = nowMillis()
        current[index] = current[index].copy(deletedAt = now, updatedAt = now)
        saveIndex(current)
        scheduleCleanup(current)
        true
    }

    fun restore(id: String): Boolean = synchronized(STORAGE_LOCK) {
        val current = purgeExpiredLocked(nowMillis()).toMutableList()
        val index = current.indexOfFirst { it.id == id && it.deletedAt != null }
        if (index < 0) return@synchronized false
        current[index] = current[index].copy(deletedAt = null, updatedAt = nowMillis())
        saveIndex(current)
        scheduleCleanup(current)
        true
    }

    fun albums(): List<GalleryAlbum> = synchronized(STORAGE_LOCK) {
        loadAlbums().sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    fun image(id: String): GalleryImage? = synchronized(STORAGE_LOCK) {
        purgeExpiredLocked(nowMillis()).firstOrNull { it.id == id }
    }

    fun imageBytes(id: String): ByteArray = synchronized(STORAGE_LOCK) {
        val item = purgeExpiredLocked(nowMillis()).firstOrNull { it.id == id } ?: error("Unknown gallery image")
        require(!item.isVideo) { "Use the seekable video source" }
        GalleryCipher.decrypt(File(imagesDir, item.encryptedFileName).readBytes(), "image:${item.id}")
    }

    fun thumbnailBytes(id: String): ByteArray = synchronized(STORAGE_LOCK) {
        val item = purgeExpiredLocked(nowMillis()).firstOrNull { it.id == id } ?: error("Unknown gallery image")
        GalleryCipher.decrypt(File(thumbsDir, item.thumbnailFileName).readBytes(), "thumb:${item.id}")
    }

    internal fun openVideo(id: String): GalleryVideoDataSource = synchronized(STORAGE_LOCK) {
        val item = purgeExpiredLocked(nowMillis()).firstOrNull { it.id == id } ?: error("Unknown gallery video")
        require(item.isVideo)
        GalleryVideoDataSource(File(imagesDir, item.encryptedFileName), item.id, item.videoBytes)
    }

    fun importUris(resolver: ContentResolver, uris: List<Uri>): List<GalleryImage> =
        synchronized(STORAGE_LOCK) {
            val current = purgeExpiredLocked(nowMillis()).toMutableList()
            val added = mutableListOf<GalleryImage>()
            val newFiles = mutableListOf<File>()

            try {
                for (uri in uris) {
                    if (isVideoUri(resolver, uri)) {
                        importVideo(resolver, uri, current, newFiles)?.let { item ->
                            val existing = current.indexOfFirst { it.id == item.id }
                            if (existing < 0) current += item else current[existing] = item
                            added += item
                        }
                        continue
                    }
                    val source = readBounded(resolver, uri)
                    val prepared = prepareImage(source)
                    try {
                        val duplicate = current.firstOrNull { it.sha256 == prepared.sha256 }
                        if (duplicate != null) {
                            if (duplicate.deletedAt != null) {
                                val restored = duplicate.copy(deletedAt = null, updatedAt = nowMillis())
                                current[current.indexOf(duplicate)] = restored
                                added += restored
                            }
                            continue
                        }

                        val id = UUID.randomUUID().toString()
                        val imageFileName = "$id.bin"
                        val thumbFileName = "$id.bin"
                        val imageFile = File(imagesDir, imageFileName)
                        val thumbFile = File(thumbsDir, thumbFileName)

                        imageFile.writeBytes(
                            GalleryCipher.encrypt(prepared.encoded, "image:$id"),
                        )
                        newFiles += imageFile
                        thumbFile.writeBytes(
                            GalleryCipher.encrypt(makeThumbnail(prepared.bitmap), "thumb:$id"),
                        )
                        newFiles += thumbFile

                        val now = nowMillis()
                        val item = GalleryImage(
                            id = id,
                            encryptedFileName = imageFileName,
                            thumbnailFileName = thumbFileName,
                            width = prepared.bitmap.width,
                            height = prepared.bitmap.height,
                            orientation = 1,
                            colorSpace = prepared.bitmap.colorSpace?.name,
                            importedAt = now,
                            updatedAt = now,
                            favorite = false,
                            sha256 = prepared.sha256,
                            mimeType = prepared.mimeType,
                            source = SOURCE_USER_PICKER,
                            shooting = prepared.shooting,
                            capturedAt = prepared.capturedAt,
                        )
                        current += item
                        added += item
                    } finally {
                        prepared.bitmap.recycle()
                    }
                }

                if (added.isNotEmpty()) {
                    saveIndex(current)
                    scheduleCleanup(current)
                }
                added
            } catch (error: Throwable) {
                newFiles.forEach { it.delete() }
                throw error
            }
        }

    fun toggleFavorite(id: String): GalleryImage? = synchronized(STORAGE_LOCK) {
        val current = purgeExpiredLocked(nowMillis()).toMutableList()
        val index = current.indexOfFirst { it.id == id && it.deletedAt == null }
        if (index < 0) return@synchronized null
        val changed = current[index].copy(
            favorite = !current[index].favorite,
            updatedAt = nowMillis(),
        )
        current[index] = changed
        saveIndex(current)
        changed
    }

    fun createAlbum(name: String): GalleryAlbum = synchronized(STORAGE_LOCK) {
        val normalized = name.trim()
        require(normalized.isNotEmpty() && normalized.length <= MAX_ALBUM_NAME_LENGTH) {
            "Invalid album name"
        }
        val current = loadAlbums().toMutableList()
        current.firstOrNull { it.name.equals(normalized, ignoreCase = true) }?.let {
            return@synchronized it
        }

        val album = GalleryAlbum(
            id = UUID.randomUUID().toString(),
            name = normalized,
            createdAt = nowMillis(),
        )
        current += album
        saveAlbums(current)
        album
    }

    fun addToAlbum(imageId: String, albumId: String): GalleryImage = synchronized(STORAGE_LOCK) {
        require(loadAlbums().any { it.id == albumId }) { "Unknown gallery album" }
        val current = purgeExpiredLocked(nowMillis()).toMutableList()
        val index = current.indexOfFirst { it.id == imageId }
        require(index >= 0) { "Unknown gallery image" }
        val old = current[index]
        require(old.deletedAt == null) { "Cannot change a deleted item" }
        if (albumId in old.albumIds) return@synchronized old

        val changed = old.copy(
            albumIds = old.albumIds + albumId,
            updatedAt = nowMillis(),
        )
        current[index] = changed
        saveIndex(current)
        changed
    }

    fun delete(id: String): Boolean = synchronized(STORAGE_LOCK) {
        val current = purgeExpiredLocked(nowMillis()).toMutableList()
        val item = current.firstOrNull { it.id == id } ?: return@synchronized false
        current.removeAll { it.id == id }
        saveIndex(current)
        deleteFiles(item)
        scheduleCleanup(current)
        true
    }

    fun rotate(id: String, degrees: Int): GalleryImage = synchronized(STORAGE_LOCK) {
        require(degrees % 90 == 0)
        edit(id) { source ->
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }
    }

    fun cropCenterSquare(id: String): GalleryImage = synchronized(STORAGE_LOCK) {
        edit(id) { source ->
            val size = minOf(source.width, source.height)
            val left = (source.width - size) / 2
            val top = (source.height - size) / 2
            Bitmap.createBitmap(source, left, top, size, size)
        }
    }

    private fun edit(id: String, transform: (Bitmap) -> Bitmap): GalleryImage {
        val current = purgeExpiredLocked(nowMillis()).toMutableList()
        val index = current.indexOfFirst { it.id == id }
        require(index >= 0) { "Unknown gallery image" }
        val old = current[index]
        require(!old.isVideo && old.deletedAt == null) { "Only active photos can be edited" }
        val originalBytes = GalleryCipher.decrypt(
            File(imagesDir, old.encryptedFileName).readBytes(),
            "image:${old.id}",
        )
        val source = checkNotNull(BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size))
        val edited = transform(source)
        if (edited !== source) source.recycle()

        val revision = UUID.randomUUID().toString()
        val newImageFileName = "${old.id}-$revision.bin"
        val newThumbFileName = "${old.id}-$revision.bin"
        val newImageFile = File(imagesDir, newImageFileName)
        val newThumbFile = File(thumbsDir, newThumbFileName)

        try {
            val encoded = encodeSanitized(edited)
            val thumb = makeThumbnail(edited)
            newImageFile.writeBytes(
                GalleryCipher.encrypt(encoded.bytes, "image:${old.id}"),
            )
            newThumbFile.writeBytes(
                GalleryCipher.encrypt(thumb, "thumb:${old.id}"),
            )

            val changed = old.copy(
                encryptedFileName = newImageFileName,
                thumbnailFileName = newThumbFileName,
                width = edited.width,
                height = edited.height,
                orientation = 1,
                colorSpace = edited.colorSpace?.name ?: old.colorSpace,
                updatedAt = nowMillis(),
                sha256 = sha256(encoded.bytes),
                mimeType = encoded.mimeType,
            )
            current[index] = changed
            saveIndex(current)

            File(imagesDir, old.encryptedFileName).delete()
            File(thumbsDir, old.thumbnailFileName).delete()
            return changed
        } catch (error: Throwable) {
            newImageFile.delete()
            newThumbFile.delete()
            throw error
        } finally {
            edited.recycle()
        }
    }

    private fun isVideoUri(resolver: ContentResolver, uri: Uri): Boolean {
        val mime = resolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(uri.lastPathSegment?.substringAfterLast('.')?.lowercase(Locale.ROOT))
        return mime?.startsWith("video/") == true
    }

    private fun importVideo(
        resolver: ContentResolver,
        uri: Uri,
        current: MutableList<GalleryImage>,
        newFiles: MutableList<File>,
    ): GalleryImage? {
        val id = UUID.randomUUID().toString()
        val videoFile = File(imagesDir, "$id.video")
        newFiles += videoFile
        val (size, hash) = checkNotNull(resolver.openInputStream(uri)).use {
            GalleryVideoDataSource.write(it, videoFile, id)
        }
        current.firstOrNull { it.isVideo && it.sha256 == hash }?.let { duplicate ->
            videoFile.delete()
            return if (duplicate.deletedAt != null) {
                duplicate.copy(deletedAt = null, updatedAt = nowMillis()).also {
                    current[current.indexOf(duplicate)] = it
                }
            } else null
        }
        val data = GalleryVideoDataSource(videoFile, id, size)
        val retriever = MediaMetadataRetriever()
        var frame: Bitmap? = null
        try {
            retriever.setDataSource(data)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            require(width > 0 && height > 0) { "Unsupported video" }
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                ?.takeIf { it.startsWith("video/") } ?: "video/mp4"
            frame = if (Build.VERSION.SDK_INT >= 27) {
                retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 512, 512)
            } else retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            val thumbnail = checkNotNull(frame) { "Unable to read video preview" }
            val thumbFile = File(thumbsDir, "$id.bin")
            newFiles += thumbFile
            thumbFile.writeBytes(GalleryCipher.encrypt(makeThumbnail(thumbnail), "thumb:$id"))
            val now = nowMillis()
            return GalleryImage(
                id = id, encryptedFileName = videoFile.name, thumbnailFileName = thumbFile.name,
                width = if (rotation % 180 == 0) width else height,
                height = if (rotation % 180 == 0) height else width,
                orientation = 1, colorSpace = thumbnail.colorSpace?.name,
                importedAt = now, updatedAt = now, favorite = false, sha256 = hash,
                mimeType = mime, source = SOURCE_USER_PICKER, shooting = emptyMap(),
                durationMillis = duration, videoBytes = size,
            )
        } finally {
            frame?.recycle()
            retriever.release()
            data.close()
        }
    }

    private data class PreparedImage(
        val bitmap: Bitmap,
        val encoded: ByteArray,
        val sha256: String,
        val mimeType: String,
        val shooting: Map<String, String>,
        val capturedAt: Long?,
    )

    private data class EncodedImage(val bytes: ByteArray, val mimeType: String)

    private fun prepareImage(source: ByteArray): PreparedImage {
        val exif = runCatching { ExifInterface(ByteArrayInputStream(source)) }.getOrNull()
        val orientation = exif?.getAttributeInt("Orientation", 1) ?: 1
        val shooting = readShootingMetadata(exif)
        val capturedAt = readCaptureTime(exif)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image" }
        var sample = 1
        while (
            (bounds.outWidth.toLong() / sample) * (bounds.outHeight.toLong() / sample) >
            MAX_DECODE_PIXELS
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = checkNotNull(BitmapFactory.decodeByteArray(source, 0, source.size, options)) {
            "Unable to decode image"
        }
        val normalized = normalizeOrientation(decoded, orientation)
        if (normalized !== decoded) decoded.recycle()
        val encoded = encodeSanitized(normalized)
        return PreparedImage(
            bitmap = normalized,
            encoded = encoded.bytes,
            sha256 = sha256(encoded.bytes),
            mimeType = encoded.mimeType,
            shooting = shooting,
            capturedAt = capturedAt,
        )
    }

    private fun normalizeOrientation(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            2 -> matrix.setScale(-1f, 1f)
            3 -> matrix.setRotate(180f)
            4 -> matrix.setScale(1f, -1f)
            5 -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            6 -> matrix.setRotate(90f)
            7 -> {
                matrix.setRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            8 -> matrix.setRotate(270f)
            else -> return source
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /** Re-encoding is intentional: source EXIF/XMP/IPTC/MakerNote/print blocks are not copied. */
    private fun encodeSanitized(bitmap: Bitmap): EncodedImage {
        val output = ByteArrayOutputStream()
        val alpha = bitmap.hasAlpha()
        val format = if (alpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        check(bitmap.compress(format, if (alpha) 100 else 96, output)) { "Unable to encode image" }
        return EncodedImage(
            bytes = output.toByteArray(),
            mimeType = if (alpha) "image/png" else "image/jpeg",
        )
    }

    private fun makeThumbnail(bitmap: Bitmap): ByteArray {
        val scale = minOf(1f, THUMBNAIL_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height))
        val width = maxOf(1, (bitmap.width * scale).toInt())
        val height = maxOf(1, (bitmap.height * scale).toInt())
        val thumb = if (width == bitmap.width && height == bitmap.height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
        val output = ByteArrayOutputStream()
        check(thumb.compress(Bitmap.CompressFormat.JPEG, 88, output)) { "Unable to encode thumbnail" }
        if (thumb !== bitmap) thumb.recycle()
        return output.toByteArray()
    }

    private fun readShootingMetadata(exif: ExifInterface?): Map<String, String> {
        if (exif == null) return emptyMap()
        val result = linkedMapOf<String, String>()
        SHOOTING_TAGS.forEach { tag ->
            exif.getAttribute(tag)?.takeIf { it.isNotBlank() }?.let { result[tag] = it }
        }
        if (result["PhotographicSensitivity"] == null) {
            exif.getAttribute("ISOSpeedRatings")?.takeIf { it.isNotBlank() }?.let {
                result["PhotographicSensitivity"] = it
            }
        }
        return result
    }

    private fun readCaptureTime(exif: ExifInterface?): Long? {
        if (exif == null) return null
        val value = listOf("DateTimeOriginal", "DateTimeDigitized", "DateTime")
            .firstNotNullOfOrNull { tag ->
                exif.getAttribute(tag)?.takeIf { it.isNotBlank() }
            } ?: return null

        return runCatching {
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
                isLenient = false
            }.parse(value)?.time
        }.getOrNull()
    }

    private fun readBounded(resolver: ContentResolver, uri: Uri): ByteArray {
        val stream = checkNotNull(resolver.openInputStream(uri)) { "Unable to open selected image" }
        stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_INPUT_BYTES) { "Selected image is too large" }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }

    private fun loadIndex(): List<GalleryImage> {
        val encrypted = readAtomic(indexFile) ?: return emptyList()
        val json = String(
            GalleryCipher.decrypt(encrypted, INDEX_PURPOSE),
            Charsets.UTF_8,
        )
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) add(fromJson(array.getJSONObject(i)))
        }
    }

    private fun saveIndex(items: List<GalleryImage>) {
        val array = JSONArray()
        items.forEach { array.put(toJson(it)) }
        saveEncryptedArray(indexFile, INDEX_PURPOSE, array)
    }

    private fun loadAlbums(): List<GalleryAlbum> {
        val encrypted = readAtomic(albumsFile) ?: return emptyList()
        val json = String(
            GalleryCipher.decrypt(encrypted, ALBUMS_PURPOSE),
            Charsets.UTF_8,
        )
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    GalleryAlbum(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        createdAt = item.optLong("createdAt", 0L),
                    ),
                )
            }
        }
    }

    private fun saveAlbums(items: List<GalleryAlbum>) {
        val array = JSONArray()
        items.forEach { album ->
            array.put(
                JSONObject().apply {
                    put("id", album.id)
                    put("name", album.name)
                    put("createdAt", album.createdAt)
                },
            )
        }
        saveEncryptedArray(albumsFile, ALBUMS_PURPOSE, array)
    }

    private fun readAtomic(target: File): ByteArray? = try {
        AtomicFile(target).readFully()
    } catch (_: FileNotFoundException) {
        null
    }

    private fun saveEncryptedArray(target: File, purpose: String, array: JSONArray) {
        val encrypted = GalleryCipher.encrypt(array.toString().toByteArray(Charsets.UTF_8), purpose)
        val atomic = AtomicFile(target)
        val output = atomic.startWrite()
        try {
            output.write(encrypted)
            atomic.finishWrite(output)
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        }
    }

    private fun reconcileOrphans(items: List<GalleryImage>) {
        val referencedImages = items.mapTo(hashSetOf()) { it.encryptedFileName }
        val referencedThumbs = items.mapTo(hashSetOf()) { it.thumbnailFileName }
        imagesDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name !in referencedImages) file.delete()
        }
        thumbsDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name !in referencedThumbs) file.delete()
        }
    }

    private fun toJson(item: GalleryImage) = JSONObject().apply {
        put("id", item.id)
        put("file", item.encryptedFileName)
        put("thumb", item.thumbnailFileName)
        put("width", item.width)
        put("height", item.height)
        put("orientation", item.orientation)
        put("colorSpace", item.colorSpace ?: JSONObject.NULL)
        put("importedAt", item.importedAt)
        put("updatedAt", item.updatedAt)
        put("favorite", item.favorite)
        put("sha256", item.sha256)
        put("mimeType", item.mimeType)
        put("source", item.source)
        put("shooting", JSONObject(item.shooting))
        put("capturedAt", item.capturedAt ?: JSONObject.NULL)
        put("albums", JSONArray().apply { item.albumIds.forEach { put(it) } })
        put("deletedAt", item.deletedAt ?: JSONObject.NULL)
        put("durationMillis", item.durationMillis ?: JSONObject.NULL)
        put("videoBytes", item.videoBytes)
    }

    private fun fromJson(json: JSONObject): GalleryImage {
        val shootingJson = json.optJSONObject("shooting") ?: JSONObject()
        val shooting = linkedMapOf<String, String>()
        val keys = shootingJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            shooting[key] = shootingJson.getString(key)
        }

        val albumIds = buildList {
            val albums = json.optJSONArray("albums") ?: JSONArray()
            for (i in 0 until albums.length()) {
                albums.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }
        }

        return GalleryImage(
            id = json.getString("id"),
            encryptedFileName = json.getString("file"),
            thumbnailFileName = json.getString("thumb"),
            width = json.getInt("width"),
            height = json.getInt("height"),
            orientation = json.optInt("orientation", 1),
            colorSpace = json.optString("colorSpace").takeIf { it.isNotBlank() && it != "null" },
            importedAt = json.getLong("importedAt"),
            updatedAt = json.getLong("updatedAt"),
            favorite = json.optBoolean("favorite", false),
            sha256 = json.getString("sha256"),
            mimeType = json.optString("mimeType", "image/jpeg"),
            source = json.optString("source", SOURCE_USER_PICKER),
            shooting = shooting,
            capturedAt = if (json.has("capturedAt") && !json.isNull("capturedAt")) {
                json.optLong("capturedAt")
            } else {
                null
            },
            albumIds = albumIds,
            deletedAt = if (json.has("deletedAt") && !json.isNull("deletedAt")) json.getLong("deletedAt") else null,
            durationMillis = if (json.has("durationMillis") && !json.isNull("durationMillis")) json.getLong("durationMillis") else null,
            videoBytes = json.optLong("videoBytes", 0),
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        const val SOURCE_USER_PICKER = "USER_PICKER"
        private const val INDEX_PURPOSE = "index"
        private const val ALBUMS_PURPOSE = "albums"
        private const val MAX_INPUT_BYTES = 64 * 1024 * 1024
        private const val MAX_DECODE_PIXELS = 24_000_000L
        private const val THUMBNAIL_SIZE = 512
        private const val MAX_ALBUM_NAME_LENGTH = 80
        private val STORAGE_LOCK = Any()

        private val SHOOTING_TAGS = listOf(
            "ExposureTime",
            "FNumber",
            "ApertureValue",
            "PhotographicSensitivity",
            "FocalLength",
            "Flash",
            "ExposureBiasValue",
            "ExposureMode",
            "ExposureProgram",
            "MeteringMode",
            "WhiteBalance",
            "LightSource",
            "SceneCaptureType",
            "DigitalZoomRatio",
            "FocalLengthIn35mmFilm",
            "Contrast",
            "Saturation",
            "Sharpness",
        )
    }
}
