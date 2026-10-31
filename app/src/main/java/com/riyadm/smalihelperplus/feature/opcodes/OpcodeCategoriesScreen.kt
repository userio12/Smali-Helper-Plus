package com.riyadm.smalihelperplus.feature.opcodes

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.core.designsystem.component.EmptyState
import com.riyadm.smalihelperplus.core.designsystem.component.SkeletonBlock
import com.riyadm.smalihelperplus.data.opcode.CategoryInfo
import com.riyadm.smalihelperplus.feature.search.SearchEntryBar
import com.riyadm.smalihelperplus.navigation.AppRoute
import com.riyadm.smalihelperplus.navigation.LearnRoute
import com.riyadm.smalihelperplus.navigation.LessonRoute
import com.riyadm.smalihelperplus.navigation.OpcodeListRoute
import com.riyadm.smalihelperplus.navigation.SearchRoute

/**
 * Top-level "Opcodes" tab: search entry, a compact "Smali basics" row linking to the academy, then the core reference:
 * "All instructions" and one card per opcode group.
 */
@Composable
fun OpcodeCategoriesScreen(onNavigate: (AppRoute) -> Unit) {
    val viewModel: OpcodeCategoriesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    DetailScaffold(
        title = "Opcodes",
        onBack = null,
        large = true,
        actions = {
            IconButton(onClick = { onNavigate(SearchRoute()) }) {
                Icon(Icons.Outlined.Search, contentDescription = "Search opcodes")
            }
        },
    ) { padding ->
        when {
            state.loading -> CategoriesSkeleton(padding)
            state.categories.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                    title = "No instructions yet",
                    message = "The opcode reference has not been installed. Open Updates to check the documentation content.",
                )
            }
            else -> {
                val layoutDirection = LocalLayoutDirection.current
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(320.dp),
                    modifier = Modifier.fillMaxSize().testTag("categories_grid"),
                    contentPadding = PaddingValues(
                        start = 16.dp + padding.calculateStartPadding(layoutDirection),
                        end = 16.dp + padding.calculateEndPadding(layoutDirection),
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SearchEntryBar(
                            placeholder = "Search ${state.total} instructions",
                            onClick = { onNavigate(SearchRoute()) },
                            modifier = Modifier.testTag("opcodes_search"),
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SmaliBasicsSection(
                            onOpenLesson = { onNavigate(LessonRoute(it)) },
                            onOpenAcademy = { onNavigate(LearnRoute) },
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        GroupHeader("Instruction groups", Modifier.testTag("opcodes_groups_header"))
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        AllInstructionsCard(state.total) { onNavigate(OpcodeListRoute(null)) }
                    }
                    items(state.categories, key = { it.category.id }) { info ->
                        CategoryCard(info) { onNavigate(OpcodeListRoute(info.category.id)) }
                    }
                }
            }
        }
    }
}

/** One "Smali basics" shortcut: a beginner topic of the reference that opens the matching academy lesson. */
private data class BasicsTopic(val lessonId: String, val title: String, val subtitle: String, val icon: ImageVector)

private val basicsTopics = listOf(
    BasicsTopic("what-is-smali", "What is Smali?", "Start here: the language and where it lives", Icons.Outlined.School),
    BasicsTopic("registers", "Registers", "v and p names, frames and wide pairs", Icons.Outlined.Memory),
    BasicsTopic("data-types", "Types & descriptors", "I, J, Z, arrays and Lpkg/Name;", Icons.Outlined.Code),
    BasicsTopic("method-signatures", "Method signatures", "How a call names its target method", Icons.Outlined.Api),
    BasicsTopic("debugging-validation", "Debugging & validation", "Debug directives and safe round-trips", Icons.Outlined.Verified),
)

@Composable
private fun GroupHeader(title: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        action()
    }
}

/** Compact, horizontally scrolling row of beginner lessons so the opcode groups below stay one swipe away. */
@Composable
private fun SmaliBasicsSection(onOpenLesson: (String) -> Unit, onOpenAcademy: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("opcodes_basics"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupHeader("Smali basics") {
            TextButton(onClick = onOpenAcademy, modifier = Modifier.testTag("opcodes_basics_all")) { Text("All lessons") }
        }
        Row(
            Modifier
                .bleedHorizontally(16.dp)
                .height(IntrinsicSize.Max)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            basicsTopics.forEach { topic ->
                BasicsCard(topic, Modifier.fillMaxHeight()) { onOpenLesson(topic.lessonId) }
            }
        }
    }
}

/** Lets a horizontally scrolling row reach the screen edges while its first and last items stay aligned with the grid. */
private fun Modifier.bleedHorizontally(bleed: Dp): Modifier = layout { measurable, constraints ->
    val extra = bleed.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.maxWidth + 2 * extra, maxWidth = constraints.maxWidth + 2 * extra),
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}

@Composable
private fun BasicsCard(topic: BasicsTopic, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.width(156.dp).testTag("basics_${topic.lessonId}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(topic.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(topic.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                topic.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AllInstructionsCard(total: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("categories_all"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("All instructions ($total)", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Browse every Dalvik opcode ordered by value",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun CategoryCard(info: CategoryInfo, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("category_${info.category.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    info.category.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(info.category.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    info.category.blurb,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            SmallTag(
                "${info.count}",
                container = MaterialTheme.colorScheme.surfaceContainerHighest,
                content = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun CategoriesSkeleton(padding: PaddingValues) {
    Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth(), height = 56.dp)
        SkeletonBlock(Modifier.fillMaxWidth(), height = 80.dp)
        repeat(5) { SkeletonBlock(Modifier.fillMaxWidth(), height = 72.dp) }
    }
}
