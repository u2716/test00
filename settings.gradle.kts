pluginManagement {
    buildscript {
        repositories {
            google()
            mavenCentral()
            maven("https://storage.googleapis.com/r8-releases/raw") {
                content { includeModule("com.android.tools", "r8") }
            }
        }
        dependencies {
            // Kotlin 2.4 requires D8/R8 9.1.29 or newer.
            classpath("com.android.tools:r8:9.1.29")
        }
    }
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://www.jitpack.io")
    }
}

rootProject.name = "ScreenLookup"

include(":app")
include(":dictionary")
include(":audio")
include(":anki")
include(":dictionary-ui")
include(":gemini")
include(":ocr")

// Conditional include, mirrors Chimahon's HAS_LOCAL_OCR switch.
val hasLocalOcr = file("ocr-engines/build.gradle.kts").exists()
if (hasLocalOcr) {
    include(":ocr-engines")
}
