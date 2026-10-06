plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.shikongxiangbian.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shikongxiangbian.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 16
        versionName = "0.13.2"
        manifestPlaceholders["appLabel"] = "时空象变"
    }

    // Use the exact path cached by Actions; AGP's default location may differ.
    signingConfigs.getByName("debug").storeFile = file(
        System.getenv("SHIKONG_DEBUG_KEYSTORE")
            ?: "${System.getProperty("user.home")}/.android/debug.keystore"
    )

    buildTypes {
        getByName("debug") {
            // Older distributed 0.6 APKs have a different debug certificate.
            // A separate package lets users try the new build without deleting old records.
            applicationIdSuffix = ".preview"
            manifestPlaceholders["appLabel"] = "时空象变·新版"
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("cn.6tail:lunar:1.7.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
