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
                // Force CMake to compile without debug symbols (-g).
                // This is exactly what PlayTranslate does to keep libMNN.so small.
                arguments += "-DCMAKE_BUILD_TYPE=Release"
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
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
            ndk {
                debugSymbolLevel = "NONE"
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
    // MNN is built from the third-party/MNN git submodule via CMake.
    // See ocr-engines/src/main/cpp/CMakeLists.txt.
}
