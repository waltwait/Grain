import java.util.Properties
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signing secrets stay local. Without this file, release builds remain unsigned.
val releaseSigningFile = rootProject.file(".signing/release.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use(::load)
}
fun releaseSigningValue(name: String): String = releaseSigning.getProperty(name)?.takeIf { it.isNotBlank() }
    ?: throw GradleException("Missing $name in .signing/release.properties")

val configuredUpdateUrl = providers.gradleProperty("grainUpdateUrl").orNull
fun updateFeedField(): String {
    // Private releases open in the user's browser; an optional hosted feed enables native updates.
    val url = configuredUpdateUrl.orEmpty()
    if (url.isNotBlank()) {
        val uri = URI(url)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null) {
            "grainUpdateUrl must be an HTTPS metadata URL without embedded credentials."
        }
    }
    return "\"" + url.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

android {
    namespace = "tw.luma.camera"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "tw.luma.camera"
        minSdk = 29
        targetSdk = 37
        versionCode = 28
        versionName = "0.6.5"
        buildConfigField("String", "UPDATE_FEED_URL", updateFeedField())
        buildConfigField("String", "UPDATE_CHANNEL", "\"release\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    if (releaseSigningFile.isFile) {
        signingConfigs {
            create("grainRelease") {
                storeFile = rootProject.file(releaseSigningValue("storeFile"))
                storeType = releaseSigning.getProperty("storeType", "PKCS12")
                storePassword = releaseSigningValue("storePassword")
                keyAlias = releaseSigningValue("keyAlias")
                keyPassword = releaseSigningValue("keyPassword")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            buildConfigField("String", "UPDATE_CHANNEL", "\"personal-fuji\"")
        }
        release {
            isMinifyEnabled = true
            if (releaseSigningFile.isFile) signingConfig = signingConfigs.getByName("grainRelease")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("personal") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            buildConfigField("String", "UPDATE_CHANNEL", "\"personal-fuji\"")
        }
    }
    // Personal updates retain the official local pack and the installed release signature.
    sourceSets.getByName("personal").assets.directories.add("src/debug/assets")
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

val verifyPersonalFujiAssets = tasks.register("verifyPersonalFujiAssets") {
    val directory = file("src/debug/assets/luts/fujifilm")
    inputs.files(fileTree(directory) { include("*.cube", "sources.json") })
    doLast {
        check(releaseSigningFile.isFile) { "Personal updates require the existing release signing configuration." }
        val styles = listOf("CLASSIC-CHROME", "CLASSIC-Neg.", "REALA-ACE", "PROVIA", "Velvia",
            "ASTIA", "PRO-Neg.Std", "ETERNA", "ETERNA-BB", "ACROS")
        val missing = styles.map { "FLog2_to_${it}_33grid_V.1.00.cube" }
            .filterNot { directory.resolve(it).isFile }
        check(missing.isEmpty() && directory.resolve("sources.json").isFile) {
            "Personal Fuji pack is incomplete. Prepare all ten official LUTs before building a personal update."
        }
    }
}
tasks.matching { it.name == "prePersonalBuild" }.configureEach { dependsOn(verifyPersonalFujiAssets) }

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.camera:camera-core:1.6.2")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("androidx.camera:camera-video:1.6.2")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui-compose:1.11.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:rules:1.7.0")
}
