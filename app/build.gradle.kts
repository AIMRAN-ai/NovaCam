plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.github.ben-manes.versions") version "0.51.0" apply false
}

android {
    namespace = "com.pixelforge.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pixelforge.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-phase1"
        buildConfigField("boolean", "ENABLE_FFMPEG_KIT", "false")
    }

    flavorDimensions += "decoder"
    productFlavors {
        create("mediaCodec") {
            dimension = "decoder"
            buildConfigField("boolean", "ENABLE_FFMPEG_KIT", "false")
        }
        create("ffmpeg") {
            dimension = "decoder"
            buildConfigField("boolean", "ENABLE_FFMPEG_KIT", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // DataStore for settings
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Image loading with Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:glide-compiler:4.16.0")

    // Serialization for settings/metadata
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")

    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Audio time-stretch library (Sonic) - requires JitPack repository
    // Uncomment when JitPack is available in build environment:
    // implementation("com.github.besherh:sonic-android:1.0.0")

    val ffmpegKitEnabled = project.hasProperty("enableFfmpegKit")
    if (ffmpegKitEnabled) {
        implementation("com.arthenica:ffmpeg-kit-full-gpl:6.0")
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test.ext:junit:1.1.5")
    
    // Benchmark
    androidTestImplementation("androidx.benchmark:benchmark-junit4:1.2.4")
    androidTestImplementation("androidx.benchmark:benchmark-macro-junit4:1.2.4")
}
