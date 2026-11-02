import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.pacemate.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pacemate.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    // Fixed signing key, so every new build installs over the old one and keeps your data.
    // The key is never committed. It comes from either:
    //  - CI: env vars set by the workflow from the repo secrets
    //    PACEMATE_KEYSTORE_BASE64 and PACEMATE_KEYSTORE_PASSWORD, or
    //  - local builds: app/keystore.properties (git-ignored) next to the keystore file.
    val localProps = rootProject.file("app/keystore.properties").takeIf { it.exists() }
        ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }
    val keystorePath = System.getenv("PACEMATE_KEYSTORE_FILE") ?: localProps?.getProperty("storeFile")
    val keystorePassword = System.getenv("PACEMATE_KEYSTORE_PASSWORD") ?: localProps?.getProperty("storePassword")
    val fixedSigning = if (!keystorePath.isNullOrBlank() && !keystorePassword.isNullOrBlank()) {
        signingConfigs.create("fixed") {
            storeFile = file(keystorePath)
            storePassword = keystorePassword
            keyAlias = System.getenv("PACEMATE_KEY_ALIAS") ?: localProps?.getProperty("keyAlias") ?: "pacemate"
            keyPassword = System.getenv("PACEMATE_KEY_PASSWORD") ?: localProps?.getProperty("keyPassword") ?: keystorePassword
        }
    } else {
        logger.warn("PaceMate: no fixed signing key configured; using a temporary debug key.")
        null
    }

    buildTypes {
        debug {
            if (fixedSigning != null) signingConfig = fixedSigning
        }
        release {
            isMinifyEnabled = false
            signingConfig = fixedSigning ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/INDEX.LIST",
                "META-INF/*.SF",
                "META-INF/versions/**",
                "**/module-info.class",
            )
        }
    }
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
}
