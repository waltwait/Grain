package tw.luma.camera.update

import org.junit.Assert.*
import org.junit.Test

class UpdateInfoTest {
    private val hash = "a".repeat(64)
    private val json = """{"schemaVersion":1,"packageName":"tw.luma.camera","versionName":"0.6.3","versionCode":26,"minSdk":29,"channel":"personal-fuji","bundledFujiCount":10,"apkUrl":"https://updates.example.test/Grain.apk","apkSize":1024,"apkSha256":"$hash","signingCertificateSha256":"$hash","notes":"修復與更新"}"""
    private fun info() = UpdateInfo.parse(json)
    private fun rejected(body: String) { assertThrows(IllegalArgumentException::class.java) { UpdateInfo.parse(body) } }

    @Test fun parsesPersonalFeedAndComparesVersionCode() {
        val next = info()
        assertEquals("0.6.3", next.versionName)
        assertTrue(next.isNewerThan(25))
        assertFalse(next.isNewerThan(26))
        assertFalse(next.isNewerThan(27))
        next.requireCompatible("tw.luma.camera", "personal-fuji", 36)
    }
    @Test fun refusesAnUpdateThatChangesThePersonalChannel() {
        assertThrows(IllegalArgumentException::class.java) { info().requireCompatible("tw.luma.camera", "release", 36) }
    }
    @Test fun refusesOtherPackages() { rejected(json.replace("tw.luma.camera", "other.app")) }
    @Test fun requiresAllTenFujiLooksForThePersonalFeed() { rejected(json.replace("\"bundledFujiCount\":10", "\"bundledFujiCount\":0")) }
    @Test fun refusesFractionalAndQuotedVersionNumbers() {
        rejected(json.replace("\"versionCode\":26", "\"versionCode\":26.5"))
        rejected(json.replace("\"versionCode\":26", "\"versionCode\":\"26\""))
    }
    @Test fun requiresHttpsAndNoEmbeddedCredentials() {
        rejected(json.replace("https://updates", "http://updates"))
        rejected(json.replace("https://updates", "https://owner:password@updates"))
        rejected(json.replace("Grain.apk", "Grain.apk#fragment"))
    }
    @Test fun rejectsUnknownSchemaAndOversizedDownloads() {
        rejected(json.replace("\"schemaVersion\":1", "\"schemaVersion\":2"))
        rejected(json.replace("\"apkSize\":1024", "\"apkSize\":1000000000"))
    }
    @Test fun rejectsMalformedHashes() { rejected(json.replace(hash, "not-a-hash")) }
    @Test fun rejectsStructuredReleaseNotesAndNonTextVersionNames() {
        rejected(json.replace("\"notes\":\"修復與更新\"", "\"notes\":{\"message\":\"wrong type\"}"))
        rejected(json.replace("\"versionName\":\"0.6.3\"", "\"versionName\":26"))
        assertEquals("", UpdateInfo.parse(json.replace("\"notes\":\"修復與更新\"", "\"notes\":null")).notes)
    }
    @Test fun checksTheAndroidVersion() {
        assertThrows(IllegalArgumentException::class.java) { info().requireCompatible("tw.luma.camera", "personal-fuji", 28) }
    }
    @Test fun downloadedPackageMustMatchTheManifestAndInstalledSigner() {
        val expected = info()
        val candidate = DownloadedPackage("tw.luma.camera", 26, "0.6.3", 29, false, setOf(hash), true)
        UpdatePolicy.requireInstallable(expected, candidate, 25, setOf(hash))
        listOf(candidate.copy(packageName = "other.app"), candidate.copy(versionCode = 25),
            candidate.copy(versionName = "wrong"), candidate.copy(debuggable = true),
            candidate.copy(signers = setOf("b".repeat(64))), candidate.copy(fujiComplete = false)).forEach { wrong ->
            assertThrows(IllegalArgumentException::class.java) { UpdatePolicy.requireInstallable(expected, wrong, 25, setOf(hash)) }
        }
        assertThrows(IllegalArgumentException::class.java) { UpdatePolicy.requireInstallable(expected, candidate, 26, setOf(hash)) }
    }
    @Test fun redirectsStillRequireAnHttpsTarget() {
        assertEquals("https://cdn.example.test/app.apk", UpdateInfo.httpsUrl("https://cdn.example.test/app.apk").toString())
        assertThrows(IllegalArgumentException::class.java) { UpdateInfo.httpsUrl("file:///tmp/app.apk") }
    }
}
