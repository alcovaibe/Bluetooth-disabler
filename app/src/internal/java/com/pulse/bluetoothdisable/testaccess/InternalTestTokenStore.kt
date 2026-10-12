package com.pulse.bluetoothdisable.testaccess

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypt at rest and exclude from both cloud backup and device transfer. */
internal class InternalTestTokenStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "internal-test-token"))

    fun read(): String? {
        val input = try { file.openRead() } catch (_: FileNotFoundException) { return null }
        val bytes = input.use {
            val buffer = ByteArray(256)
            var size = 0
            while (size < buffer.size) {
                val count = it.read(buffer, size, buffer.size - size)
                if (count < 0) break
                size += count
            }
            require(it.read() == -1 && size >= 29)
            buffer.copyOf(size)
        }
        val ivSize = bytes[0].toInt()
        require(ivSize == 12 && bytes.size > 1 + ivSize)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 1 + ivSize)))
        val token = cipher.doFinal(bytes, 1 + ivSize, bytes.size - 1 - ivSize).toString(Charsets.UTF_8)
        require(TestConfigClient.TOKEN_PATTERN.matches(token))
        return token
    }

    fun write(token: String) {
        require(TestConfigClient.TOKEN_PATTERN.matches(token))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        require(iv.size == 12)
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val output = file.startWrite()
        try {
            output.write(iv.size)
            output.write(iv)
            output.write(encrypted)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    fun clear() {
        file.delete()
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keystore.deleteEntry(KEY_ALIAS)
    }

    private fun key(): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keystore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
        }.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "internal_test_token_v1"
    }
}
