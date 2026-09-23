import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Load keystore properties if available (for release signing)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.awcjack.dualquickime"
    compileSdk = 34

    defaultConfig {
        // Provisional standalone id so this prototype cannot overwrite the
        // upstream DualQuickIME app or the supplied legacy keyboard.
        applicationId = "dev.local.mixedchinesekeyboard"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0"
    }

    // Release signing configuration (only if keystore.properties exists)
    if (keystorePropertiesFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use release signing if available
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    // Product flavors: full (with voice input) and lite (without voice input)
    flavorDimensions += "version"
    productFlavors {
        create("full") {
            dimension = "version"
            // Full version includes voice input (requires INTERNET permission for model download)
            buildConfigField("boolean", "VOICE_INPUT_ENABLED", "true")
        }
        create("lite") {
            dimension = "version"
            // Lite version without voice input (no INTERNET permission required)
            applicationIdSuffix = ".lite"
            versionNameSuffix = "-lite"
            buildConfigField("boolean", "VOICE_INPUT_ENABLED", "false")
        }
    }

    // Generate separate APKs per ABI to reduce download size
    // arm64-v8a: Modern 64-bit devices (most common)
    // armeabi-v7a: Older 32-bit devices
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true // Also build a universal APK for fallback
        }
    }

    buildFeatures {
        buildConfig = true
        // Enable AIDL processing so IVoiceRecognizer.aidl (used by the
        // VoiceService in the full flavor) is compiled to its stub class.
        // Off by default in AGP 7+; without this the .aidl file is silently
        // skipped and the Kotlin call sites fail to resolve.
        aidl = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.cardview:cardview:1.0.0")
    // Security library for EncryptedSharedPreferences (clipboard history encryption)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Sherpa-ONNX for offline voice recognition (Cantonese/Chinese/English)
    // Using official k2-fsa AAR which includes VAD support
    // Only included in the full flavor
    "fullImplementation"(files("libs/sherpa-onnx-1.12.34.aar"))

    // OpenCC for Simplified to Traditional Chinese conversion
    // Pure Java library, no JNI needed. The lite keyboard also uses it for the
    // selected-text conversion action and remains fully offline.
    "fullImplementation"("io.github.laisuk:openccjava:1.2.0")
    "liteImplementation"("io.github.laisuk:openccjava:1.2.0")
}
