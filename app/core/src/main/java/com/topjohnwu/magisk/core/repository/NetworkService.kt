package com.topjohnwu.magisk.core.repository

import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.data.GithubApiServices
import com.topjohnwu.magisk.core.data.RawUrl
import com.topjohnwu.magisk.core.model.Release
import com.topjohnwu.magisk.core.model.UpdateInfo
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException

class NetworkService(
    private val raw: RawUrl,
    private val api: GithubApiServices,
) {
    suspend fun fetchUpdate() = safe {
        fetchMetroUpdate()
    }

    suspend fun fetchUpdate(version: Int) = safe {
        fetchMetroUpdate().takeIf { it.versionCode == version } ?: UpdateInfo()
    }

    /**
     * The Metro manager is published under the project's own rolling `release` tag.
     *
     * The published APK carries no version in its file name, so [asMetroInfo] falls back to the
     * release's last update time to detect a re-uploaded build. If the tag is ever missing we
     * fall back to the newest published release.
     */
    private suspend fun fetchMetroUpdate(): UpdateInfo {
        val release = runCatching { api.fetchReleaseByTag("release") }
            .recoverCatching { api.fetchLatestRelease() }
            .getOrNull()
            ?: return UpdateInfo()
        return release.asMetroInfo()
    }

    private fun Release.asMetroInfo(): UpdateInfo {
        // Prefer a release-flavoured APK; debug builds are only published for ad-hoc testing.
        val asset = assets.firstOrNull {
            it.name.endsWith(".apk", ignoreCase = true) &&
                !it.name.contains("debug", ignoreCase = true)
        } ?: assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: return UpdateInfo()

        // Resolve a version code in priority order: a versioned tag (v31.0 -> 31000), trailing
        // digits in the asset name (Magisk-Metro-31001.apk), and finally the release's last
        // update time as epoch seconds. The rolling release re-uploads the APK, which bumps
        // `updated_at`, so a newer build always reads as newer than the installed one.
        val code = tag.versionCodeOrNull()
            ?: asset.name.substringBeforeLast('.').takeLastWhile(Char::isDigit).toIntOrNull()
            ?: updatedTime.epochSecond.toInt()

        return UpdateInfo(
            version = name.ifBlank { tag },
            versionCode = code,
            link = asset.url,
            note = "## $name\n\n$body"
        )
    }

    /** "v31.0" -> 31000, "31001" -> 31001, anything else -> null. */
    private fun String.versionCodeOrNull(): Int? {
        val trimmed = trimStart('v', 'V')
        trimmed.toIntOrNull()?.let { return it }
        if ('.' !in trimmed) return null
        val major = trimmed.substringBefore('.').toIntOrNull() ?: return null
        val minor = trimmed.substringAfter('.')
            .takeWhile(Char::isDigit)
            .toIntOrNull() ?: 0
        return major * 1000 + minor
    }

    private suspend inline fun <T> safe(factory: suspend () -> T): T? {
        return try {
            if (Info.isConnected.value == true)
                factory()
            else
                null
        } catch (e: Exception) {
            Timber.e(e)
            null
        }
    }

    private inline fun <T> wrap(factory: () -> T): T {
        return try {
            factory()
        } catch (e: HttpException) {
            throw IOException(e)
        }
    }

    // Fetch files
    suspend fun fetchFile(url: String) = wrap { raw.fetchFile(url) }
    suspend fun fetchString(url: String) = wrap { raw.fetchString(url) }
    suspend fun fetchModuleJson(url: String) = wrap { raw.fetchModuleJson(url) }
}
