plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt") // TODO: Remove when we migrate to KSP
    id("org.jetbrains.kotlin.android")
    id("dagger.hilt.android.plugin")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets.all {
        kotlin.srcDir("build/generated/ksp/$name/kotlin")
    }
}

android {
    // Kotlin
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // TODO: Remove after migrating to KSP
    kapt {
        correctErrorTypes = true
    }

    // Android
    compileSdk = catalog.version("compile-sdk").toInt()
    defaultConfig {
        minSdk = catalog.version("min-sdk").toInt()
    }

    // Kotest
    testOptions {
        unitTests.all {
            // Required by Kotest
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    implementation(libs.bundles.arrow)
    implementation(libs.bundles.kotlin)
    implementation(libs.timber)

    implementation(libs.bundles.hilt)
    // TODO: Migrate to KSP when supported
    kapt(catalog.library("hilt-compiler"))

    implementation(catalog.library("kotlinx-serialization-json"))

    testImplementation(libs.bundles.testing)
}

// TODO: Remove after migrating to KSP
kapt {
    correctErrorTypes = true
}