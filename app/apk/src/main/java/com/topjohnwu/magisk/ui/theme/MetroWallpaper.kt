package com.topjohnwu.magisk.ui.theme

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.ui.metro.LocalMetroRootSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Image wallpaper for the Metro theme.
 *
 * The picked image (a persisted SAF Uri) is decoded once off the main thread at a sampled
 * size, and two artifacts are cached in memory: the full image for page backgrounds and a
 * pre-blurred half-resolution copy for the tiles' glass fill. A dominant-colour histogram
 * derives the accent (and its readable foreground) that recolors the whole UI while the
 * wallpaper is active. Every failure mode (missing file, revoked grant, decode error)
 * simply leaves [active] false so the UI falls back to the regular theme.
 */
class MetroWallpaperArt(
    val image: androidx.compose.ui.graphics.ImageBitmap,
    val blurred: androidx.compose.ui.graphics.ImageBitmap,
    val accent: Color,
    val onAccent: Color,
    val accentArgb: Int,
)

object MetroWallpaper {

    var art by mutableStateOf<MetroWallpaperArt?>(null)
        private set
    var active by mutableStateOf(false)
        private set

    /** 0..100 persisted blur preference, mirrored for snapshot reads. */
    var blurLevel by mutableStateOf(0)
        private set

    /** True when the configured image could not be loaded; the settings page surfaces it. */
    var loadFailed by mutableStateOf(false)
        private set

    private val versionState = mutableIntStateOf(0)

    /** Bumped by [invalidate] so composition observers re-run [ensureLoaded]. */
    val version: Int get() = versionState.intValue

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var loadedKey: String? = null

    fun invalidate() {
        versionState.intValue++
    }

    /** Idempotent; called from composition whenever [version] or the UI state changes. */
    fun ensureLoaded() {
        blurLevel = Config.metroWallpaperBlur.coerceIn(0, 100)
        val uri = Config.metroWallpaperUri
        if (!Config.metroWallpaperEnabled || uri.isBlank()) {
            job?.cancel()
            loadedKey = null
            art = null
            active = false
            loadFailed = false
            return
        }
        // The slider commits a new blur level only when the drag finishes; each committed
        // level gets its own decoded/blurred bitmap cache entry.
        val key = "$uri#$blurLevel"
        if (key == loadedKey) return
        loadedKey = key
        job?.cancel()
        loadFailed = false
        job = scope.launch {
            val decoded = runCatching { decode(AppContext, uri, blurLevel) }.getOrNull()
            withContext(Dispatchers.Main) {
                art = decoded
                active = decoded != null
                loadFailed = decoded == null
                if (decoded != null && Config.metroWallpaperAccent != decoded.accentArgb) {
                    // Cache the extracted accent so the palette is stable across restarts.
                    Config.metroWallpaperAccent = decoded.accentArgb
                }
            }
        }
    }
}

/** Blur radius in px applied to the half-resolution tile bitmap. */
private fun blurRadiusPx(blurLevel: Int): Int = (blurLevel / 100f * 16f).roundToInt()

/**
 * Decodes the picked image at a sampled size. [ImageDecoder] is the primary path on API 28+
 * because it understands every platform format (HEIC/HEIF included, which plain
 * BitmapFactory can choke on depending on the OEM) and reports real errors; the classic
 * bounds-probe + decodeStream path remains for older devices. The result must be a
 * SOFTWARE bitmap: the blur pass reads pixels directly.
 */
private fun decodeBase(resolver: ContentResolver, uri: Uri): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return runCatching {
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val maxSide = maxOf(info.size.width, info.size.height)
                var sample = 1
                while (maxSide / (sample * 2) >= 1600) sample *= 2
                decoder.setTargetSampleSize(sample)
            }
        }.onFailure { Timber.e(it, "metro wallpaper ImageDecoder failed") }
            .getOrNull()
            ?: decodeViaFactory(resolver, uri)
    }
    return decodeViaFactory(resolver, uri)
}

private fun decodeViaFactory(resolver: ContentResolver, uri: Uri): Bitmap? {
    // decodeStream returns null with inJustDecodeBounds (it only fills the options), so
    // the probe's job is purely to discover the bounds.
    val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, probe) } ?: return null
    if (probe.outWidth <= 0 || probe.outHeight <= 0) return null
    var sample = 1
    while (maxOf(probe.outWidth, probe.outHeight) / (sample * 2) >= 1600) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}

private fun decode(context: Context, uriString: String, blurLevel: Int): MetroWallpaperArt? {
    val uri = uriString.toUri()
    val resolver = context.contentResolver
    val base = decodeBase(resolver, uri) ?: return null

    // Pre-blurred half-res copy: tiles sample from it via a BitmapPainter crop, so no
    // per-tile RenderEffect and no repeated bitmap allocations.
    val halfW = (base.width * 0.5f).roundToInt().coerceAtLeast(1)
    val halfH = (base.height * 0.5f).roundToInt().coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(base, halfW, halfH, true)
    val radius = blurRadiusPx(blurLevel)
    val blurred = if (radius >= 1) {
        val px = IntArray(halfW * halfH)
        small.getPixels(px, 0, halfW, 0, 0, halfW, halfH)
        gaussianApproxBlur(px, halfW, halfH, radius)
        Bitmap.createBitmap(px, halfW, halfH, Bitmap.Config.ARGB_8888)
    } else {
        small
    }

    val (accent, onAccent) = extractAccent(base)
    return MetroWallpaperArt(
        image = base.asImageBitmap(),
        blurred = blurred.asImageBitmap(),
        accent = Color(accent),
        onAccent = Color(onAccent),
        accentArgb = accent,
    )
}

/** Three box-blur passes: the standard cheap Gaussian approximation, run off the main thread. */
private fun gaussianApproxBlur(px: IntArray, w: Int, h: Int, radius: Int) {
    if (w <= 0 || h <= 0 || radius < 1) return
    val temp = IntArray(px.size)
    repeat(3) {
        boxBlurHorizontal(px, temp, w, h, radius)
        boxBlurVertical(temp, px, w, h, radius)
    }
}

private fun boxBlurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
    val window = 2 * radius + 1
    for (row in 0 until h) {
        val off = row * w
        var rs = 0
        var gs = 0
        var bs = 0
        for (i in -radius..radius) {
            val p = src[off + i.coerceIn(0, w - 1)]
            rs += (p shr 16) and 0xFF
            gs += (p shr 8) and 0xFF
            bs += p and 0xFF
        }
        for (x in 0 until w) {
            dst[off + x] = AndroidColor.rgb(rs / window, gs / window, bs / window)
            val pOut = src[off + (x - radius).coerceIn(0, w - 1)]
            val pIn = src[off + (x + radius + 1).coerceIn(0, w - 1)]
            rs += ((pIn shr 16) and 0xFF) - ((pOut shr 16) and 0xFF)
            gs += ((pIn shr 8) and 0xFF) - ((pOut shr 8) and 0xFF)
            bs += (pIn and 0xFF) - (pOut and 0xFF)
        }
    }
}

private fun boxBlurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
    val window = 2 * radius + 1
    for (col in 0 until w) {
        var rs = 0
        var gs = 0
        var bs = 0
        for (i in -radius..radius) {
            val p = src[i.coerceIn(0, h - 1) * w + col]
            rs += (p shr 16) and 0xFF
            gs += (p shr 8) and 0xFF
            bs += p and 0xFF
        }
        for (y in 0 until h) {
            dst[y * w + col] = AndroidColor.rgb(rs / window, gs / window, bs / window)
            val pOut = src[(y - radius).coerceIn(0, h - 1) * w + col]
            val pIn = src[(y + radius + 1).coerceIn(0, h - 1) * w + col]
            rs += ((pIn shr 16) and 0xFF) - ((pOut shr 16) and 0xFF)
            gs += ((pIn shr 8) and 0xFF) - ((pOut shr 8) and 0xFF)
            bs += (pIn and 0xFF) - (pOut and 0xFF)
        }
    }
}

private const val ACCENT_FALLBACK = 0xFF2D7DD2.toInt()

/**
 * Dominant-colour extraction: a 4-bit-per-channel histogram over a 64x64 thumbnail,
 * scored by frequency * saturation * mid-luminance fitness, then normalized into a
 * readable accent range. Returns (accent, onAccent) with black/white foreground chosen
 * by luminance, matching [MetroColors.readableOn].
 */
private fun extractAccent(bmp: Bitmap): Pair<Int, Int> {
    val thumb = Bitmap.createScaledBitmap(bmp, 64, 64, true)
    val px = IntArray(64 * 64)
    thumb.getPixels(px, 0, 64, 0, 0, 64, 64)
    thumb.recycle()

    // key -> [count, rSum, gSum, bSum]
    val buckets = HashMap<Int, LongArray>(1024)
    for (p in px) {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
        val agg = buckets.getOrPut(key) { LongArray(4) }
        agg[0]++
        agg[1] += r
        agg[2] += g
        agg[3] += b
    }

    var best: LongArray? = null
    var bestScore = -1.0
    for (agg in buckets.values) {
        val count = agg[0]
        val r = (agg[1] / count).toInt()
        val g = (agg[2] / count).toInt()
        val b = (agg[3] / count).toInt()
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val sat = if (mx == 0) 0f else (mx - mn) / mx.toFloat()
        val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        val lumFit = (1f - kotlin.math.abs(lum - 0.45f) * 1.6f).coerceAtLeast(0.12f)
        val score = count * (0.4 + sat) * lumFit
        if (score > bestScore) {
            bestScore = score
            best = agg
        }
    }
    val agg = best ?: buckets.maxByOrNull { it.value[0] }?.value
        ?: return ACCENT_FALLBACK to AndroidColor.WHITE

    val count = agg[0]
    val r = (agg[1] / count).toInt()
    val g = (agg[2] / count).toInt()
    val b = (agg[3] / count).toInt()
    val hsv = FloatArray(3)
    AndroidColor.RGBToHSV(r, g, b, hsv)
    hsv[1] = hsv[1].coerceIn(0.32f, 0.85f)
    hsv[2] = hsv[2].coerceIn(0.42f, 0.74f)
    val accent = AndroidColor.HSVToColor(hsv)
    val on = if (ColorUtils.calculateLuminance(accent) > 0.45) AndroidColor.BLACK else AndroidColor.WHITE
    return accent to on
}

/**
 * Full-window wallpaper drawn behind NavDisplay: with it enabled, every screen's own
 * background becomes translucent, so home and secondary pages share one continuous
 * backdrop with no per-page image wiring.
 */
@Composable
fun MetroWallpaperBackdrop(
    modifier: Modifier = Modifier,
    secondaryPage: Boolean = false,
) {
    if (!MetroWallpaper.active) return
    val art = MetroWallpaper.art ?: return
    val dark = isSystemInDarkTheme()
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            bitmap = art.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Light readability veil: the photo itself is the background; only enough scrim
        // for text contrast, heavier in dark mode where surfaces are dark.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (dark) 0.28f else 0.06f)),
        )
        if (secondaryPage) {
            // Keep the veil above the image and below NavDisplay. This also covers the
            // status-bar area and survives opaque Scaffold surfaces in destination pages.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = if (dark) 0.18f else 0.76f)),
            )
        }
    }
}

/**
 * Translucent page background: solid when no wallpaper is active, a light tint over the
 * shared backdrop otherwise — the photo must remain clearly visible as THE background.
 * Replaces plain `.background(colorScheme.background)`.
 */
@Composable
fun Modifier.metroScreenBackground(): Modifier {
    val wallpaper = MetroWallpaper.active
    val dark = isSystemInDarkTheme()
    val background = MaterialTheme.colorScheme.background
    if (!wallpaper) return this.background(background)
    // Readability treatment over the photo, shared by home and every secondary page:
    // a light veil in light mode (white scrim) or a dark veil in dark mode, topped with
    // the scheme colour — the wallpaper stays visible but text always reads.
    // Draw both layers in one pass. This keeps the veil behind page content while
    // avoiding a later opaque background modifier covering the white layer.
    return this.drawBehind {
        drawRect(Color.White.copy(alpha = if (dark) 0.18f else 0.76f))
        drawRect(background.copy(alpha = if (dark) 0.28f else 0.18f))
    }
}

/** Tile fill over the blurred wallpaper: a neutral dark veil for text contrast that
 *  recedes slightly as blur increases, plus a whisper of the tile's own accent so the
 *  board keeps a hint of colour identity. The photo stays clearly visible. */
private fun glassVeilAlpha(blurLevel: Int): Float = 0.34f + 0.16f * (1f - blurLevel / 100f)

/**
 * Frosted-glass fill for a Start tile: samples the region of the pre-blurred wallpaper
 * that sits EXACTLY behind the tile. The crop math mirrors what ContentScale.Crop does
 * for the full-screen backdrop (uniform scale to cover, centred), so the glass lines up
 * pixel-for-pixel with the background instead of stretching.
 */
@Composable
fun MetroTileGlass(color: Color, tileRect: Rect?) {
    val art = MetroWallpaper.art
    if (!MetroWallpaper.active || art == null) return
    val rootSize = LocalMetroRootSize.current
    val rect = tileRect ?: return
    if (rootSize.width == 0 || rootSize.height == 0) return

    val img = art.blurred
    // ContentScale.Crop mapping: the image is scaled uniformly to COVER the root, centred.
    val coverScale = max(
        rootSize.width.toFloat() / img.width,
        rootSize.height.toFloat() / img.height,
    )
    val dispW = img.width * coverScale
    val dispH = img.height * coverScale
    val offX = (rootSize.width - dispW) / 2f
    val offY = (rootSize.height - dispH) / 2f
    // Root px -> image px, then clamp the crop inside the bitmap.
    val left = ((rect.left - offX) / coverScale).roundToInt().coerceIn(0, img.width - 1)
    val top = ((rect.top - offY) / coverScale).roundToInt().coerceIn(0, img.height - 1)
    val w = (rect.width / coverScale).roundToInt().coerceIn(1, img.width - left)
    val h = (rect.height / coverScale).roundToInt().coerceIn(1, img.height - top)
    val painter = remember(img, left, top, w, h) {
        BitmapPainter(img, IntOffset(left, top), IntSize(w, h))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        // Neutral dark veil for white-text contrast…
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = glassVeilAlpha(MetroWallpaper.blurLevel))),
        )
        // …with only a whisper of the tile's accent on top.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color.copy(alpha = 0.18f)),
        )
    }
}
