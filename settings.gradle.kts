pluginManagement {
    repositories {
        if (!providers.gradleProperty("coreOnly").isPresent) google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Versions live here (not in the root build file) so plugins are only
    // resolved by the modules that apply them. This lets `-PcoreOnly` build and
    // test the pure-Kotlin planning engine without the Android toolchain.
    plugins {
        id("com.android.application") version "8.7.3"
        id("org.jetbrains.kotlin.android") version "2.1.0"
        id("org.jetbrains.kotlin.jvm") version "2.1.0"
        id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (!providers.gradleProperty("coreOnly").isPresent) google()
        mavenCentral()
    }
}

rootProject.name = "PaceMate"
include(":core")
if (!providers.gradleProperty("coreOnly").isPresent) {
    include(":app")
}
