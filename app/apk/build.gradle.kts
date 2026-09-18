import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application")
    kotlin("plugin.parcelize")
    alias(libs.plugins.kotlin.compose)
}


setupMainApk()

android {
    buildFeatures {
        compose = true
    }

    // Disable compose mapping tasks (ASM 9.7 incompatible with Java 25 class files)
    tasks.configureEach {
        if (name.contains("ComposeMapping", ignoreCase = true) ||
            name.contains("ArtProfile", ignoreCase = true)) {
            enabled = false
        }
    }


    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        jniLibs {
            excludes += "lib/*/libandroidx.graphics.path.so"
        }
    }

    defaultConfig {
        proguardFile("proguard-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    // Legacy XML/DataBinding destinations are not part of the Navigation3 APK.
    exclude("**/*Fragment.kt")
    exclude("**/MetroObservables.kt")
    exclude("**/ContributorViewModel.kt")
}

dependencies {
    implementation(project(":core"))
    coreLibraryDesugaring(libs.jdk.libs)

    // Compose
    implementation(libs.compose.ui)
    implementation(libs.accompanist.drawablepainter)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.compose.material3)
    implementation(libs.material)

    // Navigation3
    implementation(libs.navigation3.runtime)
    implementation(libs.navigationevent.compose)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.navigation3.ui)
}
