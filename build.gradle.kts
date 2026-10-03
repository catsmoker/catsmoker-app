// Top-level build file where you can add configuration options common to all subprojects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hilt.android) apply false
    // Firebase: makes google-services.json values available to Firebase SDKs.
    // Version lives in gradle/libs.versions.toml (googleServices). No buildscript block.
    alias(libs.plugins.google.services) apply false
}

allprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-Xlint:deprecation")
    }
}