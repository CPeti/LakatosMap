package com.pczumbel.navigator.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pczumbel.navigator.data.CaveHit
import com.pczumbel.navigator.data.formatDistance

@Composable
fun CaveSearchBar(
    query: String,
    results: List<CaveHit>,
    totalCaves: Int,
    onQueryChange: (String) -> Unit,
    onPick: (CaveHit) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(modifier = modifier.fillMaxWidth()) {
        Card(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text("Search caves by name or ref") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close search")
                    }
                },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
            )
        }

        if (query.isNotBlank()) {
            Spacer(Modifier.padding(top = 6.dp))
            Card(Modifier.fillMaxWidth()) {
                if (results.isEmpty()) {
                    Text(
                        text = if (totalCaves == 0) {
                            "No caves downloaded yet — use Fetch cave entrances."
                        } else {
                            "No match in $totalCaves caves."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    // Capped height so the list never swallows the whole map.
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(results, key = { it.cave.id }) { hit ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(hit) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(hit.cave.displayName, style = MaterialTheme.typography.bodyLarge)
                                    val subtitle = listOfNotNull(
                                        hit.cave.registerRef?.let { "ref $it" },
                                        hit.cave.entranceType,
                                    ).joinToString(" · ")
                                    if (subtitle.isNotBlank()) {
                                        Text(
                                            subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                hit.distanceMetres?.let {
                                    Text(
                                        formatDistance(it),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
