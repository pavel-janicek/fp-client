plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.fpclient.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fpclient.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 38
        versionName = "2.0.2"
    }

    signingConfigs {
        // Only wired up when keystore credentials are provided via the
        // environment (local release signing). F-Droid builds unsigned
        // releases from source and re-signs with their own key, so the
        // config must simply be absent — never fail — when unset.
        if (System.getenv("KEYSTORE_PATH") != null) {
            create("release") {
                storeFile = file(System.getenv("KEYSTORE_PATH")!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
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
    // Note: with AGP 9 built-in Kotlin there is no kotlinOptions{} DSL.
    // kotlin jvmTarget defaults to android.compileOptions.targetCompatibility (17).
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    packaging {
        resources {
            excludes += "META-INF/dependencies/*"
            excludes += "META-INF/com.android.tools.build.gradle/*"
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-service:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.2")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Persistence
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0")

    // Background notification polling (Iteration 8f) — the only new dependency the whole
    // push feature needs: the periodic worker that checks the instance for new notifications.
    implementation("androidx.work:work-runtime-ktx:2.10.5")

    // Images
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Chrome Custom Tabs (opens federated activities on their origin server, like the web UI)
    implementation("androidx.browser:browser:1.8.0")

    // Mapping (mirrors the OSM tiles + GeoJSON polyline rendering the web UI uses)
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // JVM tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.mockito:mockito-core:5.13.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    // Instrumented Compose smoke tests
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}