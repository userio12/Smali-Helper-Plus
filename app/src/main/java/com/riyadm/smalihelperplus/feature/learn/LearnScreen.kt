package com.riyadm.smalihelperplus.feature.learn

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.core.designsystem.component.EmptyState
import com.riyadm.smalihelperplus.core.designsystem.component.ListSkeleton
import com.riyadm.smalihelperplus.domain.learning.LearningSummary
import com.riyadm.smalihelperplus.domain.learning.LessonLevel
import com.riyadm.smalihelperplus.domain.learning.LessonProgressStatus
import com.riyadm.smalihelperplus.domain.learning.LessonSummary
import com.riyadm.smalihelperplus.navigation.AppRoute
import com.riyadm.smalihelperplus.navigation.FlashcardsRoute
import com.riyadm.smalihelperplus.navigation.LearningProgressRoute
import com.riyadm.smalihelperplus.navigation.LessonRoute
import com.riyadm.smalihelperplus.navigation.QuizRoute
import com.riyadm.smalihelperplus.navigation.UpdatesRoute

@Composable
fun LearnScreen(onNavigate: (AppRoute) -> Unit) {
    val viewModel: LearnViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    DetailScaffold(
        title = "Learn",
        onBack = null,
        large = true,
        modifier = Modifier.testTag("learn_screen"),
        actions = {
            IconButton(onClick = { onNavigate(LearningProgressRoute) }, modifier = Modifier.testTag("learn_progress_action")) {
                Icon(Icons.Outlined.Insights, contentDescription = "Progress and achievements")
            }
        },
    ) { padding ->
        when (val s = state) {
            LearnUiState.Loading -> ListSkeleton(modifier = Modifier.padding(padding))
            LearnUiState.Empty -> EmptyState(
                icon = Icons.Outlined.School,
                title = "No lessons installed",
                message = "The bundled lessons could not be installed. Check for documentation updates to download them again.",
                modifier = Modifier.padding(padding),
                actionLabel = "Check for updates",
                onAction = { onNavigate(UpdatesRoute) },
            )
            is LearnUiState.Content -> LearnContent(s, padding, onNavigate)
        }
    }
}

@Composable
private fun LearnContent(state: LearnUiState.Content, padding: PaddingValues, onNavigate: (AppRoute) -> Unit) {
    val summary = state.summary
    CenteredContent {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("learn_list"),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") { HeaderCard(summary) }
            summary.continueLesson?.let { lesson ->
                item(key = "continue") { ContinueCard(lesson) { onNavigate(LessonRoute(lesson.id)) } }
            }
            item(key = "daily") { DailyPracticeCard(summary) { onNavigate(QuizRoute(QuizRoute.DAILY)) } }
            item(key = "practice") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PracticeTile(
                        icon = Icons.Outlined.Style,
                        title = "Flashcards",
                        subtitle = if (summary.dueFlashcards == 0) "All caught up" else "${summary.dueFlashcards} due",
                        modifier = Modifier.weight(1f).testTag("learn_flashcards"),
                        onClick = { onNavigate(FlashcardsRoute) },
                    )
                    PracticeTile(
                        icon = Icons.Outlined.Shuffle,
                        title = "Mixed quiz",
                        subtitle = "10 random questions",
                        modifier = Modifier.weight(1f).testTag("learn_mixed_quiz"),
                        onClick = { onNavigate(QuizRoute(QuizRoute.MIXED)) },
                    )
                }
            }
            item(key = "progress") { ProgressEntry(summary) { onNavigate(LearningProgressRoute) } }
            state.levels.forEach { (level, lessons) ->
                item(key = "level-${level.id}") { LevelHeader(level, lessons) }
                items(lessons, key = { it.id }) { lesson ->
                    LessonRow(lesson) { onNavigate(LessonRoute(lesson.id)) }
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(summary: LearningSummary) {
    val percent = (summary.progressFraction * 100).toInt()
    Card(
        modifier = Modifier.fillMaxWidth().testTag("learn_header"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = summary.progressFraction,
                size = 84.dp,
                label = "$percent%",
                contentDescription = "Overall progress $percent percent, ${summary.completedLessons} of ${summary.totalLessons} lessons completed",
            )
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${summary.completedLessons} of ${summary.totalLessons} lessons",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.LocalFireDepartment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (summary.currentStreak > 0) "${pluralize(summary.currentStreak, "day")} streak" else "Start a streak today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("learn_streak"),
                    )
                }
                Text(
                    "Best streak: ${pluralize(summary.longestStreak, "day")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun ContinueCard(lesson: LessonSummary, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("learn_continue"),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                Icons.AutoMirrored.Outlined.MenuBook,
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.onSecondaryContainer,
                size = 48.dp,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (lesson.status == LessonProgressStatus.IN_PROGRESS) "Continue learning" else "Start learning",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(lesson.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${lesson.level.title} · Lesson ${lesson.order} · ${lesson.estimatedMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun DailyPracticeCard(summary: LearningSummary, onClick: () -> Unit) {
    val goalProgress = if (summary.dailyGoal == 0) 0f else (summary.todayActivity.toFloat() / summary.dailyGoal).coerceAtMost(1f)
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("learn_daily_practice"),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Outlined.Today, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Daily practice", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (summary.dailyPracticeDoneToday) "Done for today. Practise again any time."
                        else "8 questions picked for today",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (summary.dailyPracticeDoneToday) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = "Completed today", tint = correctColors().accent)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { goalProgress },
                    modifier = Modifier.weight(1f).height(8.dp),
                    drawStopIndicator = {},
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "${summary.todayActivity.coerceAtMost(999)}/${summary.dailyGoal} today",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.semantics {
                        contentDescription = "Daily goal: ${summary.todayActivity} of ${summary.dailyGoal} activities today"
                    },
                )
            }
            Text(
                "Completing lessons, finishing quizzes and reviewing flashcards count toward your goal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PracticeTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBadge(icon, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProgressEntry(summary: LearningSummary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("learn_progress"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.EmojiEvents, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Progress & achievements", style = MaterialTheme.typography.titleSmall)
                Text(
                    summary.averageQuizScore?.let { "Quiz average $it% · ${pluralize(summary.quizzesTaken, "quiz", "quizzes")}" }
                        ?: "Stats, activity heatmap and badges",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun LevelHeader(level: LessonLevel, lessons: List<LessonSummary>) {
    val done = lessons.count { it.status == LessonProgressStatus.COMPLETED }
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp, end = 4.dp).testTag("learn_level_${level.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            level.title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Text("$done/${lessons.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LessonRow(lesson: LessonSummary, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("learn_lesson_${lesson.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            val completed = lesson.status == LessonProgressStatus.COMPLETED
            Surface(
                shape = CircleShape,
                color = if (completed) correctColors().container else scheme.surfaceContainerHighest,
                contentColor = if (completed) correctColors().content else scheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (completed) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(22.dp))
                    } else {
                        Text("${lesson.order}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(lesson.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    lesson.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(lesson)
                    Spacer(Modifier.width(8.dp))
                    Text("${lesson.estimatedMinutes} min", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}
