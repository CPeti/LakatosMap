package com.pczumbel.navigator.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.IconButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Settings and one-off actions live here, rather than as a stack of floating buttons over the
 * map. Things toggled while looking at the map (layers, cave visibility, locate) are side
 * buttons instead, reachable with a thumb.
 */
@Composable
fun OptionsMenu(
    offlineOnly: Boolean,
    showCaveNames: Boolean,
    onToggleOffline: (Boolean) -> Unit,
    onToggleCaveNames: (Boolean) -> Unit,
    onDownloadArea: () -> Unit,
    onFetchCaves: () -> Unit,
    onOpenPacks: () -> Unit,
    onSetMapyKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            tonalElevation = 2.dp,
        ) {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Options")
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Download this area…") },
                leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDownloadArea()
                },
            )
            DropdownMenuItem(
                text = { Text("Fetch cave entrances…") },
                leadingIcon = { Icon(Icons.Filled.Terrain, contentDescription = null) },
                onClick = {
                    expanded = false
                    onFetchCaves()
                },
            )
            DropdownMenuItem(
                text = { Text("Offline maps…") },
                leadingIcon = { Icon(Icons.Filled.SdStorage, contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpenPacks()
                },
            )
            DropdownMenuItem(
                text = { Text("Mapy.com API key…") },
                leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                onClick = {
                    expanded = false
                    onSetMapyKey()
                },
            )

            HorizontalDivider()

            // Toggles stay open: these are things you flip while looking at the map, and
            // closing the menu on each tap would mean reopening it to flip the other one.
            DropdownMenuItem(
                text = { Text("Show cave names") },
                leadingIcon = { Icon(Icons.Filled.Label, contentDescription = null) },
                trailingIcon = {
                    Checkbox(checked = showCaveNames, onCheckedChange = { onToggleCaveNames(it) })
                },
                onClick = { onToggleCaveNames(!showCaveNames) },
            )
            DropdownMenuItem(
                text = { Text("Offline mode") },
                leadingIcon = { Icon(Icons.Filled.CloudOff, contentDescription = null) },
                trailingIcon = {
                    Checkbox(checked = offlineOnly, onCheckedChange = { onToggleOffline(it) })
                },
                onClick = { onToggleOffline(!offlineOnly) },
            )
        }
    }
}

/** Paste-in dialog for the Mapy.com key. Saving it blank (or Clear) removes it. */
@Composable
fun MapyKeyDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mapy.com API key") },
        text = {
            Column {
                Text(
                    "Needed for the Mapy.com outdoor layer. Get a free key at developer.mapy.com. " +
                        "It is stored on this phone only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("API key") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (current.isNotEmpty()) {
                    TextButton(onClick = { onSave("") }) { Text("Clear") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
