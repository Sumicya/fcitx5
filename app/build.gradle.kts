// AGP 9 has built-in Kotlin support: no org.jetbrains.kotlin.android plugin.
plugins {
    alias(libs.plugins.android.application)
}

// yy.m.d.ci: CI 在一次运行内算好后用 -PversionName / -PversionCode 传进来，
// 本地不带参数构建拿到的是开发版本。
val releaseVersionName = providers.gradleProperty("versionName")
val releaseVersionCode = providers.gradleProperty("versionCode")

android {
    namespace = "sumicya.fcitx5"
    compileSdk = 36

    defaultConfig {
        applicationId = "sumicya.fcitx5"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode.orNull?.toIntOrNull() ?: 1
        versionName = releaseVersionName.orNull ?: "0.0.0.dev"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // the one and only third party dependency: Material 3 Expressive, so the
    // settings screen uses the same components as everything else on the phone
    implementation(libs.material)
    testImplementation("junit:junit:4.13.2")
}

// The dictionary asset is generated, not committed: it is 5 MB of derived data
// downloaded from download.fcitx-im.org, so building without it only needs the
// network once.
val generateDictionary = tasks.register<Exec>("generateDictionary") {
    workingDir = rootProject.layout.projectDirectory.asFile
    onlyIf {
        listOf("pinyin.dict", "st.txt", "py.txt").any {
            !rootProject.layout.projectDirectory.file("app/src/main/assets/$it").asFile.exists()
        }
    }
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
