plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// build.sh drives this project; these mirror the variables it already used.
val release = System.getenv("ANDROID_BUILD_TYPE") == "release"
val repoRoot = rootDir.resolve("../..").canonicalFile

android {
    namespace = "org.goro"
    compileSdk = 35
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "org.goro"
        minSdk = 29
        targetSdk = 35
        versionCode = System.getenv("ANDROID_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("ANDROID_VERSION_NAME") ?: "0.1-dev"
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        if (release) {
            create("release") {
                storeFile = file(System.getenv("ANDROID_KEYSTORE") ?: error("Set ANDROID_KEYSTORE"))
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: error("Set ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: "goro"
                keyPassword = storePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (release) signingConfig = signingConfigs.getByName("release")
        }
    }

    sourceSets["main"].apply {
        // Java and Kotlin sources live together, as the Java sources always did.
        java.setSrcDirs(listOf("../java"))
        // build.sh puts the Go c-shared library here.
        jniLibs.setSrcDirs(listOf(repoRoot.resolve("dist/android/lib")))
        res.srcDir(layout.buildDirectory.dir("generated/goroRes"))
    }

    packaging {
        // Keeps the previous extractNativeLibs="true" behaviour.
        jniLibs.useLegacyPackaging = true
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

val copyIcon by tasks.registering(Copy::class) {
    from(repoRoot.resolve("internal/appicon/icon.png"))
    into(layout.buildDirectory.dir("generated/goroRes/drawable"))
}
tasks.named("preBuild") { dependsOn(copyIcon) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.core:core-ktx:1.15.0")
}
