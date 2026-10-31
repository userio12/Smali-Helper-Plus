package com.riyadm.smalihelperplus.feature.opcodes

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.content.OperandDto
import com.riyadm.smalihelperplus.core.designsystem.component.CodeBlock
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.core.designsystem.component.EmptyState
import com.riyadm.smalihelperplus.core.designsystem.component.SkeletonBlock
import com.riyadm.smalihelperplus.core.designsystem.component.copyToClipboard
import com.riyadm.smalihelperplus.core.designsystem.component.shareText
import com.riyadm.smalihelperplus.domain.model.Opcode
import com.riyadm.smalihelperplus.navigation.AppRoute
import com.riyadm.smalihelperplus.navigation.OpcodeDetailRoute
import com.riyadm.smalihelperplus.navigation.PlaygroundRoute
import kotlinx.coroutines.launch

@Composable
fun OpcodeDetailScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val viewModel: OpcodeDetailViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val opcode = state.opcode
    DetailScaffold(
        title = opcode?.name ?: "Opcode",
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        actions = {
            if (opcode != null) {
                IconButton(
                    onClick = {
                        copyToClipboard(context, opcode.name, OpcodeText.summary(opcode))
                        viewModel.showMessage("Copied ${opcode.name} summary")
                    },
                    modifier = Modifier.testTag("opcode_copy"),
                ) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy opcode summary") }
                IconButton(
                    onClick = viewModel::toggleBookmark,
                    modifier = Modifier.testTag("opcode_bookmark"),
                ) {
                    Icon(
                        if (state.bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (state.bookmarked) "Remove bookmark" else "Add bookmark",
                        tint = if (state.bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { shareText(context, opcode.name, OpcodeText.summary(opcode)) },
                    modifier = Modifier.testTag("opcode_share"),
                ) { Icon(Icons.Outlined.Share, contentDescription = "Share opcode") }
            }
        },
    ) { padding ->
        when {
            state.loading -> DetailSkeleton(padding)
            opcode == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = "Opcode not found",
                    message = "There is no instruction with this name in the installed documentation.",
                    actionLabel = "Go back",
                    onAction = onBack,
                )
            }
            else -> OpcodeDetailContent(
                opcode = opcode,
                related = state.related,
                padding = padding,
                onOpenRelated = { onNavigate(OpcodeDetailRoute(it)) },
                onOpenInPlayground = {
                    viewModel.offerExampleToPlayground()
                    onNavigate(PlaygroundRoute)
                },
                onOpenReference = { url ->
                    if (!OpcodeText.isOpenableUrl(url) || !openUrl(context, url)) {
                        scope.launch { snackbarHostState.showSnackbar("No browser app found to open this link") }
                    }
                },
            )
        }
    }
}

@Composable
private fun OpcodeDetailContent(
    opcode: Opcode,
    related: List<Opcode>,
    padding: PaddingValues,
    onOpenRelated: (String) -> Unit,
    onOpenInPlayground: () -> Unit,
    onOpenReference: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeaderCard(opcode)

            SectionCard("Syntax") {
                CodeBlock(code = opcode.syntax, label = "SMALI", showCopy = true)
            }

            if (opcode.operands.isNotEmpty()) {
                SectionCard("Operands") { OperandsTable(opcode.operands) }
            }

            if (opcode.description.isNotBlank()) {
                SectionCard("Explanation") { BodyText(opcode.description) }
            }

            if (opcode.technical.isNotBlank()) {
                SectionCard("Technical description") { BodyText(opcode.technical) }
            }

            if (opcode.example.isNotBlank()) {
                SectionCard("Example") {
                    CodeBlock(code = opcode.example, label = "SMALI EXAMPLE")
                    if (opcode.exampleExplanation.isNotBlank()) BodyText(opcode.exampleExplanation)
                    Button(onClick = onOpenInPlayground, modifier = Modifier.testTag("opcode_open_playground")) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open in Playground")
                    }
                }
            }

            opcode.javaEquivalent?.let { java ->
                SectionCard("Java equivalent") {
                    CodeBlock(code = java, language = "java", label = "JAVA (educational pseudocode)")
                    Disclaimer()
                }
            }

            opcode.kotlinEquivalent?.let { kotlin ->
                SectionCard("Kotlin equivalent") {
                    CodeBlock(code = kotlin, language = "kotlin", label = "KOTLIN (educational pseudocode)")
                    Disclaimer()
                }
            }

            if (opcode.registerUsage.isNotBlank()) {
                SectionCard("Register usage") { BodyText(opcode.registerUsage) }
            }

            if (opcode.compatibility.isNotBlank()) {
                SectionCard("Compatibility") {
                    BodyText(opcode.compatibility)
                }
            }

            if (opcode.commonMistakes.isNotEmpty()) {
                SectionCard("Common mistakes") {
                    opcode.commonMistakes.forEach { mistake ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("•", style = MaterialTheme.typography.bodyLarge)
                            Text(mistake, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            if (related.isNotEmpty()) {
                SectionCard("Related opcodes") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        related.forEach { other ->
                            AssistChip(
                                onClick = { onOpenRelated(other.name) },
                                label = { Text(other.name, fontFamily = FontFamily.Monospace) },
                                modifier = Modifier.testTag("related_${other.name}"),
                            )
                        }
                    }
                }
            }

            if (opcode.references.isNotEmpty()) {
                SectionCard("Official references") {
                    opcode.references.forEachIndexed { index, ref ->
                        if (index > 0) HorizontalDivider()
                        Surface(onClick = { onOpenReference(ref.url) }, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(ref.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        ref.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Open ${ref.title}")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun HeaderCard(opcode: Opcode) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SelectionContainer {
                Text(
                    opcode.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Text(opcode.summary, style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val container = MaterialTheme.colorScheme.surface
                val content = MaterialTheme.colorScheme.onSurface
                SmallTag(opcode.hexValue, container = container, content = content, monospace = true)
                SmallTag(opcode.categoryTitle, container = container, content = content)
                SmallTag("Format ${opcode.format}", container = container, content = content, monospace = true)
                SmallTag("API ${opcode.minApi}+", container = container, content = content)
                SmallTag("DEX ${opcode.dexVersion}+", container = container, content = content, monospace = true)
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun BodyText(text: String) {
    SelectionContainer { Text(text.trim(), style = MaterialTheme.typography.bodyLarge) }
}

@Composable
private fun Disclaimer() {
    Text(
        "Illustrative pseudocode to explain the behaviour. It is not decompiler output.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun OperandsTable(operands: List<OperandDto>) {
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(
                "Operand",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(96.dp),
            )
            Text(
                "Meaning",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        operands.forEachIndexed { index, operand ->
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    operand.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(96.dp).padding(end = 8.dp),
                )
                Text(operand.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DetailSkeleton(padding: PaddingValues) {
    Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth(), height = 120.dp)
        SkeletonBlock(Modifier.fillMaxWidth(), height = 80.dp)
        SkeletonBlock(Modifier.fillMaxWidth(), height = 160.dp)
        SkeletonBlock(Modifier.fillMaxWidth(), height = 120.dp)
    }
}

/** Opens [url] in a browser. Returns false if no app can handle it. */
private fun openUrl(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.trim().toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}
