plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "sk.perun.weather"
    compileSdk = 35

    defaultConfig {
        applicationId = "sk.perun.weather"
        minSdk = 35
        targetSdk = 35
        versionCode = ((System.currentTimeMillis() / 1000 - 1767225600) / 60).toInt()
        versionName = "1.1.1"
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    testImplementation("junit:junit:4.13.2")
}
