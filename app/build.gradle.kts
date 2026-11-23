plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.aurelis.symphony"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.aurelis.symphony"
        minSdk = 28
        targetSdk = 35
        versionCode = 25
        versionName = "1.2.16"
        ndk { abiFilters += "arm64-v8a" }
    }
    // A single private keystore must be reused for EVERY future update.
    // GitHub Actions supplies these via repository secrets, never tracked files.
    val storePath = providers.environmentVariable("AURELIS_SIGNING_STORE_FILE").orNull
    val storePass = providers.environmentVariable("AURELIS_SIGNING_STORE_PASSWORD").orNull
    val keyAlias = providers.environmentVariable("AURELIS_SIGNING_KEY_ALIAS").orNull
    val keyPass = providers.environmentVariable("AURELIS_SIGNING_KEY_PASSWORD").orNull
    val stableKeyPresent = listOf(storePath, storePass, keyAlias, keyPass).all { !it.isNullOrBlank() }
    signingConfigs {
        if (stableKeyPresent) create("aurelisStable") {
            storeFile = file(storePath!!)
            storePassword = storePass
            this.keyAlias = keyAlias
            keyPassword = keyPass
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (stableKeyPresent) signingConfig = signingConfigs.getByName("aurelisStable")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE.md", "META-INF/LICENSE-notice.md", "META-INF/NOTICE.md") }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("androidx.media3:media3-common:1.11.1")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.27.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
}
