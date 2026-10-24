plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val hasLocalOcr = file("../ocr-engines/build.gradle.kts").exists()

android {
    namespace = "app.screenlookup"
    ndkVersion = "29.0.14206865"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.screenlookup"
        minSdk = 26
        targetSdk = 36
        versionCode = project.findProperty("releaseVersionCode")?.toString()?.toInt() ?: 1
        versionName = project.findProperty("releaseVersionName")?.toString() ?: "0.1.0"

        buildConfigField("boolean", "HAS_LOCAL_OCR", hasLocalOcr.toString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".beta"
        }

        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":dictionary"))
    implementation(project(":audio"))
    implementation(project(":anki"))
    implementation(project(":dictionary-ui"))
    implementation(project(":gemini"))
    implementation(project(":ocr"))

    if (hasLocalOcr) {
        implementation(project(":ocr-engines"))
    }

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.github.skydoves:colorpicker-compose:1.0.8")
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    debugImplementation(libs.compose.ui.tooling)
}
