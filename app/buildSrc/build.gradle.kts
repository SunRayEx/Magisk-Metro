plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
}

gradlePlugin {
    plugins {
        register("MagiskPlugin") {
            id = "MagiskPlugin"
            implementationClass = "MagiskPlugin"
        }
    }
}

dependencies {
    // Keep the Kotlin Gradle plugin aligned with the version used by the
    // Compose compiler and project plugins. AGP declares its own bundled KGP,
    // so do not let that transitive 2.2.x dependency win on buildSrc's classpath.
    implementation(kotlin("gradle-plugin", libs.versions.kotlin.get()))
    implementation(libs.android.gradle.plugin) {
        exclude(group = "org.jetbrains.kotlin")
    }
    compileOnly(libs.android.build.sdk.common)
    implementation(libs.jgit)
}
