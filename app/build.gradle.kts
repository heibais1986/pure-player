plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
}

val keystorePath = providers.gradleProperty("RELEASE_STORE_FILE").orNull
val releaseKeystore = keystorePath?.let { rootProject.file(it) }
val debugKeystorePath = providers.gradleProperty("DEBUG_STORE_FILE").orNull
val debugKeystore = debugKeystorePath?.let { rootProject.file(it) }

android {
    namespace = "com.pureplayer.music"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pureplayer.music"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    if (releaseKeystore?.isFile == true) {
        signingConfigs {
            create("release") {
                storeFile = releaseKeystore
                storePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    if (debugKeystore?.isFile == true) {
        signingConfigs {
            getByName("debug") {
                storeFile = debugKeystore
                storePassword = providers.gradleProperty("DEBUG_STORE_PASSWORD").orNull ?: "android"
                keyAlias = providers.gradleProperty("DEBUG_KEY_ALIAS").orNull ?: "androiddebugkey"
                keyPassword = providers.gradleProperty("DEBUG_KEY_PASSWORD").orNull ?: "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseKeystore?.isFile == true) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.navigation:navigation-compose:2.7.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.media:media:1.7.0")

    implementation("io.coil-kt:coil-compose:2.6.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
