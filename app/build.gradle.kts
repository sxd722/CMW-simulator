plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Read Gemini API key from local.properties
val geminiApiKey: String = run {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.readLines()
            .find { it.startsWith("GEMINI_API_KEY=") }
            ?.substringAfter("GEMINI_API_KEY=")
            ?.trim()
            ?: ""
    } else ""
}

android {
    namespace = "com.example.cmw_simulator"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.cmw_simulator"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Inject API key into BuildConfig
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "META-INF/versions/9/previous-compilation-data.bin"
            excludes += "**/*.kotlin_builtins"
            excludes += "**/*.kotlin_metadata"
        }
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)

    // Activity & Lifecycle
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Remote Compose (Player)
    implementation(libs.remote.player.core)
    implementation(libs.remote.player.view)
    implementation(libs.remote.player.compose)

    // Remote Compose (Creation)
    implementation(libs.remote.creation.core)
    implementation(libs.remote.core)

    // Google AI SDK (Gemini)
    implementation(libs.generative.ai)

    // OkHttp (for GLM API calls)
    implementation(libs.okhttp)

    // A2UI Compose renderer
    implementation(project(":a2ui-compose"))



    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
