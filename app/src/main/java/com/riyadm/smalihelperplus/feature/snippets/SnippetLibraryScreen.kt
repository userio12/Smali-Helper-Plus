package com.riyadm.smalihelperplus.feature.snippets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.database.entity.SnippetEntity
import com.riyadm.smalihelperplus.core.designsystem.component.CodeBlock
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.core.designsystem.component.EmptyState
import com.riyadm.smalihelperplus.core.designsystem.component.ListSkeleton
import com.riyadm.smalihelperplus.core.designsystem.component.SmaliHighlighter
import com.riyadm.smalihelperplus.core.designsystem.component.copyToClipboard
import com.riyadm.smalihelperplus.core.designsystem.component.shareText
import com.riyadm.smalihelperplus.core.designsystem.theme.CodeTextStyle
import com.riyadm.smalihelperplus.core.designsystem.theme.LocalCodeColors
import com.riyadm.smalihelperplus.data.snippets.SnippetCollections
import com.riyadm.smalihelperplus.data.snippets.SnippetFilter
import com.riyadm.smalihelperplus.data.snippets.SnippetScope
import com.riyadm.smalihelperplus.navigation.AppRoute
import com.riyadm.smalihelperplus.navigation.PlaygroundRoute

private const val PREVIEW_LINES = 8

@Composable
fun SnippetLibraryScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val viewModel: SnippetLibraryViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // null = sheet closed, 0 = new snippet, otherwise the id of the snippet being edited.
    var editorTarget by rememberSaveable { mutableStateOf<Long?>(null) }
    var expandedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingDelete by remember { mutableStateOf<SnippetEntity?>(null) }

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    DetailScaffold(
        title = "Snippet library",
        onBack = onBack,
        snackbarHostState = snackbar,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editorTarget = 0L },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New snippet") },
                modifier = Modifier.testTag("snippet_fab"),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 340.dp),
                modifier = Modifier.fillMaxSize().testTag("snippet_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "filters") {
                    Filters(state, viewModel::setQuery, viewModel::setScope)
                }
                when {
                    state.loading -> item(span = { GridItemSpan(maxLineSpan) }, key = "loading") { ListSkeleton() }
                    state.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                        EmptyContent(state.scope, state.query, state.totalCount) { editorTarget = 0L }
                    }
                    else -> items(state.items, key = { it.snippet.id }) { item ->
                        SnippetCard(
                            item = item,
                            expanded = expandedId == item.snippet.id,
                            onToggleExpanded = { expandedId = if (expandedId == item.snippet.id) null else item.snippet.id },
                            onOpenPlayground = {
                                viewModel.offerToPlayground(item.snippet)
                                onNavigate(PlaygroundRoute)
                            },
                            onBookmark = { viewModel.toggleBookmark(item) },
                            onEdit = { editorTarget = item.snippet.id },
                            onDelete = { pendingDelete = item.snippet },
                            onDuplicate = { viewModel.duplicate(item.snippet) },
                        )
                    }
                }
            }
        }
    }

    val target = editorTarget
    if (target != null) {
        val editing = if (target == 0L) null else state.items.firstOrNull { it.snippet.id == target }?.snippet
        if (target == 0L || editing != null) {
            SnippetEditorSheet(
                editing = editing,
                collectionChoices = state.collectionChoices,
                defaultCollection = (state.scope as? SnippetScope.Collection)?.name ?: SnippetCollections.MY_SNIPPETS,
                onDismiss = { editorTarget = null },
                onSave = { title, description, collection, code, tags ->
                    viewModel.save(editing?.id, title, description, collection, code, tags)
                    editorTarget = null
                },
            )
        } else {
            editorTarget = null
        }
    }

    pendingDelete?.let { snippet ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete snippet?") },
            text = { Text("\"${snippet.title}\" will be removed from My snippets. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(snippet); pendingDelete = null }, modifier = Modifier.testTag("snippet_confirm_delete")) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Filters(state: SnippetLibraryState, onQuery: (String) -> Unit, onScope: (SnippetScope) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().testTag("snippet_search"),
            placeholder = { Text("Search snippets, tags or code") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
        )
        data class Chip(val label: String, val scope: SnippetScope, val tag: String)
        val chips = buildList {
            add(Chip("All", SnippetScope.All, "snippet_chip_all"))
            add(Chip("Bookmarked", SnippetScope.Bookmarked, "snippet_chip_bookmarked"))
            add(Chip(SnippetCollections.MY_SNIPPETS, SnippetScope.Mine, "snippet_chip_mine"))
            state.collections.forEach { add(Chip(it, SnippetScope.Collection(it), "snippet_chip_$it")) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(chips, key = { it.tag }) { chip ->
                FilterChip(
                    selected = state.scope == chip.scope,
                    onClick = { onScope(chip.scope) },
                    label = { Text(chip.label) },
                    modifier = Modifier.testTag(chip.tag),
                )
            }
        }
    }
}

@Composable
private fun EmptyContent(scope: SnippetScope, query: String, total: Int, onCreate: () -> Unit) {
    when {
        query.isNotBlank() || (total > 0 && scope !is SnippetScope.Mine && scope != SnippetScope.Bookmarked) -> EmptyState(
            icon = Icons.Outlined.SearchOff,
            title = "No snippets found",
            message = "Try a different search or collection.",
        )
        scope == SnippetScope.Bookmarked -> EmptyState(
            icon = Icons.Outlined.BookmarkBorder,
            title = "No bookmarks yet",
            message = "Tap the bookmark icon on a snippet to keep it here.",
        )
        scope == SnippetScope.Mine -> EmptyState(
            icon = Icons.Outlined.Inventory2,
            title = "No snippets of your own yet",
            message = "Save your own smali, or duplicate a built-in snippet from its menu.",
            actionLabel = "New snippet",
            onAction = onCreate,
        )
        else -> EmptyState(
            icon = Icons.Outlined.Inventory2,
            title = "No snippets available",
            message = "The built-in library has not been installed yet. You can still create your own snippets.",
            actionLabel = "New snippet",
            onAction = onCreate,
        )
    }
}

@Composable
private fun SnippetCard(
    item: SnippetItem,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenPlayground: () -> Unit,
    onBookmark: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
) {
    val snippet = item.snippet
    val context = LocalContext.current
    val isUser = SnippetFilter.isUserSnippet(snippet)
    var menu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag("snippet_item_${snippet.id}"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.fillMaxWidth().clickable(onClickLabel = if (expanded) "Collapse" else "Expand", onClick = onToggleExpanded)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(snippet.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Surface(shape = RoundedCornerShape(8.dp), color = if (isUser) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            if (isUser) snippet.collection + " • yours" else snippet.collection,
                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
                if (snippet.description.isNotBlank()) {
                    Text(
                        snippet.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (snippet.tags.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    snippet.tags.forEach { tag ->
                        Text("#$tag", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (expanded) {
                CodeBlock(code = snippet.code.trimEnd(), label = "smali", showCopy = false)
            } else {
                CodePreview(snippet.code, Modifier.clickable(onClickLabel = "Expand", onClick = onToggleExpanded))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenPlayground, modifier = Modifier.testTag("snippet_playground_${snippet.id}")) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = "Open in Playground")
                }
                IconButton(onClick = { copyToClipboard(context, snippet.title, snippet.code) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy code")
                }
                IconButton(onClick = { shareText(context, snippet.title, snippet.code) }) {
                    Icon(Icons.Outlined.Share, contentDescription = "Share snippet")
                }
                IconButton(onClick = onBookmark, modifier = Modifier.testTag("snippet_bookmark_${snippet.id}")) {
                    Icon(
                        if (item.bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (item.bookmarked) "Remove bookmark" else "Bookmark snippet",
                        tint = if (item.bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    IconButton(onClick = { menu = true }, modifier = Modifier.testTag("snippet_menu_${snippet.id}")) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (isUser) {
                            DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                        }
                        DropdownMenuItem(text = { Text("Duplicate to My snippets") }, onClick = { menu = false; onDuplicate() })
                    }
                }
            }
        }
    }
}

@Composable
private fun CodePreview(code: String, modifier: Modifier = Modifier) {
    val colors = LocalCodeColors.current
    val lines = remember(code) { code.trimEnd().lines() }
    val shown = remember(code) {
        if (lines.size > PREVIEW_LINES) lines.take(PREVIEW_LINES).joinToString("\n") + "\n… ${lines.size - PREVIEW_LINES} more lines" else lines.joinToString("\n")
    }
    val text = remember(shown, colors) { SmaliHighlighter.highlight(shown, colors) }
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.background),
    ) {
        Box(Modifier.horizontalScroll(rememberScrollState()).padding(14.dp)) {
            Text(text, style = CodeTextStyle, color = colors.text, softWrap = false)
        }
    }
}

@Composable
private fun SnippetEditorSheet(
    editing: SnippetEntity?,
    collectionChoices: List<String>,
    defaultCollection: String,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, collection: String, code: String, tags: String) -> Unit,
) {
    val key = editing?.id ?: 0L
    var title by rememberSaveable(key) { mutableStateOf(editing?.title.orEmpty()) }
    var description by rememberSaveable(key) { mutableStateOf(editing?.description.orEmpty()) }
    var collection by rememberSaveable(key) { mutableStateOf(editing?.collection ?: defaultCollection) }
    var code by rememberSaveable(key) { mutableStateOf(editing?.code.orEmpty()) }
    var tags by rememberSaveable(key) { mutableStateOf(editing?.tags?.joinToString(", ").orEmpty()) }
    var menuOpen by remember { mutableStateOf(false) }

    val canSave = title.isNotBlank() && code.isNotBlank() && collection.length <= 40

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (editing == null) "New snippet" else "Edit snippet", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = title, onValueChange = { title = it.take(80) }, label = { Text("Title") },
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("snippet_title"),
            )
            OutlinedTextField(
                value = description, onValueChange = { description = it.take(400) }, label = { Text("Description (optional)") },
                minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth().testTag("snippet_description"),
            )
            val suggestions = collectionChoices.filter { it.contains(collection, ignoreCase = true) && !it.equals(collection, ignoreCase = true) }
            ExposedDropdownMenuBox(expanded = menuOpen && collectionChoices.isNotEmpty(), onExpandedChange = { menuOpen = it }) {
                OutlinedTextField(
                    value = collection,
                    onValueChange = { collection = it.take(40); menuOpen = true },
                    label = { Text("Collection (pick one or type a new name)") },
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen) },
                    isError = collection.length > 40,
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth().testTag("snippet_collection"),
                )
                val options = if (suggestions.isEmpty() && collection.isBlank()) collectionChoices else if (collection.isBlank()) collectionChoices else suggestions
                if (options.isNotEmpty()) {
                    ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        options.forEach { name ->
                            DropdownMenuItem(text = { Text(name) }, onClick = { collection = name; menuOpen = false })
                        }
                    }
                }
            }
            OutlinedTextField(
                value = code, onValueChange = { code = it },
                label = { Text("Smali code") }, textStyle = CodeTextStyle,
                minLines = 6, maxLines = 14, modifier = Modifier.fillMaxWidth().testTag("snippet_code"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
            )
            OutlinedTextField(
                value = tags, onValueChange = { tags = it }, label = { Text("Tags (comma separated)") },
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("snippet_tags"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = { onSave(title, description, collection, code, tags) },
                    enabled = canSave,
                    modifier = Modifier.weight(1f).testTag("snippet_save"),
                ) { Text("Save") }
            }
        }
    }
}
