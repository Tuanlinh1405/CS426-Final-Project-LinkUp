import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.linkup.data"
    compileSdk {
        version = release(36) { minorApiLevel = 1 }
    }
    defaultConfig {
        minSdk = 24
        // -P still wins in CI. A developer can keep the physical-device URL in ignored local.properties.
        val localApiUrl = rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { stream ->
            Properties().apply { load(stream) }.getProperty("linkup.apiBaseUrl")
        }
        val apiUrl = providers.gradleProperty("linkup.apiBaseUrl").orNull ?: localApiUrl ?: "http://10.0.2.2:8080/"
        require(apiUrl.endsWith("/") && apiUrl.matches(Regex("https?://[^\\\"\\s]+/"))) { "linkup.apiBaseUrl must be an HTTP(S) URL ending in /" }
        buildConfigField("String", "API_BASE_URL", "\"$apiUrl\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlin.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation(libs.junit)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
