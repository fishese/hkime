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
        // User-selected public app ID. This installs beside older HK IME test builds.
        applicationId = "cc.fishese.hkime"
        minSdk = 24
        targetSdk = 34
        versionCode = 31
        versionName = "0.3.27"
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

    // OpenCC for Simplified to Traditional Chinese conversion
    // Pure Java library, no JNI needed; remains fully offline.
    implementation("io.github.laisuk:openccjava:1.2.0")
}
