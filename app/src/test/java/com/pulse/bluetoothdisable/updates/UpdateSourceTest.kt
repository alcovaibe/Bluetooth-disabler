package com.pulse.bluetoothdisable.updates

import java.io.IOException
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class UpdateSourceTest {
    private val json = """{"schemaVersion":1,"platform":"android","channel":"stable","applicationId":"com.pulse.bluetoothdisable","release":null}"""
    private fun response(code: Int = 200, bytes: ByteArray = json.toByteArray(), type: String = "application/json") = Response.Builder()
        .request(Request.Builder().url(UpdateUrls.MANIFEST).build()).protocol(Protocol.HTTP_1_1)
        .code(code).message("Fixture").body(bytes.toResponseBody(type.toMediaType()))

    @Test fun clientDisablesRedirectsCookiesAuthenticationAndRetries() {
        SiteUpdateSource().use { source ->
            assertFalse(source.client.followRedirects)
            assertFalse(source.client.followSslRedirects)
            assertFalse(source.client.retryOnConnectionFailure)
            assertSame(CookieJar.NO_COOKIES, source.client.cookieJar)
            assertNull(source.client.cache)
            assertEquals(15000, source.client.callTimeoutMillis)
        }
    }
    @Test fun validJsonAndConditionalResponse() {
        val cached = response().header("ETag", "\"fixture\"").build().use { SiteUpdateSource.parseResponse(it, null) }
        assertNull(cached.manifest.release)
        response(304).build().use { assertSame(cached, SiteUpdateSource.parseResponse(it, cached)) }
        response(304).build().use { assertThrows(InvalidUpdateResponse::class.java) { SiteUpdateSource.parseResponse(it, null) } }
        val unsafe = response().header("ETag", "unquoted").build().use { SiteUpdateSource.parseResponse(it, null) }
        assertNull(unsafe.etag)
    }
    @Test fun redirectsHttpErrorsWrongTypeAndMalformedJsonAreRejected() {
        for (code in listOf(301, 302, 303, 307, 308)) response(code).header("Location", "https://evil.example/").build().use {
            assertThrows(InvalidUpdateResponse::class.java) { SiteUpdateSource.parseResponse(it, null) }
        }
        for (code in listOf(403, 404, 500)) response(code).build().use {
            assertThrows(IOException::class.java) { SiteUpdateSource.parseResponse(it, null) }
        }
        response(type = "text/html").build().use { assertThrows(InvalidUpdateResponse::class.java) { SiteUpdateSource.parseResponse(it, null) } }
        response(bytes = "{".toByteArray()).build().use { assertThrows(InvalidUpdateResponse::class.java) { SiteUpdateSource.parseResponse(it, null) } }
    }
    @Test fun declaredAndStreamedSizesAreBounded() {
        response(bytes = ByteArray(16385)).build().use { assertThrows(InvalidUpdateResponse::class.java) { SiteUpdateSource.parseResponse(it, null) } }
        val streamed = object : ResponseBody() {
            override fun contentType() = "application/json".toMediaType()
            override fun contentLength() = -1L
            override fun source() = Buffer().write(ByteArray(16385))
        }
        response().body(streamed).build().use { assertThrows(InvalidUpdateResponse::class.java) { SiteUpdateSource.parseResponse(it, null) } }
    }
}
