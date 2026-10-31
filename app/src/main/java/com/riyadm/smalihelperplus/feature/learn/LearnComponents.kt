package com.riyadm.smalihelperplus.feature.learn

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riyadm.smalihelperplus.domain.learning.AchievementIcon
import com.riyadm.smalihelperplus.domain.learning.LessonLevel
import com.riyadm.smalihelperplus.domain.learning.LessonProgressStatus
import com.riyadm.smalihelperplus.domain.learning.LessonSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Maximum content width so cards stay readable on tablets. */
internal val LearnMaxWidth = 840.dp

/** Centers [content] and caps its width on large screens. */
@Composable
internal fun CenteredContent(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = LearnMaxWidth).fillMaxWidth()) { content() }
    }
}

/** Circular progress ring with a centered label. */
@Composable
internal fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    strokeWidth: Dp = 8.dp,
    label: String? = null,
    contentDescription: String? = null,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "ring")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val color = MaterialTheme.colorScheme.primary
    Box(
        modifier.size(size).then(
            if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (animated > 0f) {
                drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        if (label != null) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Immutable
internal data class FeedbackColors(val container: Color, val content: Color, val accent: Color)

/** Green "correct" colors that work on light and dark schemes (Material has no success role). */
@Composable
internal fun correctColors(): FeedbackColors {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (dark) {
        FeedbackColors(Color(0xFF1E4D2B), Color(0xFFC8F5D0), Color(0xFF7FD99A))
    } else {
        FeedbackColors(Color(0xFFD7F5DD), Color(0xFF0B3D1A), Color(0xFF1B7F3B))
    }
}

@Composable
internal fun incorrectColors(): FeedbackColors = FeedbackColors(
    MaterialTheme.colorScheme.errorContainer,
    MaterialTheme.colorScheme.onErrorContainer,
    MaterialTheme.colorScheme.error,
)

/** Small rounded label. */
@Composable
internal fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50), modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun LevelPill(level: LessonLevel, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (level) {
        LessonLevel.BEGINNER -> scheme.primaryContainer to scheme.onPrimaryContainer
        LessonLevel.INTERMEDIATE -> scheme.secondaryContainer to scheme.onSecondaryContainer
        LessonLevel.ADVANCED -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    Pill(level.title, container, content, modifier)
}

@Composable
internal fun StatusPill(lesson: LessonSummary, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    when (lesson.status) {
        LessonProgressStatus.COMPLETED -> {
            val c = correctColors()
            val text = lesson.bestQuizScore?.let { "Completed · $it%" } ?: "Completed"
            Pill(text, c.container, c.content, modifier, Icons.Outlined.CheckCircle)
        }
        LessonProgressStatus.IN_PROGRESS -> {
            val text = lesson.bestQuizScore?.let { "In progress · $it%" } ?: "In progress"
            Pill(text, scheme.secondaryContainer, scheme.onSecondaryContainer, modifier)
        }
        LessonProgressStatus.NOT_STARTED -> Pill("Not started", scheme.surfaceContainerHighest, scheme.onSurfaceVariant, modifier)
    }
}

/** Circle with an icon, used as leading visual in cards. */
@Composable
internal fun IconBadge(icon: ImageVector, container: Color, content: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(modifier.size(size).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
internal fun StatTile(value: String, label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun achievementIcon(icon: AchievementIcon): ImageVector = when (icon) {
    AchievementIcon.LESSON -> Icons.AutoMirrored.Outlined.MenuBook
    AchievementIcon.LEVEL -> Icons.Outlined.School
    AchievementIcon.QUIZ -> Icons.Outlined.Quiz
    AchievementIcon.STREAK -> Icons.Outlined.LocalFireDepartment
    AchievementIcon.FLASHCARD -> Icons.Outlined.Style
    AchievementIcon.GOAL -> Icons.Outlined.TaskAlt
    AchievementIcon.TROPHY -> Icons.Outlined.EmojiEvents
}

// Created per call so a locale change at runtime is picked up.
private val DateFormatter: DateTimeFormatter get() = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val DateTimeFormat: DateTimeFormatter get() = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

internal fun formatDate(millis: Long): String =
    DateFormatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

internal fun formatDateTime(millis: Long): String =
    DateTimeFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

internal fun pluralize(count: Int, one: String, many: String = one + "s") = "$count ${if (count == 1) one else many}"

/** True when [text] looks like a smali listing rather than a prose answer. */
internal fun looksLikeSmali(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    val first = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: return false
    if (first.startsWith(".") || first.startsWith(":")) return true
    return SmaliInstructionStart.containsMatchIn(first) && !first.contains(". ") && first.split(' ').size <= 12
}

private val SmaliInstructionStart = Regex("^(?:[a-z]+(?:-[a-z0-9]+)*(?:/[a-z0-9]+)?)(?:\\s+[vp{]|\\s*$)")
