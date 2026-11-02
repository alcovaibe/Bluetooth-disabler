package com.pulse.bluetoothdisable.updates

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class UpdateInstallerInstrumentedTest {
    @Test fun installerUsesContentUriWithTemporaryReadAccessAndNarrowPath() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File.createTempFile("fixture-", ".apk", directory).apply { writeBytes(byteArrayOf(1, 2, 3)) }
        try {
            val intent = UpdateInstaller.installIntent(context, file)
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("application/vnd.android.package-archive", intent.type)
            assertEquals("content", intent.data!!.scheme)
            assertEquals(context.packageName + ".updates", intent.data!!.authority)
            assertNotEquals(0, intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
            assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            context.contentResolver.openInputStream(intent.data!!)!!.use { assertArrayEquals(byteArrayOf(1, 2, 3), it.readBytes()) }
            val provider = context.packageManager.resolveContentProvider(context.packageName + ".updates", 0)!!
            assertFalse(provider.exported)
            assertThrows(IllegalArgumentException::class.java) {
                FileProvider.getUriForFile(context, context.packageName + ".updates", File(context.cacheDir, "private.txt"))
            }
            assertEquals("package:${context.packageName}", UpdateInstaller.permissionIntent(context).data.toString())
        } finally { file.delete() }
    }
}
