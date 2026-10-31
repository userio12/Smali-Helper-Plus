package com.riyadm.smalihelperplus.feature.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.designsystem.component.EmptyState
import com.riyadm.smalihelperplus.core.designsystem.component.ListSkeleton
import com.riyadm.smalihelperplus.core.designsystem.component.SectionHeader
import com.riyadm.smalihelperplus.domain.model.OpcodeCategory
import com.riyadm.smalihelperplus.feature.opcodes.OpcodeRow
import com.riyadm.smalihelperplus.navigation.AppRoute
import com.riyadm.smalihelperplus.navigation.OpcodeDetailRoute

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val viewModel: SearchViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recents by viewModel.recentSearches.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    // The text field owns the text (so typing is never overwritten by a lagging state update); the view model is
    // informed of every change. External changes (suggestion, recent search, clear) go through setText.
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(viewModel.currentQuery, TextRange(viewModel.currentQuery.length)))
    }
    LaunchedEffect(Unit) { viewModel.setQuery(field.text) }
    val setText: (String) -> Unit = { text ->
        field = TextFieldValue(text, TextRange(text.length))
        viewModel.setQuery(text)
    }

    val openResult: (String) -> Unit = { name ->
        viewModel.saveQuery()
        keyboard?.hide()
        onNavigate(OpcodeDetailRoute(name))
    }

    Scaffold(
        topBar = {
            SearchTopBar(
                field = field,
                activeFilters = state.filters.activeCount,
                onFieldChange = {
                    field = it
                    viewModel.setQuery(it.text)
                },
                onClear = { setText("") },
                onSubmit = {
                    viewModel.saveQuery()
                    keyboard?.hide()
                },
                onBack = onBack,
                onOpenFilters = { showFilters = true },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { padding ->
        when {
            !state.ready -> Box(Modifier.padding(padding)) { ListSkeleton(rows = 6) }
            !state.isSearching -> RecentSearchesPanel(
                padding = padding,
                recents = recents.map { it.query },
                totalOpcodes = state.totalOpcodes,
                onUse = setText,
                onDelete = viewModel::deleteRecent,
                onClear = { confirmClear = true },
            )
            else -> ResultsPanel(
                padding = padding,
                state = state,
                onSuggestion = setText,
                onOpen = openResult,
                onClearFilters = viewModel::resetFilters,
                onScrollStarted = { keyboard?.hide() },
            )
        }
    }

    if (showFilters) {
        FilterSheet(
            filters = state.filters,
            formats = state.formats,
            apiLevels = state.apiLevels,
            onChange = viewModel::setFilters,
            onReset = viewModel::resetFilters,
            onDismiss = { showFilters = false },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear search history?") },
            text = { Text("All recent searches will be removed from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearRecent()
                        confirmClear = false
                    },
                    modifier = Modifier.testTag("search_clear_history_confirm"),
                ) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SearchTopBar(
    field: TextFieldValue,
    activeFilters: Int,
    onFieldChange: (TextFieldValue) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onOpenFilters: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate back") }
            TextField(
                value = field,
                onValueChange = onFieldChange,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp).focusRequester(focusRequester).testTag("search_field"),
                singleLine = true,
                shape = CircleShape,
                placeholder = { Text("Search opcodes, formats, concepts") },
                trailingIcon = {
                    if (field.text.isNotEmpty()) {
                        IconButton(onClick = onClear, modifier = Modifier.testTag("search_clear")) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search text")
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            )
            IconButton(onClick = onOpenFilters, modifier = Modifier.testTag("search_filters")) {
                BadgedBox(badge = { if (activeFilters > 0) Badge { Text("$activeFilters") } }) {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = if (activeFilters > 0) "Filters, $activeFilters active" else "Filters",
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentSearchesPanel(
    padding: PaddingValues,
    recents: List<String>,
    totalOpcodes: Int,
    onUse: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (recents.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Outlined.Search,
                title = "Search the reference",
                message = "Look up any of $totalOpcodes instructions by name, format (try 35c), category or what it does. " +
                    "Typos are fine - search works offline.",
            )
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("search_recents"),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
            start = padding.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
            end = padding.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
        ),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent searches", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onClear, modifier = Modifier.testTag("search_clear_history")) { Text("Clear history") }
            }
        }
        items(recents, key = { it }) { text ->
            RecentSearchRow(text, onUse = { onUse(text) }, onDelete = { onDelete(text) })
        }
    }
}

@Composable
private fun RecentSearchRow(text: String, onUse: () -> Unit, onDelete: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) onDelete()
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer))
        },
    ) {
        Surface(onClick = onUse, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text, modifier = Modifier.weight(1f).padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Close, contentDescription = "Remove \"$text\" from history") }
            }
        }
    }
}

@Composable
private fun ResultsPanel(
    padding: PaddingValues,
    state: SearchUiState,
    onSuggestion: (String) -> Unit,
    onOpen: (String) -> Unit,
    onClearFilters: () -> Unit,
    onScrollStarted: () -> Unit,
) {
    val output = state.output
    if (output == null) {
        Box(Modifier.padding(padding)) { ListSkeleton(rows = 5) }
        return
    }
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState.isScrollInProgress) { if (gridState.isScrollInProgress) onScrollStarted() }
    // Jump back to the first result whenever the query or filters change.
    LaunchedEffect(output.query, state.filters) { gridState.scrollToItem(0) }
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current

    LazyVerticalGrid(
        columns = GridCells.Adaptive(360.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize().testTag("search_results"),
        contentPadding = PaddingValues(
            start = 16.dp + padding.calculateStartPadding(layoutDirection),
            end = 16.dp + padding.calculateEndPadding(layoutDirection),
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (output.suggestions.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("search_suggestions")) {
                    items(output.suggestions, key = { it }) { suggestion ->
                        SuggestionChip(
                            onClick = { onSuggestion(replaceLastTerm(state.query, suggestion)) },
                            label = { Text(suggestion, fontFamily = FontFamily.Monospace) },
                        )
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            val n = output.results.size
            Text(
                if (n == 1) "1 result" else "$n results",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp).testTag("search_count"),
            )
        }
        if (output.results.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                NoResults(
                    query = output.query,
                    didYouMean = output.didYouMean,
                    filtersActive = state.filters.isActive,
                    onUseSuggestion = onSuggestion,
                    onClearFilters = onClearFilters,
                )
            }
        } else {
            items(output.results, key = { it.opcode.name }) { result ->
                OpcodeRow(
                    result = result,
                    bookmarked = result.opcode.name in state.bookmarked,
                    onClick = { onOpen(result.opcode.name) },
                    modifier = Modifier.testTag("search_result_${result.opcode.name}"),
                    showMatchLabel = true,
                )
            }
        }
    }
}

@Composable
private fun NoResults(
    query: String,
    didYouMean: String?,
    filtersActive: Boolean,
    onUseSuggestion: (String) -> Unit,
    onClearFilters: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        EmptyState(
            icon = Icons.Outlined.SearchOff,
            title = "No results",
            message = if (query.isBlank()) "No instructions match the selected filters." else "Nothing matches \"$query\".",
            actionLabel = if (filtersActive) "Clear filters" else null,
            onAction = if (filtersActive) onClearFilters else null,
        )
        if (didYouMean != null) {
            AssistChip(
                onClick = { onUseSuggestion(didYouMean) },
                label = { Text("Did you mean $didYouMean?") },
                modifier = Modifier.testTag("search_did_you_mean"),
            )
        }
    }
}

/** Replaces the last whitespace separated term of [query] with [completion]. */
internal fun replaceLastTerm(query: String, completion: String): String {
    val trimmed = query.trimEnd()
    val cut = trimmed.lastIndexOf(' ')
    return if (cut < 0) completion else trimmed.substring(0, cut + 1) + completion
}

@Composable
private fun FilterSheet(
    filters: com.riyadm.smalihelperplus.domain.search.SearchFilters,
    formats: List<String>,
    apiLevels: List<Int>,
    onChange: ((com.riyadm.smalihelperplus.domain.search.SearchFilters) -> com.riyadm.smalihelperplus.domain.search.SearchFilters) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onReset, enabled = filters.isActive, modifier = Modifier.testTag("filters_reset")) { Text("Reset") }
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("filters_done")) { Text("Done") }
            }

            SheetLabel("Category")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OpcodeCategory.entries.forEach { category ->
                    val selected = category.id in filters.categories
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onChange { f -> f.copy(categories = if (selected) f.categories - category.id else f.categories + category.id) }
                        },
                        label = { Text(category.title) },
                    )
                }
            }

            if (formats.isNotEmpty()) {
                SheetLabel("Instruction format")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    formats.forEach { format ->
                        val selected = format in filters.formats
                        FilterChip(
                            selected = selected,
                            onClick = { onChange { f -> f.copy(formats = if (selected) f.formats - format else f.formats + format) } },
                            label = { Text(format, fontFamily = FontFamily.Monospace) },
                        )
                    }
                }
            }

            if (apiLevels.size > 1) {
                SheetLabel("Available at API level")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filters.maxApi == null,
                        onClick = { onChange { it.copy(maxApi = null) } },
                        label = { Text("Any") },
                    )
                    apiLevels.forEach { level ->
                        FilterChip(
                            selected = filters.maxApi == level,
                            onClick = { onChange { it.copy(maxApi = level) } },
                            label = { Text("≤ API $level") },
                        )
                    }
                }
            }

            SheetLabel("Other")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filters.hasJavaEquivalent,
                    onClick = { onChange { it.copy(hasJavaEquivalent = !it.hasJavaEquivalent) } },
                    label = { Text("Has Java equivalent") },
                )
                FilterChip(
                    selected = filters.wideOnly,
                    onClick = { onChange { it.copy(wideOnly = !it.wideOnly) } },
                    label = { Text("Wide (64-bit)") },
                )
                FilterChip(
                    selected = filters.rangeOnly,
                    onClick = { onChange { it.copy(rangeOnly = !it.rangeOnly) } },
                    label = { Text("Range") },
                )
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
