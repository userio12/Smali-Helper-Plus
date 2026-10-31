package com.riyadm.smalihelperplus.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadm.smalihelperplus.core.common.CodeHandoff
import com.riyadm.smalihelperplus.core.database.dao.ContentMetadataDao
import com.riyadm.smalihelperplus.core.database.dao.LearningDao
import com.riyadm.smalihelperplus.core.database.dao.LessonDao
import com.riyadm.smalihelperplus.core.database.dao.SnippetDao
import com.riyadm.smalihelperplus.core.database.entity.BookmarkEntity
import com.riyadm.smalihelperplus.core.database.entity.ContentMetadataEntity
import com.riyadm.smalihelperplus.core.database.entity.RecentViewEntity
import com.riyadm.smalihelperplus.core.database.entity.SnippetEntity
import com.riyadm.smalihelperplus.data.library.BookmarkRepository
import com.riyadm.smalihelperplus.data.library.HistoryRepository
import com.riyadm.smalihelperplus.data.opcode.OpcodeRepository
import com.riyadm.smalihelperplus.domain.model.Opcode
import com.riyadm.smalihelperplus.feature.library.LibraryItemOpener
import com.riyadm.smalihelperplus.navigation.AppRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val greeting: String = HomeLogic.greetingFor(LocalDateTime.now().hour),
    val featured: Opcode? = null,
    val opcodeCount: Int = 0,
    val continueLearning: ContinueLearning = ContinueLearning.None,
    val stats: LearningStats = LearningStats(),
    val recent: List<RecentViewEntity> = emptyList(),
    val snippets: List<SnippetEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val bookmarkCount: Int = 0,
    val content: List<ContentMetadataEntity> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    opcodes: OpcodeRepository,
    lessonDao: LessonDao,
    learningDao: LearningDao,
    snippetDao: SnippetDao,
    contentMetadataDao: ContentMetadataDao,
    bookmarks: BookmarkRepository,
    history: HistoryRepository,
    private val opener: LibraryItemOpener,
    private val codeHandoff: CodeHandoff,
) : ViewModel() {

    private val clock = MutableStateFlow(LocalDateTime.now())
    private val today: Flow<Long> = clock.map { it.toLocalDate().toEpochDay() }.distinctUntilChanged()


    private val learning: Flow<Pair<ContinueLearning, LearningStats>> = combine(
        lessonDao.observeAll(),
        learningDao.observeProgress(),
        learningDao.observeQuizResults(),
        learningDao.observeStudyDays(),
        today,
    ) { lessons, progress, quizzes, days, epochDay ->
        HomeLogic.continueLearning(lessons, progress) to
            HomeLogic.stats(lessons, progress, quizzes, days.map { it.epochDay }, epochDay)
    }

    private data class Library(
        val recent: List<RecentViewEntity>,
        val snippets: List<SnippetEntity>,
        val bookmarks: List<BookmarkEntity>,
        val content: List<ContentMetadataEntity>,
    )

    private val library: Flow<Library> = combine(
        history.observeRecentViews(RECENT_LIMIT),
        snippetDao.observeUserSnippets(),
        bookmarks.observeAll(),
        contentMetadataDao.observeAll(),
    ) { recent, snippets, marks, content -> Library(recent, snippets, marks, content) }

    private val featured: Flow<Opcode?> = today.flatMapLatest { opcodes.observeFeatured(it) }

    val uiState: StateFlow<HomeUiState> = combine(
        featured,
        opcodes.observeTotalCount(),
        learning,
        library,
        clock,
    ) { featuredOpcode, count, (next, stats), lib, now ->
        HomeUiState(
            loading = false,
            greeting = HomeLogic.greetingFor(now.hour),
            featured = featuredOpcode,
            opcodeCount = count,
            continueLearning = next,
            stats = stats,
            recent = lib.recent,
            snippets = lib.snippets.take(SNIPPET_PREVIEW),
            bookmarks = lib.bookmarks.take(BOOKMARK_PREVIEW),
            bookmarkCount = lib.bookmarks.size,
            content = lib.content,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Refreshes the greeting and day-dependent content (opcode of the day, streak) when the screen is resumed. */
    fun refreshClock() = clock.update { LocalDateTime.now() }

    /** Opens a recent / bookmarked item; [navigate] receives the route. */
    fun open(type: String, targetId: String, navigate: (AppRoute) -> Unit) {
        viewModelScope.launch { opener.resolve(type, targetId)?.let(navigate) }
    }

    /** Hands a saved snippet's code to the Playground (the caller then navigates there). */
    fun offerSnippetToPlayground(snippet: SnippetEntity) {
        codeHandoff.offer(CodeHandoff.PLAYGROUND, snippet.code, snippet.title)
    }

    private companion object {
        const val RECENT_LIMIT = 10
        const val SNIPPET_PREVIEW = 3
        const val BOOKMARK_PREVIEW = 4
    }
}
