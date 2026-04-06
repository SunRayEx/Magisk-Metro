plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

repositories {
    maven { url = uri("https://jitpack.io") }
}

android {
    namespace = "com.magiskube.magisk"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        applicationId = "com.magiskube.magisk"
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
        
        ndk {
            // Filter for arm64-v8a only (Magisk only supports arm64)
            abiFilters += listOf("arm64-v8a")
        }
    }
    
    // Configure packaging options for native libraries
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    
    // Configure jniLibs source directory to include prebuilt .so files
    sourceSets {
        getByName("main") {
            jniLibs.srcDir("src/main/jniLibs")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isJniDebuggable = false
            isRenderscriptDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

flutter {
    source = "../.."
}

dependencies {
    implementation("androidx.webkit:webkit:1.8.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
}

// Task to copy Magisk native binaries to jniLibs directory
// This ensures binaries are available as .so files for System.loadLibrary
tasks.register("copyMagiskLibraries") {
    description = "Copy Magisk native binaries to jniLibs for proper installation"
    group = "build"
    
    doLast {
        // Target: jniLibs/arm64-v8a (will be installed to lib/arm64-v8a in APK)
        val jniLibsDir = file("src/main/jniLibs/arm64-v8a")
        jniLibsDir.mkdirs()
        
        // Source: native/out directory (built from Rust)
        val nativeOutDir = file("../../../native/out/arm64-v8a")
        
        // Source: assets directory (pre-built binaries)
        val assetsDir = file("src/main/assets")
        
        var copiedCount = 0
        var totalSize = 0L
        
        // Copy magisk binary as libmagisk.so
        val magiskBinary = listOf(
            File(nativeOutDir, "magisk"),
            File(assetsDir, "magisk")
        ).find { it.exists() }
        if (magiskBinary != null) {
            val targetFile = File(jniLibsDir, "libmagisk.so")
            magiskBinary.copyTo(targetFile, overwrite = true)
            copiedCount++
            totalSize += magiskBinary.length()
            println("Copied: magisk -> libmagisk.so (${magiskBinary.length()} bytes)")
        }
        
        // Copy magiskboot binary as libmagiskboot.so
        val magiskbootBinary = listOf(
            File(nativeOutDir, "magiskboot"),
            File(assetsDir, "magiskboot")
        ).find { it.exists() }
        if (magiskbootBinary != null) {
            val targetFile = File(jniLibsDir, "libmagiskboot.so")
            magiskbootBinary.copyTo(targetFile, overwrite = true)
            copiedCount++
            totalSize += magiskbootBinary.length()
            println("Copied: magiskboot -> libmagiskboot.so (${magiskbootBinary.length()} bytes)")
        }
        
        // Copy magiskinit binary as libmagiskinit.so
        val magiskinitBinary = listOf(
            File(nativeOutDir, "magiskinit"),
            File(assetsDir, "magiskinit")
        ).find { it.exists() }
        if (magiskinitBinary != null) {
            val targetFile = File(jniLibsDir, "libmagiskinit.so")
            magiskinitBinary.copyTo(targetFile, overwrite = true)
            copiedCount++
            totalSize += magiskinitBinary.length()
            println("Copied: magiskinit -> libmagiskinit.so (${magiskinitBinary.length()} bytes)")
        }
        
        // Copy magiskpolicy binary as libmagiskpolicy.so
        val magiskpolicyBinary = listOf(
            File(nativeOutDir, "magiskpolicy"),
            File(assetsDir, "magiskpolicy")
        ).find { it.exists() }
        if (magiskpolicyBinary != null) {
            val targetFile = File(jniLibsDir, "libmagiskpolicy.so")
            magiskpolicyBinary.copyTo(targetFile, overwrite = true)
            copiedCount++
            totalSize += magiskpolicyBinary.length()
            println("Copied: magiskpolicy -> libmagiskpolicy.so (${magiskpolicyBinary.length()} bytes)")
        }
        
        // Copy libinit-ld.so directly
        val initLdFile = listOf(
            File(nativeOutDir, "libinit-ld.so"),
            File(jniLibsDir, "libinit-ld.so")
        ).find { it.exists() }
        if (initLdFile != null && initLdFile.parentFile != jniLibsDir) {
            val targetFile = File(jniLibsDir, "libinit-ld.so")
            initLdFile.copyTo(targetFile, overwrite = true)
            copiedCount++
            totalSize += initLdFile.length()
            println("Copied: libinit-ld.so (${initLdFile.length()} bytes)")
        }
        
        println("\n=== Summary ===")
        println("Copied $copiedCount binaries to jniLibs/arm64-v8a/")
        println("Total size: $totalSize bytes (${totalSize / 1024 / 1024} MB)")
        println("These will be installed to: context.applicationInfo.nativeLibraryDir")
        
        if (copiedCount == 0) {
            println("\nWARNING: No binaries copied!")
            println("Please ensure native binaries exist in:")
            println("  - native/out/arm64-v8a/")
            println("  - OR src/main/assets/")
        }
    }
}

// Run copy task before preBuild
tasks.named("preBuild") {
    dependsOn("copyMagiskLibraries")
}
