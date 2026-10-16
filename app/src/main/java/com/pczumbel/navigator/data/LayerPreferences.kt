package com.pczumbel.navigator.data

import android.content.Context
import com.pczumbel.navigator.map.LayerSource
import com.pczumbel.navigator.map.MapLayers

/**
 * Remembers the chosen layers across restarts. SharedPreferences rather than DataStore:
 * two string values, read once at startup, written on a tap.
 */
class LayerPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("layers", Context.MODE_PRIVATE)

    var baseId: String
        get() = prefs.getString(KEY_BASE, MapLayers.OPEN_TOPO.id) ?: MapLayers.OPEN_TOPO.id
        set(value) = prefs.edit().putString(KEY_BASE, value).apply()

    var overlayIds: List<String>
        get() = prefs.getString(KEY_OVERLAYS, "")
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        set(value) = prefs.edit().putString(KEY_OVERLAYS, value.joinToString(",")).apply()

    var cavesVisible: Boolean
        get() = prefs.getBoolean(KEY_CAVES_VISIBLE, true)
        set(value) = prefs.edit().putBoolean(KEY_CAVES_VISIBLE, value).apply()

    var mapyApiKey: String
        get() = prefs.getString(KEY_MAPY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_MAPY_API_KEY, value).apply()

    fun base(): LayerSource = MapLayers.baseById(baseId)

    fun overlays(): List<LayerSource> = overlayIds.mapNotNull { MapLayers.overlayById(it) }

    private companion object {
        const val KEY_BASE = "base"
        const val KEY_OVERLAYS = "overlays"
        const val KEY_CAVES_VISIBLE = "caves_visible"
        const val KEY_MAPY_API_KEY = "mapy_api_key"
    }
}
