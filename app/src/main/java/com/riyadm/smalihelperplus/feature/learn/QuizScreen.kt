package com.riyadm.smalihelperplus.feature.learn

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.designsystem.component.CodeBlock
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.core.designsystem.component.EmptyState
import com.riyadm.smalihelperplus.core.designsystem.component.ListSkeleton
import com.riyadm.smalihelperplus.domain.learning.QuizItem
import com.riyadm.smalihelperplus.navigation.AppRoute
import com.riyadm.smalihelperplus.navigation.LessonRoute

@Composable
fun QuizScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val viewModel: QuizViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    DetailScaffold(
        title = state.title.ifBlank { "Quiz" },
        onBack = onBack,
        modifier = Modifier.testTag("quiz_screen"),
    ) { padding ->
        when {
            state.loading -> ListSkeleton(modifier = Modifier.padding(padding))
            state.questions.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Quiz,
                title = "No questions available",
                message = if (state.mode == QuizMode.LESSON) "This lesson has no quiz." else "Lessons are not installed yet.",
                modifier = Modifier.padding(padding),
                actionLabel = "Go back",
                onAction = onBack,
            )
            state.finished -> ResultView(
                state = state,
                padding = padding,
                onRetry = viewModel::retry,
                onBack = onBack,
                onNextLesson = { id -> onNavigate(LessonRoute(id)) },
            )
            else -> QuestionView(state, padding, viewModel::select, viewModel::next)
        }
    }
}

@Composable
private fun QuestionView(state: QuizUiState, padding: PaddingValues, onSelect: (Int) -> Unit, onNext: () -> Unit) {
    val total = state.questions.size
    CenteredContent(Modifier.padding(padding)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Question ${state.index + 1} of $total",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f).testTag("quiz_counter"),
                    )
                    Text(
                        "${state.correctCount} correct",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = { (state.index + if (state.answered) 1 else 0).toFloat() / total },
                    modifier = Modifier.fillMaxWidth().height(8.dp).testTag("quiz_progress"),
                    drawStopIndicator = {},
                )
            }
            AnimatedContent(
                targetState = state.index,
                transitionSpec = {
                    (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
                },
                modifier = Modifier.weight(1f),
                label = "question",
            ) { index ->
                val item = state.questions[index]
                val selected = state.answers.getOrNull(index)
                QuestionBody(item, selected, onSelect)
            }
            Surface(tonalElevation = 2.dp) {
                Button(
                    onClick = onNext,
                    enabled = state.answered,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("quiz_next"),
                ) {
                    Text(if (state.isLast) "See results" else "Next question")
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun QuestionBody(item: QuizItem, selected: Int?, onSelect: (Int) -> Unit) {
    val q = item.question
    val answered = selected != null
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(item.lessonTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(q.question, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("quiz_question"))
        q.code?.let { CodeBlock(code = it, language = if (looksLikeSmali(it)) "smali" else "text", showCopy = false) }
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            q.options.forEachIndexed { i, option ->
                OptionCard(
                    index = i,
                    text = option,
                    isSelected = selected == i,
                    isCorrect = i == q.correctIndex,
                    answered = answered,
                    onClick = { onSelect(i) },
                )
            }
        }
        AnimatedVisibility(answered) {
            val correct = selected == q.correctIndex
            val colors = if (correct) correctColors() else incorrectColors()
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.container, contentColor = colors.content),
                modifier = Modifier.fillMaxWidth().testTag("quiz_feedback"),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (correct) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (correct) "Correct!" else "Not quite",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (!correct) {
                        Text("Correct answer: ${q.options[q.correctIndex]}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    Text(q.explanation, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun OptionCard(index: Int, text: String, isSelected: Boolean, isCorrect: Boolean, answered: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val good = correctColors()
    val bad = incorrectColors()
    val (container, content, border) = when {
        answered && isCorrect -> Triple(good.container, good.content, good.accent)
        answered && isSelected -> Triple(bad.container, bad.content, bad.accent)
        else -> Triple(scheme.surfaceContainerLow, scheme.onSurface, scheme.outlineVariant)
    }
    val icon = when {
        answered && isCorrect -> Icons.Outlined.CheckCircle
        answered && isSelected -> Icons.Outlined.Cancel
        else -> Icons.Outlined.RadioButtonUnchecked
    }
    val status = when {
        !answered -> null
        isCorrect -> "correct answer"
        isSelected -> "your answer, incorrect"
        else -> null
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        border = BorderStroke(if (answered && (isCorrect || isSelected)) 2.dp else 1.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quiz_option_$index")
            .selectable(selected = isSelected, enabled = !answered, role = Role.RadioButton, onClick = onClick)
            .semantics { if (status != null) stateDescription = status },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (answered && (isCorrect || isSelected)) border else scheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ResultView(
    state: QuizUiState,
    padding: PaddingValues,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onNextLesson: (String) -> Unit,
) {
    val correct = state.outcome?.correct ?: state.correctCount
    val total = state.questions.size
    val percent = com.riyadm.smalihelperplus.domain.learning.scorePercent(correct, total)
    val message = when {
        percent == 100 -> "Perfect score!"
        percent >= 80 -> "Great work!"
        percent >= 50 -> "Good effort. Review the explanations and try again."
        else -> "Keep practising. Re-read the lesson and give it another go."
    }
    CenteredContent(Modifier.padding(padding)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("quiz_result"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            ProgressRing(
                progress = correct.toFloat() / total,
                size = 140.dp,
                strokeWidth = 12.dp,
                label = "$percent%",
                contentDescription = "Score $percent percent",
            )
            Text("$correct of $total correct", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("quiz_score"))
            Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            val outcome = state.outcome
            if (outcome == null) {
                Text("Saving result…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                if (state.saveFailed) {
                    Text(
                        "Your result could not be saved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (outcome.isNewBest && state.mode == QuizMode.LESSON) {
                    Pill("New best score", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                }
                outcome.newAchievements.forEach { a ->
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                            contentDescription = "Achievement unlocked: ${a.title}. ${a.description}"
                        },
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(
                                Icons.Outlined.EmojiEvents,
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Achievement unlocked", style = MaterialTheme.typography.labelMedium)
                                Text(a.title, style = MaterialTheme.typography.titleSmall)
                                Text(a.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            ReviewList(state)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.mode == QuizMode.LESSON && state.nextLessonId != null) {
                    Button(onClick = { onNextLesson(state.nextLessonId) }, modifier = Modifier.fillMaxWidth().testTag("quiz_next_lesson")) {
                        Text("Next lesson: ${state.nextLessonTitle ?: ""}", maxLines = 1)
                    }
                }
                FilledTonalButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().testTag("quiz_retry")) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.mode == QuizMode.MIXED) "New mixed quiz" else "Retry")
                }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().testTag("quiz_back")) {
                    Text(if (state.mode == QuizMode.LESSON) "Back to lesson" else "Done")
                }
            }
        }
    }
}

@Composable
private fun ReviewList(state: QuizUiState) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Review", style = MaterialTheme.typography.titleMedium)
        state.questions.forEachIndexed { i, item ->
            val right = state.answers.getOrNull(i) == item.question.correctIndex
            val colors = if (right) correctColors() else incorrectColors()
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    if (right) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                    contentDescription = if (right) "Correct" else "Incorrect",
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.question.question, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                    if (!right) {
                        Text(
                            "Answer: ${item.question.options[item.question.correctIndex]}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

