import java.util.Properties

/**
 * Release signing: same shared keystore as MyLRC Auto / MySing / LRC 编辑器,
 * via the gitignored `keystore.properties` at the project root
 * (storeFile / storePassword / keyAlias / keyPassword), or the
 * BL_KEYSTORE_* environment variables. Without either, release
 * builds stay unsigned.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(propertyKey: String, environmentKey: String): String? =
    keystoreProperties.getProperty(propertyKey)?.takeIf { it.isNotBlank() }
        ?: System.getenv(environmentKey)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "BL_KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "BL_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "BL_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "BL_KEY_PASSWORD")
val canSignRelease = listOf(
    releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword,
).all { it != null } && file(releaseStoreFile!!).exists()

plugins {
    // AGP 9 has built-in Kotlin support; no separate kotlin-android plugin.
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.dannycom888.lrcnotepad"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dannycom888.lrcnotepad"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    if (canSignRelease) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            if (canSignRelease) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    testImplementation(libs.junit)
}
