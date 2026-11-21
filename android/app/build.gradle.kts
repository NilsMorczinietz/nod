plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Version from gradle.properties (bumped by scripts/version.mjs).
val nodVersionName = providers.gradleProperty("nod.versionName").get()
val nodVersionCode = providers.gradleProperty("nod.versionCode").get().toInt()

// Release signing from the environment (CI secrets or the local shell).
val releaseKeystore = providers.environmentVariable("NOD_KEYSTORE_FILE").orNull
val hasReleaseKey = releaseKeystore != null &&
    listOf("NOD_KEYSTORE_PASSWORD", "NOD_KEY_ALIAS", "NOD_KEY_PASSWORD").all { providers.environmentVariable(it).isPresent }

// `-Pnod.localTest=true`: a minified release build that can talk to a local HTTP relay
// and accepts the adb push injector – for smoke tests only, never for published APKs.
val localTest = providers.gradleProperty("nod.localTest").orNull == "true"

android {
    namespace = "io.github.nilsmorczinietz.nod"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "io.github.nilsmorczinietz.nod"
        minSdk = 26
        targetSdk = 37
        versionCode = nodVersionCode
        versionName = nodVersionName
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseKeystore!!)
                storePassword = providers.environmentVariable("NOD_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("NOD_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("NOD_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "LOCAL_TEST", "true")
            manifestPlaceholders["networkSecurityConfig"] = "@xml/network_security_config_local"
            manifestPlaceholders["debugPushEnabled"] = "true"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean", "LOCAL_TEST", localTest.toString())
            manifestPlaceholders["networkSecurityConfig"] =
                if (localTest) "@xml/network_security_config_local" else "@xml/network_security_config"
            manifestPlaceholders["debugPushEnabled"] = localTest.toString()
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                val buildsRelease = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
                if (buildsRelease) logger.warn(
                    "w: Nod: NOD_KEYSTORE_FILE/NOD_KEYSTORE_PASSWORD/NOD_KEY_ALIAS/NOD_KEY_PASSWORD not set – " +
                        "the release APK is signed with the DEBUG key (local testing only).",
                )
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.play.services.code.scanner)
}
