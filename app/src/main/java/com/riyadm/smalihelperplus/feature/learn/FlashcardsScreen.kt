package com.riyadm.smalihelperplus.feature.learn

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.smalihelperplus.core.designsystem.component.CodeBlock
import com.riyadm.smalihelperplus.core.designsystem.component.DetailScaffold
import com.riyadm.smalihelperplus.core.designsystem.component.EmptyState
import com.riyadm.smalihelperplus.core.designsystem.component.ListSkeleton
import com.riyadm.smalihelperplus.domain.learning.FlashcardItem
import com.riyadm.smalihelperplus.domain.learning.LeitnerScheduler
import com.riyadm.smalihelperplus.domain.learning.LeitnerState
import com.riyadm.smalihelperplus.domain.learning.ReviewRating
import com.riyadm.smalihelperplus.navigation.AppRoute

@Suppress("UNUSED_PARAMETER")
@Composable
fun FlashcardsScreen(onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val viewModel: FlashcardsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.unlocked.collect { list -> snackbar.showSnackbar("Achievement unlocked: ${list.joinToString { it.title }}") }
    }
    DetailScaffold(
        title = "Flashcards",
        onBack = onBack,
        snackbarHostState = snackbar,
        modifier = Modifier.testTag("flashcards_screen"),
    ) { padding ->
        when (val s = state) {
            FlashcardsUiState.Loading -> ListSkeleton(modifier = Modifier.padding(padding))
            is FlashcardsUiState.Ready -> if (s.totalCards == 0) {
                EmptyState(
                    icon = Icons.Outlined.Style,
                    title = "No flashcards installed",
                    message = "Flashcards ship with the lesson content. Check for documentation updates to install them.",
                    modifier = Modifier.padding(padding),
                )
            } else {
                FlashcardsContent(
                    state = s,
                    padding = padding,
                    onTopic = viewModel::selectTopic,
                    onStart = viewModel::start,
                    onFlip = viewModel::flip,
                    onRate = viewModel::rate,
                    onEnd = viewModel::endSession,
                )
            }
        }
    }
}

@Composable
private fun FlashcardsContent(
    state: FlashcardsUiState.Ready,
    padding: PaddingValues,
    onTopic: (String?) -> Unit,
    onStart: (Boolean) -> Unit,
    onFlip: () -> Unit,
    onRate: (ReviewRating) -> Unit,
    onEnd: () -> Unit,
) {
    CenteredContent(Modifier.padding(padding)) {
        Column(Modifier.fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("flashcard_topics"),
            ) {
                item(key = "all") {
                    FilterChip(
                        selected = state.selectedTopic == null,
                        onClick = { onTopic(null) },
                        label = { Text("All topics") },
                        modifier = Modifier.testTag("flashcard_topic_all"),
                    )
                }
                items(state.topics, key = { it }) { topic ->
                    FilterChip(
                        selected = state.selectedTopic == topic,
                        onClick = { onTopic(topic) },
                        label = { Text(topic) },
                        modifier = Modifier.testTag("flashcard_topic_$topic"),
                    )
                }
            }
            val session = state.session
            when {
                session == null -> Overview(state, onStart)
                state.current == null -> SessionComplete(session, state, onStart, onEnd)
                else -> Review(session, state.current, onFlip, onRate, onEnd)
            }
        }
    }
}

@Composable
private fun Overview(state: FlashcardsUiState.Ready, onStart: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.dueInTopic == 0) {
            EmptyState(
                icon = Icons.Outlined.DoneAll,
                title = "All caught up",
                message = "No cards are due${state.selectedTopic?.let { " in $it" } ?: ""}. Come back later, or review every card now.",
                actionLabel = "Review all ${pluralize(state.cardsInTopic, "card")}",
                onAction = { onStart(true) },
                modifier = Modifier.testTag("flashcard_caught_up"),
            )
        } else {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "${pluralize(state.dueInTopic, "card")} due",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("flashcard_due_count"),
                    )
                    Text(
                        "Cards you know well come back less often. Rate each card honestly to keep the schedule accurate.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Button(onClick = { onStart(false) }, modifier = Modifier.fillMaxWidth().testTag("flashcard_start")) {
                        Text("Start review")
                    }
                }
            }
            TextButton(onClick = { onStart(true) }, modifier = Modifier.testTag("flashcard_review_all")) {
                Text("Review all ${pluralize(state.cardsInTopic, "card")} instead")
            }
        }
        Text(
            "Leitner boxes: missed cards come back the same day. Each correct answer moves a card up a box, so it returns after 1, 3, 7 and then 14 days.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Review(
    session: FlashcardSession,
    card: FlashcardItem,
    onFlip: () -> Unit,
    onRate: (ReviewRating) -> Unit,
    onEnd: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${session.remaining} left · ${session.reviewed} reviewed",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f).testTag("flashcard_counter"),
            )
            TextButton(onClick = onEnd, modifier = Modifier.testTag("flashcard_end")) { Text("End session") }
        }
        LinearProgressIndicator(
            progress = { session.position.toFloat() / session.queue.size.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            drawStopIndicator = {},
        )
        key(session.position) {
            FlipCard(card, session.flipped, onFlip)
        }
        if (!session.flipped) {
            FilledTonalButton(onClick = onFlip, modifier = Modifier.fillMaxWidth().testTag("flashcard_flip")) {
                Icon(Icons.Outlined.TouchApp, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Show answer")
            }
        } else {
            Text("How well did you know it?", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ReviewRating.entries.forEach { rating ->
                    RatingButton(rating, card, Modifier.weight(1f)) { onRate(rating) }
                }
            }
        }
    }
}

@Composable
private fun FlipCard(card: FlashcardItem, flipped: Boolean, onFlip: () -> Unit) {
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(450), label = "flip")
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (rotation <= 90f) scheme.surfaceContainerHigh else scheme.secondaryContainer,
            contentColor = if (rotation <= 90f) scheme.onSurface else scheme.onSecondaryContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clickable(role = Role.Button, onClickLabel = if (flipped) "Show question" else "Show answer", onClick = onFlip)
            .semantics { stateDescription = if (flipped) "Answer side" else "Question side" }
            .testTag("flashcard_card"),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 260.dp)
                .graphicsLayer { if (rotation > 90f) rotationY = 180f }
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (rotation <= 90f) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Pill(card.topic, scheme.secondaryContainer, scheme.onSecondaryContainer)
                    Text(card.front, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Text(
                        if (card.box == 0) "New card" else "Box ${card.box} of ${LeitnerScheduler.MAX_BOX}",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("flashcard_back")) {
                    Text(card.back, style = MaterialTheme.typography.bodyLarge)
                    card.code?.let { CodeBlock(code = it, language = if (looksLikeSmali(it)) "smali" else "text", showCopy = false) }
                }
            }
        }
    }
}

@Composable
private fun RatingButton(rating: ReviewRating, card: FlashcardItem, modifier: Modifier, onClick: () -> Unit) {
    val state = if (card.box == 0) null else LeitnerState(card.box, 0, card.reviewCount)
    val days = LeitnerScheduler.review(state, rating, 0).nextReviewEpochDay
    val whenText = when (days) {
        0L -> "Today"
        1L -> "1 day"
        else -> "$days days"
    }
    val label = when (rating) {
        ReviewRating.AGAIN -> "Again"
        ReviewRating.HARD -> "Hard"
        ReviewRating.GOOD -> "Good"
        ReviewRating.EASY -> "Easy"
    }
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (rating) {
        ReviewRating.AGAIN -> scheme.errorContainer to scheme.onErrorContainer
        ReviewRating.HARD -> scheme.surfaceContainerHighest to scheme.onSurface
        ReviewRating.GOOD -> scheme.primaryContainer to scheme.onPrimaryContainer
        ReviewRating.EASY -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.heightIn(min = 56.dp).testTag("flashcard_${rating.name.lowercase()}"),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(whenText, style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun SessionComplete(
    session: FlashcardSession,
    state: FlashcardsUiState.Ready,
    onStart: (Boolean) -> Unit,
    onEnd: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("flashcard_session_complete"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EmptyState(
            icon = Icons.Outlined.DoneAll,
            title = "Session complete",
            message = "You reviewed ${pluralize(session.reviewed, "card")}." +
                if (state.dueInTopic > 0) " ${pluralize(state.dueInTopic, "card")} still due." else " Nothing else is due right now.",
        )
        if (state.dueInTopic > 0) {
            Button(onClick = { onStart(false) }, modifier = Modifier.fillMaxWidth()) { Text("Review due cards") }
        }
        OutlinedButton(onClick = { onStart(true) }, modifier = Modifier.fillMaxWidth().testTag("flashcard_review_all")) {
            Text("Review all ${pluralize(state.cardsInTopic, "card")}")
        }
        TextButton(onClick = onEnd, modifier = Modifier.testTag("flashcard_done")) { Text("Done") }
        Spacer(Modifier.height(8.dp))
    }
}

