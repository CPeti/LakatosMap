package com.pczumbel.navigator.tiles

import kotlinx.serialization.Serializable
import org.osmdroid.util.BoundingBox

/**
 * A record of an area the user deliberately downloaded.
 *
 * osmdroid stores every tile in one shared sqlite cache with no notion of "areas", so without
 * this bookkeeping there is no way to show the user what they have or to delete one area
 * without nuking the lot.
 */
@Serializable
data class TilePack(
    val id: String,
    val name: String,
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
    val zoomMin: Int,
    val zoomMax: Int,
    val tileCount: Int,
    val createdAtEpochMs: Long,
    val sourceName: String,
) {
    fun boundingBox(): BoundingBox = BoundingBox(north, east, south, west)

    val estimatedBytes: Long get() = tileCount * com.pczumbel.navigator.map.AVG_TILE_BYTES
}

@Serializable
data class TilePackStore(val packs: List<TilePack> = emptyList())

fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / 1024.0 / 1024 / 1024)
    bytes >= 1024L * 1024 -> "%.0f MB".format(bytes / 1024.0 / 1024)
    bytes >= 1024L -> "%.0f kB".format(bytes / 1024.0)
    else -> "$bytes B"
}
