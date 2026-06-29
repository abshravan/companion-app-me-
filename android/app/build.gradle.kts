// Android application: the only module with framework entry points (service + UI).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.relayme.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.relayme.app"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "0.2.0" // Milestone 2
    }

    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation(project(":core"))
    implementation(project(":protocol"))
    implementation(project(":bluetooth"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.service)
}
