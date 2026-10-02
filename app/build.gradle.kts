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

dependencies {
    testImplementation("junit:junit:4.13.2")
}

// The dictionary asset is generated, not committed: it is 5 MB of derived data
// downloaded from download.fcitx-im.org, so building without it only needs the
// network once.
val generateDictionary = tasks.register<Exec>("generateDictionary") {
    workingDir = rootProject.layout.projectDirectory
    onlyIf { !rootProject.layout.projectDirectory.file("app/src/main/assets/pinyin.dict").asFile.exists() }
    commandLine("python3", "scripts/build_dict.py")
}
tasks.named("preBuild") { dependsOn(generateDictionary) }

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    testLogging {
        showStandardStreams = true
        showExceptions = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}
