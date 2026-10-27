import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "net.jkraemer.photos"
    compileSdk = 36
    defaultConfig {
        applicationId = "net.jkraemer.photos"
        minSdk = 35
        targetSdk = 36
        // CI passes these from the release tag (-PversionName) and the commit
        // count (-PversionCode); local builds fall back to dev values.
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "dev"
        // Tesseract's native libraries are large; the app runs on 64-bit ARM phones only.
        ndk { abiFilters += "arm64-v8a" }
    }
    // Stored uncompressed so the app can read a model's length without inflating it.
    androidResources { noCompress += "traineddata" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { unitTests { isIncludeAndroidResources = true } }
    // Release signing comes from the environment (CI secrets); without it,
    // assembleRelease produces an unsigned APK and local builds are unaffected.
    signingConfigs {
        create("release") {
            System.getenv("RELEASE_KEYSTORE")?.let { path ->
                storeFile = file(path)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }
}
// Compose UI tests need ui-test-manifest's activity, which only the debug
// variant merges; the release variant runs the same tests, so skip it.
androidComponents {
    beforeVariants(selector().withBuildType("release")) {
        (it as com.android.build.api.variant.HasHostTestsBuilder)
            .hostTests.getValue(com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE).enable = false
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

/**
 * Tesseract's German and English models, downloaded from tessdata_best at a
 * pinned commit and checked against their SHA-256 sums. Too large to commit;
 * the app ships them as assets and downloads nothing at run time.
 */
abstract class DownloadTessdata : DefaultTask() {
    @get:Input abstract val commit: Property<String>
    @get:Input abstract val sha256: MapProperty<String, String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction fun download() {
        val dir = outputDir.get().dir("tessdata").asFile.apply { mkdirs() }
        for ((name, expected) in sha256.get()) {
            val file = File(dir, name)
            val url = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_best/${commit.get()}/$name"
            URI(url).toURL().openStream().use { input -> file.outputStream().use { input.copyTo(it) } }
            val actual = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }
            if (actual != expected) {
                file.delete()
                throw GradleException("$name from $url has SHA-256 $actual, expected $expected")
            }
        }
    }
}
val downloadTessdata = tasks.register<DownloadTessdata>("downloadTessdata") {
    commit = "e12c65a915945e4c28e237a9b52bc4a8f39a0cec"
    sha256 = mapOf(
        "deu.traineddata" to "8407331d6aa0229dc927685c01a7938fc5a641d1a9524f74838cdac599f0d06e",
        "eng.traineddata" to "8280aed0782fe27257a68ea10fe7ef324ca0f8d85bd2fd145d1c2b560bcb66ba",
    )
}
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(downloadTessdata, DownloadTessdata::outputDir)
    }
}
dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.telephoto.zoomable.image.coil3)
    implementation(libs.compose.material.icons)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.exifinterface)
    implementation(libs.tesseract4android)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.coil.test)
    testImplementation(libs.lifecycle.runtime.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
