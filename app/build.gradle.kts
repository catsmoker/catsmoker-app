import java.security.KeyStore
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.catsmoker.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.catsmoker.app"
        minSdk = 27
        targetSdk = 37
        // versionCode 7: IFileService gained readFile/writeFile — the bump is what forces
        // Shizuku to restart the daemonized helper whose AIDL no longer matches (see the
        // ShellRunner KDoc). Shipped helpers keep serving the old AIDL until this moves.
        versionCode = 7
        versionName = "2.0.1"

        // Only locales the app actually ships (see res/xml/locales_config.xml): strips the
        // dozens of transitive locales dragged in by material/splashscreen/work.
        // Shrinking alone cannot do this.
        resConfigs("en", "en-rGB", "ar-rSA", "es-rES", "zh-rCN")

        vectorDrawables {
            useSupportLibrary = true
        }

        val localProperties = Properties()
        val localPropertiesFile = project.rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }
        // Play-store branch serves AdMob. IDs come from local.properties so the real
        // production IDs never enter git; unset keys fall back to Google's documented
        // sample (test) IDs, which serve test ads and are safe to build with.
        val admobAppId = localProperties.getProperty("ADMOB_APP_ID") ?: "ca-app-pub-3940256099942544~3347511713"
        val admobBannerId = localProperties.getProperty("ADMOB_BANNER_ID") ?: "ca-app-pub-3940256099942544/6300978111"
        val admobInterstitialId = localProperties.getProperty("ADMOB_INTERSTITIAL_ID") ?: "ca-app-pub-3940256099942544/1033173712"
        manifestPlaceholders["ADMOB_APP_ID"] = admobAppId
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$admobInterstitialId\"")
    }

    // Release-signing credentials (git-ignored signing.properties at the repo root;
    // four keys required: storeFile, storePassword, keyAlias, keyPassword).
    val signingProps = Properties()
    val signingPropsFile = project.rootProject.file("signing.properties")
    if (signingPropsFile.exists()) {
        signingProps.load(signingPropsFile.inputStream())
    }
    val hasReleaseSigning = signingProps.containsKey("storeFile")
        && signingProps.containsKey("storePassword")
        && signingProps.containsKey("keyAlias")
        && signingProps.containsKey("keyPassword")
    if (!hasReleaseSigning) {
        logger.warn("signing.properties missing or incomplete — release will be debug-signed.")
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
                storeType = signingProps.getProperty("storeType", KeyStore.getDefaultType())
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Permanent release key (PLAYSTORE.md §4): credentials live in the git-ignored
            // signing.properties at the repo root. Without that file the release build stays
            // debug-signed so any clone still builds.
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
        getByName("debug") {
            isMinifyEnabled = false
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = false
        compose = true
        buildConfig = true
        aidl = true // Required for Shizuku UserService
    }

    // Prevents build failure if minor warnings occur
    lint {
        abortOnError = false
        // Surfaces future unused-resource/deprecation warnings on release builds; with
        // abortOnError=false this can never block a release. Zero runtime/APK change.
        checkReleaseBuilds = true
    }

    ndkVersion = "27.0.12077973"

    packaging {
        resources {
            excludes += listOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0"
            )
        }
    }
}

dependencies {
    // --- AndroidX Core & UI ---
    implementation(libs.material) // Theme.Material3.* parents in res/values/themes.xml
    implementation(libs.activity)
    implementation(libs.core)
    implementation(libs.documentfile)
    implementation(libs.core.splashscreen)
    implementation(libs.core.ktx)

    // --- Jetpack Compose ---
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.runtime.tracing)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.ktx)

    // --- Hilt DI ---
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    // @HiltWorker needs hilt-work's factory plus its own annotation processor.
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // --- Background scheduling (the recurring dexopt sweep) ---
    implementation(libs.work.runtime.ktx)

    // --- Coroutines ---
    implementation(libs.kotlinx.coroutines.android)

    // --- JSON ---
    implementation(libs.gson)

    // --- Ads (AdMob; playstore branch — see PLAYSTORE.md) ---
    implementation(libs.play.services.ads)

    // --- Root & System ---
    implementation(libs.libsu.core)

    // --- Shizuku ---
    implementation(libs.shizuku.api)
    implementation(libs.provider)

    // --- Testing ---
    testImplementation(libs.junit)
    testImplementation(libs.json.test)
}

ksp {
    arg("dagger.fastInit", "ENABLED")
    arg("dagger.hilt.android.internal.disableAndroidSuperclassValidation", "true")
}