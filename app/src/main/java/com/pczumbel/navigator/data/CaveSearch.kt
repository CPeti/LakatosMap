package com.pczumbel.navigator.data

import java.text.Normalizer
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Normalises a name for searching: strips accents, case, and all punctuation/whitespace.
 *
 * Both halves matter for Hungarian cave names. Accents, because nobody types á/ö/ő into a
 * search box with cold hands — NFD decomposition splits those into combining marks that the
 * first regex removes. And punctuation, because the names are relentlessly hyphenated:
 * "Pál-völgyi-barlang" folds to "palvolgyibarlang", so typing "palvolgy" finds it. Without
 * the second step the hyphen defeats the substring match and the search looks broken.
 */
fun foldAccents(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .replace("[^\\p{L}\\p{Nd}]+".toRegex(), "")
        .lowercase()

data class CaveHit(val cave: Cave, val distanceMetres: Double?)

/**
 * Ranks by match quality first, then distance: an exact prefix match for what you typed is
 * almost always what you meant, even if a substring match happens to be nearer.
 */
fun searchCaves(
    caves: List<Cave>,
    query: String,
    fromLat: Double?,
    fromLon: Double?,
    limit: Int = 60,
): List<CaveHit> {
    val needle = foldAccents(query.trim())
    if (needle.isEmpty()) return emptyList()

    return caves.asSequence()
        .mapNotNull { cave ->
            val name = foldAccents(cave.displayName)
            val ref = cave.registerRef?.let { foldAccents(it) }
            val rank = when {
                name.startsWith(needle) -> 0
                ref != null && ref.startsWith(needle) -> 1
                name.contains(needle) -> 2
                ref != null && ref.contains(needle) -> 3
                else -> return@mapNotNull null
            }
            val distance = if (fromLat != null && fromLon != null) {
                haversineMetres(fromLat, fromLon, cave.lat, cave.lon)
            } else {
                null
            }
            Triple(rank, distance ?: Double.MAX_VALUE, CaveHit(cave, distance))
        }
        .sortedWith(compareBy({ it.first }, { it.second }))
        .map { it.third }
        .take(limit)
        .toList()
}

private const val EARTH_RADIUS_M = 6_371_000.0

fun haversineMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(a).coerceAtMost(1.0))
}

fun formatDistance(metres: Double): String =
    if (metres >= 1000) "%.1f km".format(metres / 1000) else "%.0f m".format(metres)
