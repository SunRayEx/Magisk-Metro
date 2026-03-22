package com.magiskube.magisk.webui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Utility class for loading app icons
 * Based on APatch's AppIconUtil design
 */
object AppIconUtil {
    private const val TAG = "AppIconUtil"
    
    /**
     * Load app icon synchronously
     * @param context Context
     * @param packageName Package name of the app
     * @param size Desired size in pixels
     * @return Bitmap of the app icon, or null if not found
     */
    fun loadAppIconSync(context: Context, packageName: String, size: Int = 128): Bitmap? {
        return try {
            val packageManager = context.packageManager
            val drawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                ).loadIcon(packageManager)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0).loadIcon(packageManager)
            }
            
            drawableToBitmap(drawable, size)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Package not found: $packageName")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error loading app icon for $packageName", e)
            null
        }
    }
    
    /**
     * Load app icon asynchronously
     * @param context Context
     * @param packageName Package name of the app
     * @param size Desired size in pixels
     * @return Bitmap of the app icon, or null if not found
     */
    suspend fun loadAppIcon(context: Context, packageName: String, size: Int = 128): Bitmap? {
        return withContext(Dispatchers.IO) {
            loadAppIconSync(context, packageName, size)
        }
    }
    
    /**
     * Convert drawable to bitmap
     */
    private fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap {
        if (drawable is BitmapDrawable) {
            val bitmap = drawable.bitmap
            if (bitmap.width == size && bitmap.height == size) {
                return bitmap
            }
            return Bitmap.createScaledBitmap(bitmap, size, size, true)
        }
        
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
    
    /**
     * Convert bitmap to PNG byte array
     */
    fun bitmapToPng(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
    
    /**
     * Get app icon as base64 string
     */
    fun getAppIconBase64(context: Context, packageName: String, size: Int = 128): String? {
        val bitmap = loadAppIconSync(context, packageName, size) ?: return null
        val pngBytes = bitmapToPng(bitmap)
        return android.util.Base64.encodeToString(pngBytes, android.util.Base64.NO_WRAP)
    }
    
    /**
     * Get app icon data URI
     */
    fun getAppIconDataUri(context: Context, packageName: String, size: Int = 128): String? {
        val base64 = getAppIconBase64(context, packageName, size) ?: return null
        return "data:image/png;base64,$base64"
    }
}
