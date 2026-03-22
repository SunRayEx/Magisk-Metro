package com.magiskube.magisk.webui

/**
 * Utility functions for WebUI
 * 
 * Note: Root shell execution is handled directly in WebViewInterface
 * using Java Process API, without external dependencies.
 */

/**
 * Execute a root command synchronously
 */
fun execRootCommand(command: String): Pair<Int, String> {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("su"))
        val outputStream = process.outputStream
        val inputStream = process.inputStream

        outputStream.write((command + "\n").toByteArray())
        outputStream.write("exit\n".toByteArray())
        outputStream.flush()
        outputStream.close()

        val output = inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        process.destroy()

        Pair(exitCode, output.trimEnd('\n'))
    } catch (e: Exception) {
        Pair(1, "")
    }
}

/**
 * Check if root access is available
 */
fun hasRootAccess(): Boolean {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("su"))
        val outputStream = process.outputStream
        outputStream.write("id\n".toByteArray())
        outputStream.write("exit\n".toByteArray())
        outputStream.flush()
        outputStream.close()
        val exitCode = process.waitFor()
        process.destroy()
        exitCode == 0
    } catch (e: Exception) {
        false
    }
}
