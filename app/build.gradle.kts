import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import java.util.Properties

// App-level build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
    // The compose compiler plugin version is now managed by the compose-bom
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.trailerly"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.trailerly"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        vectorDrawables {
            useSupportLibrary = true
        }

        // TMDB API key loaded from local.properties
        val tmdbKey = gradleLocalProperties(rootDir, providers).getProperty("TMDB_API_KEY") ?: "YOUR_API_KEY_HERE"
        buildConfigField("String", "TMDB_API_KEY", "\"${tmdbKey}\"")

        // YouTube API key loaded from local.properties (optional for fallback feature)
        val youtubeKey = gradleLocalProperties(rootDir, providers).getProperty("YOUTUBE_API_KEY") ?: ""
        buildConfigField("String", "YOUTUBE_API_KEY", "\"${youtubeKey}\"")

        // Validate TMDB API key for release builds
        if (gradle.startParameter.taskNames.any { it.contains("Release", true) }) {
            if (tmdbKey.isBlank() || tmdbKey == "YOUR_API_KEY_HERE") {
                throw GradleException("TMDB_API_KEY is not configured or is set to placeholder value. Please set a valid API key in local.properties.")
            }
        }
    }

    signingConfigs {
        if (gradle.startParameter.taskNames.any { it.contains("Release", true) }) {
            create("release") {
                val keystoreProperties = Properties()
                val keystorePropertiesFile = rootProject.file("keystore.properties")
                if (keystorePropertiesFile.exists()) {
                    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
                    val storeFilePath = keystoreProperties.getProperty("storeFile", "")
                    val storePassword = keystoreProperties.getProperty("storePassword", "")
                    val keyAlias = keystoreProperties.getProperty("keyAlias", "")
                    val keyPassword = keystoreProperties.getProperty("keyPassword", "")

                    if (storeFilePath.isNotEmpty() && storePassword.isNotEmpty() &&
                        keyAlias.isNotEmpty() && keyPassword.isNotEmpty()) {
                        storeFile = file(storeFilePath)
                        this.storePassword = storePassword
                        this.keyAlias = keyAlias
                        this.keyPassword = keyPassword
                    } else {
                        throw GradleException("keystore.properties exists but contains empty values. Please ensure all signing properties are set.")
                    }
                } else {
                    throw GradleException("keystore.properties not found. Please create it with your signing configuration. See PRODUCTION_DEPLOYMENT.md for details.")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Only apply signing config if it was created (i.e., for release builds with valid keystore)
            if (signingConfigs.findByName("release") != null) {
                signingConfig = signingConfigs.getByName("release")
            }

            firebaseCrashlytics {
                mappingFileUploadEnabled = true
            }

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Kotlin standard library
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.21")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Core Android
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-process:2.9.4")
    // These are now managed by compose-bom
    implementation("androidx.activity:activity-compose")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx")


    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")


    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.6")

    // ViewModel
    // These are now managed by compose-bom
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose")
    implementation("androidx.lifecycle:lifecycle-runtime-compose")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.57.2")
    kapt("com.google.dagger:hilt-android-compiler:2.57.2")
    // This is now managed by compose-bom
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-moshi:3.0.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation("com.squareup.okhttp3:logging-interceptor:5.3.0")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.15.2")

    // Firebase dependencies
    // When using the BoM, you don't specify versions for individual Firebase libraries.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.android.gms:play-services-auth:21.4.0")
    implementation("com.google.android.gms:play-services-base:18.9.0")
    implementation ("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-firestore")

    // DataStore for local persistence
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.datastore:datastore-preferences-core:1.1.7")

    // YouTube Player for trailers
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:13.0.0")

    // Paging
    implementation("androidx.paging:paging-runtime:3.3.6")
    implementation("androidx.paging:paging-compose:3.3.6")

    // Timber for logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
