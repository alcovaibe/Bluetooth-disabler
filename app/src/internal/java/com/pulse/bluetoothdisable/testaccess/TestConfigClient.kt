package com.pulse.bluetoothdisable.testaccess

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

internal class TestConfigClient {
    fun fetch(token: String): TestConfig {
        require(TOKEN_PATTERN.matches(token))
        val connection = URL(ENDPOINT).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            val status = connection.responseCode
            if (status == 401 || status == 403) return TestConfig(false, 0)
            if (status != 200) throw IOException("Test config unavailable")
            require(connection.contentType?.substringBefore(';')?.trim()
                ?.equals("application/json", ignoreCase = true) == true)
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(1_024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 4_096)
                    output.write(buffer, 0, count)
                }
            }
            val json = JSONObject(output.toString("UTF-8"))
            val allowed = json.get("allowLauncherWithoutDeviceOwner") as? Boolean
                ?: throw IllegalArgumentException("Invalid flag")
            val ttl = json.get("validForSeconds") as? Int
                ?: throw IllegalArgumentException("Invalid lease")
            require(if (allowed) ttl in 1..900 else ttl == 0)
            return TestConfig(allowed, ttl)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val ENDPOINT = "https://bluetoothdisable.app/api/test-config"
        val TOKEN_PATTERN = Regex("^[A-Za-z0-9_-]{43}$")
    }
}
