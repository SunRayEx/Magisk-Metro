plugins {
    id("com.android.application")
    kotlin("plugin.parcelize")
<<<<<<< HEAD
    alias(libs.plugins.kotlin.compose)
    id("com.android.legacy-kapt")
    id("androidx.navigation.safeargs.kotlin")
=======
    alias(libs.plugins.compose.compiler)
>>>>>>> 37063225d4f344a8f41de8201f679e57098cb7e6
}


setupMainApk()

android {
    buildFeatures {
<<<<<<< HEAD
        dataBinding = true
=======
>>>>>>> 37063225d4f344a8f41de8201f679e57098cb7e6
        compose = true
    }

    // Disable compose mapping tasks (ASM 9.7 incompatible with Java 25 class files)
    tasks.configureEach {
        if (name.contains("ComposeMapping", ignoreCase = true)) {
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

<<<<<<< HEAD
    // Jetpack Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)

    // Make sure kapt runs with a proper kotlin-stdlib
    kapt(kotlin("stdlib"))

=======
    // Navigation3
    implementation(libs.navigation3.runtime)
    implementation(libs.navigationevent.compose)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.navigation3.ui)
>>>>>>> 37063225d4f344a8f41de8201f679e57098cb7e6
}
