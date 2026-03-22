package com.magiskube.magisk.webui

import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat

/**
 * Provides Material You (Monet) colors for WebUI styling
 * Based on APatch's MonetColorsProvider design
 */
object MonetColorsProvider {
    
    /**
     * Generate CSS variables from the current theme colors
     */
    fun getColorsCss(context: Context): String {
        val colors = getThemeColors(context)
        val sb = StringBuilder()
        
        sb.append(":root {\n")
        
        // Primary colors
        sb.append("  --primary: ${colors.primary};\n")
        sb.append("  --primary-dark: ${colors.primaryDark};\n")
        sb.append("  --on-primary: ${colors.onPrimary};\n")
        sb.append("  --primary-container: ${colors.primaryContainer};\n")
        sb.append("  --on-primary-container: ${colors.onPrimaryContainer};\n")
        
        // Secondary colors
        sb.append("  --secondary: ${colors.secondary};\n")
        sb.append("  --on-secondary: ${colors.onSecondary};\n")
        sb.append("  --secondary-container: ${colors.secondaryContainer};\n")
        sb.append("  --on-secondary-container: ${colors.onSecondaryContainer};\n")
        
        // Tertiary colors
        sb.append("  --tertiary: ${colors.tertiary};\n")
        sb.append("  --on-tertiary: ${colors.onTertiary};\n")
        
        // Surface colors
        sb.append("  --surface: ${colors.surface};\n")
        sb.append("  --on-surface: ${colors.onSurface};\n")
        sb.append("  --surface-variant: ${colors.surfaceVariant};\n")
        sb.append("  --on-surface-variant: ${colors.onSurfaceVariant};\n")
        
        // Background colors
        sb.append("  --background: ${colors.background};\n")
        sb.append("  --on-background: ${colors.onBackground};\n")
        
        // Error colors
        sb.append("  --error: ${colors.error};\n")
        sb.append("  --on-error: ${colors.onError};\n")
        
        // Outline
        sb.append("  --outline: ${colors.outline};\n")
        
        // Status bar and navigation bar
        sb.append("  --status-bar-color: ${colors.surface};\n")
        sb.append("  --navigation-bar-color: ${colors.surface};\n")
        
        sb.append("}\n")
        
        return sb.toString()
    }
    
    /**
     * Get theme colors as hex strings
     */
    private fun getThemeColors(context: Context): ThemeColors {
        // Try to get Material 3 colors from the context theme
        // Fall back to default colors if not available
        
        return try {
            // Try to resolve dynamic colors (Android 12+)
            ThemeColors(
                primary = colorToHex(resolveColorAttr(context, android.R.attr.colorPrimary, Color.BLUE)),
                primaryDark = colorToHex(resolveColorAttr(context, android.R.attr.colorPrimaryDark, Color.BLUE)),
                onPrimary = colorToHex(resolveColorAttr(context, android.R.attr.colorControlNormal, Color.WHITE)),
                primaryContainer = colorToHex(resolveColorAttr(context, android.R.attr.colorControlHighlight, Color.BLUE)),
                onPrimaryContainer = colorToHex(resolveColorAttr(context, android.R.attr.textColorPrimary, Color.BLACK)),
                
                secondary = colorToHex(resolveColorAttr(context, android.R.attr.colorSecondary, Color.GRAY)),
                onSecondary = colorToHex(resolveColorAttr(context, android.R.attr.colorControlNormal, Color.WHITE)),
                secondaryContainer = colorToHex(resolveColorAttr(context, android.R.attr.colorControlHighlight, Color.GRAY)),
                onSecondaryContainer = colorToHex(resolveColorAttr(context, android.R.attr.textColorPrimary, Color.BLACK)),
                
                tertiary = colorToHex(resolveColorAttr(context, android.R.attr.colorAccent, Color.BLUE)),
                onTertiary = colorToHex(resolveColorAttr(context, android.R.attr.colorControlNormal, Color.WHITE)),
                
                surface = colorToHex(resolveColorAttr(context, android.R.attr.windowBackground, Color.WHITE)),
                onSurface = colorToHex(resolveColorAttr(context, android.R.attr.textColorPrimary, Color.BLACK)),
                surfaceVariant = colorToHex(resolveColorAttr(context, android.R.attr.colorBackground, Color.LTGRAY)),
                onSurfaceVariant = colorToHex(resolveColorAttr(context, android.R.attr.textColorSecondary, Color.DKGRAY)),
                
                background = colorToHex(resolveColorAttr(context, android.R.attr.colorBackground, Color.WHITE)),
                onBackground = colorToHex(resolveColorAttr(context, android.R.attr.textColorPrimary, Color.BLACK)),
                
                error = colorToHex(resolveColorAttr(context, android.R.attr.colorError, Color.RED)),
                onError = colorToHex(resolveColorAttr(context, android.R.attr.colorControlNormal, Color.WHITE)),
                
                outline = colorToHex(resolveColorAttr(context, android.R.attr.colorControlNormal, Color.GRAY))
            )
        } catch (e: Exception) {
            // Default colors
            ThemeColors(
                primary = "#2196F3",
                primaryDark = "#1976D2",
                onPrimary = "#FFFFFF",
                primaryContainer = "#BBDEFB",
                onPrimaryContainer = "#0D47A1",
                
                secondary = "#607D8B",
                onSecondary = "#FFFFFF",
                secondaryContainer = "#CFD8DC",
                onSecondaryContainer = "#37474F",
                
                tertiary = "#03A9F4",
                onTertiary = "#FFFFFF",
                
                surface = "#FFFFFF",
                onSurface = "#000000",
                surfaceVariant = "#F5F5F5",
                onSurfaceVariant = "#757575",
                
                background = "#FFFFFF",
                onBackground = "#000000",
                
                error = "#F44336",
                onError = "#FFFFFF",
                
                outline = "#9E9E9E"
            )
        }
    }
    
    @ColorInt
    private fun resolveColorAttr(context: Context, attr: Int, @ColorInt defaultColor: Int): Int {
        val typedValue = android.util.TypedValue()
        return if (context.theme.resolveAttribute(attr, typedValue, true)) {
            try {
                ContextCompat.getColor(context, typedValue.resourceId)
            } catch (e: Exception) {
                defaultColor
            }
        } else {
            defaultColor
        }
    }
    
    /**
     * Convert color int to hex string
     */
    private fun colorToHex(@ColorInt color: Int): String {
        return "#${Integer.toHexString(color).substring(2).uppercase()}"
    }
    
    /**
     * Data class to hold theme colors as hex strings
     */
    private data class ThemeColors(
        val primary: String,
        val primaryDark: String,
        val onPrimary: String,
        val primaryContainer: String,
        val onPrimaryContainer: String,
        
        val secondary: String,
        val onSecondary: String,
        val secondaryContainer: String,
        val onSecondaryContainer: String,
        
        val tertiary: String,
        val onTertiary: String,
        
        val surface: String,
        val onSurface: String,
        val surfaceVariant: String,
        val onSurfaceVariant: String,
        
        val background: String,
        val onBackground: String,
        
        val error: String,
        val onError: String,
        
        val outline: String
    )
}
