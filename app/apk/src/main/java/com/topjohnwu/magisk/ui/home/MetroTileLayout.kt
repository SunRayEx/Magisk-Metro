package com.topjohnwu.magisk.ui.home

import com.topjohnwu.magisk.core.Config

/** Persisted phone Start-screen geometry. All mutations are packed before saving, so tiles never
 * overlap even when a layout is restored on a narrower grid. */
internal data class MetroTilePlacement(
    val id: String,
    val column: Int,
    val row: Int,
    val width: Int,
    val height: Int,
)

internal object MetroTileLayout {
    const val Magisk = "magisk"
    const val Modules = "modules"
    const val Apps = "apps"
    const val Settings = "settings"
    const val Logs = "logs"
    const val Contributors = "contributors"
    const val Sponsor = "sponsor"
    const val Support = "support"
    const val Donate = "donate"

    val ids = listOf(Magisk, Modules, Apps, Settings, Logs, Contributors, Sponsor, Support, Donate)

    fun grid(): Pair<Int, Int> = when (Config.metroTileGrid) {
        1 -> 4 to 6
        2 -> 4 to 8
        else -> 3 to 6
    }

    fun load(
        columns: Int,
        rows: Int,
        extraIds: List<String> = emptyList(),
        extraWidths: Map<String, Int> = emptyMap(),
    ): List<MetroTilePlacement> {
        val hidden = Config.metroHiddenTiles.split(',').filter(String::isNotBlank).toSet()
        val visibleBuiltIns = ids.filterNot { it in hidden }
        val allIds = visibleBuiltIns + extraIds.filterNot { it in ids }
        val parsed = Config.metroTileLayout.split(';').mapNotNull { token ->
            val values = token.split(':')
            if (values.size != 5 || values[0] !in allIds) null else runCatching {
                MetroTilePlacement(values[0], values[1].toInt(), values[2].toInt(), values[3].toInt(), values[4].toInt())
            }.getOrNull()
        }
        // Preserve user-dragged positions: keep any parsed placement, only fall back to
        // defaults/extra placement for ids that are newly visible or newly added. The
        // previous `parsed.size == allIds.size` branch discarded every custom position
        // as soon as a single built-in was hidden/shown or a custom tile was added.
        if (parsed.size == allIds.size) {
            return pack(parsed, columns, rows)
        }
        val parsedById = parsed.associateBy { it.id }
        val defaultsById = defaults(columns).filterNot { it.id in hidden }.associateBy { it.id }
        val source = allIds.mapNotNull { id ->
            parsedById[id] ?: defaultsById[id] ?: run {
                val index = extraIds.indexOf(id)
                if (index < 0) null else MetroTilePlacement(
                    id,
                    index % columns,
                    5 + index / columns,
                    extraWidths[id].orDefault(1).coerceIn(1, columns),
                    1,
                )
            }
        }
        return pack(source, columns, rows)
    }

    private fun Int?.orDefault(default: Int) = this ?: default

    fun isVisible(id: String) = id !in Config.metroHiddenTiles.split(',').filter(String::isNotBlank)

    fun hide(id: String) {
        Config.metroHiddenTiles = (Config.metroHiddenTiles.split(',') + id)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(",")
    }

    /** Restores exactly one built-in tile; restoring one must not undo the user's other choices. */
    fun show(id: String) {
        Config.metroHiddenTiles = Config.metroHiddenTiles.split(',')
            .filter(String::isNotBlank)
            .filterNot { it == id }
            .joinToString(",")
    }

    fun showAll() {
        Config.metroHiddenTiles = ""
    }

    fun save(placements: List<MetroTilePlacement>, columns: Int, rows: Int): List<MetroTilePlacement> {
        val packed = pack(placements, columns, rows)
        Config.metroTileLayout = packed.joinToString(";") { "${it.id}:${it.column}:${it.row}:${it.width}:${it.height}" }
        return packed
    }

    fun move(placements: List<MetroTilePlacement>, id: String, column: Int, row: Int, columns: Int, rows: Int) =
        pack(placements.map { if (it.id == id) it.copy(column = column, row = row) else it }, columns, rows)

    fun resize(placements: List<MetroTilePlacement>, id: String, columns: Int, rows: Int): List<MetroTilePlacement> {
        val item = placements.firstOrNull { it.id == id } ?: return placements
        val next = when (item.width to item.height) {
            1 to 1 -> item.copy(width = 2, height = 1)
            2 to 1 -> item.copy(width = 2, height = 2)
            else -> item.copy(width = 1, height = 1)
        }
        return pack(placements.map { if (it.id == id) next else it }, columns, rows)
    }

    fun resizeTo(placements: List<MetroTilePlacement>, id: String, width: Int, height: Int, columns: Int, rows: Int) =
        pack(placements.map { if (it.id == id) it.copy(width = width, height = height) else it }, columns, rows)

    fun resizeRect(
        placements: List<MetroTilePlacement>,
        id: String,
        column: Int,
        row: Int,
        width: Int,
        height: Int,
        columns: Int,
        rows: Int,
    ) = pack(
        placements.map {
            if (it.id == id) it.copy(column = column, row = row, width = width, height = height) else it
        },
        columns,
        rows,
    )

    /** Checks a live drag preview without packing other tiles out of the way. */
    fun canPlace(placements: List<MetroTilePlacement>, candidate: MetroTilePlacement, columns: Int, rows: Int): Boolean =
        candidate.column >= 0 && candidate.row >= 0 &&
            candidate.width >= 1 && candidate.height >= 1 &&
            candidate.column + candidate.width <= columns && candidate.row + candidate.height <= rows &&
            placements.asSequence().filter { it.id != candidate.id }.none { other ->
                candidate.column < other.column + other.width &&
                    candidate.column + candidate.width > other.column &&
                    candidate.row < other.row + other.height &&
                    candidate.row + candidate.height > other.row
            }

    private fun defaults(columns: Int) = listOf(
        MetroTilePlacement(Magisk, 0, 0, 2, 2),
        MetroTilePlacement(Modules, columns - 1, 0, 1, 1),
        MetroTilePlacement(Apps, columns - 1, 1, 1, 1),
        MetroTilePlacement(Settings, 0, 2, 2, 1),
        MetroTilePlacement(Logs, columns - 1, 2, 1, 2),
        MetroTilePlacement(Contributors, 0, 3, 2, 1),
        MetroTilePlacement(Sponsor, 0, 4, 1, 1),
        MetroTilePlacement(Support, 1, 4, 1, 1),
        MetroTilePlacement(Donate, 2, 4, 1, 1),
    )

    private fun pack(source: List<MetroTilePlacement>, columns: Int, rows: Int): List<MetroTilePlacement> {
        val occupied = Array(rows) { BooleanArray(columns) }
        fun fits(item: MetroTilePlacement, x: Int, y: Int): Boolean =
            x >= 0 && y >= 0 && x + item.width <= columns && y + item.height <= rows &&
                (y until y + item.height).all { yy -> (x until x + item.width).all { xx -> !occupied[yy][xx] } }
        fun occupy(item: MetroTilePlacement) {
            for (y in item.row until item.row + item.height) for (x in item.column until item.column + item.width) occupied[y][x] = true
        }
        val order = ids + source.map { it.id }.filterNot { it in ids }.distinct()
        return source.sortedBy { order.indexOf(it.id) }.map { raw ->
            val normalized = raw.copy(width = raw.width.coerceIn(1, columns), height = raw.height.coerceIn(1, rows))
            val slot = sequence {
                yield(normalized.column.coerceIn(0, columns - normalized.width) to normalized.row.coerceIn(0, rows - normalized.height))
                for (y in 0..rows - normalized.height) for (x in 0..columns - normalized.width) yield(x to y)
            }.firstOrNull { fits(normalized, it.first, it.second) }
            val placed = slot?.let { normalized.copy(column = it.first, row = it.second) } ?: normalized.copy(column = 0, row = 0)
            occupy(placed)
            placed
        }
    }
}