plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "chimahon.local.ocr"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    defaultConfig {
        minSdk = 26

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17")
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        getByName("debug") {
            externalNativeBuild {
                cmake {
                    abiFilters("arm64-v8a")
                }
            }
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
    implementation(project(":ocr"))
    implementation(libs.kotlinx.coroutines.android)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.30.0")
}
