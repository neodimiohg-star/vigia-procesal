import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// En GitHub Actions cada compilación tiene un número creciente; así cada APK
// nuevo puede instalarse encima del anterior sin perder los procesos guardados.
val numeroCompilacion = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "co.vigiaprocesal.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "co.vigiaprocesal.app"
        minSdk = 26
        targetSdk = 35
        versionCode = numeroCompilacion
        versionName = "1.0.$numeroCompilacion"
    }

    // La llave de firma NO está en el repositorio: GitHub Actions la toma de los
    // secrets del repositorio y la pasa por variables de entorno. Sin ellas (por
    // ejemplo, compilando en un computador) se usa la firma de depuración.
    val rutaLlave = System.getenv("VIGIA_KEYSTORE_PATH")
    val claveLlave = System.getenv("VIGIA_KEYSTORE_PASSWORD")
    val hayLlave = !rutaLlave.isNullOrBlank() && !claveLlave.isNullOrBlank() && file(rutaLlave).exists()

    signingConfigs {
        if (hayLlave) {
            create("vigia") {
                storeFile = file(rutaLlave!!)
                storeType = "pkcs12"
                storePassword = claveLlave
                keyAlias = "vigia"
                keyPassword = claveLlave
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hayLlave) signingConfigs.getByName("vigia") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
