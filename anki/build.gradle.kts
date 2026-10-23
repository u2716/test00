plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "chimahon.anki"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
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

    implementation("androidx.core:core-ktx:1.13.1")
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
}
