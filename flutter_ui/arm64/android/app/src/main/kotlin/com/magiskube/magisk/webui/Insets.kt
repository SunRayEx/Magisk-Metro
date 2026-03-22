package com.magiskube.magisk.webui

/**
 * Represents window insets for WebUI styling
 * Based on APatch's Insets design
 */
data class Insets(
    val top: Int,
    val bottom: Int,
    val left: Int,
    val right: Int
) {
    /**
     * Generate CSS for the insets
     */
    fun getCss(): String {
        return """
            :root {
                --window-inset-top: ${top}px;
                --window-inset-bottom: ${bottom}px;
                --window-inset-left: ${left}px;
                --window-inset-right: ${right}px;
            }
            
            body {
                padding-top: var(--window-inset-top);
                padding-bottom: var(--window-inset-bottom);
                padding-left: var(--window-inset-left);
                padding-right: var(--window-inset-right);
            }
        """.trimIndent()
    }

    companion object {
        @JvmField
        val NONE = Insets(0, 0, 0, 0)
    }
}
