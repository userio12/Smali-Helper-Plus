package com.riyadm.smalihelperplus.feature.learn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.content.ExerciseDto
import com.riyadm.smalihelperplus.core.content.LessonDto
import com.riyadm.smalihelperplus.core.content.LessonSectionDto
import com.riyadm.smalihelperplus.core.designsystem.component.CodeBlock
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.core.designsystem.component.EmptyState
import com.riyadm.smalihelperplus.core.designsystem.component.ListSkeleton
import com.riyadm.smalihelperplus.core.designsystem.component.shareText
import com.riyadm.smalihelperplus.domain.learning.LessonLevel
import com.riyadm.smalihelperplus.domain.learning.LessonProgressStatus
import com.riyadm.smalihelperplus.navigation.AppRoute
import com.riyadm.smalihelperplus.navigation.LessonRoute
import com.riyadm.smalihelperplus.navigation.OpcodeDetailRoute
import com.riyadm.smalihelperplus.navigation.PlaygroundRoute
import com.riyadm.smalihelperplus.navigation.QuizRoute

@Composable
fun LessonScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val viewModel: LessonViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val content = state as? LessonUiState.Content
    DetailScaffold(
        title = content?.lesson?.title ?: "Lesson",
        onBack = onBack,
        large = true,
        snackbarHostState = snackbar,
        modifier = Modifier.testTag("lesson_screen"),
        actions = {
            if (content != null) {
                IconButton(onClick = viewModel::toggleBookmark, modifier = Modifier.testTag("lesson_bookmark")) {
                    Icon(
                        if (content.bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (content.bookmarked) "Remove bookmark" else "Bookmark lesson",
                    )
                }
                IconButton(
                    onClick = { shareText(context, content.lesson.title, lessonShareText(content.lesson)) },
                    modifier = Modifier.testTag("lesson_share"),
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = "Share lesson")
                }
            }
        },
    ) { padding ->
        when (val s = state) {
            LessonUiState.Loading -> ListSkeleton(modifier = Modifier.padding(padding))
            LessonUiState.NotFound -> EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = "Lesson not found",
                message = "This lesson is not part of the installed content.",
                modifier = Modifier.padding(padding),
                actionLabel = "Go back",
                onAction = onBack,
            )
            is LessonUiState.Content -> LessonContent(
                state = s,
                padding = padding,
                onNavigate = onNavigate,
                onComplete = viewModel::markComplete,
                onReopen = viewModel::markIncomplete,
                onTryInPlayground = { code ->
                    viewModel.sendToPlayground(code, s.lesson.title)
                    onNavigate(PlaygroundRoute)
                },
            )
        }
    }
}

@Composable
private fun LessonContent(
    state: LessonUiState.Content,
    padding: PaddingValues,
    onNavigate: (AppRoute) -> Unit,
    onComplete: () -> Unit,
    onReopen: () -> Unit,
    onTryInPlayground: (String) -> Unit,
) {
    val lesson = state.lesson
    CenteredContent {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("lesson_list"),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "intro") { LessonIntro(state) }
            itemsIndexed(lesson.sections, key = { i, _ -> "section-$i" }) { _, section ->
                SectionView(section, onTryInPlayground)
            }
            if (lesson.exercises.isNotEmpty()) {
                item(key = "exercises-header") { BlockTitle("Practice exercises", Icons.Outlined.FitnessCenter) }
                itemsIndexed(lesson.exercises, key = { _, e -> "exercise-${e.id}" }) { index, exercise ->
                    ExerciseCard(index + 1, exercise, onTryInPlayground)
                }
            }
            if (lesson.relatedOpcodes.isNotEmpty()) {
                item(key = "opcodes") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BlockTitle("Related opcodes", null)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            lesson.relatedOpcodes.forEach { op ->
                                AssistChip(
                                    onClick = { onNavigate(OpcodeDetailRoute(op)) },
                                    label = { Text(op, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) },
                                    modifier = Modifier.testTag("lesson_opcode_$op"),
                                )
                            }
                        }
                    }
                }
            }
            item(key = "actions") { LessonActions(state, onNavigate, onComplete, onReopen) }
        }
    }
}

@Composable
private fun LessonIntro(state: LessonUiState.Content) {
    val lesson = state.lesson
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LessonLevel.fromId(lesson.level)?.let { LevelPill(it) }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Outlined.Schedule,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "${lesson.estimatedMinutes} min · Lesson ${lesson.order}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            state.summary?.let { StatusPill(it) }
        }
        Text(lesson.summary, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun BlockTitle(title: String, icon: ImageVector?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun SectionView(section: LessonSectionDto, onTryInPlayground: (String) -> Unit) {
    when (section.type) {
        "code", "java" -> {
            val isSmali = section.type == "code"
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                section.title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                CodeBlock(
                    code = section.body,
                    language = if (isSmali) "smali" else "java",
                    label = if (isSmali) "SMALI" else "JAVA",
                )
                if (isSmali) {
                    TextButton(onClick = { onTryInPlayground(section.body) }) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open in Playground")
                    }
                }
            }
        }
        "tip" -> CalloutCard(
            title = section.title ?: "Tip",
            body = section.body,
            icon = Icons.Outlined.Lightbulb,
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        "warning" -> CalloutCard(
            title = section.title ?: "Watch out",
            body = section.body,
            icon = Icons.Outlined.WarningAmber,
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            section.title?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            RichText(section.body)
        }
    }
}

@Composable
private fun CalloutCard(title: String, body: String, icon: ImageVector, container: Color, content: Color) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                RichText(body, color = content)
            }
        }
    }
}

/**
 * Paragraphs separated by blank lines; lines starting with "- " render as bullets, "1. " as numbered items. As in
 * Markdown, a single line break inside a paragraph or list item is a soft wrap and is joined with a space.
 */
@Composable
internal fun RichText(body: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        body.split("\n\n").forEach { paragraph ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                joinSoftBreaks(paragraph).forEach { trimmed ->
                    val bullet = trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")
                    val numbered = Regex("^(\\d+)[.)] ").find(trimmed)
                    when {
                        bullet -> Row {
                            Text("•", style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.width(18.dp))
                            Text(trimmed.drop(2), style = MaterialTheme.typography.bodyLarge, color = color)
                        }
                        numbered != null -> Row {
                            Text("${numbered.groupValues[1]}.", style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.width(26.dp))
                            Text(trimmed.substring(numbered.value.length), style = MaterialTheme.typography.bodyLarge, color = color)
                        }
                        else -> Text(trimmed, style = MaterialTheme.typography.bodyLarge, color = color)
                    }
                }
            }
        }
    }
}

private val listItemStart = Regex("^(?:[-*•]|\\d+[.)]) ")

/** Trimmed logical lines of one paragraph: list items start a new line, any other line continues the previous one. */
internal fun joinSoftBreaks(paragraph: String): List<String> {
    val out = mutableListOf<String>()
    paragraph.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
        if (out.isEmpty() || listItemStart.containsMatchIn(line)) out += line else out[out.lastIndex] = out.last() + " " + line
    }
    return out
}

@Composable
private fun ExerciseCard(number: Int, exercise: ExerciseDto, onTryInPlayground: (String) -> Unit) {
    var expanded by rememberSaveable(exercise.id) { mutableStateOf(false) }
    var showHint by rememberSaveable(exercise.id + "_hint") { mutableStateOf(false) }
    var showSolution by rememberSaveable(exercise.id + "_solution") { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val solutionIsCode = looksLikeSmali(exercise.solution)
    val playgroundCode = exercise.starterCode?.takeIf { looksLikeSmali(it) } ?: exercise.solution.takeIf { solutionIsCode }

    Card(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("exercise_${exercise.id}")
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "Exercise $number",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse exercise" else "Expand exercise",
                    modifier = Modifier.rotate(rotation),
                )
            }
            Text(exercise.prompt, style = MaterialTheme.typography.bodyLarge, maxLines = if (expanded) Int.MAX_VALUE else 3)
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    exercise.starterCode?.let { starter ->
                        CodeBlock(code = starter, language = if (looksLikeSmali(starter)) "smali" else "text", label = "STARTER")
                    }
                    if (exercise.hint != null) {
                        if (showHint) {
                            CalloutCard(
                                title = "Hint",
                                body = exercise.hint,
                                icon = Icons.Outlined.Lightbulb,
                                container = MaterialTheme.colorScheme.secondaryContainer,
                                content = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    if (showSolution) {
                        if (solutionIsCode) {
                            CodeBlock(code = exercise.solution, language = "smali", label = "SOLUTION")
                        } else {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Solution", style = MaterialTheme.typography.titleSmall)
                                    RichText(exercise.solution)
                                }
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (exercise.hint != null && !showHint) {
                            OutlinedButton(onClick = { showHint = true }, modifier = Modifier.testTag("exercise_hint_${exercise.id}")) {
                                Text("Show hint")
                            }
                        }
                        if (!showSolution) {
                            FilledTonalButton(onClick = { showSolution = true }, modifier = Modifier.testTag("exercise_solution_${exercise.id}")) {
                                Text("Reveal solution")
                            }
                        }
                        if (playgroundCode != null) {
                            OutlinedButton(
                                onClick = { onTryInPlayground(playgroundCode) },
                                modifier = Modifier.testTag("exercise_try_${exercise.id}"),
                            ) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Try in Playground")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonActions(
    state: LessonUiState.Content,
    onNavigate: (AppRoute) -> Unit,
    onComplete: () -> Unit,
    onReopen: () -> Unit,
) {
    val completed = state.summary?.status == LessonProgressStatus.COMPLETED
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.lesson.quiz.isNotEmpty()) {
            Button(
                onClick = { onNavigate(QuizRoute(state.lesson.id)) },
                modifier = Modifier.fillMaxWidth().testTag("lesson_quiz"),
            ) {
                Icon(Icons.Outlined.Quiz, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    state.summary?.bestQuizScore?.let { "Take the quiz again (best $it%)" }
                        ?: "Take the quiz (${pluralize(state.lesson.quiz.size, "question")})",
                )
            }
        }
        if (completed) {
            val c = correctColors()
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = c.container, contentColor = c.content),
                modifier = Modifier.fillMaxWidth().testTag("lesson_completed_banner"),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Lesson completed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        state.summary.completedAtMillis?.let {
                            Text("on ${formatDate(it)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick = onReopen, modifier = Modifier.testTag("lesson_mark_incomplete")) { Text("Mark as not done") }
                }
            }
        } else {
            FilledTonalButton(onClick = onComplete, modifier = Modifier.fillMaxWidth().testTag("lesson_complete")) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Mark as complete")
            }
        }
        if (state.nextLessonId != null) {
            OutlinedButton(
                onClick = { onNavigate(LessonRoute(state.nextLessonId)) },
                modifier = Modifier.fillMaxWidth().testTag("lesson_next"),
            ) {
                Text("Next: ${state.nextLessonTitle}", maxLines = 1)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Plain-text version of a lesson for the share sheet. */
internal fun lessonShareText(lesson: LessonDto): String = buildString {
    appendLine(lesson.title)
    appendLine()
    appendLine(lesson.summary)
    lesson.sections.forEach { s ->
        appendLine()
        s.title?.let { appendLine(it) }
        when (s.type) {
            "code" -> { appendLine("```smali"); appendLine(s.body); appendLine("```") }
            "java" -> { appendLine("```java"); appendLine(s.body); appendLine("```") }
            "tip" -> appendLine("Tip: ${s.body}")
            "warning" -> appendLine("Warning: ${s.body}")
            else -> appendLine(s.body)
        }
    }
    appendLine()
    append("Shared from Smali Helper Plus")
}
