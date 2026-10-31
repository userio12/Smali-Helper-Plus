package com.riyadm.smalihelperplus.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.core.designsystem.component.EmptyState
import com.riyadm.smalihelperplus.domain.tools.ToolSearch
import com.riyadm.smalihelperplus.navigation.AiAssistantRoute
import com.riyadm.smalihelperplus.navigation.AppRoute
import com.riyadm.smalihelperplus.navigation.CheatSheetsRoute
import com.riyadm.smalihelperplus.navigation.CodeComparisonRoute
import com.riyadm.smalihelperplus.navigation.CompatibilityCheckerRoute
import com.riyadm.smalihelperplus.navigation.ControlFlowRoute
import com.riyadm.smalihelperplus.navigation.DescriptorConverterRoute
import com.riyadm.smalihelperplus.navigation.FormatExplorerRoute
import com.riyadm.smalihelperplus.navigation.MethodExplorerRoute
import com.riyadm.smalihelperplus.navigation.NumberConverterRoute
import com.riyadm.smalihelperplus.navigation.RegisterCalculatorRoute
import com.riyadm.smalihelperplus.navigation.RegisterVisualizerRoute
import com.riyadm.smalihelperplus.navigation.SmaliExplainerRoute
import com.riyadm.smalihelperplus.navigation.SnippetLibraryRoute

private enum class ToolGroup(val title: String) {
    CONVERTERS("Converters"), ANALYSIS("Analysis"), REFERENCE("Reference"), LIBRARY("Library")
}

private data class ToolEntry(
    val tag: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val route: AppRoute,
    val group: ToolGroup,
    val keywords: List<String> = emptyList(),
)

private val TOOLS = listOf(
    ToolEntry("register_calculator", "Register calculator", "Work out .locals, p-registers and wide pairs from a signature.", Icons.Outlined.Calculate, RegisterCalculatorRoute, ToolGroup.CONVERTERS, listOf("locals", "registers", "parameters", "p0", "wide")),
    ToolEntry("number_converter", "Number converter", "Hex, binary, octal, two's complement, float bits and the right const instruction.", Icons.Outlined.Pin, NumberConverterRoute, ToolGroup.CONVERTERS, listOf("hex", "binary", "float", "const", "literal", "ieee")),
    ToolEntry("descriptor_converter", "Descriptor converter", "Java types and signatures to Dalvik descriptors and back.", Icons.Outlined.SwapHoriz, DescriptorConverterRoute, ToolGroup.CONVERTERS, listOf("type", "proto", "signature", "field", "method reference")),
    ToolEntry("method_explorer", "Method explorer", "Explore the classes and methods inside smali code.", Icons.Outlined.Search, MethodExplorerRoute, ToolGroup.ANALYSIS, listOf("class", "outline", "methods")),
    ToolEntry("control_flow", "Control flow", "Understand the branches and blocks of a method.", Icons.Outlined.AccountTree, ControlFlowRoute, ToolGroup.ANALYSIS, listOf("graph", "cfg", "blocks", "branches")),
    ToolEntry("smali_explainer", "Smali explainer", "Plain-language explanations of what smali code does.", Icons.Outlined.Lightbulb, SmaliExplainerRoute, ToolGroup.ANALYSIS, listOf("explain", "understand", "pseudocode")),
    ToolEntry("register_visualizer", "Register visualizer", "Visualise how registers hold values in a method.", Icons.Outlined.Memory, RegisterVisualizerRoute, ToolGroup.ANALYSIS, listOf("registers", "trace", "state")),
    ToolEntry("code_comparison", "Code comparison", "Diff two smali files with intra-line highlights.", Icons.Outlined.Compare, CodeComparisonRoute, ToolGroup.ANALYSIS, listOf("diff", "compare", "patch")),
    ToolEntry("compatibility_checker", "Compatibility checker", "Find instructions that need a newer Android API or DEX version.", Icons.Outlined.PhoneAndroid, CompatibilityCheckerRoute, ToolGroup.ANALYSIS, listOf("minsdk", "api", "dex version")),
    ToolEntry("format_explorer", "Instruction formats", "Layout diagrams of every Dalvik instruction format.", Icons.Outlined.ViewColumn, FormatExplorerRoute, ToolGroup.REFERENCE, listOf("encoding", "bits", "12x", "35c", "layout")),
    ToolEntry("cheat_sheets", "Cheat sheets", "Descriptors, access flags, directives, invokes, patterns and more.", Icons.Outlined.Description, CheatSheetsRoute, ToolGroup.REFERENCE, listOf("reference", "flags", "directives", "patterns")),
    ToolEntry("snippet_library", "Snippet library", "Ready-made smali snippets plus your own collection.", Icons.Outlined.Inventory2, SnippetLibraryRoute, ToolGroup.LIBRARY, listOf("examples", "templates", "samples")),
    ToolEntry("ai_assistant", "AI assistant", "Ask questions about smali and get explanations.", Icons.Outlined.AutoAwesome, AiAssistantRoute, ToolGroup.LIBRARY, listOf("ai", "chat", "help")),
)

@Composable
fun ToolsScreen(onNavigate: (AppRoute) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(query) { TOOLS.filter { ToolSearch.matches(query, it.title, it.description, it.keywords) } }

    DetailScaffold(title = "Tools", onBack = null, large = true) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 168.dp),
                modifier = Modifier.fillMaxSize().testTag("tools_grid"),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().testTag("tools_search"),
                        placeholder = { Text("Search tools") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, contentDescription = "Clear search") }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                    )
                }
                if (visible.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                        EmptyState(
                            icon = Icons.Outlined.SearchOff,
                            title = "No matching tool",
                            message = "Try a word like \"hex\", \"diff\" or \"registers\".",
                        )
                    }
                }
                ToolGroup.entries.forEach { group ->
                    val inGroup = visible.filter { it.group == group }
                    if (inGroup.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "header_${group.name}") {
                            Text(
                                group.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp).semantics { contentDescription = "${group.title} tools" },
                            )
                        }
                        items(inGroup, key = { it.tag }) { tool ->
                            ToolCardItem(tool) { onNavigate(tool.route) }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }, key = "spacer") { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun ToolCardItem(tool: ToolEntry, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag("tool_${tool.tag}"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(tool.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(tool.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
