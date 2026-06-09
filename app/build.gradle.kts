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

    // AppFunctions (expose local capabilities to AI models)
    implementation(libs.appfunctions.core)
    implementation(libs.appfunctions.service)
    // ksp(libs.appfunctions.compiler) // KSP not available for Kotlin 2.3.21; manual registration

    // Remote Compose (Player only — no creation/server-side deps)
    implementation(libs.remote.player.core)
    implementation(libs.remote.player.view)
    implementation(libs.remote.player.compose)

    // Google AI SDK (Gemini)
    implementation(libs.generative.ai)

    // OkHttp (for GLM API calls)
    implementation(libs.okhttp)

    // Material Icons Extended
    implementation(libs.compose.material.icons)

    // A2UI Compose renderer
    implementation(project(":a2ui-compose"))

    // Graphics Shapes (RoundedPolygon, Morph for non-square widgets)
    implementation("androidx.graphics:graphics-shapes:1.1.0")



    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
