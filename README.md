# LakatosMap

Offline orienteering helper for cave-entrance score events. Android only.
See [PLAN.md](PLAN.md) for the phase roadmap.

## Status

- **Phase 0 — toolchain + skeleton:** done.
- **Phase 1 — offline map:** done and verified on a Pixel 7 (Android 17 / API 37).
- **Cave layer + live position:** done and verified on device (pulled forward from Phases 2/4).
- Remaining Phase 2 work (events, selection, scoring) and Phases 5+ not started.

Verified on device: OpenTopoMap tiles render with contours; area download reports live
progress and completes; downloaded areas persist and are listed; **with wifi and mobile data
both disabled, the map pans and renders entirely from cache**; deleting an area really
evicts its tiles (`cache.db` 18.9 MB → 24 KB).

Also verified: options menu; cave name labels; accent/punctuation-folded search ("palvolgy" →
Pál-völgyi-barlang); walking route to Pál-völgyi-barlang (11.6 km, 2 h 22 min, ↑123 m, 1.2×
detour) drawn on the map; multi-stop routes from the Directions planner; and offline routing
with wifi off (same result as online, "computed offline").

Also verified: layer switching (OpenTopoMap ⇄ Esri satellite) with the Turistautak overlay
stacked on top, surviving an app restart; a two-layer sequential download (OpenTopoMap +
Turistautak) rendering together with wifi and mobile data disabled.

Also verified: 61 cave entrances fetched from Overpass for the Buda Hills and rendered as
tappable markers; marker tap opens the detail sheet; location permission flow, blue position
puck, follow mode, and compass-aligned map rotation (heading shown live in the chip).

Not yet exercised: MBTiles side-loading (no `.mbtiles` file to hand).

## Controls

Settings and one-off actions live in the **options menu** (⋮, top right): download this
area, fetch cave entrances, offline maps, plus two checkboxes — *Show cave names* and
*Offline mode*. Next to the menu is **search**.

Things toggled while looking at the map are **side buttons** (bottom right): map layers,
show/hide caves (persisted), and locate. They sit in the same column as the cave/route
cards, above them, so an open card pushes them up rather than covering them. With caves
hidden, a cave selected from search is still drawn.

### Cave name labels

osmdroid has no marker-label concept, so the name is rendered into the icon bitmap itself
(`map/CaveIconFactory.kt`), with a white halo so it stays readable over both dark forest and
pale limestone. The consequence is the anchor: a labelled bitmap is far wider than the symbol,
so anchoring at 0.5 would put the *text's* centre on the coordinate and leave the symbol west
of the real entrance. The anchor is therefore computed per icon. Bitmaps are LRU-cached;
rebuilding them on every pan would stutter.

Labels are not collision-avoided — in a dense cluster they overlap. Fixing that needs a label
placement pass, which is only worth it if it proves annoying in the field.

## Search

Magnifier in the top bar. Matches on name and `ref`, ranked by prefix match first and then
distance from your position (falling back to map centre), showing distance per hit.

Matching normalises **both** accents and punctuation — `data/CaveSearch.kt`. Both halves are
needed for Hungarian: accents because nobody types á/ö/ő with cold hands, and punctuation
because the names are relentlessly hyphenated. "Pál-völgyi-barlang" folds to
`palvolgyibarlang`, so `palvolgy` finds it. Stripping only accents leaves `pal-volgyi-barlang`,
where the hyphen defeats the substring match and the search looks broken for the most famous
cave in Budapest.

The search corpus is loaded once when search opens rather than observed continuously.

## Route planning

**Directions** side button opens a non-modal planner: an ordered list of waypoints — start
(defaults to *My location*), any number of stops, end. Each row can be set to my location, the
map centre, a cave from search, or the currently selected cave; rows move up/down, can be
removed, and the whole list can be reversed. Numbered pins (S, 1, 2… E) show on the map as
soon as a point is set. The planner collapses to a one-line summary for walking. From a cave
card: *Directions here* (when no plan is open) or *Add to route* (while planning).

Routing uses **BRouter** with the `hiking-beta` profile, which prefers paths, tracks and marked
trails and weights climbing. The summary shows distance, walking time, ascent, the detour
factor against the straight line, and whether it was **computed offline or online**. There is
no distance limit (a 213 km test route through a stop came back in 1.3 s).

### Offline routing

The BRouter engine itself is embedded (`app/libs/brouter-1.7.10-all.jar`, MIT) along with the
`hiking-beta` profile (`assets/brouter/`). It is the same engine version brouter.de runs, so
results match: the same three waypoints gave 13,053 m / 9,760 s / ↑211 m both online and on
the desktop engine, and 13.0 km / 2 h 43 min / ↑210 m on the phone with wifi off.

Routing data comes in **fixed 5°×5° tiles** — the only size it exists in. Hungary needs two:
`E15_N45` (~124 MB: west incl. Budapest, Buda Hills, Pilis, Bakony, Mecsek) and `E20_N45`
(~65 MB: east incl. Bükk, Aggtelek). They live in *Offline maps → Routing data*, each area
card says whether offline routing is ready there, and *Download this area* offers the tile
the area falls in (ticked by default).

`RouteService` routes **offline whenever every waypoint's tile is installed** (bounding box
padded ~5 km, since a path can stray outside it), and only otherwise calls brouter.de. With
no signal and a tile missing, the error names the tile to download.

## Cave detail sheet

Tapping a cave opens a draggable sheet (ut.no style) drawn over the map and side buttons, with
three resting heights: **collapsed** (ends exactly on the line under name, actions and key facts),
**half** (map visible above; the content scrolls inside), and **full**. Only the header drags;
the content only scrolls, so reading at half height never jumps the sheet. Below the header:

- **Photos** from the register's gallery (survey maps included), full-screen viewer with swipe,
  pinch-zoom and double-tap zoom. Coil's disk cache keeps viewed photos offline.

- **National cave register** — fetched live from termeszetvedelem.hu using OSM `cave:ref`
  (`data/Cadastre.kt`), parsed from the page, cached on the phone so it reads offline after the
  first view. Unit-tested against a saved copy of the real Almási-zsomboly page.
- **OpenStreetMap** — access, protection, notes, coordinates (copyable), Wikipedia/OSM links,
  and every raw tag. Caves now store `cave:ref` and all tags (Room schema v2, migrated in place).

The register publishes no coordinates (cave locations are withheld deliberately), so it can't add
caves to the map: the map shows the ~2,050 entrances that are mapped in OSM, out of the register's
5,000+.

## Event mode

**Event** side button (flag). An event is a named set of points — catalogue caves and/or custom
locations — plus a **start spot** and a *finish back at the start* switch. Events live in their
own database (`events.db`), so creating them never touched the cave catalogue's schema.

- **Editor:** add points by cave search, the selected cave, the map centre, or typed
  coordinates (decimal `47.5329, 19.0161` or DMS `47°31'58"N 19°00'58"E`, previewed as you
  type). The start can be any of those, or *My location* resolved when planning. Each point has
  a visited checkbox; *Reset visited* clears them.
- **Enter event mode:** the map shows **only the event's points** — orange to visit, grey ✓
  visited, ∩ for caves, • for custom locations — and the catalogue is hidden. A banner shows
  progress. Persists across restarts.
- **Event point sheet:** tapping a point opens the same cave sheet as outside event mode
  (register, photos, OSM), with **Mark visited / Unmark** as the first action. Custom locations
  use the same sheet with their name and coordinates.
- **Save register & photos for offline** (event editor): fetches every register entry for the
  event's caves and downloads all their photos, with progress and a saved/total status. Photos go
  to an app-private disk cache in `filesDir` (not `cacheDir`, which Android can wipe), with HTTP
  cache headers ignored since the register sends none. Lakatos (9 caves): 9 entries, 36 photos,
  11 MB, ~10 s; verified displaying with wifi and data off.
- **Plan route:** shortest order through every unvisited point, then a real hiking route
  along it in the Directions planner (offline when routing data is downloaded). Before anything
  is visited it starts from the event's start spot; once you've visited something it starts
  from where you are. With *finish at start* it loops back.

Ordering (`route/TourSolver.kt`) is exact (Held–Karp) up to 12 points and nearest-neighbour +
2-opt + Or-opt beyond, unit-tested against the exact solver (within 10 % on random instances).
It orders by **straight-line** distance and only the chosen order is routed — a full routed
distance matrix for 30 points is 435 routes, minutes on a phone. On hilly ground that can
occasionally pick an order that is shorter on paper than on foot.

## Map layers

The layers button opens a picker with a base map and stackable overlays. Each layer shows a
cloud icon indicating whether it can be taken offline — the most important property of a
layer in this app, so it is on the row rather than buried in a help screen. The choice
persists across restarts (`SharedPreferences`), and attribution follows the active layers.

| Layer | Kind | Offline |
|---|---|---|
| OpenTopoMap | base | ✅ |
| Esri World Topo | base | ❌ Esri terms forbid bulk download |
| Satellite (Esri) | base | ❌ as above |
| OpenStreetMap | base | ❌ OSMF tile policy forbids it |
| **Turistautak** | overlay | ✅ |
| Turistautak colour | overlay | ✅ |
| Waymarked Trails | overlay | ❌ |

"Download this area" covers the base map **and** every enabled overlay, sequentially, with
per-layer progress. Layers whose operators forbid bulk download are skipped, and the sheet
says so explicitly ("Not downloadable, will be blank offline: …") rather than letting you
discover it in a forest.

Non-downloadable sources are marked `FLAG_NO_BULK` in their `TileSourcePolicy`, so osmdroid's
CacheManager refuses them outright — the policy is enforced, not just documented.

### Turistautak

The Hungarian waymarked-trail layer from turistautak.openstreetmap.hu. Two things were worth
discovering:

1. **It is an overlay, not a base map.** The tiles are 88–94% transparent, carrying only trail
   lines and blaze symbols. That turns out to be ideal: OpenTopoMap contours underneath,
   Hungarian waymarks on top, which is close to what OsmAnd shows.
2. **The endpoint needs the a/b/c subdomain.** `tile.openstreetmap.hu` does not resolve at
   all; `a.tile.openstreetmap.hu/tt/{z}/{x}/{y}.png` does. The URL was read out of the site's
   own `OpenStreetMap.js` (`OpenLayers.Layer.OSM.tt`). `ttszin` is the same data in blaze
   colours. Serves z8–17; z18 returns an empty tile.

It is a small volunteer-run server (map by Kolesár) — download modest areas only.

## Cave entrances

The fetch button offers two scopes: **this view** or **all of Hungary** (~7,800 raw nodes →
**2,048 after clipping to the border**, ~2 MB, ~15 s, one-off).

Below zoom 14 (`CLUSTER_BELOW_ZOOM`) nearby caves merge into numbered clusters; tapping one
zooms to fit its members. Clustering is a fixed grid in world-pixel space (~50 dp cells), so
membership depends only on the cave and the integer zoom — clusters don't reshuffle while
panning, and are only recomputed when the zoom crosses an integer. The **selected cave is
never clustered**: it's pulled out before grouping and drawn last, on top. Only the
viewport's caves are queried, snapped to a ~2 km grid.

Tapping a cave opens a **non-modal** card — the map stays pannable and zoomable underneath —
and the focused cave's marker turns red.

### Why the border is clipped client-side

Asking Overpass for the country directly (`area["ISO3166-1"="HU"]` + nationwide node
extraction) returns **504 on every public mirror**, including the kumi.systems one after
220 s. A plain bbox answers in ~14 s, so the app over-fetches a bbox and clips to the real
border in `data/HungaryBoundary.kt`.

The boundary is Hungary's OSM outline simplified from 36k points to 1,421 (~1 km tolerance,
27 kB asset). Validated against eight known points, including the discriminating pair
**Baradla (HU, inside)** and **Domica (SK, 1.5 km away, outside)**. A cave within ~1 km of
the border may be classified wrongly; that is the deliberate price of not shipping an 800 kB
polygon.

The cave layer is the same data OsmAnd's cave layer draws: OpenStreetMap `natural=cave_entrance`
nodes (plus `natural=sinkhole`, which mappers use inconsistently for the same thing). The
difference is where it comes from — OsmAnd reads prebuilt offline vector maps, while this app
queries the **Overpass API** for the current map view and caches the results in Room, after
which they are available offline forever.

- `data/OverpassClient.kt` — one POST per area. The body must be **raw**, not form-encoded:
  Overpass answers a form-encoded POST with `406 Not Acceptable`.
- `data/Cave.kt` — keyed on the OSM node id, so re-fetching overlapping areas upserts rather
  than duplicating. Keeps `ref` (the cadastral number Hungarian caves are published under),
  `ele`, length, depth and description when OSM has them.
- Per-event selection and scoring deliberately live elsewhere (the event join row), since the
  catalog is stable and only the yearly subset changes.

Overpass is volunteer-run and rate-limited. The app queries once per area on an explicit tap,
never on a timer; a 429 or 504 is surfaced to the user as "wait a minute and retry".

### Bundled catalogue

The APK ships a snapshot of all Hungarian entrances (`assets/caves_hu.json`, ~720 kB raw,
~90 kB compressed in the APK), loaded on first launch into an empty catalogue, so a fresh
install has caves without any network. An existing catalogue is never overwritten; "Fetch
cave entrances…" still refreshes from Overpass. Regenerate the snapshot before each release:

```
python tools/update_bundled_caves.py
```

It runs the same query and the same border clip as the in-app "All of Hungary" fetch.

## Position and orientation

The locate button cycles **off → follow → follow + rotate**, like every other map app.

- Position marker (`location/HeadingLocationOverlay.kt`): blue dot with a **heading beam** that
  follows the compass even when standing still (falls back to GPS course when there's no compass
  reading). The compass runs whenever the app is on screen with location allowed, and stops in
  the background.
- **Map rotation:** two-finger twist rotates the map. Whenever it isn't north-up a **compass
  button** appears top-right, needle pointing north; tap it to spin back to north (also leaves
  rotate mode).
- Rotation uses `TYPE_ROTATION_VECTOR` via `location/HeadingProvider.kt`, with two corrections
  that matter outdoors: **magnetic → true north** via `GeomagneticField` (~5–6° in Hungary,
  an ~100 m error over a 1 km leg), and axis remapping so the heading is right in landscape.
  Readings are wrap-aware low-pass filtered, so 359°→1° doesn't spin the map through 180°.
- The compass sensor is only registered while rotate mode is on, since it is a real battery
  cost on a long course.

## Building

This machine has a **stale `JAVA_HOME` (`D:\programs\jdk`, which does not exist)**, and the
system `java` on PATH is JDK 8 — too old for AGP 8.7. Android Studio's bundled JBR 21 is
used instead. Either fix `JAVA_HOME` permanently:

```powershell
setx JAVA_HOME "D:\programs\Android\Android Studio\jbr"
```

…or set it per shell before building:

```powershell
$env:JAVA_HOME = "D:\programs\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```

Local toolchain paths:

| Thing | Path |
|---|---|
| Android Studio | `D:\programs\Android\Android Studio` |
| Bundled JDK (JBR 21) | `D:\programs\Android\Android Studio\jbr` |
| Android SDK | `C:\Users\pczum\AppData\Local\Android\Sdk` |
| adb | `…\Sdk\platform-tools\adb.exe` |

Installed SDK packages: `platform-tools`, `platforms;android-35`, `build-tools;35.0.0`.
`local.properties` points at the SDK and is git-ignored.

> The standalone Gradle 8.0.2 at `D:\programs\Gradle` is too old for AGP 8.7 — always use
> `.\gradlew.bat` (8.9), never `gradle`.

## Running

```powershell
$env:JAVA_HOME = "D:\programs\Android\Android Studio\jbr"
.\gradlew.bat installDebug          # needs a connected device
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
```

No device or emulator is currently set up. Either:

- **Physical phone** (recommended — you need real GPS for Phase 4): enable Developer
  Options → USB debugging, plug in, accept the RSA prompt.
- **Emulator**: `sdkmanager "emulator" "system-images;android-35;google_apis;x86_64"`,
  then `avdmanager create avd -n nav35 -k "system-images;android-35;google_apis;x86_64"`.
  Fine for map and UI work; GPS only via mock locations.

## What Phase 1 does

- `NavigatorApp` / `configureOsmdroid` — sets the osmdroid user agent (OSM servers 403 the
  default one) and pins the tile cache under app-private external storage, so no storage
  permission is needed on any API level.
- `map/TileSources.kt` — OpenTopoMap raster source (contours + paths) with an explicit
  `TileSourcePolicy`. The policy is required: `CacheManager` refuses to bulk-download any
  source flagged `NO_BULK`, which is how osmdroid defines stock Mapnik.
- `map/OsmMapView.kt` — `MapView` bound to the Compose lifecycle, hoisted camera, and an
  offline-only toggle.
- `tiles/TilePackDownloader.kt` — wraps `CacheManager.downloadAreaAsync` with progress,
  cancel, and a tile-count estimate.
- `tiles/MbtilesImporter.kt` — side-load a prebuilt `.mbtiles` pack and render from it with
  the network cut.
- `ui/MapScreen.kt` — map, live coords, attribution, download-area sheet.
- `ui/TilePacksScreen.kt` — downloaded areas and imported archives, with delete.

## Known gaps in Phase 1

1. **Overlapping areas share tiles.** osmdroid keeps one global tile cache with no notion of
   ownership, so deleting an area evicts every tile in its bbox/zoom range — including tiles
   another overlapping pack still needs. Observed directly: deleting a z13–16 pack emptied an
   identical-bbox z13–15 pack as well. Acceptable for distinct event areas; if packs ever
   need to overlap, the fix is reference-counting tiles, which is a real chunk of work.
2. **MBTiles import is untested.** The code path exists but no `.mbtiles` file has been run
   through it.
3. **Rotating the map rotates the labels with it.** Unavoidable with raster tiles — the text
   is baked into the image. OsmAnd keeps labels upright because it renders vector maps. The
   only real fix is switching to vector tiles (MapLibre + a contour source), which is a large
   change; worth it only if upside-down labels actually bother you in the field.
4. **Grid clustering has edge artifacts.** Two caves either side of a cell boundary land in
   separate clusters that can sit almost on top of each other. A neighbour-merge pass would
   fix it if it becomes annoying.
5. **Fetching caves needs network.** It is a one-off per area and the results are cached in
   Room for offline use, but you must pull the area before leaving signal — same as tiles.
6. **`targetSdk` is 35 but the test device runs API 37.** Fine for now — it just means
   Android's compatibility behaviours are active rather than the newest defaults.
7. `PreferenceManager` deprecation warning comes from osmdroid's own `Configuration.load`
   signature; harmless, would need `androidx.preference` to silence.
