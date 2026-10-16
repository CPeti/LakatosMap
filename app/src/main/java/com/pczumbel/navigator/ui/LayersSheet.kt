package com.pczumbel.navigator.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pczumbel.navigator.map.LayerSource
import com.pczumbel.navigator.map.MapLayers

@Composable
fun LayersSheet(
    selectedBase: LayerSource,
    selectedOverlays: List<LayerSource>,
    onBaseSelected: (LayerSource) -> Unit,
    onOverlayToggled: (LayerSource, Boolean) -> Unit,
    onOpenPacks: () -> Unit,
    mapyKeySet: Boolean,
) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
    ) {
        Text("Map layers", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        Text("Base map", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        MapLayers.bases.forEach { layer ->
            LayerRow(
                layer = layer,
                selected = layer.id == selectedBase.id,
                onClick = { onBaseSelected(layer) },
            ) {
                RadioButton(selected = layer.id == selectedBase.id, onClick = { onBaseSelected(layer) })
            }
            if (layer.id == MapLayers.MAPY_OUTDOOR.id && !mapyKeySet) {
                Text(
                    "Needs an API key: tap to enter one (also in the ⋮ menu).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 56.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("Overlays", style = MaterialTheme.typography.titleSmall)
        Text(
            "Drawn on top of the base map. Turistautak is the Hungarian waymarked-trail layer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        MapLayers.overlays.forEach { layer ->
            val checked = selectedOverlays.any { it.id == layer.id }
            LayerRow(
                layer = layer,
                selected = checked,
                onClick = { onOverlayToggled(layer, !checked) },
            ) {
                Switch(checked = checked, onCheckedChange = { onOverlayToggled(layer, it) })
            }
        }

        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onOpenPacks, modifier = Modifier.fillMaxWidth()) {
            Text("Offline maps…")
        }
    }
}

@Composable
private fun LayerRow(
    layer: LayerSource,
    selected: Boolean,
    onClick: () -> Unit,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        control()
        Column(Modifier.padding(start = 8.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(layer.label, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.padding(horizontal = 3.dp))
                // Whether a layer can be taken offline is the single most important fact
                // about it for this app, so it is shown inline rather than buried.
                Icon(
                    imageVector = if (layer.bulkDownloadable) {
                        Icons.Filled.CloudDownload
                    } else {
                        Icons.Filled.CloudOff
                    },
                    contentDescription = if (layer.bulkDownloadable) {
                        "Can be downloaded for offline use"
                    } else {
                        "Online only"
                    },
                    modifier = Modifier.height(15.dp),
                    tint = if (layer.bulkDownloadable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
            }
            Text(
                layer.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Shown unconditionally. Revealing it only for the selected layer changed the
            // row's height on every toggle, which made the whole sheet re-measure and snap.
            layer.downloadNote?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
