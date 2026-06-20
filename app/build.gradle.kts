plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.playlists.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.playlists.app"
        // Android 4.3 (Jelly Bean MR2). View-based UI + Pdfium for PDF rendering
        // because android.graphics.pdf.PdfRenderer requires API 21+.
        minSdk = 18
        targetSdk = 34
        multiDexEnabled = true
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = (System.getenv("GITHUB_RUN_NUMBER")?.let { "1.0.$it" } ?: "1.0")

        ndk {
            val allowedAbis = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            val singleAbi = project.findProperty("abi")?.toString()
            if (singleAbi != null) {
                require(singleAbi in allowedAbis) {
                    "Unknown abi '$singleAbi'. Use one of: ${allowedAbis.joinToString()}"
                }
            }
            abiFilters.clear()
            abiFilters.addAll(if (singleAbi != null) listOf(singleAbi) else allowedAbis)
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("playlists") {
            storeFile = file("keystore/playlists.keystore")
            storePassword = "playlistsapp"
            keyAlias = "playlists"
            keyPassword = "playlistsapp"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("playlists")
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("playlists")
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
        viewBinding = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // Pinned to versions that still declare minSdk 14–16 so the app runs on Android 4.3 (API 18).
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.4.2")
    implementation("com.google.android.material:material:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("androidx.fragment:fragment-ktx:1.5.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    implementation("androidx.activity:activity-ktx:1.7.2")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("androidx.multidex:multidex:2.0.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Pdfium for API 18; platform PdfRenderer used on API 21+ inside PdfHelper.
    implementation("com.github.barteksc:pdfium-android:1.9.0")

    // Pinch-zoom for images
    implementation("com.github.chrisbanes:PhotoView:2.3.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
