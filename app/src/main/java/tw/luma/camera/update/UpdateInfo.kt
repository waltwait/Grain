package tw.luma.camera.update

import org.json.JSONObject
import java.net.URI

data class UpdateInfo(
    val packageName: String, val versionName: String, val versionCode: Long, val minSdk: Int,
    val channel: String, val bundledFujiCount: Int, val apkUrl: String, val apkSize: Long,
    val apkSha256: String, val certificateSha256: String, val notes: String,
) {
    fun isNewerThan(installedCode: Long) = versionCode > installedCode
    fun requireCompatible(installedPackage: String, installedChannel: String, sdk: Int) {
        require(packageName == installedPackage) { "更新套件與目前 App 不符" }
        require(channel == installedChannel) { "更新版本的濾鏡組合與目前 App 不符" }
        require(minSdk <= sdk) { "新版需要較新的 Android" }
    }
    companion object {
        const val MAX_APK_BYTES = 100L * 1024 * 1024
        private val hash = Regex("[0-9a-fA-F]{64}")
        fun httpsUrl(value: String): URI {
            val uri = runCatching { URI(value) }.getOrElse { throw IllegalArgumentException("更新網址格式不正確") }
            require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null) { "更新網址必須使用 HTTPS" }
            return uri
        }
        fun parse(text: String): UpdateInfo = try {
            val json = JSONObject(text)
            fun number(name: String): Long {
                val value = json.get(name)
                require(value is Int || value is Long) { "更新版本資訊格式不正確" }
                return (value as Number).toLong()
            }
            fun string(name: String): String {
                val value = json.get(name)
                require(value is String) { "更新版本資訊格式不正確" }
                return value
            }
            val notes = json.opt("notes").let { value ->
                if (value == null || value === JSONObject.NULL) ""
                else { require(value is String) { "更新說明格式不正確" }; value }
            }
            require(number("schemaVersion") == 1L) { "更新資訊版本尚未支援" }
            val info = UpdateInfo(string("packageName"), string("versionName"), number("versionCode"),
                number("minSdk").also { require(it in 1..999) }.toInt(), string("channel"),
                number("bundledFujiCount").also { require(it in 0..10) }.toInt(), string("apkUrl"),
                number("apkSize"), string("apkSha256").lowercase(),
                string("signingCertificateSha256").lowercase(), notes)
            require(info.packageName == "tw.luma.camera") { "更新套件與 Grain 不符" }
            require(info.versionName.isNotBlank() && info.versionName.length <= 64 && info.versionCode in 1..Int.MAX_VALUE.toLong()) { "更新版本資訊不正確" }
            require(info.channel in setOf("personal-fuji", "release")) { "更新通道尚未支援" }
            require(info.channel != "personal-fuji" || info.bundledFujiCount == 10) { "更新包缺少富士濾鏡" }
            require(info.apkSize in 1..MAX_APK_BYTES && hash.matches(info.apkSha256) && hash.matches(info.certificateSha256)) { "更新檔案資訊不正確" }
            require(info.notes.length <= 4000) { "更新說明過長" }
            httpsUrl(info.apkUrl)
            info
        } catch (error: IllegalArgumentException) { throw error }
          catch (_: Exception) { throw IllegalArgumentException("無法讀取更新版本資訊") }
    }
}

data class DownloadedPackage(val packageName: String, val versionCode: Long, val versionName: String,
    val minSdk: Int, val debuggable: Boolean, val signers: Set<String>, val fujiComplete: Boolean)

object UpdatePolicy {
    fun requireInstallable(info: UpdateInfo, apk: DownloadedPackage, installedCode: Long, installedSigners: Set<String>) {
        require(info.isNewerThan(installedCode) && apk.versionCode == info.versionCode && apk.versionName == info.versionName) { "更新 APK 版本不符" }
        require(apk.packageName == info.packageName && apk.minSdk == info.minSdk && !apk.debuggable) { "更新 APK 與目前 App 不相容" }
        require(installedSigners.isNotEmpty() && apk.signers == installedSigners && info.certificateSha256 in apk.signers) { "更新 APK 的簽章不同，無法直接更新" }
        require(info.channel != "personal-fuji" || apk.fujiComplete) { "更新 APK 缺少富士濾鏡" }
    }
}
