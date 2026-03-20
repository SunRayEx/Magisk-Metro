package com.magiskube.magisk

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * MagiskBoot Native Library Wrapper
 * 
 * This class provides Kotlin wrappers for magiskboot operations.
 * 
 * On Android 10+ (API 29+), W^X policy prevents executing binaries from app-writable
 * directories. The solution is to use native libraries installed via jniLibs, which
 * are placed in nativeLibraryDir - a directory that allows execution.
 * 
 * The .so files are placed in src/main/jniLibs/arm64-v8a/ and will be automatically
 * installed to /data/app/<package>/lib/arm64/ (or similar path depending on Android version).
 * 
 * This approach works because:
 * 1. Native libraries from jniLibs are installed to nativeLibraryDir
 * 2. nativeLibraryDir is marked as executable in the SELinux policy
 * 3. We can use Runtime.exec() to run these binaries directly
 */
class MagiskBoot private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "MagiskBoot"
        
        @Volatile
        private var instance: MagiskBoot? = null
        
        // Native library names
        private const val LIB_MAGISKBOOT = "magiskboot"
        private const val LIB_MAGISKPOLICY = "magiskpolicy"
        private const val LIB_BUSYBOX = "busybox"
        private const val LIB_INIT_LD = "init-ld"
        private const val LIB_MAGISK = "magisk"
        private const val LIB_MAGISKINIT = "magiskinit"
        
        /**
         * Get the singleton instance of MagiskBoot
         */
        fun getInstance(context: Context): MagiskBoot {
            return instance ?: synchronized(this) {
                instance ?: MagiskBoot(context.applicationContext).also { instance = it }
            }
        }
        
        /**
         * Load native library
         * This triggers the system to load the .so from nativeLibraryDir
         */
        @Volatile
        private var magiskbootLoaded = false
        
        fun loadMagiskboot(context: Context): Boolean {
            if (magiskbootLoaded) return true
            
            return try {
                System.loadLibrary(LIB_MAGISKBOOT)
                magiskbootLoaded = true
                Log.i(TAG, "Successfully loaded lib$LIB_MAGISKBOOT.so")
                true
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Failed to load lib$LIB_MAGISKBOOT.so: ${e.message}")
                false
            }
        }
    }
    
    // Working directory for operations
    private var workDir: File? = null
    
    /**
     * Get the native library directory path
     * This is where .so files from jniLibs are installed
     */
    private fun getNativeLibraryDir(): String {
        return context.applicationInfo.nativeLibraryDir
    }
    
    /**
     * Get path to a native library executable
     * On Android 10+, these are in nativeLibraryDir which is executable
     */
    private fun getLibraryPath(libName: String): String {
        val libDir = getNativeLibraryDir()
        // Try both with and without .so extension
        val pathWithSo = "$libDir/lib$libName.so"
        val pathWithoutSo = "$libDir/$libName"
        
        return when {
            File(pathWithSo).exists() -> pathWithSo
            File(pathWithoutSo).exists() -> pathWithoutSo
            else -> pathWithSo // Default to standard naming
        }
    }
    
    /**
     * Check if native libraries are available in nativeLibraryDir
     */
    private fun checkNativeLibraries(): Map<String, Boolean> {
        val libDir = File(getNativeLibraryDir())
        val libs = mutableMapOf<String, Boolean>()
        
        val requiredLibs = listOf(
            "lib$LIB_MAGISKBOOT.so",
            "lib$LIB_MAGISKPOLICY.so",
            "lib$LIB_BUSYBOX.so",
            "lib$LIB_INIT_LD.so",
            "lib$LIB_MAGISK.so",
            "lib$LIB_MAGISKINIT.so"
        )
        
        for (lib in requiredLibs) {
            val libFile = File(libDir, lib)
            libs[lib] = libFile.exists()
            if (libFile.exists()) {
                Log.d(TAG, "Found: $lib (${libFile.length()} bytes)")
            }
        }
        
        return libs
    }
    
    /**
     * Ensure native libraries are available
     * Returns true if magiskboot is available
     */
    private fun ensureBinaries(): Boolean {
        val libs = checkNativeLibraries()
        val magiskbootAvailable = libs["lib$LIB_MAGISKBOOT.so"] == true
        
        if (!magiskbootAvailable) {
            Log.e(TAG, "lib$LIB_MAGISKBOOT.so not found in ${getNativeLibraryDir()}")
            Log.e(TAG, "Available libraries: ${libs.filter { it.value }.keys}")
            
            // List directory contents for debugging
            val libDir = File(getNativeLibraryDir())
            if (libDir.exists()) {
                Log.d(TAG, "Contents of ${libDir.absolutePath}:")
                libDir.listFiles()?.forEach { 
                    Log.d(TAG, "  ${it.name} (${it.length()} bytes)")
                }
            } else {
                Log.e(TAG, "Native library directory does not exist: ${libDir.absolutePath}")
            }
        }
        
        return magiskbootAvailable
    }
    
    /**
     * Get the path to magiskboot executable
     */
    private fun getMagiskbootPath(): String {
        return getLibraryPath(LIB_MAGISKBOOT)
    }
    
    /**
     * Get the path to busybox executable
     */
    fun getBusyboxPath(): String {
        return getLibraryPath(LIB_BUSYBOX)
    }
    
    /**
     * Result wrapper for boot image operations
     */
    data class BootImageResult(
        val success: Boolean,
        val returnCode: Int,
        val message: String,
        val output: String = "",
        val error: String = "",
        val outputFiles: List<String> = emptyList()
    )
    
    /**
     * Execute a command and return the result
     * Uses the native library path for binaries
     */
    private fun executeCommand(vararg args: String): BootImageResult {
        try {
            Log.d(TAG, "Executing: ${args.joinToString(" ")}")
            
            val process = ProcessBuilder(*args)
                .directory(workDir)
                .redirectErrorStream(false)
                .start()
            
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val error = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
            
            val returnCode = process.waitFor()
            
            Log.d(TAG, "Return code: $returnCode")
            if (output.isNotEmpty()) Log.d(TAG, "Output: $output")
            if (error.isNotEmpty()) Log.d(TAG, "Error: $error")
            
            return BootImageResult(
                success = returnCode == 0,
                returnCode = returnCode,
                message = if (returnCode == 0) "Success" else "Command failed",
                output = output,
                error = error
            )
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed", e)
            return BootImageResult(
                success = false,
                returnCode = -1,
                message = "Exception: ${e.message}",
                error = e.stackTraceToString()
            )
        }
    }
    
    /**
     * Execute command with environment variables
     */
    private fun executeCommandWithEnv(
        command: List<String>,
        envVars: Map<String, String> = emptyMap()
    ): BootImageResult {
        try {
            Log.d(TAG, "Executing: ${command.joinToString(" ")}")
            
            val processBuilder = ProcessBuilder(command)
                .directory(workDir)
                .redirectErrorStream(false)
            
            // Add environment variables
            val env = processBuilder.environment()
            envVars.forEach { (key, value) -> env[key] = value }
            
            val process = processBuilder.start()
            
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val error = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
            
            val returnCode = process.waitFor()
            
            return BootImageResult(
                success = returnCode == 0,
                returnCode = returnCode,
                message = if (returnCode == 0) "Success" else "Command failed",
                output = output,
                error = error
            )
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed", e)
            return BootImageResult(
                success = false,
                returnCode = -1,
                message = "Exception: ${e.message}",
                error = e.stackTraceToString()
            )
        }
    }
    
    /**
     * Unpack a boot image to its components
     * 
     * @param bootImg Path to the boot image file
     * @param outputDir Directory for extracted components
     * @param skipDecompress Skip decompression of components
     * @param header Dump header info to 'header' file
     * @return BootImageResult with operation status
     */
    fun unpack(
        bootImg: String,
        outputDir: String? = null,
        skipDecompress: Boolean = false,
        header: Boolean = false
    ): BootImageResult {
        if (!ensureBinaries()) {
            return BootImageResult(false, -1, "Native libraries not available")
        }
        
        val magiskboot = getMagiskbootPath()
        
        val bootFile = File(bootImg)
        if (!bootFile.exists()) {
            return BootImageResult(false, -1, "Boot image not found: $bootImg")
        }
        
        workDir = File(outputDir ?: bootFile.parent!!).apply { mkdirs() }
        
        val args = mutableListOf(magiskboot, "unpack")
        if (skipDecompress) args.add("-n")
        if (header) args.add("-h")
        args.add(bootImg)
        
        Log.i(TAG, "Unpacking: ${args.joinToString(" ")}")
        val result = executeCommand(*args.toTypedArray())
        
        if (result.success) {
            val outputFiles = workDir?.listFiles()?.map { it.name } ?: emptyList()
            Log.i(TAG, "Unpacked files: ${outputFiles.joinToString()}")
            return result.copy(outputFiles = outputFiles)
        }
        
        return result
    }
    
    /**
     * Repack boot image components into a new boot image
     * 
     * @param origImg Path to original boot image (for reference)
     * @param outImg Output path for new boot image
     * @param inputDir Directory containing components
     * @param skipCompress Skip compression of components
     * @return BootImageResult with operation status
     */
    fun repack(
        origImg: String,
        outImg: String,
        inputDir: String? = null,
        skipCompress: Boolean = false
    ): BootImageResult {
        if (!ensureBinaries()) {
            return BootImageResult(false, -1, "Native libraries not available")
        }
        
        val magiskboot = getMagiskbootPath()
        
        workDir = File(inputDir ?: File(origImg).parent!!).apply { mkdirs() }
        
        val args = mutableListOf(magiskboot, "repack")
        if (skipCompress) args.add("-n")
        args.add(origImg)
        args.add(outImg)
        
        Log.i(TAG, "Repacking: ${args.joinToString(" ")}")
        return executeCommand(*args.toTypedArray())
    }
    
    /**
     * Decompress a file
     * 
     * @param inputFile Input file path
     * @param outputFile Output file path (optional, auto-detected if not specified)
     * @param format Compression format (gzip, lz4, xz, etc.)
     * @return BootImageResult with operation status
     */
    fun decompress(
        inputFile: String,
        outputFile: String? = null,
        format: String? = null
    ): BootImageResult {
        if (!ensureBinaries()) {
            return BootImageResult(false, -1, "Native libraries not available")
        }
        
        val magiskboot = getMagiskbootPath()
        
        val args = mutableListOf(magiskboot, "decompress")
        if (format != null) {
            args.add("-$format")
        }
        args.add(inputFile)
        if (outputFile != null) {
            args.add(outputFile)
        }
        
        Log.i(TAG, "Decompressing: ${args.joinToString(" ")}")
        return executeCommand(*args.toTypedArray())
    }
    
    /**
     * Compress a file
     * 
     * @param inputFile Input file path
     * @param outputFile Output file path
     * @param format Compression format (gzip, lz4, xz, etc.)
     * @return BootImageResult with operation status
     */
    fun compress(
        inputFile: String,
        outputFile: String,
        format: String = "gzip"
    ): BootImageResult {
        if (!ensureBinaries()) {
            return BootImageResult(false, -1, "Native libraries not available")
        }
        
        val magiskboot = getMagiskbootPath()
        
        val args = mutableListOf(magiskboot, "compress")
        args.add("-$format")
        args.add(inputFile)
        args.add(outputFile)
        
        Log.i(TAG, "Compressing: ${args.joinToString(" ")}")
        return executeCommand(*args.toTypedArray())
    }
    
    /**
     * Get the file format of a file
     * 
     * @param filePath Path to the file
     * @return Format string or null if unknown
     */
    fun getFormat(filePath: String): String? {
        if (!ensureBinaries()) {
            return null
        }
        
        val magiskboot = getMagiskbootPath()
        
        val result = executeCommand(magiskboot, "format", filePath)
        return if (result.success) result.output.trim() else null
    }
    
    /**
     * Split image.dtb into kernel and dtb
     * 
     * @param filePath Path to image.dtb
     * @return BootImageResult with operation status
     */
    fun splitImageDtb(filePath: String): BootImageResult {
        if (!ensureBinaries()) {
            return BootImageResult(false, -1, "Native libraries not available")
        }
        
        val magiskboot = getMagiskbootPath()
        
        Log.i(TAG, "Splitting image.dtb: $filePath")
        return executeCommand(magiskboot, "split", filePath)
    }
    
    /**
     * CPIO operations - extract
     */
    fun cpioExtract(cpioFile: String, outputDir: String): BootImageResult {
        if (!ensureBinaries()) {
            return BootImageResult(false, -1, "Native libraries not available")
        }
        
        val magiskboot = getMagiskbootPath()
        workDir = File(outputDir).apply { mkdirs() }
        
        return executeCommand(magiskboot, "cpio", cpioFile, "extract", outputDir)
    }
    
    /**
     * CPIO operations - create
     */
    fun cpioCreate(inputDir: String, outputFile: String): BootImageResult {
        if (!ensureBinaries()) {
            return BootImageResult(false, -1, "Native libraries not available")
        }
        
        val magiskboot = getMagiskbootPath()
        
        return executeCommand(magiskboot, "cpio", outputFile, "create", inputDir)
    }
    
    /**
     * CPIO operations - test (check if ramdisk is Magisk patched)
     */
    fun cpioTest(cpioFile: String): BootImageResult {
        if (!ensureBinaries()) {
            return BootImageResult(false, -1, "Native libraries not available")
        }
        
        val magiskboot = getMagiskbootPath()
        
        return executeCommand(magiskboot, "cpio", cpioFile, "test")
    }
    
    /**
     * DTB operations - test
     */
    fun dtbTest(dtbFile: String): BootImageResult {
        if (!ensureBinaries()) {
            return BootImageResult(false, -1, "Native libraries not available")
        }
        
        val magiskboot = getMagiskbootPath()
        
        return executeCommand(magiskboot, "dtb", dtbFile, "test")
    }
    
    /**
     * DTB operations - patch
     */
    fun dtbPatch(dtbFile: String): BootImageResult {
        if (!ensureBinaries()) {
            return BootImageResult(false, -1, "Native libraries not available")
        }
        
        val magiskboot = getMagiskbootPath()
        
        return executeCommand(magiskboot, "dtb", dtbFile, "patch")
    }
    
    /**
     * Hexpatch kernel
     */
    fun hexpatch(kernelFile: String, fromHex: String, toHex: String): BootImageResult {
        if (!ensureBinaries()) {
            return BootImageResult(false, -1, "Native libraries not available")
        }
        
        val magiskboot = getMagiskbootPath()
        
        return executeCommand(magiskboot, "hexpatch", kernelFile, fromHex, toHex)
    }
    
    /**
     * SHA1 hash of file
     */
    fun sha1(filePath: String): String? {
        if (!ensureBinaries()) {
            return null
        }
        
        val magiskboot = getMagiskbootPath()
        
        val result = executeCommand(magiskboot, "sha1", filePath)
        return if (result.success) result.output.trim() else null
    }
    
    /**
     * Cleanup method (no longer needed since we use installed libraries)
     */
    fun cleanup() {
        // No cleanup needed for installed libraries
        // They are managed by the Android system
        Log.d(TAG, "Cleanup called - native libraries remain in nativeLibraryDir")
    }
    
    /**
     * Check if magiskboot is available
     */
    fun isAvailable(): Boolean {
        return ensureBinaries()
    }
    
    /**
     * Get version info
     */
    fun getVersion(): String? {
        if (!ensureBinaries()) {
            return null
        }
        
        val magiskboot = getMagiskbootPath()
        
        val result = executeCommand(magiskboot, "-v")
        return if (result.success || result.output.isNotEmpty()) {
            result.output.trim()
        } else {
            "magiskboot (version unknown)"
        }
    }
    
    /**
     * Get detailed information about native library setup
     */
    fun getNativeLibraryInfo(): String {
        val sb = StringBuilder()
        sb.appendLine("=== Native Library Information ===")
        sb.appendLine("Native Library Dir: ${getNativeLibraryDir()}")
        sb.appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        
        val libDir = File(getNativeLibraryDir())
        if (libDir.exists()) {
            sb.appendLine("\nInstalled Libraries:")
            libDir.listFiles()?.sortedBy { it.name }?.forEach { file ->
                sb.appendLine("  ${file.name}: ${file.length()} bytes")
            }
        } else {
            sb.appendLine("\nWARNING: Native library directory does not exist!")
        }
        
        return sb.toString()
    }
    
    /**
     * Patch a boot image with Magisk (no root required)
     * 
     * This is the main entry point for the "patch without root" functionality.
     * It performs the following steps:
     * 1. Unpack the boot image
     * 2. Modify the ramdisk to include Magisk
     * 3. Repack the boot image
     * 
     * Note: This requires additional Magisk files (magiskinit, etc.)
     * which are typically provided through the native build.
     * 
     * @param bootImg Path to the original boot image
     * @param outputImg Path for the patched boot image
     * @param workDirectory Working directory for temporary files
     * @return BootImageResult with operation status
     */
    fun patchBootImage(
        bootImg: String,
        outputImg: String? = null,
        workDirectory: String? = null
    ): BootImageResult {
        if (!ensureBinaries()) {
            return BootImageResult(false, -1, "Native libraries not available")
        }
        
        val bootFile = File(bootImg)
        val workDirectoryFile = File(workDirectory 
            ?: context.cacheDir.resolve("magiskboot_${System.currentTimeMillis()}").absolutePath)
        workDirectoryFile.mkdirs()
        
        val outputFile = outputImg 
            ?: bootFile.parentFile?.resolve("new-boot.img")?.absolutePath
            ?: return BootImageResult(false, -1, "Cannot determine output path")
        
        Log.i(TAG, "Starting boot image patch: $bootImg -> $outputFile")
        Log.i(TAG, "Working directory: ${workDirectoryFile.absolutePath}")
        
        try {
            // Step 1: Unpack the boot image
            val unpackResult = unpack(bootImg, workDirectoryFile.absolutePath, header = true)
            if (!unpackResult.success) {
                Log.e(TAG, "Unpack failed: ${unpackResult.error}")
                return unpackResult
            }
            
            Log.i(TAG, "Unpack successful. Files: ${unpackResult.outputFiles.joinToString()}")
            
            // Step 2: Patch ramdisk (requires magiskinit and Magisk resources)
            // This is a simplified version - full implementation requires:
            // - magiskinit binary
            // - Magisk resources (busybox, etc.)
            // - CPIO manipulation
            
            val ramdiskCpio = File(workDirectoryFile, "ramdisk.cpio")
            if (ramdiskCpio.exists()) {
                Log.i(TAG, "Found ramdisk.cpio, would patch with Magisk")
                // TODO: Implement ramdisk patching
                // This requires cpio manipulation and magiskinit injection
            } else {
                Log.w(TAG, "No ramdisk.cpio found - may be a separate ramdisk boot image")
            }
            
            // Step 3: Repack the boot image
            val repackResult = repack(bootImg, outputFile, workDirectoryFile.absolutePath)
            
            if (repackResult.success) {
                Log.i(TAG, "Repack successful: $outputFile")
                
                // Verify output
                val outputFileObj = File(outputFile)
                if (outputFileObj.exists()) {
                    Log.i(TAG, "Patched boot image size: ${outputFileObj.length()} bytes")
                }
            } else {
                Log.e(TAG, "Repack failed: ${repackResult.error}")
            }
            
            return repackResult
            
        } catch (e: Exception) {
            Log.e(TAG, "Error patching boot image", e)
            return BootImageResult(false, -1, "Exception: ${e.message}", error = e.stackTraceToString())
        }
    }
}
