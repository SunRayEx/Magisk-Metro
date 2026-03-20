plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
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
        }
    }
}

flutter {
    source = "../.."
}

dependencies {
    implementation("androidx.webkit:webkit:1.8.0")
}

// Task to copy Magisk native libraries to jniLibs directory
// This ensures .so files are installed to nativeLibraryDir (executable on Android 10+)
tasks.register("copyMagiskLibraries") {
    description = "Copy Magisk native libraries to jniLibs for proper installation"
    group = "build"
    
    doLast {
        // Target: jniLibs/arm64-v8a (will be installed to lib/arm64-v8a in APK)
        val jniLibsDir = file("src/main/jniLibs/arm64-v8a")
        jniLibsDir.mkdirs()
        
        // Source directories to search for .so files
        val sourceDirs = listOf(
            // From app/core merged_jni_libs (primary source)
            file("../../../../app/core/build/intermediates/merged_jni_libs/debug/mergeDebugJniLibFolders/out/arm64-v8a"),
            // From native/out directory (built from Rust)
            file("../../../native/out/arm64-v8a"),
            // From build directory
            file("../../../../build/intermediates/merged_native_libs/debug/mergeDebugNativeLibs/out/lib/arm64-v8a")
        )
        
        // Native libraries to copy - these will be available via System.loadLibrary
        val librariesToCopy = listOf(
            // Core Magisk libraries
            "libmagiskboot.so",    // Boot image operations - main library for magiskboot
            "libmagiskpolicy.so",  // SELinux policy operations
            "libbusybox.so",       // Busybox utilities (renamed for proper installation)
            "libinit-ld.so",       // Init loader
            // Additional native binaries (these work as executables too)
            "libmagisk.so",        // Main magisk daemon
            "libmagiskinit.so"     // Init replacement
        )
        
        var copiedCount = 0
        var totalSize = 0L
        
        for (libName in librariesToCopy) {
            var copied = false
            
            // Search all source directories
            for (sourceDir in sourceDirs) {
                val sourceFile = File(sourceDir, libName)
                if (sourceFile.exists()) {
                    val targetFile = File(jniLibsDir, libName)
                    sourceFile.copyTo(targetFile, overwrite = true)
                    copiedCount++
                    totalSize += sourceFile.length()
                    println("Copied: $libName from ${sourceDir.absolutePath} (${sourceFile.length()} bytes)")
                    copied = true
                    break
                }
            }
            
            if (!copied) {
                println("WARNING: $libName not found in any source directory")
            }
        }
        
        // Also copy standalone magiskboot binary if available (for direct execution)
        for (sourceDir in sourceDirs) {
            val magiskbootBinary = File(sourceDir, "magiskboot")
            if (magiskbootBinary.exists()) {
                // Copy as libmagiskboot.so so it can be loaded via System.loadLibrary
                val targetFile = File(jniLibsDir, "libmagiskboot.so")
                magiskbootBinary.copyTo(targetFile, overwrite = true)
                totalSize += magiskbootBinary.length()
                println("Copied: magiskboot binary as libmagiskboot.so (${magiskbootBinary.length()} bytes)")
                break
            }
        }
        
        println("\n=== Summary ===")
        println("Copied $copiedCount libraries to jniLibs/arm64-v8a/")
        println("Total size: $totalSize bytes (${totalSize / 1024 / 1024} MB)")
        println("These will be installed to: context.applicationInfo.nativeLibraryDir")
        
        if (copiedCount == 0) {
            println("\nWARNING: No libraries copied!")
            println("Please build native libraries first:")
            println("  python build.py -r all")
            println("  OR")
            println("  cd app && ./gradlew :core:assembleDebug")
        }
    }
}

// Run copy task before preBuild
tasks.named("preBuild") {
    dependsOn("copyMagiskLibraries")
}
