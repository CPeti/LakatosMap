# LakatosMap — offline orienteering helper for cave-entrance score events

Android-only field app. Shows an offline outdoors map (paths + contours), plots known
cave entrances, lets you pick which ones are in this year's competition, and plans a
route that maximises score inside a time budget.

## Decisions locked

| Choice | Decision | Why |
|---|---|---|
| Stack | Kotlin + Jetpack Compose, minSdk 26, targetSdk 35 | Android-only; native GPS + offline storage; solver is plain Kotlin |
| Map engine | **osmdroid** (`org.osmdroid:osmdroid-android`) in an `AndroidView` | Raster tiles, `MBTiles`/sqlite offline archives, and a built-in bbox downloader (`CacheManager`) — the single biggest shortcut for the offline requirement |
| Tiles | OpenTopoMap raster (contours + paths), pre-downloaded per area | Karst terrain needs contours; raster is far simpler than vector-with-contours |
| Storage | Room (SQLite) for catalog + events; osmdroid's own sqlite cache for tiles | — |
| Routing metric | Straight-line (Haversine) + configurable speed + per-cave dwell time | Cave entrances are mostly off-trail; path-graph routing deferred (Phase 8) |
| Solver | Greedy insertion → 2-opt / Or-opt → random restarts | 30 POIs is small; sub-200 ms on-device, no server |

### Non-goals (for now)
- No georeferenced OCAD/PDF race-map import.
- No iOS.
- No live multi-device / event-server sync.
- No turn-by-turn navigation — this is a bearing-and-distance tool, not a road sat-nav.

---

## Data model

The catalog is stable; what changes yearly is **which** caves are in and **what they score**.
So selection and score live on the event's join row, never on the cave.

```kotlin
@Entity data class Cave(
  @PrimaryKey val id: Long,      // stable; from source catalog
  val externalRef: String?,      // cadastre / survey number
  val name: String,
  val lat: Double, val lon: Double,
  val elevation: Double?,        // optional, for Tobler later
  val notes: String?
)

@Entity data class Event(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,              // "Karst Score-O 2026"
  val timeBudgetMinutes: Int,    // T
  val startLat: Double, val startLon: Double,
  val finishLat: Double?, val finishLon: Double?,  // null => loop back to start
  val speedKmh: Double = 4.0,
  val dwellMinutesPerCave: Int = 3
)

@Entity(primaryKeys = ["eventId","caveId"]) data class EventEntry(
  val eventId: Long, val caveId: Long,
  val score: Int,
  val included: Boolean = true,  // in this year's competition at all
  val visitedAt: Long? = null    // field punch log
)
```

`EventEntry` carries `visitedAt` so the app doubles as a punch log — it's one nullable
column now and saves a migration later.

---

## Phases

Each phase is independently demoable on a device. Ship Phase 1–3 before touching the solver.

### Phase 0 — Toolchain + skeleton ✅ done
- Android Studio was already installed at `D:\programs\Android\Android Studio`; its bundled
  JBR 21 is the build JDK. Stale `JAVA_HOME` and the JDK 8 on PATH are both unusable — see
  README.
- Android SDK installed via `sdkmanager`: `platform-tools`, `platforms;android-35`,
  `build-tools;35.0.0`.
- Gradle wrapper 8.9 (the standalone Gradle 8.0.2 on this machine cannot configure AGP 8.7).
- Compose app skeleton with version catalog and manual DI.

**Exit:** ✅ `assembleDebug` produces an APK. ⬜ No device or AVD yet, so nothing has run.

### Phase 1 — Map + offline tile packs ✅ done, verified on device
- osmdroid `MapView` wrapped in `AndroidView`, hoisted camera state so Compose survives rotation.
- Set `Configuration.getInstance().userAgentValue` to your app id. **Required** — OSM-family
  servers hard-block the default Java UA.
- OpenTopoMap as an `XYTileSource` (attribution overlay is a licence obligation, not a nicety).
- "Download this area" screen: draw/confirm a bbox, choose max zoom, call
  `CacheManager.downloadAreaAsync`, show progress + tile count + estimated MB, allow cancel.
- Area manager: list downloaded areas, show size, delete one.

**Sizing sanity check:** a 10 × 10 km box at z10–16 is ~2,800 tiles ≈ 50–60 MB raster.
Your "quite small area" is comfortably in phone-storage noise. z17 roughly quadruples it;
cap the picker at z16 by default.

> **Tile-policy caveat, read before shipping:** OpenTopoMap's tile usage policy discourages
> bulk/automated downloading. One small personal area is within the spirit of it; distributing
> the app to a club and having everyone scrape bboxes is not. The clean fixes, in order of
> effort: (a) a Thunderforest Outdoors API key (free tier, contours, explicitly allows this),
> (b) self-host tiles, or (c) build an MBTiles pack once with QGIS/`tilemaker` and ship/
> side-load that file — this also makes the app work on a fresh install with no network at all.
> I'd build the import-an-MBTiles-file path in this phase too; it's ~1 hour with osmdroid's
> `MBTilesFileArchive` and removes the policy question entirely.

**Exit:** ✅ met — with wifi and mobile data disabled, the downloaded area pans and renders
from cache with contours visible.

**Measured, correcting the estimate above:** OpenTopoMap tiles over dense urban terrain run
~40 kB each, not the ~20 kB first assumed (175 tiles → 6.9 MB). A 10 × 10 km box at z10–16
is therefore closer to **110 MB** than 60 MB. Still trivial for a phone; forest tiles, with
far less to draw, should come in well under this.

### Phase 2 — Cave catalog 🟡 partly done
**Done:** Room DB + DAO, and the catalog now comes from **OSM via Overpass** rather than a
file — `natural=cave_entrance` / `natural=sinkhole` nodes for the current map view, keyed on
OSM node id so overlapping fetches upsert. Markers are tappable with a detail sheet. This is
the same source OsmAnd's cave layer uses.

**Still to do:** events, per-event selection, and scoring (below). File importers are now
optional rather than the primary path — add them only if you have a catalogue that OSM lacks.
- ~~**CSV/TSV paste or file**~~ — `id,ref,name,lat,lon,elev,notes`.
- ~~**GPX waypoint import**~~ — `<wpt lat lon><name>`; standard interchange.
- Upsert on `externalRef` so re-importing an updated catalog doesn't duplicate.
- Render as a clustered marker overlay (`RadiusMarkerClusterer`) — 500+ entrances
  un-clustered will stutter on a mid-range phone.
- Tap a marker → bottom sheet with name, ref, coords (decimal **and** UTM/MGRS), notes.

**Exit:** import a real catalog file, see every entrance on the offline map.

### Phase 3 — Events: selection + scores
- Create/edit/delete events (name, T, start, optional finish, speed, dwell).
- Selection list: searchable, sorted by name or distance-from-start, with a checkbox and
  an inline score field per cave. Bulk actions: select-all-in-view, select-by-radius, clear.
- Map shows included caves highlighted and labelled with their score; excluded ones dimmed.
- Header chip: *n selected · total score S*.

**Exit:** set up this year's competition end-to-end and have it persist across restarts.

### Phase 4 — Field mode (do this before the solver) 🟡 partly done
This is the part you actually use standing in a field, and it's cheap.

**Done:** live position puck, runtime permission flow, follow mode, and compass-aligned map
rotation (true north corrected, axis-remapped, wrap-aware smoothing). See README.

**Still to do:** the foreground service, breadcrumbs, punch log, and next-target card.
- ~~Live position~~ from osmdroid's `MyLocationNewOverlay` + `ACCESS_FINE_LOCATION`.
- A **foreground service** for tracking, with a notification — Android kills background
  location otherwise, and the app is useless if it dies in your pocket.
- Next-target card: distance, bearing, and a compass arrow (`TYPE_ROTATION_VECTOR`,
  corrected for magnetic declination via `GeomagneticField`).
- Breadcrumb trail overlay; tap-to-punch marks `visitedAt`.
- Keep-screen-on toggle and a battery-saver interval.

**Exit:** walk outside, position tracks, arrow points at the right cave.

### Phase 4.5 — Routing ✅ done (new, not in the original plan)
Walking routes via BRouter's `hiking-beta` profile. Started as a single cave from your position,
capped at 10 km; now a multi-stop planner with no distance cap, and routes offline from downloaded BRouter
tiles (see README). Gives distance, time, ascent and detour factor.

**This changes a Phase 8 assumption.** The original plan said to start with straight-line
distances and only add path-aware routing if the field proved it necessary. Real routes are
already available for free, and the first measured example came out at **1.2× the straight
line with 123 m of ascent** — so the multi-cave solver below should cost legs by *routed*
distance, not Haversine, wherever a route is available. With the engine now embedded, routing
every pair of 30 caves offline is feasible (~0.5 s per route on desktop), but 435 routes is still
minutes on a phone; the realistic design is a cached
distance matrix, seeded from straight-line estimates and refined with real routes for the
legs the solver actually chooses.

### Phase 3 + 5 — Events and visit-all route ✅ done (as "event mode")
Events with points (caves or custom locations), start spot, finish-at-start, visited marks,
event-only map display, and a shortest-order route through the unvisited points. See README.
Scoring and the time-budget solver (Phase 6) are not done yet.

### Phase 5 — Visit-all route (TSP) — original notes
- `Tour` = ordered cave list + total distance + estimated time.
- Nearest-neighbour seed → 2-opt → Or-opt, from the event start, loop or point-to-point.
- Exact Held–Karp for n ≤ 13 so small cases are provably optimal (and it's a free test oracle
  for the heuristic).
- Draw the tour as a polyline with leg distances and a running ETA.

**Exit:** 30 selected caves ordered sensibly in well under a second.

### Phase 6 — Score route under time budget (the Orienteering Problem)
- Maximise Σ score subject to `time(route) ≤ T`, where
  `time = Σ(leg_km / speedKmh) * 60 + dwell * visits`.
- Greedy insertion ranked by `score / Δtime`, then 2-opt + Or-opt + drop/add moves,
  then ~50 randomised restarts, keep the best. Budget the search at ~300 ms.
- Results panel: chosen caves, total score, predicted time vs T, slack remaining.
- Let the user **pin** a cave as mandatory and **ban** one, then re-solve — in practice you
  always have a "must visit this one" constraint, and it's a one-line change to the seed.
- Live re-solve in field mode: re-plan from current position with remaining time.

**Exit:** a 30-cave / 4-hour scenario returns a plausible high-score loop; pinning works.

### Phase 7 — Polish
- Export planned route + punch log as GPX/CSV to share.
- Dark/high-contrast map option for low light.
- Back up/restore the DB to a file.
- Baseline profile + Compose recomposition check on the map screen.

### Phase 8 — Optional, only if straight-line proves wrong in the field
- Path-aware distances: extract OSM `highway=path|track|footway` for the bbox into a graph,
  run A*, with an off-trail penalty for the last leg to each entrance.
- Tobler's hiking function for slope-adjusted time, needs an elevation raster (SRTM tile).

Hold off on Phase 8 until you've compared predicted vs actual times for a real outing —
in dense karst the straight-line estimate with a tuned `speedKmh` is often closer than a
naive path graph that doesn't know which paths are overgrown.

---

## Risks

| Risk | Mitigation |
|---|---|
| Tile-source policy / availability | Build the MBTiles import path in Phase 1; it makes the app source-independent |
| Background location killed by OEM battery managers (Xiaomi/Samsung are aggressive) | Foreground service + ask the user to exempt the app; test on the actual phone early |
| Solver "correct but useless" — great score, terrible terrain | Pin/ban controls + compare predicted vs actual time after the first real use |
| Marker overlay jank with a large catalog | Clustering from the start (Phase 2), not as a later fix |
| Compass unreliable near iron / in caves | Show accuracy state and fall back to GPS course-over-ground when moving |

## Suggested module layout

```
app/
  core-db/        Room entities, DAOs, migrations
  core-geo/       Haversine, UTM/MGRS, bearing, declination
  feature-map/    osmdroid wrapper, overlays, tile-pack manager
  feature-caves/  catalog import, list, detail
  feature-event/  event editing, selection, scoring
  feature-route/  TSP + OP solvers (pure Kotlin, JVM-unit-testable)
  feature-field/  location service, compass, punch log
```

Keep `feature-route` free of Android imports — the solvers are then plain JVM unit tests,
which is where nearly all of the real logic risk lives.

## First three sessions

1. Phase 0 + Phase 1 up to a plain online OpenTopoMap view.
2. Finish Phase 1: bbox download, area manager, verify in aeroplane mode.
3. Phase 2: Room + CSV import + clustered markers.
