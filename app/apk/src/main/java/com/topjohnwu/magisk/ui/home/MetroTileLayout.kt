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

    /**
     * The constrained-axis cap for each orientation: columns when portrait, rows when landscape.
     * The other axis is unbounded and simply scrolls. There is exactly one grid shape:
     *
     *   portrait  -> 3 columns wide, vertically endless board
     *   landscape -> 4 rows tall, horizontally endless board
     */
    fun grid(): Pair<Int, Int> = 3 to 4

    /** The capped axis for this orientation: columns in portrait, rows in landscape. */
    fun fixedSpan(landscape: Boolean) = if (landscape) grid().second else grid().first

    /**
     * How many cells the placements actually occupy along the free (scrollable) axis: rows when
     * portrait, columns when landscape. The board is only this long, so the page can never scroll
     * past the last tile.
     */
    fun usedFreeExtent(placements: List<MetroTilePlacement>, landscape: Boolean): Int =
        placements.maxOfOrNull { placement ->
            val axis = placement.toAxis(landscape)
            axis.b + axis.spanB
        } ?: 0

    /** Axis-space placement: [a] is the constrained axis, [b] the free one. */
    private data class Axis(val id: String, val a: Int, val b: Int, val spanA: Int, val spanB: Int)

    private fun MetroTilePlacement.toAxis(landscape: Boolean) = if (landscape) {
        Axis(id, row, column, height, width)
    } else {
        Axis(id, column, row, width, height)
    }

    private fun Axis.toTile(landscape: Boolean) = if (landscape) {
        MetroTilePlacement(id, b, a, spanB, spanA)
    } else {
        MetroTilePlacement(id, a, b, spanA, spanB)
    }

    fun load(
        landscape: Boolean,
        extraIds: List<String> = emptyList(),
        extraWidths: Map<String, Int> = emptyMap(),
        freeBound: Int = Int.MAX_VALUE,
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
        // defaults/extra placement for ids that are newly visible or newly added.
        if (parsed.size == allIds.size) {
            return packAll(parsed, landscape, freeBound = freeBound)
        }
        val cap = fixedSpan(landscape)
        val parsedById = parsed.associateBy { it.id }
        val defaultsById = defaultsAxis(landscape, cap).map { it.toTile(landscape) }
            .filterNot { it.id in hidden }.associateBy { it.id }
        val source = allIds.mapNotNull { id ->
            parsedById[id] ?: defaultsById[id] ?: run {
                val index = extraIds.indexOf(id)
                if (index < 0) null else {
                    val width = extraWidths[id] ?: 1
                    // Custom tiles are 1 cell tall on the constrained axis; their group width
                    // runs along the free axis so a 2-wide group still reads as one tile.
                    Axis(
                        id,
                        a = index % cap,
                        b = index / cap,
                        spanA = if (landscape) 1 else width,
                        spanB = if (landscape) width else 1,
                    ).toTile(landscape)
                }
            }
        }
        return packAll(source, landscape, freeBound = freeBound)
    }

    fun isVisible(id: String) = id !in Config.metroHiddenTiles.split(',').filter(String::isNotBlank)

    fun hide(id: String) {
        Config.metroHiddenTiles = (Config.metroHiddenTiles.split(',') + id)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(",")
        MetroUiState.invalidate()
    }

    /** Restores exactly one built-in tile; restoring one must not undo the user's other choices. */
    fun show(id: String) {
        Config.metroHiddenTiles = Config.metroHiddenTiles.split(',')
            .filter(String::isNotBlank)
            .filterNot { it == id }
            .joinToString(",")
        MetroUiState.invalidate()
    }

    fun showAll() {
        Config.metroHiddenTiles = ""
        MetroUiState.invalidate()
    }

    /** Packs the placements and persists them, so a drag or a resize survives any later
     * reload (a grid change, a theme apply, a process death). */
    fun save(placements: List<MetroTilePlacement>, landscape: Boolean): List<MetroTilePlacement> =
        persist(packAll(placements, landscape))

    private fun persist(placements: List<MetroTilePlacement>): List<MetroTilePlacement> {
        Config.metroTileLayout = placements.joinToString(";") {
            "${it.id}:${it.column}:${it.row}:${it.width}:${it.height}"
        }
        return placements
    }

    fun move(placements: List<MetroTilePlacement>, id: String, column: Int, row: Int, landscape: Boolean) =
        packAll(placements.map { if (it.id == id) it.copy(column = column, row = row) else it }, landscape, growFree = 2)

    fun resize(placements: List<MetroTilePlacement>, id: String, landscape: Boolean): List<MetroTilePlacement> {
        val item = placements.firstOrNull { it.id == id } ?: return placements
        val next = when (item.width to item.height) {
            1 to 1 -> item.copy(width = 2, height = 1)
            2 to 1 -> item.copy(width = 2, height = 2)
            else -> item.copy(width = 1, height = 1)
        }
        return packAll(placements.map { if (it.id == id) next else it }, landscape)
    }

    fun resizeTo(placements: List<MetroTilePlacement>, id: String, width: Int, height: Int, landscape: Boolean) =
        packAll(placements.map { if (it.id == id) it.copy(width = width, height = height) else it }, landscape)

    fun resizeRect(
        placements: List<MetroTilePlacement>,
        id: String,
        column: Int,
        row: Int,
        width: Int,
        height: Int,
        landscape: Boolean,
    ) = packAll(
        placements.map {
            if (it.id == id) it.copy(column = column, row = row, width = width, height = height) else it
        },
        landscape,
        growFree = 2,
    )

    /** Checks a live drag preview without packing other tiles out of the way. */
    fun canPlace(placements: List<MetroTilePlacement>, candidate: MetroTilePlacement, landscape: Boolean): Boolean {
        val cap = fixedSpan(landscape)
        val axis = candidate.toAxis(landscape)
        return axis.a >= 0 && axis.b >= 0 && axis.spanA >= 1 && axis.spanB >= 1 &&
            axis.a + axis.spanA <= cap &&
            placements.asSequence().filter { it.id != candidate.id }.none { other ->
                val o = other.toAxis(landscape)
                axis.a < o.a + o.spanA &&
                    axis.a + axis.spanA > o.a &&
                    axis.b < o.b + o.spanB &&
                    axis.b + axis.spanB > o.b
            }
    }

    /** The classic default board, written in column/row space for a 4-wide phone grid. */
    private fun defaultsAxis(landscape: Boolean, cap: Int): List<Axis> {
        val raw = listOf(
            MetroTilePlacement(Magisk, 0, 0, 2, 2),
            MetroTilePlacement(Modules, 3, 0, 1, 1),
            MetroTilePlacement(Apps, 3, 1, 1, 1),
            MetroTilePlacement(Settings, 0, 2, 2, 1),
            MetroTilePlacement(Logs, 3, 2, 1, 2),
            MetroTilePlacement(Contributors, 0, 3, 2, 1),
            MetroTilePlacement(Sponsor, 0, 4, 1, 1),
            MetroTilePlacement(Support, 1, 4, 1, 1),
            MetroTilePlacement(Donate, 2, 4, 1, 1),
        )
        return raw.map { it.toAxis(landscape) }.map { item ->
            // The constrained axis is capped; clamp whatever cannot fit there.
            item.copy(a = item.a.coerceIn(0, (cap - item.spanA).coerceAtLeast(0)))
        }
    }

    private fun packAll(
        source: List<MetroTilePlacement>,
        landscape: Boolean,
        growFree: Int = 0,
        freeBound: Int = Int.MAX_VALUE,
    ): List<MetroTilePlacement> {
        val cap = fixedSpan(landscape)
        val axis = source.map { it.toAxis(landscape) }
        // The free axis is unbounded while the board is being customized, so the packing
        // grid grows to whatever the placements need (plus head room for an in-flight
        // drag). When the board cannot scroll, the caller pins the visible extent instead
        // and every tile is packed into that many rows/columns.
        val freeBound = if (freeBound == Int.MAX_VALUE) {
            ((axis.maxOfOrNull { it.b + it.spanB } ?: 0) + growFree).coerceAtLeast(8)
        } else {
            freeBound.coerceAtLeast(1)
        }
        return packAxis(axis, cap, freeBound).map { it.toTile(landscape) }
    }

    private fun packAxis(source: List<Axis>, cap: Int, freeBound: Int): List<Axis> {
        // A bounded occupancy grid cannot always hold every tile (too many tiles for the
        // visible extent); those are kept just past the bound rather than stacked on top
        // of another tile at the origin, so the origin never shows an overlap.
        val occupied = Array(freeBound) { BooleanArray(cap) }
        val overflow = HashSet<Long>()
        fun key(a: Int, b: Int) = b.toLong() * cap + a
        fun fits(item: Axis, a: Int, b: Int): Boolean =
            a >= 0 && b >= 0 && a + item.spanA <= cap &&
                (b until b + item.spanB).all { bb ->
                    (a until a + item.spanA).all { aa ->
                        if (bb < freeBound) !occupied[bb][aa] else key(aa, bb) !in overflow
                    }
                }
        fun occupy(item: Axis) {
            for (b in item.b until item.b + item.spanB) for (a in item.a until item.a + item.spanA) {
                if (b < freeBound) occupied[b][a] = true else overflow.add(key(a, b))
            }
        }
        val order = ids + source.map { it.id }.filterNot { it in ids }.distinct()
        return source.sortedBy { order.indexOf(it.id) }.map { raw ->
            val normalized = raw.copy(spanA = raw.spanA.coerceIn(1, cap), spanB = raw.spanB.coerceAtLeast(1))
            val limit = freeBound
            val slot = sequence {
                yield(
                    normalized.a.coerceIn(0, (cap - normalized.spanA).coerceAtLeast(0)) to
                        normalized.b.coerceIn(0, (limit - normalized.spanB).coerceAtLeast(0)),
                )
                for (b in 0..limit - normalized.spanB) for (a in 0..cap - normalized.spanA) yield(a to b)
                // Past the bound there is always room: keep appending free rows until a slot fits.
                for (b in limit + 1..Int.MAX_VALUE) for (a in 0..cap - normalized.spanA) yield(a to b)
            }.firstOrNull { fits(normalized, it.first, it.second) }
            val placed = slot?.let { normalized.copy(a = it.first, b = it.second) } ?: normalized.copy(a = 0, b = 0)
            occupy(placed)
            placed
        }
    }
}
