import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Firebase se activa solo cuando existe el google-services.json real del proyecto.
val cloudEnabled = file("google-services.json").exists()
if (cloudEnabled) apply(plugin = "com.google.gms.google-services")

val keystorePropertiesFile = rootProject.file("keystore/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

android {
    namespace = "cl.agroalert.araucania"
    compileSdk = 36
    defaultConfig {
        applicationId = "cl.agroalert.araucania"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "CLOUD_ENABLED", cloudEnabled.toString())
    }
    sourceSets {
        getByName("main").java.srcDir(if (cloudEnabled) "src/cloud/java" else "src/local/java")
    }
    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    if (cloudEnabled) {
        implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
        implementation("com.google.firebase:firebase-auth")
        implementation("com.google.firebase:firebase-firestore")
    }
    testImplementation("junit:junit:4.13.2")
}
