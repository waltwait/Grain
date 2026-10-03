package tw.luma.camera.update

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UpdateFileProviderDeviceTest {
    @Test fun installerCanReadOnlyTheDedicatedUpdateCache() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "grain-updates").apply { mkdirs() }
        val update = File(directory, "provider-test-${System.nanoTime()}.apk")
        val other = File(context.cacheDir, "private-test-${System.nanoTime()}.txt")
        try {
            val bytes = byteArrayOf(1, 2, 3, 4)
            update.writeBytes(bytes)
            other.writeText("private")
            val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", update)
            assertEquals("content", uri.scheme)
            assertArrayEquals(bytes, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
            assertThrows(IllegalArgumentException::class.java) {
                FileProvider.getUriForFile(context, context.packageName + ".updates", other)
            }
            @Suppress("DEPRECATION")
            val provider = context.packageManager.getProviderInfo(ComponentName(context, UpdateFileProvider::class.java), 0)
            assertFalse(provider.exported)
            assertTrue(provider.grantUriPermissions)
            @Suppress("DEPRECATION")
            val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
            assertTrue("android.permission.REQUEST_INSTALL_PACKAGES" in permissions)
            assertFalse(UpdateApk.signers(UpdateApk.installed(context)).isEmpty())
        } finally { update.delete(); other.delete() }
    }
}
