import java.util.Properties
import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt.android)
    // Firebase (google-services.json, project catsmoker-5b6c6, gitignored).
    // Shared configuration; Analytics startup is explicit and consent-gated.
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.catsmoker.app"
    compileSdk = 37

    androidResources {
        // Only locales the app actually ships (see res/xml/locales_config.xml): strips the
        // dozens of transitive locales dragged in by material/ads/splashscreen/work.
        // Shrinking alone cannot do this. Qualifiers match res/values-* dirs exactly;
        // in-app tags are en/ar/es/ja/zh-CN/pt-BR (AppearanceStore), en-GB is a mirror.
        localeFilters += setOf("en", "en-rGB", "ar", "es", "ja", "zh-rCN", "pt-rBR", "fr")
    }

    defaultConfig {
        applicationId = "com.catsmoker.app"
        minSdk = 27
        targetSdk = 37
        // versionCode 7: IFileService gained readFile/writeFile — the bump is what forces
        // Shizuku to restart the daemonized helper whose AIDL no longer matches (see the
        // ShellRunner KDoc). Shipped helpers keep serving the old AIDL until this moves.
        versionCode = 13
        versionName = "2.0.7"

        vectorDrawables {
            useSupportLibrary = true
        }

        // Nothing's Glyph Developer Kit API key, from the git-ignored local.properties.
        // Nothing stopped requiring it from Android 16, but the meta-data stays for the
        // Android 14/15 phones that still check it. The fallback is a dummy: registration
        // then simply fails and the LEDs stay dark, which is the same no-op as a
        // non-Nothing device — never a build failure.
        val nothingProps = Properties()
        val nothingPropsFile = project.rootProject.file("local.properties")
        if (nothingPropsFile.exists()) {
            nothingProps.load(nothingPropsFile.inputStream())
        }
        manifestPlaceholders["NOTHING_API_KEY"] =
            nothingProps.getProperty("NOTHING_API_KEY") ?: "test"

        // No per-variant IDs here: full declares STARTIO_APP_ID, playstore declares
        // ADMOB_* + the ADMOB_APP_ID manifest placeholder in their flavor blocks.
    }

    splits {
        abi {
            // An app bundle carries every ABI in its base module; AGP rejects ABI splits
            // alongside a bundle ("multiple shrunk-resources files"). Disable splits when a
            // bundle task is requested so bundleFullRelease/bundlePlaystoreRelease can run.
            isEnable = !gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    // Single codebase, two distribution variants:
    // full = complete GitHub app (spoof/LSPosed/Magisk/Start.io/self-updater),
    // playstore = Play distribution (no spoof implementation, AdMob instead of Start.io).
    // Flavor isolation is not a promise of Play policy approval.
    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
            // Full-only R8/ProGuard rules (Start.io, LSPosed, spoof models).
            proguardFile("src/full/proguard-rules.pro")
            // Android.mk selects the entire full-only bridge from src/full/jni.
            // Play builds no app native module (including the Unity delivery code).
            externalNativeBuild {
                ndkBuild {
                    arguments.add("CATSMOKER_FULL=1")
                }
            }
            val localProperties = Properties()
            val localPropertiesFile = project.rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                localProperties.load(localPropertiesFile.inputStream())
            }
            val startIoId = localProperties.getProperty("STARTIO_APP_ID") ?: "205489527"
            buildConfigField("String", "STARTIO_APP_ID", "\"$startIoId\"")
        }
        create("playstore") {
            dimension = "distribution"
            val localProperties = Properties()
            val localPropertiesFile = project.rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                localProperties.load(localPropertiesFile.inputStream())
            }
            // Real production IDs never enter git; unset keys fall back to Google's
            // documented sample (test) IDs, which serve test ads and are safe to build with.
            val admobAppId = localProperties.getProperty("ADMOB_APP_ID") ?: "ca-app-pub-3940256099942544~3347511713"
            val admobBannerId = localProperties.getProperty("ADMOB_BANNER_ID") ?: "ca-app-pub-3940256099942544/6300978111"
            val admobInterstitialId = localProperties.getProperty("ADMOB_INTERSTITIAL_ID") ?: "ca-app-pub-3940256099942544/1033173712"
            manifestPlaceholders["ADMOB_APP_ID"] = admobAppId
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$admobInterstitialId\"")
        }
    }

    // Release-signing credentials (git-ignored signing.properties at the repo root;
    // four keys required: storeFile, storePassword, keyAlias, keyPassword).
    // Shared by both variants; without the file, releases stay debug-signed so any
    // clone still builds.
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
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    ndkVersion = "28.2.13676358"

    // Shared selection script only; implementation sources and JNI caller are full-only.
    externalNativeBuild {
        ndkBuild {
            path("src/main/jni/Android.mk")
        }
    }

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
    implementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.runtime.tracing)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coil.compose)

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

    // --- Firebase Analytics (SHARED: full + playstore, same project) ---
    // AnalyticsConsent explicitly initializes only after a current opt-in.
    // The automatic Firebase provider is removed in the shared manifest.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)

    // --- Ads (variant-specific: Start.io on full, AdMob on playstore) ---
    // String-based configurations: flavor-qualified accessors (fullImplementation,
    // playstoreImplementation, ...) are not available as Kotlin-DSL members here.
    add("fullImplementation", libs.startio.sdk)
    add("playstoreImplementation", libs.play.services.ads)
    add("playstoreImplementation", libs.ump)
    // Google Play Billing (remove_ads one-time product) is Play-only like AdMob.
    add("playstoreImplementation", libs.billing.ktx)

    // --- Root & System (shared: libsu + Shizuku serve the gaming engine on both) ---
    implementation(libs.libsu.core)
    // Xposed API is full-only and compileOnly (never packaged): only the LSPosed
    // module in src/full references it.
    add("fullCompileOnly", libs.api) // Xposed API

    // --- Shizuku ---
    implementation(libs.shizuku.api)
    implementation(libs.provider)

    // --- Nothing Glyph Interface (GDK) ---
    // Nothing's official Glyph Developer Kit, vendored from
    // Nothing-Developer-Programme/Glyph-Developer-Kit (sdk/glyph-matrix-sdk-2.0.aar).
    // `.gitignore` ignores *.aar globally; this one path has an explicit ! exception so the
    // same bytes ship in both flavors. Nothing publishes no licence file for it — see
    // docs/GAMING_MODE.md. Every call is gated on Build.MANUFACTURER, so other phones never
    // touch the service.
    implementation(files("libs/glyph-matrix-sdk-2.0.aar"))

    // --- Testing ---
    testImplementation(libs.junit)
    testImplementation(libs.json.test)
}

ksp {
    arg("dagger.fastInit", "ENABLED")
    arg("dagger.hilt.android.internal.disableAndroidSuperclassValidation", "true")
}

abstract class DistributionAuditTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val components: ListProperty<String>

    @get:Input
    abstract val compileComponents: ListProperty<String>

    @get:Input
    abstract val sourceDirectories: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val runtimeFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val reportDirectory: DirectoryProperty

    @TaskAction
    fun export() {
        val directory = reportDirectory.get().asFile.apply { mkdirs() }
        mergedManifest.get().asFile.copyTo(directory.resolve("AndroidManifest.xml"), overwrite = true)
        directory.resolve("dependencies.txt").writeText(components.get().sorted().joinToString("\n", postfix = "\n"))
        directory.resolve("compile-dependencies.txt").writeText(compileComponents.get().sorted().joinToString("\n", postfix = "\n"))
        directory.resolve("sources.txt").writeText(sourceDirectories.get().sorted().joinToString("\n", postfix = "\n"))
        directory.resolve("artifacts.txt").writeText(
            runtimeFiles.files.map { it.absolutePath }.sorted().joinToString("\n", postfix = "\n")
        )
    }
}

// Explicit exports from AGP's selected source providers and resolved graphs. Generated
// directories are included; no inferred main+flavor directory list or fallback assumptions.
androidComponents.onVariants(
    androidComponents.selector().withBuildType("release")
) { variant ->
    val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
    val runtime = configurations.named("${variant.name}RuntimeClasspath")
    val compile = configurations.named("${variant.name}CompileClasspath")
    fun graph(configuration: NamedDomainObjectProvider<Configuration>) =
        configuration.flatMap { it.incoming.resolutionResult.rootComponent }.map { root ->
            val visited = mutableSetOf<ResolvedComponentResult>()
            fun visit(component: ResolvedComponentResult) {
                if (!visited.add(component)) return
                component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { visit(it.selected) }
            }
            visit(root)
            visited.map { it.id.displayName }
        }
    val selectedSources = objects.listProperty(String::class.java).convention(emptyList())
    variant.sources.java?.all?.let { provider ->
        selectedSources.addAll(provider.map { dirs -> dirs.map { "java\t${it.asFile.absolutePath}" } })
    }
    variant.sources.kotlin?.all?.let { provider ->
        selectedSources.addAll(provider.map { dirs -> dirs.map { "kotlin\t${it.asFile.absolutePath}" } })
    }
    variant.sources.res?.all?.let { provider ->
        selectedSources.addAll(provider.map { groups -> groups.flatten().map { "res\t${it.asFile.absolutePath}" } })
    }
    variant.sources.assets?.all?.let { provider ->
        selectedSources.addAll(provider.map { groups -> groups.flatten().map { "assets\t${it.asFile.absolutePath}" } })
    }
    variant.sources.jniLibs?.all?.let { provider ->
        selectedSources.addAll(provider.map { groups -> groups.flatten().map { "jniLibs\t${it.asFile.absolutePath}" } })
    }
    tasks.register<DistributionAuditTask>("write${variant.name.replaceFirstChar { it.uppercaseChar() }}Audit") {
        group = "verification"
        description = "Export selected ${variant.name} sources, merged manifest and resolved compile/runtime graphs"
        mergedManifest.set(manifest)
        runtimeFiles.from(runtime)
        components.set(graph(runtime))
        compileComponents.set(graph(compile))
        sourceDirectories.set(selectedSources)
        reportDirectory.set(layout.buildDirectory.dir("reports/${variant.productFlavors.single { it.first == "distribution" }.second}-boundary"))
    }
}
