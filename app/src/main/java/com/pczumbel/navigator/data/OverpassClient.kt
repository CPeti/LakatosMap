package com.pczumbel.navigator.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.osmdroid.util.BoundingBox
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches `natural=cave_entrance` nodes from the Overpass API — the same OSM data OsmAnd's
 * cave layer renders, except OsmAnd reads it from a prebuilt offline vector map and we pull
 * it live per area and cache it in Room.
 *
 * Deliberately on HttpURLConnection: one POST, no retry policy worth speaking of, and adding
 * OkHttp for it would be the only reason that dependency existed.
 */
object OverpassClient {

    /**
     * Public instances are rate-limited and run by volunteers. Keep calls rare (once per
     * area, cached forever afterwards), identify ourselves honestly, and never poll.
     */
    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"
    private const val USER_AGENT = "navigator-orienteering/0.1 (offline cave map; contact via app repo)"
    private const val TIMEOUT_MS = 90_000

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class OverpassResponse(
        val elements: List<Element> = emptyList(),
        /** Set only in the bundled catalogue (tools/update_bundled_caves.py). */
        val generatedAtMs: Long? = null,
    )

    @Serializable
    private data class Element(
        val id: Long,
        val lat: Double? = null,
        val lon: Double? = null,
        val tags: Map<String, String> = emptyMap(),
    )

    /**
     * Also matches `natural=sinkhole` and shafts, which are entrances in practice and which
     * OSM mappers tag inconsistently. Nodes only — cave entrances are points by convention.
     */
    private fun query(box: BoundingBox): String {
        val bbox = "%.6f,%.6f,%.6f,%.6f".format(
            box.latSouth, box.lonWest, box.latNorth, box.lonEast,
        )
        return """
            [out:json][timeout:60];
            (
              node["natural"="cave_entrance"]($bbox);
              node["natural"="sinkhole"]($bbox);
            );
            out body;
        """.trimIndent()
    }

    /**
     * All of Hungary as a bounding box. Overspills into Slovakia, Romania, Austria, Croatia
     * and Serbia — [HungaryBoundary] clips the result. Asking Overpass for the country via
     * `area["ISO3166-1"="HU"]` instead returns 504 on every public mirror: the area lookup
     * plus a nationwide node extraction is simply too heavy.
     */
    suspend fun fetchHungaryBbox(nowMs: Long): Result<List<Cave>> = fetchCaves(
        BoundingBox(
            HungaryBoundary.NORTH,
            HungaryBoundary.EAST,
            HungaryBoundary.SOUTH,
            HungaryBoundary.WEST,
        ),
        nowMs,
    )

    suspend fun fetchCaves(box: BoundingBox, nowMs: Long): Result<List<Cave>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    // Raw body, not form-encoded: Overpass answers a form-encoded POST with
                    // 406 Not Acceptable.
                    setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                    setRequestProperty("User-Agent", USER_AGENT)
                }

                connection.outputStream.use { it.write(query(box).toByteArray()) }

                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    error(
                        when (code) {
                            429, 504 -> "Overpass is rate-limiting us ($code). Wait a minute and retry."
                            else -> "Overpass returned $code. ${detail.take(200)}"
                        },
                    )
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                parseCaves(body, nowMs)
            }
        }

    /** Overpass JSON to caves; also reads the bundled catalogue, which keeps the same shape. */
    fun parseCaves(body: String, nowMs: Long): List<Cave> =
        json.decodeFromString<OverpassResponse>(body).elements.mapNotNull { it.toCave(nowMs) }

    /** The bundled catalogue's build time, so its caves are dated to the snapshot, not the install. */
    fun parseGeneratedAt(body: String): Long? =
        json.decodeFromString<OverpassResponse>(body).generatedAtMs

    private fun Element.toCave(nowMs: Long): Cave? {
        val latitude = lat ?: return null
        val longitude = lon ?: return null
        return Cave(
            id = id,
            name = tags["name"],
            lat = latitude,
            lon = longitude,
            ref = tags["ref"] ?: tags["ref:hu"],
            elevationM = tags["ele"]?.toDoubleOrNull(),
            entranceType = tags["cave_entrance"] ?: tags["natural"],
            length = tags["length"],
            depth = tags["depth"],
            description = tags["description"] ?: tags["note"],
            fetchedAtEpochMs = nowMs,
            caveRef = tags["cave:ref"],
            tagsJson = Cave.encodeTags(tags),
        )
    }
}
