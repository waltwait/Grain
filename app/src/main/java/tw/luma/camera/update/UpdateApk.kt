package tw.luma.camera.update

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

object UpdateApk {
    val fujiFiles = listOf("CLASSIC-CHROME", "CLASSIC-Neg.", "REALA-ACE", "PROVIA", "Velvia",
        "ASTIA", "PRO-Neg.Std", "ETERNA", "ETERNA-BB", "ACROS")
        .map { "assets/luts/fujifilm/FLog2_to_" + it + "_33grid_V.1.00.cube" }

    fun installed(context: Context): PackageInfo {
        val manager = context.packageManager
        return if (Build.VERSION.SDK_INT >= 33) manager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        else { @Suppress("DEPRECATION") manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES) }
    }
    fun signers(info: PackageInfo): Set<String> = info.signingInfo?.apkContentsSigners.orEmpty().map {
        MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
    }.toSet()

    fun verify(context: Context, file: File, info: UpdateInfo, checkActive: () -> Unit = {}) {
        require(file.isFile && file.length() == info.apkSize) { "更新檔案不存在或不完整" }
        val sink = object : java.io.OutputStream() {
            override fun write(value: Int) = Unit
            override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
        }
        file.inputStream().use { UpdateDownloadIo.copy(it, sink, info.apkSize, info.apkSha256, checkActive) }
        val manager = context.packageManager
        val parsed = if (Build.VERSION.SDK_INT >= 33) manager.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        else { @Suppress("DEPRECATION") manager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES) }
        requireNotNull(parsed) { "下載檔案不是可安裝的 APK" }
        val app = requireNotNull(parsed.applicationInfo) { "更新 APK 資訊不完整" }
        val fuji = if (info.channel == "personal-fuji") ZipFile(file).use { zip -> fujiFiles.all { name -> zip.getEntry(name)?.size?.let { it > 0 } == true } } else false
        checkActive()
        val current = installed(context)
        info.requireCompatible(context.packageName, tw.luma.camera.BuildConfig.UPDATE_CHANNEL, Build.VERSION.SDK_INT)
        UpdatePolicy.requireInstallable(info, DownloadedPackage(parsed.packageName, parsed.longVersionCode, parsed.versionName.orEmpty(),
            app.minSdkVersion, app.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0, signers(parsed), fuji), current.longVersionCode, signers(current))
    }
}
