package com.magiskube.magisk.webui;

import android.webkit.MimeTypeMap;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Utility class for MIME type detection
 */
public class MimeUtil {

    private static final Map<String, String> ADDITIONAL_MIME_TYPES = new HashMap<>();

    static {
        // JavaScript
        ADDITIONAL_MIME_TYPES.put("js", "application/javascript");
        ADDITIONAL_MIME_TYPES.put("mjs", "application/javascript");
        
        // JSON
        ADDITIONAL_MIME_TYPES.put("json", "application/json");
        ADDITIONAL_MIME_TYPES.put("map", "application/json");
        
        // WebAssembly
        ADDITIONAL_MIME_TYPES.put("wasm", "application/wasm");
        
        // Fonts
        ADDITIONAL_MIME_TYPES.put("woff", "font/woff");
        ADDITIONAL_MIME_TYPES.put("woff2", "font/woff2");
        ADDITIONAL_MIME_TYPES.put("ttf", "font/ttf");
        ADDITIONAL_MIME_TYPES.put("otf", "font/otf");
        ADDITIONAL_MIME_TYPES.put("eot", "application/vnd.ms-fontobject");
        
        // Images
        ADDITIONAL_MIME_TYPES.put("svg", "image/svg+xml");
        ADDITIONAL_MIME_TYPES.put("svgz", "image/svg+xml");
        ADDITIONAL_MIME_TYPES.put("webp", "image/webp");
        ADDITIONAL_MIME_TYPES.put("ico", "image/x-icon");
        ADDITIONAL_MIME_TYPES.put("avif", "image/avif");
        
        // Audio/Video
        ADDITIONAL_MIME_TYPES.put("mp3", "audio/mpeg");
        ADDITIONAL_MIME_TYPES.put("ogg", "audio/ogg");
        ADDITIONAL_MIME_TYPES.put("oga", "audio/ogg");
        ADDITIONAL_MIME_TYPES.put("m4a", "audio/mp4");
        ADDITIONAL_MIME_TYPES.put("webm", "video/webm");
        ADDITIONAL_MIME_TYPES.put("mp4", "video/mp4");
        
        // Documents
        ADDITIONAL_MIME_TYPES.put("pdf", "application/pdf");
        ADDITIONAL_MIME_TYPES.put("xml", "application/xml");
        ADDITIONAL_MIME_TYPES.put("zip", "application/zip");
        ADDITIONAL_MIME_TYPES.put("gz", "application/gzip");
        ADDITIONAL_MIME_TYPES.put("tar", "application/x-tar");
        
        // Web components
        ADDITIONAL_MIME_TYPES.put("html", "text/html");
        ADDITIONAL_MIME_TYPES.put("htm", "text/html");
        ADDITIONAL_MIME_TYPES.put("css", "text/css");
        ADDITIONAL_MIME_TYPES.put("txt", "text/plain");
    }

    /**
     * Get MIME type from file name
     *
     * @param fileName the file name or path
     * @return MIME type string or null if unknown
     */
    @Nullable
    public static String getMimeFromFileName(@Nullable String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }

        // Extract extension
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1 || lastDot == fileName.length() - 1) {
            return null;
        }

        String extension = fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT);
        
        // Check our additional map first
        String mimeType = ADDITIONAL_MIME_TYPES.get(extension);
        if (mimeType != null) {
            return mimeType;
        }

        // Fall back to Android's MimeTypeMap
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        mimeType = mimeTypeMap.getMimeTypeFromExtension(extension);
        
        return mimeType;
    }
}
