package com.pczumbel.navigator.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pczumbel.navigator.data.RoutingData
import com.pczumbel.navigator.tiles.formatBytes

/** Approximate sizes, for the confirmation text before the real Content-Length is known. */
private val KNOWN_SIZES = mapOf("E15_N45" to "~124 MB", "E20_N45" to "~65 MB")

private val KNOWN_COVERAGE = mapOf(
    "E15_N45" to "Western Hungary incl. Budapest, Buda Hills, Pilis, Bakony, Mecsek",
    "E20_N45" to "Eastern Hungary incl. Bükk, Aggtelek, Zemplén",
)

@Composable
fun RoutingDataSection(
    segments: List<String>,
    states: Map<String, RoutingData.State>,
) {
    Column(Modifier.fillMaxWidth()) {
        Text("Routing data", style = MaterialTheme.typography.titleMedium)
        Text(
            "Lets directions work with no signal. Comes in fixed 5° tiles — the only size the " +
                "routing data exists in — so one tile covers a large region.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        segments.forEach { segment ->
            val state = states[segment] ?: RoutingData.State.Missing
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(segment, style = MaterialTheme.typography.titleSmall)
                        KNOWN_COVERAGE[segment]?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            text = when (state) {
                                RoutingData.State.Missing -> "Not downloaded · ${KNOWN_SIZES[segment] ?: "size unknown"}"
                                is RoutingData.State.Downloading ->
                                    if (state.total > 0) {
                                        "Downloading ${formatBytes(state.bytes)} of ${formatBytes(state.total)}"
                                    } else {
                                        "Starting download…"
                                    }
                                is RoutingData.State.Installed -> "Ready offline · ${formatBytes(state.bytes)}"
                                is RoutingData.State.Failed -> "Failed: ${state.message}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when (state) {
                                is RoutingData.State.Installed -> MaterialTheme.colorScheme.primary
                                is RoutingData.State.Failed -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        if (state is RoutingData.State.Downloading) {
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { state.fraction },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    when (state) {
                        is RoutingData.State.Installed -> IconButton(onClick = { RoutingData.delete(segment) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete routing data")
                        }
                        is RoutingData.State.Downloading -> IconButton(onClick = { RoutingData.cancel(segment) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel download")
                        }
                        else -> IconButton(onClick = { RoutingData.download(segment) }) {
                            Icon(Icons.Filled.Download, contentDescription = "Download routing data")
                        }
                    }
                }
            }
        }
    }
}
