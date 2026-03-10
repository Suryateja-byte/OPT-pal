import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    alias(libs.plugins.firebase.crashlytics)
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
}

android {
    namespace = "com.sidekick.opt_pal"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sidekick.opt_pal"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Read Gemini API key from local.properties
        val localProperties = File(rootProject.projectDir, "local.properties")
        if (localProperties.exists()) {
            val properties = Properties()
            properties.load(localProperties.inputStream())
            buildConfigField("String", "GEMINI_API_KEY", "\"${properties.getProperty("GEMINI_API_KEY", "")}\"")
        } else {
            buildConfigField("String", "GEMINI_API_KEY", "\"\"")
        }
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation(platform("com.google.firebase:firebase-bom:33.1.1"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck-debug")
    implementation("com.google.firebase:firebase-functions-ktx")
    implementation("com.google.firebase:firebase-config-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)

    // CameraX
    val cameraVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraVersion")
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")
    implementation("androidx.camera:camera-extensions:$cameraVersion")
    
    // Coil
    implementation("io.coil-kt:coil-compose:2.6.0")
    
    // Security - Jetpack Security for encryption
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // SQLCipher - Database encryption
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    
    // Biometric authentication
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    
    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    
    // ML Kit - Document Scanner
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
    
    // ML Kit - Text Recognition (OCR)
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")
    
    // Note: AI extraction moved to Cloud Functions (server-side with Vertex AI)
    // No client-side AI SDK needed
    
    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
}
