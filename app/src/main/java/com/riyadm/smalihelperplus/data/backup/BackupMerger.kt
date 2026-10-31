package com.riyadm.smalihelperplus.data.backup

import com.riyadm.smalihelperplus.core.database.entity.LessonStatus

/**
 * Pure merge logic. [merge] returns only the rows that must be written (new rows and rows that change an existing
 * one), so merging the same backup twice writes nothing the second time. Replacing is `merge(BackupData(), incoming)`
 * after the user tables were cleared, which also removes duplicates inside the file.
 */
object BackupMerger {

    fun merge(existing: BackupData, incoming: BackupData): BackupData {
        val bookmarkKeys = existing.bookmarks.mapTo(HashSet()) { it.type to it.targetId }
        val bookmarks = incoming.bookmarks.filter { bookmarkKeys.add(it.type to it.targetId) }

        val snippetKeys = existing.snippets.mapTo(HashSet()) { Triple(it.title, it.collection, it.code) }
        val snippets = incoming.snippets.filter { snippetKeys.add(Triple(it.title, it.collection, it.code)) }

        val progress = mergeKeyed(existing.lessonProgress, incoming.lessonProgress, { it.lessonId }, ::betterProgress)

        val quizKeys = existing.quizResults.mapTo(HashSet()) { listOf(it.lessonId, it.takenAtMillis, it.correct, it.total) }
        val quizResults = incoming.quizResults.filter { quizKeys.add(listOf(it.lessonId, it.takenAtMillis, it.correct, it.total)) }

        val flashcards = mergeKeyed(existing.flashcardProgress, incoming.flashcardProgress, { it.cardId }) { a, b ->
            if (compareValuesBy(b, a, { it.reviewCount }, { it.box }) > 0) b else a
        }

        val studyDays = mergeKeyed(existing.studyDays, incoming.studyDays, { it.epochDay }) { a, b ->
            a.copy(activityCount = maxOf(a.activityCount, b.activityCount))
        }

        val achievementIds = existing.achievements.mapTo(HashSet()) { it.id }
        val achievements = incoming.achievements.filter { achievementIds.add(it.id) }

        val search = mergeKeyed(existing.searchHistory, incoming.searchHistory, { it.query }) { a, b ->
            a.copy(lastUsedMillis = maxOf(a.lastUsedMillis, b.lastUsedMillis), useCount = maxOf(a.useCount, b.useCount))
        }

        val recents = mergeKeyed(existing.recentViews, incoming.recentViews, { it.type to it.targetId }) { a, b ->
            if (b.viewedAtMillis > a.viewedAtMillis) b else a
        }

        return BackupData(bookmarks, snippets, progress, quizResults, flashcards, studyDays, achievements, search, recents)
    }

    private fun betterProgress(a: LessonProgressBackup, b: LessonProgressBackup): LessonProgressBackup {
        val status = maxOf(a.status, b.status)
        return a.copy(
            status = status,
            lastOpenedAtMillis = maxOf(a.lastOpenedAtMillis, b.lastOpenedAtMillis),
            completedAtMillis = if (status == LessonStatus.COMPLETED) a.completedAtMillis ?: b.completedAtMillis else a.completedAtMillis,
            bestQuizScorePercent = maxOf(a.bestQuizScorePercent, b.bestQuizScorePercent),
        )
    }

    /**
     * Folds [incoming] into [existing] by key with [combine] (existing, incoming) -> result and returns the entries
     * that are new or differ from the existing one.
     */
    private fun <T, K> mergeKeyed(existing: List<T>, incoming: List<T>, key: (T) -> K, combine: (T, T) -> T): List<T> {
        val current = LinkedHashMap<K, T>()
        existing.forEach { current[key(it)] = it }
        val changed = LinkedHashMap<K, T>()
        incoming.forEach { item ->
            val k = key(item)
            val base = changed[k] ?: current[k]
            val result = if (base == null) item else combine(base, item)
            if (result != current[k]) changed[k] = result
        }
        return changed.values.toList()
    }
}
