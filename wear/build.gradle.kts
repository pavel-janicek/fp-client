plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.fpclient.android.wear"
    // Compile against API 37: the current Compose stack this module uses (BOM 2026.09.00 /
    // Wear Compose 1.7.0) declares minCompileSdk 37 in its AAR metadata, so the compile-time
    // floor moves up one API while behaviour does not — targetSdk stays 36 in lockstep with :app
    // and minSdk stays 26. AGP 9 resolves `37` to the android-37.0 platform (since API 37 the
    // minor release is part of the platform hash) and fetches it if it is missing.
    compileSdk = 37

    defaultConfig {
        // The Data Layer requires the same package name and signing certificate on both devices.
        // These APKs install on separate devices, so the watch shares :app's applicationId.
        applicationId = "com.fpclient.android"
        // Health Services is available on Wear OS 3+, which starts at API 30.
        minSdk = 30
        // Must stay >= 36 to match :app, and this is not cosmetic: it selects which body-sensors
        // permission the platform will actually grant. The switch to `android.permission.health.
        // READ_HEART_RATE` applies **only to apps targeting API 36+**. While this sat at 34 the app
        // asked for READ_HEART_RATE on API 36 watches, which the framework auto-denies without a
        // dialog, while BODY_SENSORS was simultaneously stripped from the merged manifest by its
        // `maxSdkVersion="35"` — leaving heart rate ungrantable and silently dead.
        targetSdk = 36
        // Unique versionCode distinct from :app (Google Play Console requires globally unique
        // versionCodes across all uploaded artifacts in a release listing).
        versionCode = 47
        versionName = "3.0.0"
    }

    signingConfigs {
        // Same env-driven local release signing as :app. Play requires the watch APK and the
        // phone APK to be signed with the SAME key for them to be treated as a pair, so both
        // modules read the identical KEYSTORE_* environment variables.
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
    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME/VERSION_CODE feed the About screen.
        buildConfig = true
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    // Compose stack follows Google's current Wear setup guide
    // (developer.android.com/training/wearables/compose): the shared BOM pins the phone-style
    // Compose artifacts to 1.12.x — the exact runtime Wear Compose 1.7.0 was released against.
    // Deliberately a newer BOM than :app's 2024.09.03, because Wear Compose 1.7.0 needs
    // Compose >= 1.10; its Kotlin metadata (stdlib 2.1.20) stays readable by this project's
    // Kotlin 2.2.10 compose compiler plugin.
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Compose for Wear OS (Iteration 9a). Note the artifact ids: the Wear libraries were never
    // published as plain `material`/`navigation` — under the androidx.wear.compose group they are
    // compose-foundation (curved layouts), compose-material (Wear Material components) and
    // compose-navigation (SwipeDismissableNavHost). compose-material3 exists too but Iteration 9
    // uses the classic Wear Material components named in the PLAN.
    implementation("androidx.wear.compose:compose-foundation:1.7.0")
    implementation("androidx.wear.compose:compose-material:1.7.0")
    implementation("androidx.wear.compose:compose-navigation:1.7.0")
    // compose-navigation only drags in navigation-compose 2.6.0 transitively, which predates the
    // Compose 1.12 runtime above (it still touches compose-ui symbols that have since been
    // removed). Pin the current stable instead: the NavHostController/NavGraphBuilder surface the
    // Wear NavHost builds on has been stable since navigation 2.6.
    implementation("androidx.navigation:navigation-compose:2.10.2")

    // Layout previews for the round-screen composables (no emulator needed to eyeball them).
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Phone↔watch sign-in relay (Iteration 9b).
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.wear:wear:1.4.0")
    implementation("androidx.wear:wear-ongoing:1.1.0")
    implementation("androidx.health:health-services-client:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
