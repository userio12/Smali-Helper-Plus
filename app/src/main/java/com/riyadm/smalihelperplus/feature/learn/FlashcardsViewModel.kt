package com.riyadm.smalihelperplus.feature.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadm.smalihelperplus.data.learning.LearningRepository
import com.riyadm.smalihelperplus.domain.learning.Achievement
import com.riyadm.smalihelperplus.domain.learning.FlashcardItem
import com.riyadm.smalihelperplus.domain.learning.ReviewRating
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A review session works on a snapshot of card ids so that rating a card (which changes its due date) does not
 * reshuffle the cards still waiting in the session.
 */
data class FlashcardSession(
    val topic: String?,
    val reviewAll: Boolean,
    val queue: List<String>,
    val position: Int = 0,
    val reviewed: Int = 0,
    val flipped: Boolean = false,
) {
    val currentId: String? get() = queue.getOrNull(position)
    val remaining: Int get() = (queue.size - position).coerceAtLeast(0)
}

sealed interface FlashcardsUiState {
    data object Loading : FlashcardsUiState
    data class Ready(
        val topics: List<String>,
        val selectedTopic: String?,
        val totalCards: Int,
        val dueInTopic: Int,
        val cardsInTopic: Int,
        val session: FlashcardSession?,
        val current: FlashcardItem?,
    ) : FlashcardsUiState
}

@HiltViewModel
class FlashcardsViewModel @Inject constructor(
    private val repository: LearningRepository,
) : ViewModel() {

    private val topic = MutableStateFlow<String?>(null)
    private val session = MutableStateFlow<FlashcardSession?>(null)
    private val _unlocked = Channel<List<Achievement>>(Channel.BUFFERED)
    val unlocked: Flow<List<Achievement>> = _unlocked.receiveAsFlow()

    val state: StateFlow<FlashcardsUiState> = combine(repository.flashcards, topic, session) { cards, topic, session ->
        val byId = cards.associateBy { it.id }
        val inTopic = cards.filter { topic == null || it.topic == topic }
        val active = session?.takeIf { it.topic == topic }
        FlashcardsUiState.Ready(
            topics = cards.map { it.topic }.distinct().sorted(),
            selectedTopic = topic,
            totalCards = cards.size,
            dueInTopic = inTopic.count { it.isDue },
            cardsInTopic = inTopic.size,
            session = active,
            current = active?.currentId?.let(byId::get),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FlashcardsUiState.Loading)

    fun selectTopic(value: String?) {
        if (topic.value == value) return
        topic.value = value
        session.value = null
    }

    /** Starts a session with the due cards of the selected topic, or all of its cards when [reviewAll] is true. */
    fun start(reviewAll: Boolean) {
        val ready = state.value as? FlashcardsUiState.Ready ?: return
        viewModelScope.launch {
            val cards = repository.flashcards.first()
            val pool = cards.filter { (ready.selectedTopic == null || it.topic == ready.selectedTopic) && (reviewAll || it.isDue) }
            // Lower boxes (weaker cards) first; shuffle within a box so sessions do not feel repetitive.
            val queue = pool.groupBy { it.box }.toSortedMap().values.flatMap { it.shuffled() }.map { it.id }
            session.value = FlashcardSession(topic = ready.selectedTopic, reviewAll = reviewAll, queue = queue)
        }
    }

    fun flip() {
        session.update { it?.copy(flipped = !it.flipped) }
    }

    fun rate(rating: ReviewRating) {
        val current = session.value ?: return
        val id = current.currentId ?: return
        if (!current.flipped) return
        // "Again" puts the card back at the end of this session.
        val queue = if (rating == ReviewRating.AGAIN) current.queue + id else current.queue
        session.value = current.copy(queue = queue, position = current.position + 1, reviewed = current.reviewed + 1, flipped = false)
        viewModelScope.launch {
            val unlocked = repository.reviewFlashcard(id, rating)
            if (unlocked.isNotEmpty()) _unlocked.send(unlocked)
        }
    }

    fun endSession() {
        session.value = null
    }
}
