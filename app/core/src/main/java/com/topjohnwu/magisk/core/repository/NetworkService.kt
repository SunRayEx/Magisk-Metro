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

    /** The Metro manager is distributed from the stable `release` tag, not GitHub's latest tag. */
    private suspend fun fetchMetroUpdate(): UpdateInfo = api.fetchReleaseByTag("release").asMetroInfo()

    private fun Release.asMetroInfo(): UpdateInfo {
        val asset = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: return UpdateInfo()
        val versionCode = asset.name.substringBeforeLast('.')
            .takeLastWhile(Char::isDigit)
            .toIntOrNull()
            ?: return UpdateInfo()
        return UpdateInfo(
            version = name.ifBlank { asset.name.substringBeforeLast('.') },
            versionCode = versionCode,
            link = asset.url,
            note = "## $name\n\n$body"
        )
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
