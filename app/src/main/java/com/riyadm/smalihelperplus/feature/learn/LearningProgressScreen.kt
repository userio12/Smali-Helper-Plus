package com.riyadm.smalihelperplus.feature.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.core.designsystem.component.ListSkeleton
import com.riyadm.smalihelperplus.domain.learning.ActivityHeatmap
import com.riyadm.smalihelperplus.domain.learning.AchievementState
import com.riyadm.smalihelperplus.domain.learning.LearningProgressStats
import com.riyadm.smalihelperplus.domain.learning.QuizHistoryEntry
import com.riyadm.smalihelperplus.domain.learning.StreakInsights
import com.riyadm.smalihelperplus.navigation.AppRoute
import com.riyadm.smalihelperplus.navigation.LessonRoute
import com.riyadm.smalihelperplus.navigation.QuizRoute
import java.time.LocalDate
import java.time.format.TextStyle

private const val HISTORY_PREVIEW = 10

@Composable
fun LearningProgressScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val viewModel: LearningProgressViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    DetailScaffold(
        title = "Progress & achievements",
        onBack = onBack,
        large = true,
        modifier = Modifier.testTag("progress_screen"),
    ) { padding ->
        when (val s = state) {
            ProgressUiState.Loading -> ListSkeleton(modifier = Modifier.padding(padding))
            is ProgressUiState.Content -> ProgressContent(s.stats, s.dailyGoal, padding, onNavigate)
        }
    }
}

@Composable
private fun ProgressContent(stats: LearningProgressStats, dailyGoal: Int, padding: PaddingValues, onNavigate: (AppRoute) -> Unit) {
    var showAllHistory by rememberSaveable { mutableStateOf(false) }
    val history = if (showAllHistory) stats.history else stats.history.take(HISTORY_PREVIEW)
    CenteredContent {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("progress_list"),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "levels") { LevelsCard(stats) }
            item(key = "stats") { StatsGrid(stats) }
            item(key = "heatmap") { HeatmapCard(stats, dailyGoal) }
            item(key = "achievements-title") { Heading("Achievements", "${stats.achievements.count { it.unlocked }}/${stats.achievements.size}") }
            item(key = "achievements") { AchievementsGrid(stats.achievements) }
            item(key = "history-title") { Heading("Quiz history", if (stats.history.isEmpty()) null else "${stats.history.size}") }
            if (stats.history.isEmpty()) {
                item(key = "history-empty") {
                    Text(
                        "No quizzes yet. Finish a lesson quiz or today's practice to see your results here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            } else {
                items(history, key = { it.id }) { entry -> HistoryRow(entry, onNavigate) }
                if (stats.history.size > HISTORY_PREVIEW) {
                    item(key = "history-toggle") {
                        TextButton(onClick = { showAllHistory = !showAllHistory }, modifier = Modifier.testTag("progress_history_toggle")) {
                            Text(if (showAllHistory) "Show less" else "Show all ${stats.history.size}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Heading(title: String, trailing: String?) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LevelsCard(stats: LearningProgressStats) {
    val completed = stats.levels.sumOf { it.completed }
    val total = stats.levels.sumOf { it.total }
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().testTag("progress_levels"),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            val fraction = if (total == 0) 0f else completed.toFloat() / total
            ProgressRing(
                progress = fraction,
                size = 96.dp,
                label = "$completed/$total",
                contentDescription = "$completed of $total lessons completed",
            )
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                stats.levels.forEach { level ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row {
                            Text(level.level.title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            Text("${level.completed}/${level.total}", style = MaterialTheme.typography.labelLarge)
                        }
                        LinearProgressIndicator(
                            progress = { if (level.total == 0) 0f else level.completed.toFloat() / level.total },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            drawStopIndicator = {},
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsGrid(stats: LearningProgressStats) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("progress_stats")) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(stats.averageQuizScore?.let { "$it%" } ?: "–", "Quiz average", Icons.AutoMirrored.Outlined.TrendingUp, Modifier.weight(1f))
            StatTile("${stats.quizzesTaken}", "Quizzes taken", Icons.Outlined.Quiz, Modifier.weight(1f))
            StatTile("${stats.flashcardReviews}", "Cards reviewed", Icons.Outlined.Style, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(pluralize(stats.currentStreak, "day"), "Current streak", Icons.Outlined.LocalFireDepartment, Modifier.weight(1f))
            StatTile(pluralize(stats.longestStreak, "day"), "Longest streak", Icons.Outlined.WorkspacePremium, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeatmapCard(stats: LearningProgressStats, dailyGoal: Int) {
    val weeks = 12
    val grid = remember(stats.studyDays, stats.todayEpochDay) { ActivityHeatmap.build(stats.studyDays, stats.todayEpochDay, weeks) }
    val activeDays = StreakInsights.activeDaysInLast(stats.studyDays.filterValues { it > 0 }.keys, stats.todayEpochDay, weeks * 7)
    val atRisk = StreakInsights.isAtRisk(stats.studyDays.filterValues { it > 0 }.keys, stats.todayEpochDay)
    val scheme = MaterialTheme.colorScheme
    val levels = listOf(
        scheme.surfaceContainerHighest,
        scheme.primary.copy(alpha = 0.25f),
        scheme.primary.copy(alpha = 0.5f),
        scheme.primary.copy(alpha = 0.75f),
        scheme.primary,
    )
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().testTag("progress_heatmap"),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Activity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(
                "${pluralize(activeDays, "active day")} in the last 12 weeks" +
                    if (atRisk) " · study today to keep your streak" else "",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            BoxWithConstraints(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = "Activity heatmap: ${pluralize(activeDays, "active day")} in the last 12 weeks"
                },
            ) {
                val labelWidth = 28.dp
                val gap = 4.dp
                val cell = ((maxWidth - labelWidth - gap * weeks) / weeks).coerceIn(10.dp, 28.dp)
                Column {
                    MonthLabels(grid, labelWidth, cell, gap)
                    Row {
                        Column(Modifier.width(labelWidth), verticalArrangement = Arrangement.spacedBy(gap)) {
                            listOf("Mon", "", "Wed", "", "Fri", "", "").forEach { label ->
                                Box(Modifier.height(cell), contentAlignment = Alignment.CenterStart) {
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                        grid.forEach { week ->
                            Column(Modifier.padding(start = gap), verticalArrangement = Arrangement.spacedBy(gap)) {
                                week.forEach { day ->
                                    val color = if (day.epochDay == null) Color.Transparent else levels[ActivityHeatmap.level(day.count, dailyGoal)]
                                    Box(Modifier.size(cell).clip(RoundedCornerShape(4.dp)).background(color))
                                }
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Less", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                levels.forEach { Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(it)) }
                Text("More", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("Goal: $dailyGoal/day", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MonthLabels(
    grid: List<List<com.riyadm.smalihelperplus.domain.learning.HeatmapCell>>,
    labelWidth: androidx.compose.ui.unit.Dp,
    cell: androidx.compose.ui.unit.Dp,
    gap: androidx.compose.ui.unit.Dp,
) {
    val locale = LocalConfiguration.current.locales[0]
    Row(Modifier.padding(start = labelWidth, bottom = 4.dp)) {
        var lastMonth = -1
        grid.forEach { week ->
            val firstDay = week.firstOrNull { it.epochDay != null }?.epochDay
            val month = firstDay?.let { LocalDate.ofEpochDay(it).monthValue } ?: -1
            val label = if (month != lastMonth && firstDay != null) {
                lastMonth = month
                LocalDate.ofEpochDay(firstDay).month.getDisplayName(TextStyle.SHORT, locale)
            } else {
                ""
            }
            Box(Modifier.padding(start = gap).width(cell)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                )
            }
        }
    }
}

@Composable
private fun AchievementsGrid(achievements: List<AchievementState>) {
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("progress_achievements")) {
        val columns = when {
            maxWidth >= 720.dp -> 4
            maxWidth >= 480.dp -> 3
            else -> 2
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            achievements.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { AchievementTile(it, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun AchievementTile(state: AchievementState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val a = state.achievement
    val unlockedAt = state.unlockedAtMillis
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (state.unlocked) scheme.primaryContainer else scheme.surfaceContainerLow,
            contentColor = if (state.unlocked) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        ),
        modifier = modifier
            .testTag("achievement_${a.id}")
            .semantics(mergeDescendants = true) {
                contentDescription = if (unlockedAt != null) {
                    "${a.title}, unlocked on ${formatDate(unlockedAt)}. ${a.description}"
                } else {
                    "${a.title}, locked. ${a.description}"
                }
            },
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (state.unlocked) {
                IconBadge(achievementIcon(a.icon), scheme.primary, scheme.onPrimary)
            } else {
                IconBadge(Icons.Outlined.Lock, scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
            }
            Text(a.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                a.description,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.alpha(if (state.unlocked) 1f else 0.85f),
            )
            Text(
                unlockedAt?.let { "Unlocked ${formatDate(it)}" } ?: "Locked",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun HistoryRow(entry: QuizHistoryEntry, onNavigate: (AppRoute) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val good = entry.percent >= 80
    val colors = if (good) correctColors() else null
    Card(
        onClick = {
            onNavigate(
                when (entry.lessonId) {
                    QuizRoute.DAILY, QuizRoute.MIXED -> QuizRoute(entry.lessonId)
                    else -> LessonRoute(entry.lessonId)
                },
            )
        },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().testTag("progress_history_${entry.id}"),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatDateTime(entry.takenAtMillis), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Pill(
                "${entry.correct}/${entry.total} · ${entry.percent}%",
                colors?.container ?: scheme.surfaceContainerHighest,
                colors?.content ?: scheme.onSurface,
            )
        }
    }
}
