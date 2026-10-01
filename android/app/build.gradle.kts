plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gleam.windowcleaning"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gleam.windowcleaning"
        minSdk = 26
        targetSdk = 36
        versionCode = 20000
        versionName = "2.0.0-alpha1"
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
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.webkit:webkit:1.17.1")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
}
