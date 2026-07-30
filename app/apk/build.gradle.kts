plugins {
    id("com.android.application")
    kotlin("plugin.parcelize")
    alias(libs.plugins.kotlin.compose)
    id("com.android.legacy-kapt")
    id("androidx.navigation.safeargs.kotlin")
}


setupMainApk()

kapt {
    correctErrorTypes = true
    useBuildCache = true
    mapDiagnosticLocations = true
    javacOptions {
        option("-Xmaxerrs", "1000")
    }
}

android {
    buildFeatures {
        dataBinding = true
        compose = true
    }


    compileOptions {
        isCoreLibraryDesugaringEnabled = true
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

    implementation(libs.indeterminate.checkbox)
    implementation(libs.rikka.layoutinflater)
    implementation(libs.rikka.insets)
    implementation(libs.rikka.recyclerview)

    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)

    implementation(libs.constraintlayout)
    implementation(libs.swiperefreshlayout)
    implementation(libs.recyclerview)
    implementation(libs.transition)
    implementation(libs.fragment.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)

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

}
