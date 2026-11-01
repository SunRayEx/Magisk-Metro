plugins {
    id("MagiskPlugin")
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.moshix) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)

    subprojects.forEach {
        dependsOn(":${it.name}:clean")
    }
}
