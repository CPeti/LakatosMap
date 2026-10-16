package com.pczumbel.navigator.map

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

/**
 * A selectable map layer.
 *
 * [bulkDownloadable] is a licence/etiquette fact, not a technical one: every source here
 * *could* be scraped, but only some permit it. The downloader refuses the rest.
 */
data class LayerSource(
    val id: String,
    val label: String,
    val description: String,
    val attribution: String,
    val tileSource: OnlineTileSourceBase,
    val bulkDownloadable: Boolean,
    val downloadNote: String? = null,
)

/** Esri serves z/y/x, not the z/x/y that XYTileSource assumes. */
private class ZYXTileSource(
    name: String,
    zoomMin: Int,
    zoomMax: Int,
    baseUrls: Array<String>,
    copyright: String,
    policy: TileSourcePolicy,
) : OnlineTileSourceBase(name, zoomMin, zoomMax, 256, "", baseUrls, copyright, policy) {

    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl +
            MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) + "/" +
            MapTileIndex.getX(pMapTileIndex)
}

/**
 * Mapy.com outdoor tiles at @2x.
 *
 * The source is *declared* 256 px so osmdroid lays it out like any other tile, but the URL
 * asks for the 512 px retina image. osmdroid draws whatever bitmap it gets into the tile's
 * rectangle, so the extra pixels land on screen: on a ~2.6× density phone each image pixel
 * covers ~1.3 screen pixels instead of ~2.6 — which is the whole difference between this
 * and OpenTopoMap looking blocky. Declaring 512 instead would double the size of everything,
 * labels included.
 */
/**
 * The user's Mapy.com API key, entered in the options menu and stored on the device — never
 * built into the APK, so the APK can be handed to others without sharing the key. Read on every
 * tile request, so a newly entered key takes effect without rebuilding the tile source.
 */
object MapyKey {
    @Volatile
    var value: String = ""

    val isSet: Boolean get() = value.isNotBlank()
}

private class MapyTileSource : OnlineTileSourceBase(
    "MapyOutdoor2x", 0, 19, 256, "",
    arrayOf("https://api.mapy.com/v1/maptiles/outdoor/256@2x/"),
    "© Seznam.cz, © OpenStreetMap",
    TileSourcePolicy(
        4,
        TileSourcePolicy.FLAG_NO_PREVENTIVE or TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL,
    ),
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl +
            MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getX(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) + "?apikey=" + MapyKey.value
}

private fun bulkFriendlyPolicy() = TileSourcePolicy(
    2,
    TileSourcePolicy.FLAG_NO_PREVENTIVE
        or TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL
        or TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED,
)

/**
 * Sources whose operators ask you not to bulk-download. Marking them NO_BULK means osmdroid's
 * CacheManager refuses them outright, so the policy is enforced rather than merely documented.
 */
private fun viewOnlyPolicy() = TileSourcePolicy(
    2,
    TileSourcePolicy.FLAG_NO_BULK
        or TileSourcePolicy.FLAG_NO_PREVENTIVE
        or TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL
        or TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED,
)

object MapLayers {

    val OPEN_TOPO = LayerSource(
        id = "opentopo",
        label = "OpenTopoMap",
        description = "Contours, paths and tracks. The default.",
        attribution = "© OpenTopoMap (CC-BY-SA)",
        bulkDownloadable = true,
        tileSource = XYTileSource(
            "OpenTopoMap", 4, 17, 256, ".png",
            arrayOf(
                "https://a.tile.opentopomap.org/",
                "https://b.tile.opentopomap.org/",
                "https://c.tile.opentopomap.org/",
            ),
            "© OpenTopoMap (CC-BY-SA), map data © OpenStreetMap contributors (ODbL)",
            bulkFriendlyPolicy(),
        ),
    )

    val MAPY_OUTDOOR = LayerSource(
        id = "mapy_outdoor",
        label = "Mapy.com outdoor",
        description = "Sharp high-res tiles to z19, trail marks and hillshading built in.",
        attribution = "© Seznam.cz",
        bulkDownloadable = true,
        downloadNote = "Every tile spends API credits — keep offline areas modest.",
        tileSource = MapyTileSource(),
    )

    val ESRI_TOPO = LayerSource(
        id = "esri_topo",
        label = "Esri World Topo",
        description = "Contours and trails with different cartography. Useful where OSM topo is thin.",
        attribution = "© Esri, HERE, Garmin, USGS",
        bulkDownloadable = false,
        downloadNote = "Esri's terms don't permit bulk tile download. Viewing only.",
        tileSource = ZYXTileSource(
            "EsriWorldTopo", 0, 19,
            arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/"),
            "© Esri and the GIS community",
            viewOnlyPolicy(),
        ),
    )

    val ESRI_IMAGERY = LayerSource(
        id = "esri_imagery",
        label = "Satellite (Esri)",
        description = "Aerial imagery — shows whether an entrance sits in open forest or dense scrub.",
        attribution = "© Esri, Maxar, Earthstar Geographics",
        bulkDownloadable = false,
        downloadNote = "Esri's terms don't permit bulk tile download. Viewing only.",
        tileSource = ZYXTileSource(
            "EsriWorldImagery", 0, 19,
            arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
            "© Esri, Maxar, Earthstar Geographics",
            viewOnlyPolicy(),
        ),
    )

    val OSM_CARTO = LayerSource(
        id = "osm",
        label = "OpenStreetMap",
        description = "Clearest labels and place names. No terrain.",
        attribution = "© OpenStreetMap contributors (ODbL)",
        bulkDownloadable = false,
        downloadNote = "The OSM Foundation tile policy forbids bulk download. Viewing only.",
        tileSource = XYTileSource(
            "OpenStreetMapCarto", 0, 19, 256, ".png",
            arrayOf("https://tile.openstreetmap.org/"),
            "© OpenStreetMap contributors (ODbL)",
            viewOnlyPolicy(),
        ),
    )

    /**
     * Hungarian hiking-trail markings from turistautak.openstreetmap.hu.
     *
     * These are **overlays, not base maps** — the tiles are ~90% transparent, carrying only
     * the waymarks and trail lines, meant to be drawn over a base. Which is the ideal
     * combination here: OpenTopoMap contours underneath, Hungarian trail blazes on top.
     *
     * The endpoint was read out of the site's own OpenStreetMap.js (`OpenLayers.Layer.OSM.tt`).
     * Note the a/b/c subdomain — the bare `tile.openstreetmap.hu` host does not resolve.
     */
    val TURISTAUTAK = LayerSource(
        id = "turistautak",
        label = "Turistautak (trail marks)",
        description = "Hungarian waymarked hiking trails. Overlay — pairs with any base map.",
        attribution = "Turistautak: Kolesár / openstreetmap.hu",
        bulkDownloadable = true,
        downloadNote = "Small volunteer-run server. Download modest areas only.",
        tileSource = XYTileSource(
            "Turistautak", 8, 17, 256, ".png",
            arrayOf(
                "https://a.tile.openstreetmap.hu/tt/",
                "https://b.tile.openstreetmap.hu/tt/",
                "https://c.tile.openstreetmap.hu/tt/",
            ),
            "Turistautak © Kolesár, data © OpenStreetMap contributors (ODbL)",
            bulkFriendlyPolicy(),
        ),
    )

    val TURISTAUTAK_COLOUR = LayerSource(
        id = "turistautak_szin",
        label = "Turistautak colour",
        description = "The same trails rendered in their blaze colours.",
        attribution = "Turistautak: Kolesár / openstreetmap.hu",
        bulkDownloadable = true,
        downloadNote = "Small volunteer-run server. Download modest areas only.",
        tileSource = XYTileSource(
            "TuristautakSzin", 8, 17, 256, ".png",
            arrayOf(
                "https://a.tile.openstreetmap.hu/ttszin/",
                "https://b.tile.openstreetmap.hu/ttszin/",
                "https://c.tile.openstreetmap.hu/ttszin/",
            ),
            "Turistautak © Kolesár, data © OpenStreetMap contributors (ODbL)",
            bulkFriendlyPolicy(),
        ),
    )

    val WAYMARKED_HIKING = LayerSource(
        id = "waymarked",
        label = "Waymarked Trails",
        description = "International hiking routes. Overlay.",
        attribution = "© waymarkedtrails.org (CC-BY-SA)",
        bulkDownloadable = false,
        downloadNote = "Volunteer-run; bulk download not offered.",
        tileSource = XYTileSource(
            "WaymarkedHiking", 0, 18, 256, ".png",
            arrayOf("https://tile.waymarkedtrails.org/hiking/"),
            "© waymarkedtrails.org, data © OpenStreetMap contributors (ODbL)",
            viewOnlyPolicy(),
        ),
    )

    val bases = listOf(MAPY_OUTDOOR, OPEN_TOPO, ESRI_TOPO, ESRI_IMAGERY, OSM_CARTO)
    val overlays = listOf(TURISTAUTAK, TURISTAUTAK_COLOUR, WAYMARKED_HIKING)

    fun baseById(id: String?): LayerSource = bases.firstOrNull { it.id == id } ?: OPEN_TOPO
    fun overlayById(id: String): LayerSource? = overlays.firstOrNull { it.id == id }
}

/** Rough bytes-per-tile, used only for download size estimates. */
const val AVG_TILE_BYTES = 40_000L
