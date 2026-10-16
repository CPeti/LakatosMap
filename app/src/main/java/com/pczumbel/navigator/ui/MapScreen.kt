package com.pczumbel.navigator.ui

import android.Manifest
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import com.pczumbel.navigator.location.HeadingLocationOverlay
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pczumbel.navigator.R
import com.pczumbel.navigator.data.Cave
import com.pczumbel.navigator.data.CaveHit
import com.pczumbel.navigator.data.CaveRepository
import com.pczumbel.navigator.data.LayerPreferences
import com.pczumbel.navigator.map.MapyKey
import com.pczumbel.navigator.map.MapLayers
import com.pczumbel.navigator.data.EventRepository
import com.pczumbel.navigator.data.EventPoint
import com.pczumbel.navigator.data.EventEntity
import com.pczumbel.navigator.data.RoutePlan
import com.pczumbel.navigator.data.RouteService
import com.pczumbel.navigator.data.RoutingData
import com.pczumbel.navigator.data.formatDistance
import com.pczumbel.navigator.data.formatDuration
import com.pczumbel.navigator.data.haversineMetres
import com.pczumbel.navigator.data.searchCaves
import com.pczumbel.navigator.location.HeadingProvider
import com.pczumbel.navigator.location.headingChanged
import com.pczumbel.navigator.map.AVG_TILE_BYTES
import com.pczumbel.navigator.map.CameraState
import com.pczumbel.navigator.map.CaveIconFactory
import com.pczumbel.navigator.map.CaveLayer
import com.pczumbel.navigator.route.TourSolver
import com.pczumbel.navigator.map.EventLayer
import com.pczumbel.navigator.map.LayerController
import com.pczumbel.navigator.map.LayerSource
import com.pczumbel.navigator.map.CLUSTER_BELOW_ZOOM
import com.pczumbel.navigator.map.RouteLayer
import com.pczumbel.navigator.map.applyCamera
import com.pczumbel.navigator.map.rememberMapViewWithLifecycle
import com.pczumbel.navigator.tiles.DownloadState
import com.pczumbel.navigator.tiles.TilePack
import com.pczumbel.navigator.tiles.TilePackDownloader
import com.pczumbel.navigator.tiles.TilePackRepository
import com.pczumbel.navigator.tiles.formatBytes
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import java.util.UUID

private val CameraSaver = listSaver<CameraState, Double>(
    save = { listOf(it.lat, it.lon, it.zoom) },
    restore = { CameraState(it[0], it[1], it[2]) },
)

/**
 * Three-state locate button, matching what every map app trains users to expect:
 * off → centred and following → following with the map turned to face your heading.
 */
private enum class LocateMode { OFF, FOLLOW, FOLLOW_ROTATE }

/** Snapping the viewport to a grid stops a re-query on every pixel of pan. ~2 km cells. */
private const val BOX_STEP = 0.02

private data class BoxKey(
    val south: Double,
    val north: Double,
    val west: Double,
    val east: Double,
) {
    fun toBoundingBox() = BoundingBox(north, east, south, west)
}

private fun floorTo(value: Double, step: Double) = kotlin.math.floor(value / step) * step
private fun ceilTo(value: Double, step: Double) = kotlin.math.ceil(value / step) * step

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    repo: TilePackRepository,
    caveRepo: CaveRepository,
    onOpenPacks: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var camera by rememberSaveable(stateSaver = CameraSaver) {
        mutableStateOf(CameraState.Default)
    }
    var offlineOnly by rememberSaveable { mutableStateOf(false) }
    var showCaveNames by rememberSaveable { mutableStateOf(false) }
    var showDownloadSheet by remember { mutableStateOf(false) }
    var showLayersSheet by remember { mutableStateOf(false) }
    var showFetchScopeDialog by remember { mutableStateOf(false) }
    var showMapyKeyDialog by remember { mutableStateOf(false) }
    var selectedCave by remember { mutableStateOf<Cave?>(null) }
    var locateMode by remember { mutableStateOf(LocateMode.OFF) }
    var heading by remember { mutableStateOf(0f) }
    var caveStatus by remember { mutableStateOf<String?>(null) }
    var fetchingCaves by remember { mutableStateOf(false) }

    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchCorpus by remember { mutableStateOf<List<Cave>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<CaveHit>>(emptyList()) }

    // Directions planner. Position in the list is the role: first = start, last = end.
    var plannerOpen by remember { mutableStateOf(false) }
    var plannerCollapsed by remember { mutableStateOf(false) }
    var waypoints by remember {
        mutableStateOf(listOf<Waypoint>(Waypoint.MyLocation, Waypoint.Unset))
    }
    // Which planner row the search bar is currently filling, if any.
    var pickingWaypoint by remember { mutableStateOf<Int?>(null) }

    // Event mode: one event at a time is "active"; while it is, the map shows only its points.
    val eventRepo = remember { EventRepository(context) }
    var activeEventId by remember { mutableStateOf(eventRepo.activeEventId) }
    var showEventSheet by remember { mutableStateOf(false) }
    var editingEventId by remember { mutableStateOf<Long?>(null) }
    // Set while the search bar is filling in an event's start or a new point.
    var pickingForEvent by remember { mutableStateOf<Pair<Long, EventPickTarget>?>(null) }
    var focusedEventPointId by remember { mutableStateOf<Long?>(null) }
    val activeEvent by remember(activeEventId) {
        activeEventId?.let { eventRepo.event(it) } ?: flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)
    val eventPoints by remember(activeEventId) {
        activeEventId?.let { eventRepo.points(it) } ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val eventMode = activeEventId != null
    // Looked up from the live list so the card reflects visited changes immediately.
    val focusedEventPoint = eventPoints.find { it.id == focusedEventPointId }

    // ---- cave detail sheet -----------------------------------------------------------------
    val density = LocalDensity.current
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var rootHeightPx by remember { mutableIntStateOf(0) }
    var route by remember { mutableStateOf<RoutePlan?>(null) }
    var routing by remember { mutableStateOf(false) }

    // Any edit to the waypoints invalidates the drawn route; a stale line that no longer
    // matches the list is worse than none.
    fun editWaypoints(change: (List<Waypoint>) -> List<Waypoint>) {
        waypoints = change(waypoints)
        route = null
    }

    val layerPrefs = remember { LayerPreferences(context) }
    var baseLayer by remember { mutableStateOf(layerPrefs.base()) }
    var overlayLayers by remember { mutableStateOf(layerPrefs.overlays()) }

    val mapView = rememberMapViewWithLifecycle(onCameraChanged = { camera = it })
    val layerController = remember(mapView) { LayerController(context, mapView) }
    val iconFactory = remember { CaveIconFactory(context) }
    val routeLayer = remember(mapView) { RouteLayer(mapView, iconFactory) }
    val downloader = remember { TilePackDownloader() }
    val downloadState by downloader.state.collectAsStateWithLifecycle()
    val totalCaves by caveRepo.observeCount().collectAsStateWithLifecycle(initialValue = 0)

    var cavesVisible by remember { mutableStateOf(layerPrefs.cavesVisible) }

    // Clusters are recomputed only when the integer zoom changes, not on every frame of a pinch.
    val zoomLevel = camera.zoom.toInt()

    var queryBox by remember { mutableStateOf<BoxKey?>(null) }
    LaunchedEffect(camera, cavesVisible) {
        if (!cavesVisible) {
            queryBox = null
            return@LaunchedEffect
        }
        val box = mapView.boundingBox
        val key = BoxKey(
            south = floorTo(box.latSouth, BOX_STEP),
            north = ceilTo(box.latNorth, BOX_STEP),
            west = floorTo(box.lonWest, BOX_STEP),
            east = ceilTo(box.lonEast, BOX_STEP),
        )
        if (key != queryBox) queryBox = key
    }

    val caves by remember(queryBox) {
        queryBox?.let { caveRepo.observeInBox(it.toBoundingBox()) } ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val headingProvider = remember { HeadingProvider(context) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val locationOverlay = remember(mapView) {
        HeadingLocationOverlay(GpsMyLocationProvider(context), mapView)
    }

    // ---- map rotation ----------------------------------------------------------------------
    // Mirrors mapView.mapOrientation so Compose can show the compass button; every code path
    // that rotates the map goes through setOrientation() to keep the two in step.
    var mapOrientation by remember { mutableFloatStateOf(0f) }
    fun setOrientation(degrees: Float) {
        val normalised = ((degrees % 360f) + 360f) % 360f
        mapView.mapOrientation = normalised
        mapOrientation = normalised
        mapView.invalidate()
    }
    DisposableEffect(mapView) {
        // Two-finger twist rotates the map, as in every other map app.
        val rotation = object : RotationGestureOverlay(mapView) {
            override fun onRotate(deltaAngle: Float) {
                super.onRotate(deltaAngle)
                mapOrientation = ((mapView.mapOrientation % 360f) + 360f) % 360f
                // Rotating by hand means you no longer want the compass steering the map.
                if (locateMode == LocateMode.FOLLOW_ROTATE) locateMode = LocateMode.FOLLOW
            }
        }.apply { isEnabled = true }
        mapView.overlays.add(rotation)
        onDispose { mapView.overlays.remove(rotation) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        hasLocationPermission = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocationPermission) {
            locateMode = LocateMode.FOLLOW
        } else {
            caveStatus = "Location permission denied — the locate button needs it."
        }
    }

    DisposableEffect(mapView, locationOverlay) {
        mapView.overlays.add(locationOverlay)
        onDispose {
            locationOverlay.disableMyLocation()
            locationOverlay.disableFollowLocation()
            mapView.overlays.remove(locationOverlay)
        }
    }

    val caveLayer = remember(mapView) { CaveLayer(mapView, iconFactory) }
    LaunchedEffect(caves, selectedCave?.id, showCaveNames, zoomLevel, eventMode) {
        caveLayer.update(
            // Event mode shows only the event's own points (EventLayer); the catalogue is hidden.
            caves = if (eventMode) emptyList() else caves,
            // Shown even with caves hidden: a cave picked from search should still be on the map.
            selected = if (eventMode) null else selectedCave,
            showLabels = showCaveNames,
            zoom = zoomLevel,
            onTap = { selectedCave = it },
            onClusterTap = { box ->
                // A cluster of near-identical points has a degenerate bbox; just step in.
                if (box.latitudeSpan < 1e-4 && box.longitudeSpanWithDateLine < 1e-4) {
                    mapView.controller.setZoom(camera.zoom + 2)
                    mapView.controller.animateTo(box.centerWithDateLine)
                } else {
                    mapView.zoomToBoundingBox(box.increaseByScale(1.6f), true)
                }
            },
        )
    }

    val eventLayer = remember(mapView) { EventLayer(mapView, iconFactory) }
    LaunchedEffect(eventPoints, focusedEventPointId, showCaveNames, eventMode) {
        eventLayer.update(
            points = if (eventMode) eventPoints else emptyList(),
            focusedId = focusedEventPointId,
            showLabels = showCaveNames,
        ) { point ->
            focusedEventPointId = point.id
            // Same sheet as outside event mode: the catalogue cave when the point is one,
            // otherwise a stand-in carrying the custom location's name and position.
            scope.launch {
                selectedCave = point.caveId?.let { caveRepo.byId(it) } ?: eventPointAsCave(point)
            }
        }
    }

    // Anything that closes the sheet (close, Directions, …) also ends the event-point focus.
    LaunchedEffect(selectedCave) {
        if (selectedCave == null) focusedEventPointId = null
    }

    LaunchedEffect(route, plannerOpen) {
        routeLayer.showRoute(if (plannerOpen) route else null)
    }

    // Pins follow the list live, before any route is computed. "My location" is resolved
    // against the last fix at the moment the list changes — good enough for a start pin.
    LaunchedEffect(waypoints, plannerOpen) {
        val fix = locationOverlay.lastFix?.let { GeoPoint(it.latitude, it.longitude) }
        routeLayer.showWaypoints(
            if (!plannerOpen) {
                emptyList()
            } else {
                waypoints.mapIndexedNotNull { i, w ->
                    w.resolve(fix)?.let { it to waypointBadge(i, waypoints.size) }
                }
            },
        )
    }

    LaunchedEffect(caveStatus, fetchingCaves, routing) {
        if (caveStatus != null && !fetchingCaves && !routing) {
            kotlinx.coroutines.delay(5_000)
            caveStatus = null
        }
    }

    // One-time upgrade: caves stored before schema v2 lack cave:ref and their tags, and the
    // old 1 km border dropped six real caves. Re-fetching Hungary fixes both. Silent no-op when
    // the catalogue is already current; if it fails (no signal) it simply retries next launch.
    LaunchedEffect(Unit) {
        if (caveRepo.needsTagRefresh() && !fetchingCaves) {
            fetchingCaves = true
            val result = caveRepo.downloadHungary(System.currentTimeMillis()) { caveStatus = "Updating cave details: $it" }
            fetchingCaves = false
            caveStatus = result.fold(
                onSuccess = { "Cave catalogue updated: $it entrances with full details" },
                onFailure = { "Cave details update failed (${it.message}); will retry next launch." },
            )
        }
    }

    LaunchedEffect(mapView) { mapView.applyCamera(camera) }
    LaunchedEffect(baseLayer) { layerController.setBase(baseLayer) }
    LaunchedEffect(overlayLayers) { layerController.setOverlays(overlayLayers) }
    LaunchedEffect(offlineOnly) { layerController.setOfflineOnly(offlineOnly) }

    var previousLocateMode by remember { mutableStateOf(locateMode) }
    LaunchedEffect(locateMode, hasLocationPermission) {
        if (previousLocateMode == LocateMode.FOLLOW_ROTATE && locateMode != LocateMode.FOLLOW_ROTATE) {
            setOrientation(0f)
        }
        // Entering rotate mode: turn to the current heading now. The compass collector only
        // reacts to a change of ≥1.5°, so with the phone held still it would never rotate.
        if (locateMode == LocateMode.FOLLOW_ROTATE && previousLocateMode != LocateMode.FOLLOW_ROTATE) {
            locationOverlay.heading?.let { setOrientation(-it) }
        }
        previousLocateMode = locateMode
        if (!hasLocationPermission || locateMode == LocateMode.OFF) {
            locationOverlay.disableFollowLocation()
            // OFF means "stop moving the map", not "hide me": once permission is granted the
            // dot stays, so you can look at a planned route and still see where you are.
            if (hasLocationPermission) locationOverlay.enableMyLocation() else locationOverlay.disableMyLocation()
            mapView.invalidate()
            return@LaunchedEffect
        }
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        locationOverlay.myLocation?.let {
            headingProvider.lastLocation = locationOverlay.lastFix
            mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude))
        }
    }

    // The compass runs whenever the app is on screen and location is allowed, because the
    // marker's heading beam needs it — not only in rotate mode. Stopped when the app goes to the
    // background, so it costs nothing in a pocket.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            headingProvider.headings().collect { bearing ->
                headingProvider.lastLocation = locationOverlay.lastFix
                if (headingChanged(heading, bearing)) {
                    heading = bearing
                    locationOverlay.heading = bearing
                    if (locateMode == LocateMode.FOLLOW_ROTATE) {
                        setOrientation(-bearing)
                    } else {
                        mapView.invalidate()
                    }
                }
            }
        }
    }

    // The search corpus is loaded once when search opens rather than observed: the catalog
    // only changes on an explicit fetch, and observing 2,000 rows continuously is wasteful.
    LaunchedEffect(searchOpen, totalCaves) {
        if (searchOpen && searchCorpus.size != totalCaves) {
            searchCorpus = caveRepo.all()
        }
    }

    LaunchedEffect(searchQuery, searchCorpus, camera.lat, camera.lon) {
        val fix = locationOverlay.lastFix
        searchResults = searchCaves(
            caves = searchCorpus,
            query = searchQuery,
            fromLat = fix?.latitude ?: camera.lat,
            fromLon = fix?.longitude ?: camera.lon,
        )
    }

    fun openPlannerTo(cave: Cave) {
        waypoints = listOf(
            if (hasLocationPermission) Waypoint.MyLocation else Waypoint.Unset,
            Waypoint.CavePoint(cave),
        )
        route = null
        plannerOpen = true
        plannerCollapsed = false
    }

    /** From a cave card while planning: fill the end if it's empty, else insert before it. */
    fun addCaveToRoute(cave: Cave) {
        editWaypoints { list ->
            if (list.last() == Waypoint.Unset) {
                list.dropLast(1) + Waypoint.CavePoint(cave)
            } else {
                list.dropLast(1) + Waypoint.CavePoint(cave) + list.last()
            }
        }
    }

    fun computeRoute() {
        val fix = locationOverlay.lastFix?.let { GeoPoint(it.latitude, it.longitude) }
        val points = waypoints.map { it.resolve(fix) }
        if (points.any { it == null }) {
            caveStatus = if (waypoints.contains(Waypoint.MyLocation) && fix == null) {
                "No position fix yet — switch on the locate button first."
            } else {
                "Set every point before routing."
            }
            return
        }
        routing = true
        scope.launch {
            val result = RouteService.route(points.filterNotNull())
            routing = false
            result.fold(
                onSuccess = {
                    route = it
                    // Follow mode would immediately drag the map back to you and off the route.
                    if (locateMode != LocateMode.OFF) locateMode = LocateMode.OFF
                    // Frame the whole route so a long multi-stop plan is visible at once.
                    mapView.zoomToBoundingBox(
                        org.osmdroid.util.BoundingBox.fromGeoPointsSafe(it.points).increaseByScale(1.3f),
                        true,
                    )
                },
                onFailure = {
                    route = null
                    caveStatus = it.message ?: "Could not plan a route."
                },
            )
        }
    }

    /**
     * Shortest order through every unvisited point, then a real hiking route along it.
     *
     * Where it starts: before anything is visited, from the event's start spot; once you've
     * visited something you're mid-course, so from where you are now. Ordering uses straight-line
     * distances (instant), and only the chosen order is routed — routing every pair of 30 points
     * would be 435 routes, minutes on a phone.
     */
    fun planEventRoute() {
        val event = activeEvent ?: return
        val todo = eventPoints.filterNot { it.visited }
        if (todo.isEmpty()) {
            caveStatus = "Every point is visited — nothing left to plan."
            return
        }
        val fix = locationOverlay.lastFix?.let { GeoPoint(it.latitude, it.longitude) }
        val fixedStart = if (event.startKind == EventEntity.START_POINT && event.startLat != null && event.startLon != null) {
            GeoPoint(event.startLat, event.startLon)
        } else {
            null
        }
        val fromHere = eventPoints.any { it.visited } || fixedStart == null
        val start = if (fromHere) fix else fixedStart
        if (start == null) {
            caveStatus = "No position fix — switch on locate, or give the event a fixed start."
            return
        }
        val end = if (event.finishAtStart) fixedStart ?: start else null

        val nodes = listOf(start) + todo.map { GeoPoint(it.lat, it.lon) } + listOf(end ?: start)
        val dist = Array(nodes.size) { i ->
            DoubleArray(nodes.size) { j ->
                haversineMetres(nodes[i].latitude, nodes[i].longitude, nodes[j].latitude, nodes[j].longitude)
            }
        }
        val order = TourSolver.solve(dist, todo.size, returnToEnd = end != null)

        val startWaypoint: Waypoint = if (fromHere) {
            Waypoint.MyLocation
        } else {
            Waypoint.NamedPoint("Start", start.latitude, start.longitude)
        }
        val stops = order.map { i -> todo[i].let { Waypoint.NamedPoint(it.name, it.lat, it.lon) } }
        val endWaypoint = end?.let {
            if (fromHere && fixedStart == null) Waypoint.MyLocation
            else Waypoint.NamedPoint("Finish", it.latitude, it.longitude)
        }
        waypoints = listOf(startWaypoint) + stops + listOfNotNull(endWaypoint)
        route = null
        plannerOpen = true
        plannerCollapsed = true
        focusedEventPointId = null
        caveStatus = "Visiting order for ${todo.size} point${if (todo.size == 1) "" else "s"} — routing…"
        computeRoute()
    }

    Box(modifier = Modifier.fillMaxSize().onSizeChanged { rootHeightPx = it.height }) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {

            // ---- compass: only when the map isn't north-up; tap to reset -----------------
            val tilted = mapOrientation in 1f..359f
            if (tilted) {
                Surface(
                    onClick = {
                        if (locateMode == LocateMode.FOLLOW_ROTATE) locateMode = LocateMode.FOLLOW
                        val from = mapOrientation
                        // Spin the short way round.
                        val to = if (from > 180f) 360f else 0f
                        scope.launch {
                            animate(from, to, animationSpec = tween(350)) { v, _ ->
                                mapView.mapOrientation = v
                                mapView.invalidate()
                            }
                            setOrientation(0f)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 64.dp, end = 12.dp)
                        .size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Needle points to north on the map: the map is drawn rotated by
                        // mapOrientation, so north on screen is at that same angle.
                        Icon(
                            Icons.Filled.Navigation,
                            contentDescription = "Reset to north",
                            tint = Color(0xFFD93025),
                            modifier = Modifier.size(28.dp).rotate(mapOrientation),
                        )
                    }
                }
            }

            // ---- top bar: event banner (in event mode), search, options ---------------
            Row(
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (eventMode) {
                    // Which event, progress, and the two things you do mid-course.
                    val visited = eventPoints.count { it.visited }
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Color(0xFFE8590C),
                        contentColor = Color.White,
                        shape = MaterialTheme.shapes.small,
                        tonalElevation = 3.dp,
                    ) {
                        Row(
                            Modifier.padding(start = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Flag, contentDescription = null)
                            Column(Modifier.weight(1f).padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
                                Text(
                                    activeEvent?.name ?: "Event",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "$visited/${eventPoints.size} visited",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            TextButton(
                                onClick = { planEventRoute() },
                                enabled = !routing && visited < eventPoints.size,
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                            ) { Text("Plan route") }
                            IconButton(onClick = {
                                eventRepo.activeEventId = null
                                activeEventId = null
                                focusedEventPointId = null
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Exit event mode")
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                Spacer(Modifier.padding(horizontal = 3.dp))

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                ) {
                    IconButton(onClick = { searchOpen = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search caves")
                    }
                }

                Spacer(Modifier.padding(horizontal = 3.dp))

                OptionsMenu(
                    offlineOnly = offlineOnly,
                    showCaveNames = showCaveNames,
                    onToggleOffline = { offlineOnly = it },
                    onToggleCaveNames = { showCaveNames = it },
                    onDownloadArea = { showDownloadSheet = true },
                    onFetchCaves = { if (!fetchingCaves) showFetchScopeDialog = true },
                    onOpenPacks = onOpenPacks,
                    onSetMapyKey = { showMapyKeyDialog = true },
                )
            }

            if (searchOpen) {
                CaveSearchBar(
                    query = searchQuery,
                    results = searchResults,
                    totalCaves = totalCaves,
                    onQueryChange = { searchQuery = it },
                    onPick = { hit ->
                        searchOpen = false
                        // Clear on pick, not just on dismiss: otherwise reopening search
                        // shows the previous query and typing appends to it.
                        searchQuery = ""
                        val slot = pickingWaypoint
                        val forEvent = pickingForEvent
                        if (forEvent != null) {
                            // Opened from the event editor: fill the start or add a point, then
                            // reopen the editor on the same event.
                            pickingForEvent = null
                            val (eventId, target) = forEvent
                            scope.launch {
                                if (target == EventPickTarget.START) {
                                    eventRepo.event(eventId).firstOrNull()?.let {
                                        eventRepo.update(
                                            it.copy(
                                                startKind = EventEntity.START_POINT,
                                                startLat = hit.cave.lat,
                                                startLon = hit.cave.lon,
                                                startLabel = hit.cave.displayName,
                                            ),
                                        )
                                    }
                                } else if (!eventRepo.addCave(eventId, hit.cave)) {
                                    caveStatus = "${hit.cave.displayName} is already in this event."
                                }
                            }
                            editingEventId = eventId
                            showEventSheet = true
                        } else if (slot != null) {
                            // Search was opened from a planner row: fill that row, don't
                            // select the cave or move the map.
                            pickingWaypoint = null
                            editWaypoints { list ->
                                list.toMutableList().also { it[slot] = Waypoint.CavePoint(hit.cave) }
                            }
                        } else {
                            selectedCave = hit.cave
                            mapView.controller.setZoom(
                                maxOf(camera.zoom, CLUSTER_BELOW_ZOOM + 1.0),
                            )
                            mapView.controller.animateTo(GeoPoint(hit.cave.lat, hit.cave.lon))
                        }
                    },
                    onClose = {
                        searchOpen = false
                        searchQuery = ""
                        pickingWaypoint = null
                        // Cancelled a pick for the event editor: go back to it.
                        pickingForEvent?.let { (eventId, _) ->
                            editingEventId = eventId
                            showEventSheet = true
                        }
                        pickingForEvent = null
                    },
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                )
            }

            // ---- bottom stack: side buttons, status, route summary, cave detail ----------
            // The side buttons live in the same column as the cards, above them, so an open
            // cave card pushes them up instead of covering them.
            Column(
                // fillMaxWidth: without it the column shrinks to its widest child, which with
                // no card open is the button column — and the whole stack lands mid-screen.
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.align(Alignment.End).padding(end = 8.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (fetchingCaves || routing) {
                        CircularProgressIndicator(modifier = Modifier.height(24.dp))
                    }
                    SmallFloatingActionButton(onClick = { showLayersSheet = true }) {
                        Icon(Icons.Filled.Layers, contentDescription = "Map layers")
                    }
                    SmallFloatingActionButton(
                        onClick = {
                            // Opens straight into the active event's editor when there is one.
                            editingEventId = activeEventId
                            showEventSheet = true
                        },
                        containerColor = if (eventMode) Color(0xFFE8590C) else FloatingActionButtonDefaults.containerColor,
                    ) {
                        Icon(
                            Icons.Filled.Flag,
                            contentDescription = "Events",
                            tint = if (eventMode) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    SmallFloatingActionButton(
                        onClick = {
                            if (plannerOpen) {
                                plannerCollapsed = !plannerCollapsed
                            } else {
                                plannerOpen = true
                                plannerCollapsed = false
                            }
                        },
                        containerColor = if (plannerOpen) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            FloatingActionButtonDefaults.containerColor
                        },
                    ) {
                        Icon(
                            Icons.Filled.Directions,
                            contentDescription = "Directions",
                            tint = if (plannerOpen) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    SmallFloatingActionButton(
                        onClick = {
                            cavesVisible = !cavesVisible
                            layerPrefs.cavesVisible = cavesVisible
                        },
                        containerColor = if (cavesVisible) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            FloatingActionButtonDefaults.containerColor
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_cave_toggle),
                            contentDescription = if (cavesVisible) "Hide caves" else "Show caves",
                            tint = if (cavesVisible) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    SmallFloatingActionButton(
                        onClick = {
                            if (!hasLocationPermission) {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                                return@SmallFloatingActionButton
                            }
                            locateMode = when (locateMode) {
                                LocateMode.OFF -> LocateMode.FOLLOW
                                LocateMode.FOLLOW -> LocateMode.FOLLOW_ROTATE
                                LocateMode.FOLLOW_ROTATE -> LocateMode.OFF
                            }
                        },
                        containerColor = when (locateMode) {
                            LocateMode.OFF -> FloatingActionButtonDefaults.containerColor
                            else -> MaterialTheme.colorScheme.primary
                        },
                    ) {
                        Icon(
                            imageVector = when (locateMode) {
                                LocateMode.OFF -> Icons.Filled.LocationSearching
                                LocateMode.FOLLOW -> Icons.Filled.MyLocation
                                LocateMode.FOLLOW_ROTATE -> Icons.Filled.Explore
                            },
                            contentDescription = when (locateMode) {
                                LocateMode.OFF -> "Centre on my location"
                                LocateMode.FOLLOW -> "Align map to heading"
                                LocateMode.FOLLOW_ROTATE -> "Stop following"
                            },
                            tint = if (locateMode == LocateMode.OFF) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onPrimary
                            },
                        )
                    }
                }

                caveStatus?.let { message ->
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        tonalElevation = 3.dp,
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }

                // Last in the stack, so it sits at the bottom edge and other cards stack above.
                if (plannerOpen) {
                    RoutePlannerCard(
                        waypoints = waypoints,
                        plan = route,
                        routing = routing,
                        collapsed = plannerCollapsed,
                        selectedCave = selectedCave,
                        canUseMyLocation = hasLocationPermission,
                        onSetWaypoint = { i, w ->
                            editWaypoints { it.toMutableList().also { l -> l[i] = w } }
                        },
                        onSearchFor = { i ->
                            pickingWaypoint = i
                            searchOpen = true
                        },
                        onUseMapCentre = { i ->
                            val c = mapView.mapCenter
                            editWaypoints {
                                it.toMutableList().also { l -> l[i] = Waypoint.MapPoint(c.latitude, c.longitude) }
                            }
                        },
                        onMove = { from, to ->
                            editWaypoints { it.toMutableList().also { l -> l.add(to, l.removeAt(from)) } }
                        },
                        onRemove = { i -> editWaypoints { it.filterIndexed { j, _ -> j != i } } },
                        onAddStop = {
                            editWaypoints { it.dropLast(1) + Waypoint.Unset + it.last() }
                        },
                        onReverse = { editWaypoints { it.reversed() } },
                        onRoute = { computeRoute() },
                        onToggleCollapsed = { plannerCollapsed = !plannerCollapsed },
                        onClose = {
                            plannerOpen = false
                            route = null
                            pickingWaypoint = null
                        },
                    )
                }
            }

            if (downloadState is DownloadState.Running) {
                val running = downloadState as DownloadState.Running
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp).fillMaxWidth(0.9f),
                    tonalElevation = 3.dp,
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "${running.layerLabel} (${running.layerIndex}/${running.layerCount}) — " +
                                "z${running.currentZoom}, ${running.done}/${running.total} tiles",
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { running.fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = { downloader.cancel() }) { Text("Cancel") }
                    }
                }
            }
        }

        // Cave sheet last, so it draws over the side buttons and cards instead of moving them.
        selectedCave?.let { cave ->
            CaveSheet(
                cave = cave,
                distanceMetres = locationOverlay.lastFix?.let {
                    haversineMetres(it.latitude, it.longitude, cave.lat, cave.lon)
                },
                plannerOpen = plannerOpen,
                eventName = activeEvent?.name,
                visited = focusedEventPoint?.visited,
                onToggleVisited = {
                    focusedEventPoint?.let { p -> scope.launch { eventRepo.setVisited(p, !p.visited) } }
                },
                isCatalogueCave = cave.id >= 0,
                halfHeightPx = rootHeightPx * 0.5f,
                fullHeightPx = rootHeightPx - with(density) { (statusBarInset + 8.dp).toPx() },
                onDirections = {
                    openPlannerTo(cave)
                    selectedCave = null
                },
                onAddToRoute = {
                    addCaveToRoute(cave)
                    selectedCave = null
                },
                onAddToEvent = {
                    val id = activeEventId ?: return@CaveSheet
                    scope.launch {
                        caveStatus = if (eventRepo.addCave(id, cave)) {
                            "Added ${cave.displayName} to ${activeEvent?.name}"
                        } else {
                            "${cave.displayName} is already in ${activeEvent?.name}"
                        }
                    }
                },
                onCentre = { mapView.controller.animateTo(GeoPoint(cave.lat, cave.lon)) },
                onClose = { selectedCave = null },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (showEventSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEventSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            EventSheet(
                repo = eventRepo,
                editingId = editingEventId,
                activeId = activeEventId,
                selectedCave = selectedCave,
                mapCentre = { mapView.mapCenter.let { GeoPoint(it.latitude, it.longitude) } },
                onEdit = { editingEventId = it },
                onSetActive = { id ->
                    eventRepo.activeEventId = id
                    activeEventId = id
                    focusedEventPointId = null
                    if (id != null) {
                        // Entering: get out of the way and frame the event's points.
                        showEventSheet = false
                        selectedCave = null
                        scope.launch {
                            val pts = eventRepo.points(id).firstOrNull().orEmpty()
                            if (pts.size == 1) {
                                mapView.controller.animateTo(GeoPoint(pts[0].lat, pts[0].lon))
                            } else if (pts.size > 1) {
                                mapView.zoomToBoundingBox(
                                    org.osmdroid.util.BoundingBox.fromGeoPointsSafe(pts.map { GeoPoint(it.lat, it.lon) })
                                        .increaseByScale(1.4f),
                                    true,
                                )
                            }
                        }
                    }
                },
                onSearchCave = { eventId, target ->
                    pickingForEvent = eventId to target
                    showEventSheet = false
                    searchOpen = true
                },
                onMessage = { caveStatus = it },
                lookupCave = { caveRepo.byId(it) },
            )
        }
    }

    if (showLayersSheet) {
        ModalBottomSheet(onDismissRequest = { showLayersSheet = false }) {
            LayersSheet(
                selectedBase = baseLayer,
                selectedOverlays = overlayLayers,
                onBaseSelected = {
                    if (it.id == MapLayers.MAPY_OUTDOOR.id && !MapyKey.isSet) {
                        // Without a key every tile fails; ask for one instead of showing a blank map.
                        showLayersSheet = false
                        showMapyKeyDialog = true
                    } else {
                        baseLayer = it
                        layerPrefs.baseId = it.id
                    }
                },
                onOverlayToggled = { layer, enabled ->
                    overlayLayers = if (enabled) {
                        overlayLayers + layer
                    } else {
                        overlayLayers.filterNot { it.id == layer.id }
                    }
                    layerPrefs.overlayIds = overlayLayers.map { it.id }
                },
                onOpenPacks = {
                    showLayersSheet = false
                    onOpenPacks()
                },
                mapyKeySet = MapyKey.isSet,
            )
        }
    }

    if (showMapyKeyDialog) {
        MapyKeyDialog(
            current = MapyKey.value,
            onDismiss = { showMapyKeyDialog = false },
            onSave = { key ->
                showMapyKeyDialog = false
                MapyKey.value = key
                layerPrefs.mapyApiKey = key
                if (key.isEmpty()) {
                    if (baseLayer.id == MapLayers.MAPY_OUTDOOR.id) {
                        baseLayer = MapLayers.OPEN_TOPO
                        layerPrefs.baseId = baseLayer.id
                    }
                    caveStatus = "Mapy.com key removed."
                } else {
                    baseLayer = MapLayers.MAPY_OUTDOOR
                    layerPrefs.baseId = baseLayer.id
                    // Drop tiles that failed without a key so they are fetched again with it.
                    mapView.tileProvider.clearTileCache()
                    mapView.invalidate()
                    caveStatus = "Mapy.com key saved."
                }
            },
        )
    }

    if (showFetchScopeDialog) {
        FetchScopeDialog(
            onDismiss = { showFetchScopeDialog = false },
            onCurrentView = {
                showFetchScopeDialog = false
                fetchingCaves = true
                caveStatus = "Fetching cave entrances for this view…"
                val box = mapView.boundingBox
                scope.launch {
                    val result = caveRepo.downloadArea(box, System.currentTimeMillis())
                    fetchingCaves = false
                    caveStatus = result.fold(
                        onSuccess = { "Found $it cave entrances in view" },
                        onFailure = { "Cave fetch failed: ${it.message}" },
                    )
                }
            },
            onAllHungary = {
                showFetchScopeDialog = false
                fetchingCaves = true
                scope.launch {
                    val result = caveRepo.downloadHungary(System.currentTimeMillis()) { caveStatus = it }
                    fetchingCaves = false
                    caveStatus = result.fold(
                        onSuccess = { "$it cave entrances stored for all of Hungary" },
                        onFailure = { "Hungary fetch failed: ${it.message}" },
                    )
                }
            },
        )
    }

    if (showDownloadSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showDownloadSheet = false },
            sheetState = sheetState,
        ) {
            DownloadAreaSheet(
                downloader = downloader,
                layers = listOf(baseLayer) + overlayLayers,
                currentZoom = camera.zoom.toInt(),
                boundingBoxProvider = { mapView.boundingBox },
                onStart = { name, zoomMin, zoomMax ->
                    val box = mapView.boundingBox
                    val queue = downloader.downloadableOf(listOf(baseLayer) + overlayLayers)
                    downloader.start(context, queue, box, zoomMin, zoomMax) { tiles ->
                        scope.launch {
                            repo.add(
                                TilePack(
                                    id = UUID.randomUUID().toString(),
                                    name = name.ifBlank { "Area %.3f,%.3f".format(camera.lat, camera.lon) },
                                    north = box.latNorth,
                                    south = box.latSouth,
                                    east = box.lonEast,
                                    west = box.lonWest,
                                    zoomMin = zoomMin,
                                    zoomMax = zoomMax,
                                    tileCount = tiles,
                                    createdAtEpochMs = System.currentTimeMillis(),
                                    sourceName = queue.joinToString(" + ") { it.label },
                                ),
                            )
                        }
                    }
                    showDownloadSheet = false
                },
            )
        }
    }
}

@Composable
private fun FetchScopeDialog(
    onDismiss: () -> Unit,
    onCurrentView: () -> Unit,
    onAllHungary: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fetch cave entrances") },
        text = {
            Column {
                Text(
                    "From OpenStreetMap via Overpass. Results are stored on the device and " +
                        "work offline afterwards.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "All of Hungary is ~7,800 entrances and about 2 MB. It takes roughly " +
                        "15 seconds and only needs doing once.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { Button(onClick = onAllHungary) { Text("All of Hungary") } },
        dismissButton = { OutlinedButton(onClick = onCurrentView) { Text("This view only") } },
    )
}

@Composable
private fun DownloadAreaSheet(
    downloader: TilePackDownloader,
    layers: List<LayerSource>,
    currentZoom: Int,
    boundingBoxProvider: () -> BoundingBox,
    onStart: (name: String, zoomMin: Int, zoomMax: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var zoomMin by remember { mutableStateOf(currentZoom.coerceIn(8, 16)) }
    var zoomMax by remember { mutableStateOf((currentZoom + 2).coerceIn(zoomMin, 17)) }

    val box = boundingBoxProvider()
    val downloadable = downloader.downloadableOf(layers)
    val skipped = layers - downloadable.toSet()
    val estimate = remember(zoomMin, zoomMax, box.latNorth, box.lonWest, downloadable) {
        downloadable.sumOf { downloader.estimateTiles(it, box, zoomMin, zoomMax) }
    }

    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Text("Download this area", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "The current map view defines the area. Pan and zoom to frame it before downloading.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        ZoomStepper("Min zoom", zoomMin, 6, zoomMax) { zoomMin = it }
        ZoomStepper("Max zoom", zoomMax, zoomMin, 17) { zoomMax = it }

        Spacer(Modifier.height(12.dp))
        Text(
            "≈ $estimate tiles · ${formatBytes(estimate * AVG_TILE_BYTES)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (downloadable.isNotEmpty()) {
            Text(
                "Includes: ${downloadable.joinToString(", ") { it.label }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (skipped.isNotEmpty()) {
            Text(
                "Not downloadable, will be blank offline: ${skipped.joinToString(", ") { it.label }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // Offer the routing tile(s) this area falls in, so "downloaded area" means map *and*
        // directions work offline. Ticked by default; already-installed tiles aren't listed.
        val missingRouting = remember(box.latNorth, box.lonWest, box.latSouth, box.lonEast) {
            RoutingData.segmentsFor(box).filterNot { RoutingData.isInstalled(it) }
        }
        var withRouting by remember { mutableStateOf(true) }
        if (missingRouting.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = withRouting, onCheckedChange = { withRouting = it })
                Column {
                    Text("Also download offline routing data", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${missingRouting.joinToString()} — " +
                            missingRouting.joinToString(" + ") {
                                when (it) {
                                    "E15_N45" -> "~124 MB"
                                    "E20_N45" -> "~65 MB"
                                    else -> "size unknown"
                                }
                            } +
                            ". Covers far more than this area; only needed once.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (withRouting) missingRouting.forEach { RoutingData.download(it) }
                onStart(name, zoomMin, zoomMax)
            },
            enabled = estimate in 1..200_000,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start download")
        }
    }
}

@Composable
private fun ZoomStepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }, enabled = value > min) {
            Text("−")
        }
        Text(
            text = value.toString(),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedButton(onClick = { onChange((value + 1).coerceAtMost(max)) }, enabled = value < max) {
            Text("+")
        }
    }
}

/** Custom (non-cave) event points are shown in the cave sheet via a stand-in with a negative id. */
private fun eventPointAsCave(point: EventPoint) = Cave(
    id = -point.id,
    name = point.name,
    lat = point.lat,
    lon = point.lon,
    ref = null,
    elevationM = null,
    entranceType = null,
    length = null,
    depth = null,
    description = null,
    fetchedAtEpochMs = 0,
)
