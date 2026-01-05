import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

// Load .env file
val envFile = rootProject.file("mindapps/.env")
val envProperties = Properties()
if (envFile.exists()) {
    envFile.inputStream().use { envProperties.load(it) }
}

android {
    namespace = "com.mindapps"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mindapps"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Build config fields from .env
        buildConfigField("String", "API_BASE_URL", "\"${envProperties.getProperty("API_BASE_URL", "https://thefeather.ink/apps/")}\"")
        buildConfigField("String", "APPS_JSON_ENDPOINT", "\"${envProperties.getProperty("APPS_JSON_ENDPOINT", "mind.json")}\"")
        buildConfigField("String", "DATA_ENDPOINT", "\"${envProperties.getProperty("DATA_ENDPOINT", "data.php")}\"")
        buildConfigField("String", "SECRET_KEY", "\"${envProperties.getProperty("SECRET_KEY", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation(libs.androidx.activity.compose)
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)

    // Icons
    implementation("androidx.compose.material:material-icons-extended:1.5.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.5")

    // Mudita MMD
    implementation(project(":mudita-mmd:mmd-core"))

    // JSON processing
    implementation("com.google.code.gson:gson:2.10.1")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coil for image loading (with SVG support)
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-svg:2.5.0")

    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // WorkManager for background tasks
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Launcher badge support
    implementation("me.leolin:ShortcutBadger:1.1.22@aar")

    // Splash Screen
    implementation("androidx.core:core-splashscreen:1.0.1")
}
