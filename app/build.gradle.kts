// AGP 9 has built-in Kotlin support: no org.jetbrains.kotlin.android plugin.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "sumicya.fcitx5"
    compileSdk = 36

    defaultConfig {
        applicationId = "sumicya.fcitx5"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}
