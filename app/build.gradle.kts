plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.alotube.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alotube.app"
        minSdk = 26 // con 26+ NewPipeExtractor no necesita desugaring
        targetSdk = 34
        versionCode = 1
        versionName = "2.0.0-fase1"
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

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }
}

dependencies {
    // Compose + Material 3
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Miniaturas
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Red (la usa el Downloader de NewPipeExtractor)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Extracción de YouTube (sin API key, sin cuenta de Google)
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.24.8")

    // Reproductor (se usará en la Fase 2)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
}
