plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
    id("kotlin-parcelize")
    id("com.google.gms.google-services")
    id("com.google.firebase.appdistribution")
}

import java.util.Properties

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

val resolvedN8nMode = (project.findProperty("N8N_MODE") as String?)
    ?.lowercase()
    ?.takeIf { it == "prod" || it == "test" }
    ?: "test"

android {
    namespace = "com.example.aichat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.aichat"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.2.2"

        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "N8N_TEST_URL", "\"https://timsmykov.app.n8n.cloud/webhook-test/a3b7e817-1b56-4722-8f9f-9266b7d91946\"")
        buildConfigField("String", "N8N_PROD_URL", "\"https://timsmykov.app.n8n.cloud/webhook/a3b7e817-1b56-4722-8f9f-9266b7d91946\"")
        buildConfigField("String", "N8N_MODE", "\"$resolvedN8nMode\"")
        buildConfigField("String", "VOSK_WS_URL", "\"ws://89.47.163.243:2700\"")
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                val storeFilePath = keystoreProperties.getProperty("storeFile")
                    ?: error("Missing storeFile in keystore.properties")
                storeFile = rootProject.file(storeFilePath)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xjvm-default=all", "-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/INDEX.LIST",
            "META-INF/io.netty.versions.properties",
            "META-INF/AL2.0",
            "META-INF/LGPL2.1"
        )
    }
}

firebaseAppDistribution {
    serviceCredentialsFile = rootProject.file("Agent Service Account.json").absolutePath
    appId = project.findProperty("FIREBASE_APP_ID") as String? ?: "1:611670654610:android:5b1d9a87666f1460ccf04b"
    (project.findProperty("FIREBASE_TESTERS") as String?)?.let { testers = it }
    (project.findProperty("FIREBASE_GROUPS") as String?)?.let { groups = it }
    releaseNotes = project.findProperty("FIREBASE_RELEASE_NOTES") as String? ?: "1.2: revamped voice orb visuals, manual hold-to-record, domain/http cleanup"
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    implementation("com.google.firebase:firebase-analytics")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.2")

    val composeVersion = "1.6.8"
    implementation("androidx.compose.ui:ui:$composeVersion")
    implementation("androidx.compose.ui:ui-tooling-preview:$composeVersion")
    debugImplementation("androidx.compose.ui:ui-tooling:$composeVersion")
    implementation("androidx.compose.foundation:foundation:$composeVersion")
    implementation("androidx.compose.material:material-icons-extended:$composeVersion")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.34.0")

    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("com.jakewharton.timber:timber:5.0.1")

    implementation("androidx.annotation:annotation:1.8.2")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}

kapt {
    correctErrorTypes = true
    useBuildCache = true
}
