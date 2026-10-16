package com.pulse.bluetoothdisable.updates

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateDownloadTest {
    @get:Rule val temp = TemporaryFolder()
    private fun release() = UpdateManifest.parse("""{"schemaVersion":1,"platform":"android","channel":"stable","applicationId":"com.pulse.bluetoothdisable","release":{"versionCode":30,"versionName":"1.2.3","minSdk":26,"releaseUrl":"https://github.com/alcovaibe/Bluetooth-disabler/releases/tag/v1.2.3","notes":{"ru":"Исправления","en":"Fixes"}}}""".toByteArray()).release!!

    @Test fun exactReleaseUrlAndOnlyHttpsGithubAssetRedirects() {
        val from = GithubUpdateDownloader.downloadUrl(release())
        assertEquals("https://github.com/alcovaibe/Bluetooth-disabler/releases/download/v1.2.3/BluetoothDisable-v1.2.3.apk", from.toString())
        assertEquals("release-assets.githubusercontent.com", GithubUpdateDownloader.validatedRedirect(from, "https://release-assets.githubusercontent.com/a?signature=1").host)
        for (url in listOf("http://release-assets.githubusercontent.com/a", "https://evil.example/a", "https://release-assets.githubusercontent.com.evil.example/a", "https://user@release-assets.githubusercontent.com/a", "https://release-assets.githubusercontent.com:444/a", "https://release-assets.githubusercontent.com/a#fragment", "https://github.com/other/repo/releases/download/a")) {
            assertThrows(InvalidUpdateApk::class.java) { GithubUpdateDownloader.validatedRedirect(from, url) }
        }
    }

    @Test fun downloadFollowsValidatedRedirectStreamsProgressAndVerifiesBeforeReturning() = runBlocking {
        var calls = 0
        var verified = false
        val bytes = ByteArray(80_000) { 42 }
        val client = GithubUpdateDownloader.newClient().newBuilder().addInterceptor { chain ->
            calls++
            assertNull(chain.request().header("Authorization"))
            assertNull(chain.request().header("Cookie"))
            val response = Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("Fixture")
            if (calls == 1) response.code(302).header("Location", "https://release-assets.githubusercontent.com/fixture?sig=1").body("".toResponseBody()).build()
            else response.code(200).body(bytes.toResponseBody()).build()
        }.build()
        GithubUpdateDownloader(temp.root, { file, _ -> assertArrayEquals(bytes, file.readBytes()); verified = true }, client).use {
            var received = 0L
            val file = it.download(release()) { count, total -> received = count; assertEquals(bytes.size.toLong(), total) }
            assertTrue(verified)
            assertEquals(2, calls)
            assertEquals(bytes.size.toLong(), received)
            assertTrue(file.isFile)
        }
    }

    @Test fun failuresNeverReturnAnApkAndRemovePartialFiles() = runBlocking {
        for (mode in listOf("http", "redirect", "oversize", "empty", "invalid-apk", "truncated")) {
            val folder = temp.newFolder()
            val client = GithubUpdateDownloader.newClient().newBuilder().addInterceptor { chain ->
                val body = when (mode) {
                    "oversize", "truncated" -> object : ResponseBody() {
                        override fun contentType() = null
                        override fun contentLength() = if (mode == "oversize") GithubUpdateDownloader.MAX_APK_BYTES + 1 else 100
                        override fun source() = Buffer().write(byteArrayOf(1))
                    }
                    "empty" -> byteArrayOf().toResponseBody()
                    else -> byteArrayOf(1).toResponseBody()
                }
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("Fixture")
                    .code(if (mode == "http") 404 else if (mode == "redirect") 302 else 200)
                    .header("Location", "https://evil.example/a").body(body).build()
            }.build()
            GithubUpdateDownloader(folder, { _, _ -> throw InvalidUpdateApk() }, client).use {
                try { it.download(release()) { _, _ -> }; fail(mode) } catch (_: IOException) { }
                assertEquals(mode, 0, folder.listFiles()!!.size)
            }
        }
    }
}
