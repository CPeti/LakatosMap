package com.pczumbel.navigator.tiles

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Flat-file JSON store. Deliberately not Room: this is a handful of rows read once at
 * startup, and Phase 2 will introduce Room for the cave catalog where it actually earns
 * its keep. Avoids a schema migration for throwaway bookkeeping.
 */
class TilePackRepository(context: Context) {

    private val file = File(context.filesDir, "tile_packs.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val _packs = MutableStateFlow<List<TilePack>>(emptyList())
    val packs: StateFlow<List<TilePack>> = _packs.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        _packs.value = runCatching {
            if (!file.exists()) emptyList()
            else json.decodeFromString<TilePackStore>(file.readText()).packs
        }.getOrDefault(emptyList())
    }

    suspend fun add(pack: TilePack) = withContext(Dispatchers.IO) {
        persist(_packs.value + pack)
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        persist(_packs.value.filterNot { it.id == id })
    }

    private fun persist(list: List<TilePack>) {
        _packs.value = list
        runCatching { file.writeText(json.encodeToString(TilePackStore(list))) }
    }
}
