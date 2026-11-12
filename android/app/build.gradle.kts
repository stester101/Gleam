plugins {
    id("com.android.application")
}

android {
    namespace = "com.gleam.windowcleaning"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gleam.windowcleaning"
        minSdk = 26
        targetSdk = 36
        versionCode = 20006
        versionName = "2.0.0-alpha6"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            // Non-debuggable build signed with the same stable key as alpha builds
            // (~/.android/debug.keystore restored from the GLEAM_DEBUG_KEYSTORE_B64 secret),
            // so it installs over them. Swap for a dedicated release key before Play.
            signingConfig = signingConfigs.getByName("debug")
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.webkit:webkit:1.17.1")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
}
