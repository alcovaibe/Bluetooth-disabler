import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

val releaseSigningRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName.contains("Release", ignoreCase = true)
}

fun signingValue(propertyName: String, environmentName: String): String? =
    keystoreProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }
        ?: System.getenv(environmentName)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "RELEASE_STORE_FILE")
val releaseStorePassword = signingValue("storePassword", "RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "RELEASE_KEY_PASSWORD")

val hasReleaseSigningConfig = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

// Independent versioning/signing for internal; release/debug keep defaultConfig.
val internalVersionName = "1.2.6"
val internalVersionCode = System.getenv("INTERNAL_VERSION_CODE")?.let { value ->
    value.toIntOrNull()?.takeIf { it >= 28 }
        ?: throw GradleException("INTERNAL_VERSION_CODE must be an integer >= 28")
} ?: 28
val internalStoreFile = signingValue("internalStoreFile", "INTERNAL_STORE_FILE")
val internalStorePassword = signingValue("internalStorePassword", "INTERNAL_STORE_PASSWORD")
val internalKeyAlias = signingValue("internalKeyAlias", "INTERNAL_KEY_ALIAS")
val internalKeyPassword = signingValue("internalKeyPassword", "INTERNAL_KEY_PASSWORD")
val internalSigningValues = listOf(internalStoreFile, internalStorePassword, internalKeyAlias, internalKeyPassword)
val hasInternalSigningConfig = internalSigningValues.all { !it.isNullOrBlank() }
if ((!hasInternalSigningConfig && internalSigningValues.any { !it.isNullOrBlank() }) ||
    (System.getenv("INTERNAL_SIGNING_REQUIRED") == "true" && !hasInternalSigningConfig)
) {
    throw GradleException("Persistent internal signing is required but INTERNAL_* configuration is incomplete")
}

android {
    namespace = "com.pulse.bluetoothdisable"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.pulse.bluetoothdisable"
        minSdk = 26
        targetSdk = 37
        versionCode = 32
        versionName = "1.4.0"

        buildConfigField("String", "APP_VERSION", "\"$versionName\"")
        buildConfigField("boolean", "PUBLIC_UPDATE_CHECKS", "true")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasInternalSigningConfig) {
            create("internal") {
                storeFile = rootProject.file(internalStoreFile!!)
                storePassword = internalStorePassword
                keyAlias = internalKeyAlias
                keyPassword = internalKeyPassword
            }
        }
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        create("internal") {
            initWith(getByName("debug"))
            if (hasInternalSigningConfig) {
                signingConfig = signingConfigs.getByName("internal")
            }
            applicationIdSuffix = ".internal"
            versionNameSuffix = "-internal"
            matchingFallbacks += listOf("debug")
            buildConfigField("String", "APP_VERSION", "\"$internalVersionName-internal\"")
            buildConfigField("boolean", "PUBLIC_UPDATE_CHECKS", "false")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            } else if (releaseSigningRequested) {
                throw GradleException(
                    "Release signing is not configured. " +
                        "Use local keystore.properties or RELEASE_* environment variables.",
                )
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    sourceSets {
        // The ordinary variants compile only the inert test-access implementation.
        getByName("debug").kotlin.directories += "src/standard/java"
        getByName("release").kotlin.directories += "src/standard/java"
    }
}

@Suppress("UnstableApiUsage")
androidComponents {
    onVariants(selector().withBuildType("internal")) { variant ->
        variant.outputs.forEach { output ->
            output.versionName.set("$internalVersionName-internal")
            output.versionCode.set(internalVersionCode)
        }
    }

    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set(
                output.versionName.map { version ->
                    "BluetoothDisable-v$version.apk"
                },
            )
        }
    }
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    add("internalImplementation", libs.androidx.compose.ui.tooling)
}
