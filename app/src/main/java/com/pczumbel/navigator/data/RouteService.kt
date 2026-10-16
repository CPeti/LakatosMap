package com.pczumbel.navigator.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL

data class RoutePlan(
    val points: List<GeoPoint>,
    val lengthMetres: Int,
    val durationSeconds: Int,
    val ascentMetres: Int?,
    val straightLineMetres: Double,
    /** True when computed on the phone from downloaded routing data, false when from brouter.de. */
    val offline: Boolean = false,
) {
    /** How much further than the crow flies — a quick read on how awkward the terrain is. */
    val detourFactor: Double
        get() = if (straightLineMetres > 0) lengthMetres / straightLineMetres else 1.0
}

/**
 * Walking routes from BRouter, the long-standing OSM router for foot traffic.
 *
 * The `hiking-beta` profile is the reason for choosing it: it actively prefers paths, tracks
 * and marked trails over roads, and weights climbing, which is what you want when walking to
 * a cave entrance. A car-oriented router would happily send you along an asphalt road that
 * passes 500 m from the entrance and call it done.
 */
object RouteService {

    private const val ENDPOINT = "https://brouter.de/brouter"
    private const val PROFILE = "hiking-beta"
    private const val USER_AGENT = "navigator-orienteering/0.1 (cave route planning)"

    // Long multi-stop hikes can take BRouter a while to compute.
    private const val TIMEOUT_MS = 90_000

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * One route through all [waypoints] in order — start, stops, end.
     *
     * Offline first: if the routing data covering every waypoint is downloaded, the embedded
     * engine computes it on the phone with no network at all. Otherwise it goes to brouter.de,
     * which takes the whole list in one request (`lonlats=a|b|c…`).
     */
    suspend fun route(waypoints: List<GeoPoint>): Result<RoutePlan> {
        if (waypoints.size < 2) return Result.failure(IllegalArgumentException("Need a start and an end."))

        val straight = waypoints.zipWithNext().sumOf { (a, b) ->
            haversineMetres(a.latitude, a.longitude, b.latitude, b.longitude)
        }

        if (OfflineRouter.canRoute(waypoints)) {
            return OfflineRouter.route(waypoints, straight)
        }

        return online(waypoints, straight).recoverCatching { e ->
            val missing = OfflineRouter.segmentsNeeded(waypoints).filterNot { RoutingData.isInstalled(it) }
            // The likely case in the field: no signal. Say which routing data would have helped.
            if (e is java.io.IOException && missing.isNotEmpty()) {
                throw IllegalStateException(
                    "No connection, and offline routing data for ${missing.joinToString()} isn't " +
                        "downloaded (Offline maps → Routing data).",
                )
            }
            throw e
        }
    }

    private suspend fun online(waypoints: List<GeoPoint>, straight: Double): Result<RoutePlan> = withContext(Dispatchers.IO) {
        runCatching {
            // BRouter takes lon,lat — the reverse of every other coordinate in this app.
            val lonLats = waypoints.joinToString("|") { "%.6f,%.6f".format(it.longitude, it.latitude) }
            val url = URL(
                "$ENDPOINT?lonlats=$lonLats&profile=$PROFILE&alternativeidx=0&format=geojson",
            )

            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
            }

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error(
                    when {
                        detail.contains("not found", ignoreCase = true) ->
                            "No walking route found — a point may be off the routable network."
                        else -> "Router returned $code. ${detail.take(160)}"
                    },
                )
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            parse(body, straight)
        }
    }

    private fun parse(body: String, straightLineMetres: Double): RoutePlan {
        val feature = json.parseToJsonElement(body)
            .jsonObject["features"]!!.jsonArray
            .firstOrNull()?.jsonObject
            ?: error("Router returned no route.")

        // BRouter reports these as strings, hence the manual parse rather than @Serializable.
        val props = feature["properties"]!!.jsonObject
        fun prop(name: String) = props[name]?.jsonPrimitive?.content

        val points = feature["geometry"]!!.jsonObject["coordinates"]!!.jsonArray.map {
            val pair = it.jsonArray
            GeoPoint(pair[1].jsonPrimitive.content.toDouble(), pair[0].jsonPrimitive.content.toDouble())
        }
        if (points.isEmpty()) error("Router returned an empty route.")

        return RoutePlan(
            points = points,
            lengthMetres = prop("track-length")?.toDoubleOrNull()?.toInt() ?: 0,
            durationSeconds = prop("total-time")?.toDoubleOrNull()?.toInt() ?: 0,
            ascentMetres = prop("filtered ascend")?.toDoubleOrNull()?.toInt(),
            straightLineMetres = straightLineMetres,
        )
    }
}

fun formatDuration(seconds: Int): String {
    val minutes = (seconds + 30) / 60
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}
