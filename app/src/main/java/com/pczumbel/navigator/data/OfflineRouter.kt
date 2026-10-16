package com.pczumbel.navigator.data

import btools.router.OsmNodeNamed
import btools.router.RoutingContext
import btools.router.RoutingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import java.io.File

/**
 * Runs BRouter on the phone against downloaded segments.
 *
 * Same engine version (1.7.10) and same `hiking-beta` profile as brouter.de, so an offline
 * route matches the online one exactly — verified on desktop: 13,053 m / 9,760 s / ↑211 m for
 * the same three waypoints both ways.
 */
object OfflineRouter {

    // The engine keeps per-run caches and is not meant for concurrent runs.
    private val lock = Mutex()

    /**
     * Segments needed to route between these points. The waypoints' bounding box is padded,
     * because a route can wander outside it (round a ridge, say), and a segment the path needs
     * but doesn't have makes the engine fail mid-search.
     */
    fun segmentsNeeded(waypoints: List<GeoPoint>): List<String> {
        val box = BoundingBox.fromGeoPointsSafe(waypoints)
        val pad = 0.05 // ~5 km
        return RoutingData.segmentsFor(
            BoundingBox(box.latNorth + pad, box.lonEast + pad, box.latSouth - pad, box.lonWest - pad),
        )
    }

    fun canRoute(waypoints: List<GeoPoint>): Boolean =
        segmentsNeeded(waypoints).all { RoutingData.isInstalled(it) }

    suspend fun route(waypoints: List<GeoPoint>, straightLineMetres: Double): Result<RoutePlan> =
        withContext(Dispatchers.Default) {
            lock.withLock {
                runCatching {
                    val context = RoutingContext().apply {
                        localFunction = File(RoutingData.profileDir, "hiking-beta.brf").absolutePath
                    }
                    val nodes = waypoints.mapIndexed { i, p ->
                        OsmNodeNamed().apply {
                            name = "wp$i"
                            // BRouter's fixed-point encoding: microdegrees, offset non-negative.
                            ilon = ((p.longitude + 180.0) * 1e6 + 0.5).toInt()
                            ilat = ((p.latitude + 90.0) * 1e6 + 0.5).toInt()
                        }
                    }
                    val engine = RoutingEngine(null, null, RoutingData.segmentDir, nodes, context)
                    engine.quite = true
                    engine.doRun(60_000)

                    engine.errorMessage?.let { msg ->
                        error(
                            if (msg.contains("not found", ignoreCase = true) || msg.contains("no track", ignoreCase = true)) {
                                "No walking route found — a point may be off the routable network."
                            } else {
                                "Offline router: $msg"
                            },
                        )
                    }
                    val track = engine.foundTrack ?: error("Offline router returned no route.")

                    RoutePlan(
                        points = track.nodes.map { GeoPoint(it.iLat / 1e6 - 90.0, it.iLon / 1e6 - 180.0) },
                        lengthMetres = track.distance,
                        durationSeconds = track.totalSeconds,
                        ascentMetres = track.ascend,
                        straightLineMetres = straightLineMetres,
                        offline = true,
                    )
                }
            }
        }
}
