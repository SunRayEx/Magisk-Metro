package com.magiskube.magisk.webui;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.webkit.WebResourceResponse;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * Handler class to open files from file system by root access
 * For serving module webroot files through WebViewAssetLoader
 * 
 * Based on APatch's SuFilePathHandler design
 * Supports internal CSS resources and app icon serving
 */
public final class RemoteFsPathHandler implements WebViewAssetLoader.PathHandler {
    private static final String TAG = "RemoteFsPathHandler";

    /**
     * Default value to be used as MIME type if guessing MIME type failed.
     */
    public static final String DEFAULT_MIME_TYPE = "text/plain";

    @NonNull
    private final File mDirectory;
    
    @NonNull
    private final Context mContext;
    
    @NonNull
    private final InsetsSupplier mInsetsSupplier;
    
    @NonNull
    private final OnInsetsRequestedListener mOnInsetsRequestedListener;

    /**
     * Interface to supply insets dynamically
     */
    public interface InsetsSupplier {
        @NonNull
        Insets get();
    }

    /**
     * Interface to listen for insets requests
     */
    public interface OnInsetsRequestedListener {
        void onInsetsRequested(boolean enable);
    }

    /**
     * Creates PathHandler for remote file system access via root
     *
     * @param context {@link Context} that is used to access app's internal storage.
     * @param directory the absolute path of the exposed directory (e.g., module webroot)
     * @param insetsSupplier Supplier for window insets
     * @param onInsetsRequestedListener Listener for insets requests
     */
    public RemoteFsPathHandler(@NonNull Context context, @NonNull File directory,
                               @NonNull InsetsSupplier insetsSupplier,
                               @NonNull OnInsetsRequestedListener onInsetsRequestedListener) {
        try {
            mContext = context;
            mInsetsSupplier = insetsSupplier;
            mOnInsetsRequestedListener = onInsetsRequestedListener;
            mDirectory = new File(getCanonicalDirPath(directory));
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Failed to resolve the canonical path for the given directory: "
                            + directory.getPath(), e);
        }
    }

    /**
     * Creates PathHandler for remote file system access via root (simplified constructor)
     *
     * @param context {@link Context} that is used to access app's internal storage.
     * @param directory the absolute path of the exposed directory (e.g., module webroot)
     */
    public RemoteFsPathHandler(@NonNull Context context, @NonNull File directory) {
        this(context, directory, () -> Insets.NONE, (enable) -> {});
    }

    /**
     * Opens the requested file from the exposed data directory.
     * Also handles internal resources like insets.css and colors.css
     */
    @Override
    @WorkerThread
    @NonNull
    public WebResourceResponse handle(@NonNull String path) {
        // Handle internal CSS resources
        if ("internal/insets.css".equals(path)) {
            mOnInsetsRequestedListener.onInsetsRequested(true);
            String css = mInsetsSupplier.get().getCss();
            return new WebResourceResponse(
                    "text/css",
                    "utf-8",
                    new ByteArrayInputStream(css.getBytes(StandardCharsets.UTF_8))
            );
        }
        
        if ("internal/colors.css".equals(path)) {
            String css = MonetColorsProvider.INSTANCE.getColorsCss(mContext);
            return new WebResourceResponse(
                    "text/css",
                    "utf-8",
                    new ByteArrayInputStream(css.getBytes(StandardCharsets.UTF_8))
            );
        }
        
        try {
            File file = getCanonicalFileIfChild(mDirectory, path);
            if (file != null) {
                InputStream is = openFileWithRoot(file);
                String mimeType = guessMimeType(path);
                return new WebResourceResponse(mimeType, null, is);
            } else {
                Log.e(TAG, String.format(
                        "The requested file: %s is outside the mounted directory: %s", path,
                        mDirectory));
            }
        } catch (IOException e) {
            Log.e(TAG, "Error opening the requested path: " + path, e);
        }
        return new WebResourceResponse(null, null, null);
    }

    public static String getCanonicalDirPath(@NonNull File file) throws IOException {
        String canonicalPath = file.getCanonicalPath();
        if (!canonicalPath.endsWith("/")) canonicalPath += "/";
        return canonicalPath;
    }

    public static File getCanonicalFileIfChild(@NonNull File parent, @NonNull String child)
            throws IOException {
        String parentCanonicalPath = getCanonicalDirPath(parent);
        String childCanonicalPath = new File(parent, child).getCanonicalPath();
        if (childCanonicalPath.startsWith(parentCanonicalPath)) {
            return new File(childCanonicalPath);
        }
        return null;
    }

    @NonNull
    private static InputStream handleSvgzStream(@NonNull String path,
                                                @NonNull InputStream stream) throws IOException {
        return path.endsWith(".svgz") ? new GZIPInputStream(stream) : stream;
    }

    /**
     * Open file using root shell if normal access fails
     */
    public static InputStream openFileWithRoot(@NonNull File file) throws IOException {
        String path = file.getAbsolutePath();
        
        // First try normal file access
        if (file.exists() && file.canRead()) {
            return handleSvgzStream(path, new FileInputStream(file));
        }
        
        // Use root shell to read file
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat '" + path + "'"});
            InputStream inputStream = process.getInputStream();
            InputStream errorStream = process.getErrorStream();
            
            // Read the content into a byte array since we need to close the process
            byte[] buffer = new byte[8192];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            
            inputStream.close();
            errorStream.close();
            
            // Wait for process to complete
            int exitCode = process.waitFor();
            process.destroy();
            
            if (exitCode != 0) {
                throw new IOException("Failed to read file with root, exit code: " + exitCode);
            }
            
            return handleSvgzStream(path, new ByteArrayInputStream(baos.toByteArray()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reading file", e);
        } catch (Exception e) {
            throw new IOException("Failed to read file with root access: " + path, e);
        }
    }

    /**
     * Use {@link MimeUtil#getMimeFromFileName} to guess MIME type or return the
     * {@link #DEFAULT_MIME_TYPE} if it can't guess.
     */
    @NonNull
    public static String guessMimeType(@NonNull String filePath) {
        String mimeType = MimeUtil.getMimeFromFileName(filePath);
        return mimeType == null ? DEFAULT_MIME_TYPE : mimeType;
    }
}
