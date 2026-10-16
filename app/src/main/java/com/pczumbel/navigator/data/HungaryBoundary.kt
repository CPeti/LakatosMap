package com.pczumbel.navigator.data

import android.content.Context

/**
 * Point-in-Hungary test, used to clip the bbox-based Overpass fetch down to the country.
 *
 * Why not ask Overpass for the country directly? `area["ISO3166-1"="HU"]` plus a nationwide
 * node extraction times out with a 504 on every public mirror tried — the area lookup is far
 * too heavy. A plain bbox query answers the same question in ~14 s, so we over-fetch and clip
 * here instead.
 *
 * The boundary is simplified to ~11 m (Douglas–Peucker, 36k points → 10,875, 218 kB).
 *
 * It used to be ~1 km, which looked harmless and wasn't: the karst plateaus that hold Hungary's
 * caves run right up to the Slovak border, and that tolerance silently dropped six real
 * Hungarian caves (Almási-zsomboly among them) while keeping four Slovak ones. At ~22 m and
 * finer, every cave in the bbox classifies the same as against the full-resolution border;
 * ~11 m leaves margin for entrances mapped later. ~10k edges × ~8k caves is well under a
 * second on a phone, once per Hungary fetch.
 */
object HungaryBoundary {

    /** Bounding box of Hungary, slightly padded. What we actually ask Overpass for. */
    const val SOUTH = 45.70
    const val WEST = 16.05
    const val NORTH = 48.62
    const val EAST = 22.95

    private const val ASSET = "hungary_border.csv"

    @Volatile
    private var ring: DoubleArray? = null

    /** Flat [lat, lon, lat, lon, …]; a primitive array to avoid ~11k boxed objects. */
    private fun ring(context: Context): DoubleArray = ring ?: synchronized(this) {
        ring ?: loadRing(context).also { ring = it }
    }

    private fun loadRing(context: Context): DoubleArray {
        val values = ArrayList<Double>(24_000)
        context.assets.open(ASSET).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val comma = line.indexOf(',')
                if (comma <= 0) return@forEach
                val lat = line.substring(0, comma).trim().toDoubleOrNull() ?: return@forEach
                val lon = line.substring(comma + 1).trim().toDoubleOrNull() ?: return@forEach
                values.add(lat)
                values.add(lon)
            }
        }
        return values.toDoubleArray()
    }

    fun contains(context: Context, lat: Double, lon: Double): Boolean {
        // Cheap rejection first: most of a bbox query's overspill fails this immediately.
        if (lat < SOUTH || lat > NORTH || lon < WEST || lon > EAST) return false
        return crossingNumber(ring(context), lat, lon)
    }

    /**
     * Standard even-odd ray casting, eastward ray. Treats coordinates as planar — fine at
     * Hungary's latitude at this border resolution.
     */
    private fun crossingNumber(ring: DoubleArray, lat: Double, lon: Double): Boolean {
        var inside = false
        val count = ring.size / 2
        var j = count - 1
        for (i in 0 until count) {
            val latI = ring[i * 2]
            val lonI = ring[i * 2 + 1]
            val latJ = ring[j * 2]
            val lonJ = ring[j * 2 + 1]

            if ((latI > lat) != (latJ > lat)) {
                val slope = (lonJ - lonI) / (latJ - latI)
                if (lon < lonI + slope * (lat - latI)) inside = !inside
            }
            j = i
        }
        return inside
    }
}
