package tw.luma.camera.update

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI

object UpdateHttp {
    fun open(url: String, checkActive: () -> Unit = {}): HttpURLConnection {
        var next = UpdateInfo.httpsUrl(url)
        repeat(6) {
            checkActive()
            val connection = next.toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("User-Agent", "Grain-Android-Updater")
            try {
                val status = connection.responseCode
                checkActive()
                when (status) {
                    200 -> return connection
                    301, 302, 303, 307, 308 -> {
                        val location = connection.getHeaderField("Location") ?: throw IllegalArgumentException("下載網址沒有提供重新導向位置")
                        next = UpdateInfo.httpsUrl(next.resolve(URI(location)).toString())
                    }
                    401, 403 -> throw IllegalArgumentException("更新網站需要登入或下載授權")
                    404 -> throw IllegalArgumentException("更新網站找不到版本資訊或 APK")
                    else -> throw IllegalArgumentException("更新網站暫時無法使用")
                }
            } catch (error: Exception) { connection.disconnect(); throw error }
            connection.disconnect()
        }
        throw IllegalArgumentException("更新網址重新導向次數過多")
    }

    fun readInfo(url: String, checkActive: () -> Unit): UpdateInfo {
        val connection = open(url, checkActive)
        try {
            require(connection.contentLengthLong <= 256 * 1024) { "更新資訊檔案過大" }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    checkActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 256 * 1024) { "更新資訊檔案過大" }
                    output.write(buffer, 0, count)
                }
            }
            checkActive()
            return UpdateInfo.parse(output.toString(Charsets.UTF_8.name()))
        } finally { connection.disconnect() }
    }
}
