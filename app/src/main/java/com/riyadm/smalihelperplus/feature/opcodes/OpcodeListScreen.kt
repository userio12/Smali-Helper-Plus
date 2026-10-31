package com.riyadm.smalihelperplus.feature.opcodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterListOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.core.designsystem.component.EmptyState
import com.riyadm.smalihelperplus.core.designsystem.component.ListSkeleton
import com.riyadm.smalihelperplus.domain.search.SearchFilters
import com.riyadm.smalihelperplus.navigation.AppRoute
import com.riyadm.smalihelperplus.navigation.OpcodeDetailRoute
import com.riyadm.smalihelperplus.navigation.SearchRoute

@Composable
fun OpcodeListScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val viewModel: OpcodeListViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var sortMenu by remember { mutableStateOf(false) }

    DetailScaffold(
        title = state.title,
        onBack = onBack,
        actions = {
            IconButton(onClick = { onNavigate(SearchRoute()) }) {
                Icon(Icons.Outlined.Search, contentDescription = "Search all opcodes")
            }
            Box {
                IconButton(onClick = { sortMenu = true }, modifier = Modifier.testTag("opcode_sort")) {
                    Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort: ${state.sort.label}")
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    OpcodeSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            leadingIcon = {
                                if (option == state.sort) Icon(Icons.Filled.Check, contentDescription = "Selected")
                            },
                            onClick = {
                                viewModel.setSort(option)
                                sortMenu = false
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.padding(padding)) { ListSkeleton(rows = 8) }
            return@DetailScaffold
        }
        val layoutDirection = LocalLayoutDirection.current
        LazyVerticalGrid(
            columns = GridCells.Adaptive(360.dp),
            modifier = Modifier.fillMaxSize().testTag("opcode_list"),
            contentPadding = PaddingValues(
                start = 16.dp + padding.calculateStartPadding(layoutDirection),
                end = 16.dp + padding.calculateEndPadding(layoutDirection),
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = state.filterText,
                    onValueChange = viewModel::setFilterText,
                    modifier = Modifier.fillMaxWidth().testTag("opcode_filter"),
                    singleLine = true,
                    shape = CircleShape,
                    placeholder = { Text("Filter ${state.totalInScope} instructions") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.filterText.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setFilterText("") }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear filter text")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                FilterRow(state, viewModel)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val suffix = if (state.items.size == state.totalInScope) "" else " of ${state.totalInScope}"
                Text(
                    "${state.items.size}$suffix instructions",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            if (state.items.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        icon = Icons.Outlined.FilterListOff,
                        title = if (state.totalInScope == 0) "No instructions" else "Nothing matches",
                        message = if (state.totalInScope == 0) {
                            "This category has no instructions in the installed documentation."
                        } else {
                            "Try a different search or remove some filters."
                        },
                        actionLabel = if (state.totalInScope > 0) "Clear filters" else null,
                        onAction = if (state.totalInScope > 0) viewModel::clearFilters else null,
                    )
                }
            } else {
                items(state.items, key = { it.opcode.name }) { result ->
                    OpcodeRow(
                        result = result,
                        bookmarked = result.opcode.name in state.bookmarked,
                        onClick = { onNavigate(OpcodeDetailRoute(result.opcode.name)) },
                        modifier = Modifier.opcodeItemTag(result.opcode.name),
                        showMatchLabel = state.filterText.isNotBlank(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRow(state: OpcodeListUiState, viewModel: OpcodeListViewModel) {
    val filters = state.filters
    var formatMenu by remember { mutableStateOf(false) }
    var apiMenu by remember { mutableStateOf(false) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
        if (state.availableFormats.size > 1) {
            item {
                Box {
                    FilterChip(
                        selected = filters.formats.isNotEmpty(),
                        onClick = { formatMenu = true },
                        label = {
                            Text(if (filters.formats.isEmpty()) "Format" else "Format: ${filters.formats.sorted().joinToString(", ")}")
                        },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier.testTag("filter_format"),
                    )
                    DropdownMenu(expanded = formatMenu, onDismissRequest = { formatMenu = false }) {
                        state.availableFormats.forEach { format ->
                            val selected = format in filters.formats
                            DropdownMenuItem(
                                text = { Text(format) },
                                leadingIcon = { if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected") },
                                onClick = {
                                    viewModel.updateFilters { f ->
                                        f.copy(formats = if (selected) f.formats - format else f.formats + format)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        if (state.availableApiLevels.size > 1) {
            item {
                Box {
                    FilterChip(
                        selected = filters.maxApi != null,
                        onClick = { apiMenu = true },
                        label = { Text(if (filters.maxApi == null) "Min API" else "API ≤ ${filters.maxApi}") },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier.testTag("filter_api"),
                    )
                    DropdownMenu(expanded = apiMenu, onDismissRequest = { apiMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Any API level") },
                            leadingIcon = { if (filters.maxApi == null) Icon(Icons.Filled.Check, contentDescription = "Selected") },
                            onClick = {
                                viewModel.updateFilters { it.copy(maxApi = null) }
                                apiMenu = false
                            },
                        )
                        state.availableApiLevels.forEach { level ->
                            DropdownMenuItem(
                                text = { Text("Available on API $level or lower") },
                                leadingIcon = { if (filters.maxApi == level) Icon(Icons.Filled.Check, contentDescription = "Selected") },
                                onClick = {
                                    viewModel.updateFilters { it.copy(maxApi = level) }
                                    apiMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }
        item {
            FilterChip(
                selected = filters.wideOnly,
                onClick = { viewModel.updateFilters { it.copy(wideOnly = !it.wideOnly) } },
                label = { Text("Wide (64-bit)") },
                modifier = Modifier.testTag("filter_wide"),
            )
        }
        item {
            FilterChip(
                selected = filters.rangeOnly,
                onClick = { viewModel.updateFilters { it.copy(rangeOnly = !it.rangeOnly) } },
                label = { Text("Range") },
                modifier = Modifier.testTag("filter_range"),
            )
        }
        if (filters.isActive) {
            item {
                FilterChip(
                    selected = false,
                    onClick = { viewModel.updateFilters { SearchFilters() } },
                    label = { Text("Clear") },
                    leadingIcon = { Icon(Icons.Outlined.Close, contentDescription = null) },
                )
            }
        }
    }
}
