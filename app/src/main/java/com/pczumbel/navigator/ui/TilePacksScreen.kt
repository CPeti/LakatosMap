package com.pczumbel.navigator.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pczumbel.navigator.data.RoutingData
import com.pczumbel.navigator.tiles.MbtilesImporter
import com.pczumbel.navigator.tiles.TileCacheCleaner
import com.pczumbel.navigator.tiles.TilePackRepository
import com.pczumbel.navigator.tiles.formatBytes
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TilePacksScreen(
    repo: TilePackRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val packs by repo.packs.collectAsStateWithLifecycle()
    var archives by remember { mutableStateOf(MbtilesImporter.listArchives()) }
    var status by remember { mutableStateOf<String?>(null) }
    val routingStates by RoutingData.states.collectAsStateWithLifecycle()

    // Hungary's two tiles are always offered; plus whatever a downloaded area elsewhere needs,
    // plus anything already on disk.
    val routingSegments = remember(packs, routingStates) {
        (RoutingData.HUNGARY + packs.flatMap { RoutingData.segmentsFor(it.boundingBox()) } + routingStates.keys)
            .distinct()
            .sorted()
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = "Importing…"
            val result = MbtilesImporter.import(context, uri)
            archives = MbtilesImporter.listArchives()
            status = result.fold(
                onSuccess = { "Imported ${it.name} (${formatBytes(it.length())})" },
                onFailure = { "Import failed: ${it.message}" },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline maps") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Downloaded areas", style = MaterialTheme.typography.titleMedium)
            }

            if (packs.isEmpty()) {
                item {
                    Text(
                        "None yet. On the map, frame an area and tap the download button.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            items(packs, key = { it.id }) { pack ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(pack.name, style = MaterialTheme.typography.titleSmall)
                            val missing = RoutingData.segmentsFor(pack.boundingBox())
                                .filter { routingStates[it] !is RoutingData.State.Installed }
                            Text(
                                if (missing.isEmpty()) {
                                    "Offline routing: ready"
                                } else {
                                    "Offline routing: needs ${missing.joinToString()}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (missing.isEmpty()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(
                                "z${pack.zoomMin}–${pack.zoomMax} · ${pack.tileCount} tiles · " +
                                    "~${formatBytes(pack.estimatedBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "%.4f,%.4f → %.4f,%.4f".format(
                                    pack.south, pack.west, pack.north, pack.east,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                // Tiles first, then the bookkeeping row: if eviction fails we
                                // still want the pack listed rather than orphaning its tiles.
                                TileCacheCleaner.deleteArea(context, pack)
                                    .onFailure { status = "Could not clear tiles: ${it.message}" }
                                repo.remove(pack.id)
                            }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete area")
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                RoutingDataSection(segments = routingSegments, states = routingStates)
            }

            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Imported tile packs", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Side-load an .mbtiles file to work fully offline with no tile server " +
                        "involved. Built with QGIS or tilemaker.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Text("Import .mbtiles")
                }
                status?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }

            items(archives, key = { it.absolutePath }) { archive: File ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(archive.name, style = MaterialTheme.typography.titleSmall)
                            Text(formatBytes(archive.length()), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = {
                            MbtilesImporter.delete(archive)
                            archives = MbtilesImporter.listArchives()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete archive")
                        }
                    }
                }
            }
        }
    }
}
