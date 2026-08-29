package com.topjohnwu.magisk.ui.home

import android.util.Base64
import com.topjohnwu.magisk.core.Config
import java.util.UUID

/** A user-created Start tile; groupMembers may contain one to nine packages. */
data class MetroCustomTile(
    val id: String,
    val packageName: String,
    val title: String,
    val ticker: String,
    val color: String,
    val groupMembers: List<String> = listOf(packageName),
)

/** Safe, compact preference serialization for arbitrary user-entered text. */
object MetroCustomTiles {
    private const val SEP = "|"

    fun load(): List<MetroCustomTile> = Config.metroCustomTiles
        .split(';')
        .asSequence()
        .filter(String::isNotBlank)
        .mapNotNull { record ->
            val fields = record.split(SEP)
            if (fields.size != 6) return@mapNotNull null
            val rawId = fields[0]
            // Migrate old buggy saves where the id was also Base64-encoded (and possibly
            // double-encoded after repeated saves). New saves keep the id plain.
            val id = decodeId(rawId) ?: return@mapNotNull null
            if (id.isBlank()) return@mapNotNull null
            val decoded = fields.drop(1).map(::decode)
            val members = decoded.getOrNull(4)?.split(',').orEmpty().filter(String::isNotBlank)
            if (decoded.any { it == null } || members.isEmpty()) null
            else MetroCustomTile(
                id = id,
                packageName = decoded[0]!!,
                title = decoded[1]!!,
                ticker = decoded[2]!!,
                color = decoded[3]!!,
                groupMembers = members,
            )
        }
        .toList()

    private fun decodeId(raw: String): String? {
        if (raw.startsWith("custom_")) return raw
        var cur = raw
        repeat(4) {
            val decoded = decode(cur) ?: return null
            if (decoded.startsWith("custom_")) return decoded
            cur = decoded
        }
        return null
    }

    fun add(
        packageName: String,
        title: String,
        ticker: String,
        color: String,
        groupMembers: List<String> = listOf(packageName),
    ): MetroCustomTile {
        val tile = MetroCustomTile(
            id = "custom_${UUID.randomUUID()}",
            packageName = packageName,
            title = title,
            ticker = ticker,
            color = color,
            groupMembers = groupMembers.take(9),
        )
        save(load() + tile)
        return tile
    }

    fun remove(id: String) {
        save(load().filterNot { it.id == id })
    }

    private fun save(tiles: List<MetroCustomTile>) {
        Config.metroCustomTiles = tiles.joinToString(";") { tile ->
            val encoded = listOf(
                tile.packageName,
                tile.title,
                tile.ticker,
                tile.color,
                tile.groupMembers.joinToString(","),
            ).map(::encode)
            tile.id + SEP + encoded.joinToString(SEP)
        }
        MetroUiState.invalidate()
    }

    private fun encode(value: String): String = Base64.encodeToString(
        value.toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP,
    )

    private fun decode(value: String): String? = runCatching {
        String(Base64.decode(value, Base64.URL_SAFE), Charsets.UTF_8)
    }.getOrNull()
}