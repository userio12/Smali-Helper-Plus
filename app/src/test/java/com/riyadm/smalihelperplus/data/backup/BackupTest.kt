package com.riyadm.smalihelperplus.data.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

private fun sampleData() = BackupData(
    bookmarks = listOf(BookmarkBackup("opcode", "move", "move", "Move", 1_700_000_000_000)),
    snippets = listOf(SnippetBackup("My snippet", "d", "My snippets", ".method foo()V\n.end method", listOf("a"), 1, 2)),
    lessonProgress = listOf(LessonProgressBackup("l1", 2, 1_700_000_000_000, 1_700_000_000_000, 80)),
    quizResults = listOf(QuizResultBackup("l1", 3, 4, 1_700_000_000_000)),
    flashcardProgress = listOf(FlashcardProgressBackup("c1", 2, 19_000, 3)),
    studyDays = listOf(StudyDayBackup(19_000, 4)),
    achievements = listOf(AchievementBackup("first_lesson", 1_700_000_000_000)),
    searchHistory = listOf(SearchHistoryBackup("invoke", 1_700_000_000_000, 2)),
    recentViews = listOf(RecentViewBackup("opcode", "move", "move", "", 1_700_000_000_000)),
)

private fun dto(data: BackupData = sampleData(), version: Int = 1, kind: String = BACKUP_KIND_FULL) =
    BackupDto(version, "1.0.0", "2026-01-01T00:00:00Z", kind, data)

class BackupSerializationTest {
    @Test fun roundTripsThroughJson() {
        val original = dto()
        val text = BackupValidator.writeJson.encodeToString(BackupDto.serializer(), original)
        assertThat(text).contains("\"formatVersion\": 1")
        assertThat(text).contains("\"appVersionName\"")
        assertThat(text).contains("\"exportedAt\"")
        assertThat(BackupValidator.parse(text)).isEqualTo(original)
    }

    @Test fun unknownKeysFromNewerMinorVersionsAreIgnored() {
        val text = """{"formatVersion":1,"extra":{"x":1},"data":{"bookmarks":[{"type":"lesson","targetId":"l1","title":"t","createdAtMillis":5,"future":true}]}}"""
        val parsed = BackupValidator.parse(text)
        assertThat(parsed.data.bookmarks).hasSize(1)
        assertThat(parsed.data.snippets).isEmpty()
    }

    @Test fun garbageIsRejectedWithFriendlyMessage() {
        val e = runCatching { BackupValidator.parse("<html>nope</html>") }.exceptionOrNull() as BackupException
        assertThat(e.message).contains("not a Smali Helper Plus backup")
        assertThat(runCatching { BackupValidator.parse("[]") }.exceptionOrNull()).isInstanceOf(BackupException::class.java)
    }

    @Test fun mappingKeepsAllFields() {
        val d = sampleData()
        assertThat(d.bookmarks.single().toEntity().toBackup()).isEqualTo(d.bookmarks.single())
        assertThat(d.snippets.single().toEntity().toBackup()).isEqualTo(d.snippets.single())
        assertThat(d.snippets.single().toEntity().builtInId).isNull()
        assertThat(d.lessonProgress.single().toEntity().toBackup()).isEqualTo(d.lessonProgress.single())
        assertThat(d.quizResults.single().toEntity().toBackup()).isEqualTo(d.quizResults.single())
        assertThat(d.flashcardProgress.single().toEntity().toBackup()).isEqualTo(d.flashcardProgress.single())
        assertThat(d.recentViews.single().toEntity().toBackup()).isEqualTo(d.recentViews.single())
    }

    @Test fun countsAndFileName() {
        assertThat(sampleData().counts().total).isEqualTo(9)
        assertThat(BackupData().counts().lines()).isEmpty()
        assertThat(BackupRepository.defaultFileName(false, LocalDate.of(2026, 3, 7))).isEqualTo("smali-helper-plus-backup-20260307.json")
        assertThat(BackupRepository.defaultFileName(true, LocalDate.of(2026, 3, 7))).isEqualTo("smali-helper-plus-bookmarks-20260307.json")
    }
}

class BackupValidationTest {
    private fun errors(d: BackupDto) = BackupValidator.validate(d)

    @Test fun validBackupHasNoErrors() {
        assertThat(errors(dto())).isEmpty()
    }

    @Test fun newerOrUnknownFormatVersionIsRejected() {
        assertThat(errors(dto(version = BACKUP_FORMAT_VERSION + 1)).joinToString()).contains("newer version")
        assertThat(errors(dto(version = 0))).isNotEmpty()
        assertThat(errors(dto(kind = "weird"))).isNotEmpty()
    }

    @Test fun insaneValuesAreRejected() {
        fun bad(data: BackupData) = assertThat(errors(dto(data))).isNotEmpty()
        bad(BackupData(bookmarks = listOf(BookmarkBackup("virus", "x", "t", "", 1))))
        bad(BackupData(bookmarks = listOf(BookmarkBackup("lesson", "", "t", "", 1))))
        bad(BackupData(bookmarks = listOf(BookmarkBackup("lesson", "x", "t", "", -5))))
        bad(BackupData(lessonProgress = listOf(LessonProgressBackup("l", 7, 1))))
        bad(BackupData(lessonProgress = listOf(LessonProgressBackup("l", 1, 1, bestQuizScorePercent = 101))))
        bad(BackupData(quizResults = listOf(QuizResultBackup("l", 5, 4, 1))))
        bad(BackupData(quizResults = listOf(QuizResultBackup("l", 0, 0, 1))))
        bad(BackupData(flashcardProgress = listOf(FlashcardProgressBackup("c", 99, 1, 1))))
        bad(BackupData(studyDays = listOf(StudyDayBackup(-1, 1))))
        bad(BackupData(studyDays = listOf(StudyDayBackup(1, -1))))
        bad(BackupData(searchHistory = listOf(SearchHistoryBackup("", 1, 1))))
        bad(BackupData(searchHistory = listOf(SearchHistoryBackup("q", 1, 0))))
        bad(BackupData(snippets = listOf(SnippetBackup("", code = "x"))))
        bad(BackupData(snippets = listOf(SnippetBackup("t", code = ""))))
        bad(BackupData(achievements = listOf(AchievementBackup("a", Long.MAX_VALUE))))
    }

    @Test fun oversizedStringsAndListsAreRejected() {
        val hugeCode = "x".repeat(600_000)
        assertThat(errors(dto(BackupData(snippets = listOf(SnippetBackup("t", code = hugeCode)))))).isNotEmpty()
        val many = List(100_001) { StudyDayBackup(it.toLong() % 40_000, 1) }
        assertThat(errors(dto(BackupData(studyDays = many)))).isNotEmpty()
    }

    @Test fun readLimitedStopsAtLimit() {
        val bytes = ByteArray(100)
        assertThat(bytes.inputStream().readLimited(100)).hasLength(100)
        assertThat(bytes.inputStream().readLimited(99)).isNull()
    }
}

class BackupMergerTest {
    private val empty = BackupData()

    @Test fun mergingIntoEmptyWritesEverything() {
        val plan = BackupMerger.merge(empty, sampleData())
        assertThat(plan).isEqualTo(sampleData())
    }

    @Test fun mergingTheSameBackupTwiceIsIdempotent() {
        val plan = BackupMerger.merge(sampleData(), sampleData())
        assertThat(plan.counts().total).isEqualTo(0)
    }

    @Test fun duplicatesInsideTheFileAreCollapsed() {
        val b = BookmarkBackup("lesson", "l1", "t", "", 1)
        val plan = BackupMerger.merge(empty, BackupData(bookmarks = listOf(b, b.copy(title = "other")), studyDays = listOf(StudyDayBackup(5, 1), StudyDayBackup(5, 3))))
        assertThat(plan.bookmarks).hasSize(1)
        assertThat(plan.studyDays).containsExactly(StudyDayBackup(5, 3))
    }

    @Test fun existingBookmarksAndSnippetsAreNotDuplicated() {
        val existing = BackupData(
            bookmarks = listOf(BookmarkBackup("opcode", "move", "Old title", "", 5)),
            snippets = listOf(SnippetBackup("S", "", "My snippets", "code", createdAtMillis = 1, updatedAtMillis = 1)),
        )
        val incoming = BackupData(
            bookmarks = listOf(BookmarkBackup("opcode", "move", "New title", "", 9), BookmarkBackup("opcode", "const", "c", "", 9)),
            snippets = listOf(
                SnippetBackup("S", "other description", "My snippets", "code", createdAtMillis = 7, updatedAtMillis = 7),
                SnippetBackup("S", "", "My snippets", "different code"),
            ),
        )
        val plan = BackupMerger.merge(existing, incoming)
        assertThat(plan.bookmarks.map { it.targetId }).containsExactly("const")
        assertThat(plan.snippets.map { it.code }).containsExactly("different code")
    }

    @Test fun lessonProgressKeepsTheBestOfBoth() {
        val existing = BackupData(lessonProgress = listOf(LessonProgressBackup("l", 1, 100, null, 90)))
        val incoming = BackupData(lessonProgress = listOf(LessonProgressBackup("l", 2, 50, 60, 70)))
        val merged = BackupMerger.merge(existing, incoming).lessonProgress.single()
        assertThat(merged.status).isEqualTo(2)
        assertThat(merged.lastOpenedAtMillis).isEqualTo(100)
        assertThat(merged.completedAtMillis).isEqualTo(60)
        assertThat(merged.bestQuizScorePercent).isEqualTo(90)
        // A worse incoming record changes nothing.
        val none = BackupMerger.merge(BackupData(lessonProgress = listOf(merged)), existing)
        assertThat(none.lessonProgress).isEmpty()
    }

    @Test fun flashcardStudyDaySearchAndRecentMerge() {
        val existing = BackupData(
            flashcardProgress = listOf(FlashcardProgressBackup("c", 2, 100, 5)),
            studyDays = listOf(StudyDayBackup(10, 5)),
            searchHistory = listOf(SearchHistoryBackup("q", 100, 3)),
            recentViews = listOf(RecentViewBackup("opcode", "move", "move", "", 100)),
            achievements = listOf(AchievementBackup("a", 10)),
            quizResults = listOf(QuizResultBackup("l", 1, 2, 10)),
        )
        val incoming = BackupData(
            flashcardProgress = listOf(FlashcardProgressBackup("c", 4, 200, 9), FlashcardProgressBackup("d", 1, 1, 1)),
            studyDays = listOf(StudyDayBackup(10, 2), StudyDayBackup(11, 1)),
            searchHistory = listOf(SearchHistoryBackup("q", 300, 1)),
            recentViews = listOf(RecentViewBackup("opcode", "move", "move", "", 50), RecentViewBackup("opcode", "nop", "nop", "", 50)),
            achievements = listOf(AchievementBackup("a", 99), AchievementBackup("b", 99)),
            quizResults = listOf(QuizResultBackup("l", 1, 2, 10), QuizResultBackup("l", 2, 2, 11)),
        )
        val plan = BackupMerger.merge(existing, incoming)
        assertThat(plan.flashcardProgress).containsExactly(FlashcardProgressBackup("c", 4, 200, 9), FlashcardProgressBackup("d", 1, 1, 1))
        assertThat(plan.studyDays).containsExactly(StudyDayBackup(11, 1))
        assertThat(plan.searchHistory).containsExactly(SearchHistoryBackup("q", 300, 3))
        assertThat(plan.recentViews.map { it.targetId }).containsExactly("nop")
        assertThat(plan.achievements.map { it.id }).containsExactly("b")
        assertThat(plan.quizResults).containsExactly(QuizResultBackup("l", 2, 2, 11))
    }
}
