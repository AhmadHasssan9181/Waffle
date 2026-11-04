import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20"
    id("com.google.gms.google-services")
    id("com.google.devtools.ksp") version "2.1.20-1.0.32"
}

val localProperties =
        Properties().apply {
            val file = rootProject.file("local.properties")
            if (file.exists()) file.inputStream().use { load(it) }
        }

fun secret(name: String): String =
        (localProperties.getProperty(name) ?: System.getenv(name) ?: "")
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")

android {
    testOptions { unitTests.isReturnDefaultValues = true }
    namespace = "com.noobdev.waffle"
    val buildingBundle =
            gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
    compileSdk = 36

    defaultConfig {
        applicationId = "com.noobdev.waffle"
        minSdk = 24
        targetSdk = 36
        versionCode = 9
        versionName = "1.0.0-preview.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Keys live in local.properties (gitignored) or the environment, never in source.
        buildConfigField("String", "MAPTILER_API_KEY", "\"${secret("MAPTILER_API_KEY")}\"")
        buildConfigField("String", "ORS_API_KEY", "\"${secret("ORS_API_KEY")}\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"${secret("GEMINI_API_KEY")}\"")
        buildConfigField("String", "OPENCAGE_API_KEY", "\"${secret("OPENCAGE_API_KEY")}\"")
        buildConfigField("String", "TOMTOM_API_KEY", "\"${secret("TOMTOM_API_KEY")}\"")

        if (buildingBundle) {
            ndk {
                // Phone ABIs only; Play still splits per device.
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }
    }

    splits {
        abi {
            // Bundles must not contain multiple APK splits; Play handles ABI splitting.
            isEnable = !buildingBundle
            reset()
            // Keep only phone ABIs. x86/x86_64 are emulator-only and were adding
            // ~22 MB of MapLibre native libraries to every APK.
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Local release builds are debug-signed so they can be installed for size and
            // runtime testing. Replace with a real upload key before publishing.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Compose additions
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.36.0")
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Location & maps
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.maps.android:android-maps-utils:3.8.2")
    implementation("org.maplibre.gl:android-sdk:11.8.0")

    // Lifecycle, navigation, storage, images
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Background alert sync
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:34.11.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")

    // Offline routing. 1.0 is the last GraphHopper release with offline support; the graph in
    // assets must be generated with this exact version.
    implementation("com.graphhopper:graphhopper-core:1.0")
    implementation("org.locationtech.jts:jts-core:1.19.0")
    implementation("org.slf4j:slf4j-android:1.7.36")

    // Room Database
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // java.nio / java.time backports for API 24-25 (GraphHopper uses newer JDK APIs)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
