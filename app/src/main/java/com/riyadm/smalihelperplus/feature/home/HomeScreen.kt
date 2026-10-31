package com.riyadm.smalihelperplus.feature.home

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.database.entity.BookmarkEntity
import com.riyadm.smalihelperplus.core.database.entity.BookmarkType
import com.riyadm.smalihelperplus.core.database.entity.ContentMetadataEntity
import com.riyadm.smalihelperplus.core.database.entity.RecentViewEntity
import com.riyadm.smalihelperplus.core.database.entity.SnippetEntity
import com.riyadm.smalihelperplus.core.designsystem.component.SkeletonBlock
import com.riyadm.smalihelperplus.domain.model.Opcode
import com.riyadm.smalihelperplus.feature.library.TypeBadge
import com.riyadm.smalihelperplus.feature.library.typeLabel
import com.riyadm.smalihelperplus.feature.opcodes.SmallTag
import com.riyadm.smalihelperplus.feature.search.SearchEntryBar
import com.riyadm.smalihelperplus.navigation.AboutRoute
import com.riyadm.smalihelperplus.navigation.AiAssistantRoute
import com.riyadm.smalihelperplus.navigation.AppRoute
import com.riyadm.smalihelperplus.navigation.BookmarksRoute
import com.riyadm.smalihelperplus.navigation.CheatSheetsRoute
import com.riyadm.smalihelperplus.navigation.CodeComparisonRoute
import com.riyadm.smalihelperplus.navigation.ControlFlowRoute
import com.riyadm.smalihelperplus.navigation.DescriptorConverterRoute
import com.riyadm.smalihelperplus.navigation.HistoryRoute
import com.riyadm.smalihelperplus.navigation.LearnRoute
import com.riyadm.smalihelperplus.navigation.LessonRoute
import com.riyadm.smalihelperplus.navigation.MethodExplorerRoute
import com.riyadm.smalihelperplus.navigation.NumberConverterRoute
import com.riyadm.smalihelperplus.navigation.OpcodeDetailRoute
import com.riyadm.smalihelperplus.navigation.PlaygroundRoute
import com.riyadm.smalihelperplus.navigation.RegisterCalculatorRoute
import com.riyadm.smalihelperplus.navigation.SearchRoute
import com.riyadm.smalihelperplus.navigation.SettingsRoute
import com.riyadm.smalihelperplus.navigation.SmaliExplainerRoute
import com.riyadm.smalihelperplus.navigation.SnippetLibraryRoute
import com.riyadm.smalihelperplus.navigation.UpdatesRoute
import java.text.DateFormat
import java.util.Date

/** Home dashboard (top-level). */
@Composable
fun HomeScreen(onNavigate: (AppRoute) -> Unit) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshClock() }

    val containerWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val wide = containerWidth >= 600.dp
    val columns = if (wide) 2 else 1
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize().testTag("home_list"),
                contentPadding = PaddingValues(
                    start = 16.dp + padding.calculateStartPadding(layoutDirection),
                    end = 16.dp + padding.calculateEndPadding(layoutDirection),
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                fullWidth { Header(state.greeting, onNavigate) }
                fullWidth {
                    SearchEntryBar(
                        placeholder = if (state.opcodeCount > 0) "Search ${state.opcodeCount} opcodes, formats, concepts" else "Search the reference",
                        onClick = { onNavigate(SearchRoute()) },
                        modifier = Modifier.testTag("home_search"),
                    )
                }
                if (state.loading) {
                    fullWidth { HomeSkeleton() }
                    return@LazyVerticalGrid
                }

                state.featured?.let { featured ->
                    cell { FeaturedCard(featured) { onNavigate(OpcodeDetailRoute(featured.name)) } }
                }
                cell { ContinueLearningCard(state.continueLearning, onNavigate) }
                fullWidth { StatsCard(state.stats) }
                if (state.recent.isNotEmpty()) {
                    fullWidth {
                        RecentSection(state.recent, onOpenAll = { onNavigate(HistoryRoute) }) { item ->
                            viewModel.open(item.type, item.targetId, onNavigate)
                        }
                    }
                }
                fullWidth { ToolsSection(onNavigate) }
                val hasBookmarks = state.bookmarkCount > 0
                if (!wide || !hasBookmarks) {
                    fullWidth {
                        SnippetsSection(state.snippets, onNavigate) { snippet ->
                            viewModel.offerSnippetToPlayground(snippet)
                            onNavigate(PlaygroundRoute)
                        }
                    }
                } else {
                    cell {
                        SnippetsSection(state.snippets, onNavigate) { snippet ->
                            viewModel.offerSnippetToPlayground(snippet)
                            onNavigate(PlaygroundRoute)
                        }
                    }
                }
                if (hasBookmarks) {
                    if (wide) {
                        cell { BookmarksSection(state.bookmarks, state.bookmarkCount, onNavigate, viewModel::open) }
                    } else {
                        fullWidth { BookmarksSection(state.bookmarks, state.bookmarkCount, onNavigate, viewModel::open) }
                    }
                }
                fullWidth { DocumentationCard(state.content) { onNavigate(UpdatesRoute) } }
            }
            // Home has no top app bar, so protect the transparent status bar from cards scrolling underneath it.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f)),
            )
        }
    }
}

private fun LazyGridScope.fullWidth(content: @Composable LazyGridItemScope.() -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }, content = content)

private fun LazyGridScope.cell(content: @Composable LazyGridItemScope.() -> Unit) = item(content = content)

// -------------------------------------------------------------------------------------------- header

@Composable
private fun Header(greeting: String, onNavigate: (AppRoute) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "Smali Helper Plus",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Text(greeting, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onNavigate(BookmarksRoute) }, modifier = Modifier.testTag("home_bookmarks")) {
            Icon(Icons.Outlined.BookmarkBorder, contentDescription = "Bookmarks")
        }
        IconButton(onClick = { onNavigate(HistoryRoute) }, modifier = Modifier.testTag("home_history")) {
            Icon(Icons.Outlined.History, contentDescription = "Recently viewed")
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.testTag("home_menu")) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                MenuEntry("AI assistant", Icons.Outlined.AutoAwesome, "menu_ai") { menu = false; onNavigate(AiAssistantRoute) }
                MenuEntry("Updates", Icons.Outlined.Update, "menu_updates") { menu = false; onNavigate(UpdatesRoute) }
                MenuEntry("Settings", Icons.Outlined.Settings, "menu_settings") { menu = false; onNavigate(SettingsRoute) }
                MenuEntry("About", Icons.Outlined.Info, "menu_about") { menu = false; onNavigate(AboutRoute) }
            }
        }
    }
}

@Composable
private fun MenuEntry(label: String, icon: ImageVector, tag: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
        modifier = Modifier.testTag(tag),
    )
}

// -------------------------------------------------------------------------------------------- sections

@Composable
private fun SectionTitle(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun FeaturedCard(opcode: Opcode, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("home_featured_opcode"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Opcode of the day", style = MaterialTheme.typography.labelLarge)
            Text(
                opcode.name,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(opcode.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val container = MaterialTheme.colorScheme.surface
                val content = MaterialTheme.colorScheme.onSurface
                SmallTag(opcode.hexValue, container = container, content = content, monospace = true)
                SmallTag(opcode.categoryTitle, container = container, content = content)
                SmallTag("Format ${opcode.format}", container = container, content = content, monospace = true)
            }
        }
    }
}

@Composable
private fun ContinueLearningCard(state: ContinueLearning, onNavigate: (AppRoute) -> Unit) {
    val (title, body, button, route) = when (state) {
        is ContinueLearning.Next -> CardContent(
            title = state.lesson.title,
            body = state.lesson.summary,
            button = if (state.inProgress) "Continue" else "Start lesson",
            route = LessonRoute(state.lesson.id),
        )
        ContinueLearning.AllDone -> CardContent(
            title = "All lessons completed",
            body = "Great work. Revisit lessons, practise with quizzes or review flashcards.",
            button = "Open Learn",
            route = LearnRoute,
        )
        ContinueLearning.None -> CardContent(
            title = "Start learning Smali",
            body = "Step-by-step lessons from registers and types to methods, fields and control flow.",
            button = "Open Learn",
            route = LearnRoute,
        )
    }
    Card(
        onClick = { onNavigate(route) },
        modifier = Modifier.fillMaxWidth().testTag("home_continue_learning"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.School, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                val label = when {
                    state is ContinueLearning.Next && state.inProgress -> "Continue learning"
                    state is ContinueLearning.Next -> "Up next"
                    else -> "Learn"
                }
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(body, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (state is ContinueLearning.Next) {
                Text(
                    "About ${state.lesson.estimatedMinutes} min",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Button(onClick = { onNavigate(route) }) { Text(button) }
        }
    }
}

private data class CardContent(val title: String, val body: String, val button: String, val route: AppRoute)

@Composable
private fun StatsCard(stats: LearningStats) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("home_stats"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTitle("Learning statistics")
            if (stats.lessonsTotal > 0) {
                LinearProgressIndicator(
                    progress = { stats.completedFraction },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("Lessons", "${stats.lessonsCompleted}/${stats.lessonsTotal}", Modifier.weight(1f))
                Stat("Day streak", "${stats.streak}", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("Quizzes taken", "${stats.quizzesTaken}", Modifier.weight(1f))
                Stat("Average score", stats.averageScorePercent?.let { "$it%" } ?: "-", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecentSection(recent: List<RecentViewEntity>, onOpenAll: () -> Unit, onOpen: (RecentViewEntity) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle("Recently viewed", "See all", onOpenAll)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("home_recent")) {
            items(recent, key = { "${it.type}:${it.targetId}" }) { item ->
                Card(
                    onClick = { onOpen(item) },
                    modifier = Modifier.width(176.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TypeBadge(item.type)
                        Text(
                            item.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = if (item.type == BookmarkType.OPCODE) FontFamily.Monospace else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            item.subtitle.ifBlank { typeLabel(item.type) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private data class Tool(val label: String, val icon: ImageVector, val route: AppRoute, val tag: String)

private val tools = listOf(
    Tool("Register calculator", Icons.Outlined.Calculate, RegisterCalculatorRoute, "tool_register_calculator"),
    Tool("Number converter", Icons.Outlined.Numbers, NumberConverterRoute, "tool_number_converter"),
    Tool("Descriptor converter", Icons.Outlined.SwapHoriz, DescriptorConverterRoute, "tool_descriptor_converter"),
    Tool("Method explorer", Icons.Outlined.Explore, MethodExplorerRoute, "tool_method_explorer"),
    Tool("Code comparison", Icons.Outlined.Compare, CodeComparisonRoute, "tool_code_comparison"),
    Tool("Explainer", Icons.Outlined.Lightbulb, SmaliExplainerRoute, "tool_explainer"),
    Tool("Control flow", Icons.Outlined.AccountTree, ControlFlowRoute, "tool_control_flow"),
    Tool("Cheat sheets", Icons.Outlined.Description, CheatSheetsRoute, "tool_cheat_sheets"),
)

@Composable
private fun ToolsSection(onNavigate: (AppRoute) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Quick developer tools")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2,
            modifier = Modifier.fillMaxWidth(),
        ) {
            tools.forEach { tool ->
                Surface(
                    onClick = { onNavigate(tool.route) },
                    modifier = Modifier.weight(1f).testTag(tool.tag),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(tool.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(tool.label, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun SnippetsSection(snippets: List<SnippetEntity>, onNavigate: (AppRoute) -> Unit, onOpen: (SnippetEntity) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("home_snippets"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Saved snippets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { onNavigate(SnippetLibraryRoute) }) { Text("See all") }
            }
            if (snippets.isEmpty()) {
                Text(
                    "Snippets you save from the Playground appear here for one-tap reuse.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            } else {
                snippets.forEach { snippet ->
                    Surface(onClick = { onOpen(snippet) }, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            TypeBadge(BookmarkType.SNIPPET)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(snippet.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    snippet.description.ifBlank { snippet.code.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty() },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Open ${snippet.title} in Playground")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarksSection(
    bookmarks: List<BookmarkEntity>,
    total: Int,
    onNavigate: (AppRoute) -> Unit,
    onOpen: (String, String, (AppRoute) -> Unit) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("home_bookmarks_section"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Bookmarks ($total)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { onNavigate(BookmarksRoute) }) { Text("See all") }
            }
            bookmarks.forEach { bookmark ->
                Surface(
                    onClick = { onOpen(bookmark.type, bookmark.targetId, onNavigate) },
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        TypeBadge(bookmark.type)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                bookmark.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = if (bookmark.type == BookmarkType.OPCODE) FontFamily.Monospace else null,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                bookmark.subtitle.ifBlank { typeLabel(bookmark.type) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentationCard(content: List<ContentMetadataEntity>, onOpenUpdates: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("home_documentation"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Documentation")
            if (content.isEmpty()) {
                Text(
                    "Bundled documentation is being prepared.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                content.forEach { meta ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            meta.packageId.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "v${meta.contentVersion} · ${meta.source}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val latest = content.maxOf { it.updatedAtMillis }
                Text(
                    "Last updated ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(latest))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onOpenUpdates, modifier = Modifier.testTag("home_check_updates")) {
                Icon(Icons.Outlined.Update, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Check for updates")
            }
        }
    }
}

@Composable
private fun HomeSkeleton() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth(), height = 160.dp)
        SkeletonBlock(Modifier.fillMaxWidth(), height = 160.dp)
        SkeletonBlock(Modifier.fillMaxWidth(), height = 120.dp)
        SkeletonBlock(Modifier.fillMaxWidth(), height = 200.dp)
    }
}
