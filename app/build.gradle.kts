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
        // 29: permite guardar descargas con MediaStore sin pedir permisos de almacenamiento
        minSdk = 29
        targetSdk = 34
        versionCode = 14
        versionName = "2.0.0-colores"
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
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Miniaturas
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Red (la usa el Downloader de NewPipeExtractor)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Extracción de YouTube (sin API key, sin cuenta de Google).
    // v0.26.3+ incluye el workaround de SABR; si cambias la versión, actualiza EXTRACTOR_VERSION en StreamRepository.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")

    // Almacén cifrado de la sesión (cookies)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Reproductor
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1") // directos / manifiestos HLS
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1") // manifiestos DASH
    implementation("androidx.media3:media3-session:1.4.1") // MediaSessionService + notificación
    implementation("androidx.media3:media3-ui:1.4.1")
}
