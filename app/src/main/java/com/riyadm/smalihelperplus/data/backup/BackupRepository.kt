package com.riyadm.smalihelperplus.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.riyadm.smalihelperplus.BuildConfig
import com.riyadm.smalihelperplus.core.common.IoDispatcher
import com.riyadm.smalihelperplus.core.database.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** A validated backup read from a file, ready for the user to choose Merge or Replace. */
data class ParsedBackup(val backup: BackupDto) {
    val counts: BackupCounts get() = backup.data.counts()
    val isBookmarksOnly: Boolean get() = backup.kind == BACKUP_KIND_BOOKMARKS
}

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    // ---- export ---------------------------------------------------------------------------------------------

    /** Counts of what a full backup would currently contain. */
    suspend fun currentCounts(): BackupCounts = withContext(io) { collect(bookmarksOnly = false).counts() }

    suspend fun exportToUri(uri: Uri, bookmarksOnly: Boolean): BackupCounts = withContext(io) {
        val data = db.withTransaction {
            val raw = collect(bookmarksOnly)
            portableSnippetReferences(raw, db.snippetDao().getAll())
        }
        val dto = BackupDto(
            formatVersion = BACKUP_FORMAT_VERSION,
            appVersionName = BuildConfig.VERSION_NAME,
            exportedAt = Instant.now().toString(),
            kind = if (bookmarksOnly) BACKUP_KIND_BOOKMARKS else BACKUP_KIND_FULL,
            data = data,
        )
        val text = BackupValidator.writeJson.encodeToString(BackupDto.serializer(), dto)
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: throw BackupException("The selected location could not be opened for writing.")
        } catch (e: IOException) {
            throw BackupException("Writing the backup file failed.", e)
        } catch (e: SecurityException) {
            throw BackupException("The app is not allowed to write to the selected location.", e)
        }
        data.counts()
    }

    private suspend fun collect(bookmarksOnly: Boolean): BackupData {
        val bookmarks = db.bookmarkDao().getAll().map { it.toBackup() }
        if (bookmarksOnly) return BackupData(bookmarks = bookmarks)
        val learning = db.learningDao()
        return BackupData(
            bookmarks = bookmarks,
            snippets = db.snippetDao().getAll().filter { it.builtInId == null }.map { it.toBackup() },
            lessonProgress = learning.getProgress().map { it.toBackup() },
            quizResults = learning.getQuizResults().map { it.toBackup() },
            flashcardProgress = learning.getFlashcardProgress().map { it.toBackup() },
            studyDays = learning.getStudyDays().map { it.toBackup() },
            achievements = learning.getAchievements().map { it.toBackup() },
            searchHistory = db.searchHistoryDao().observeRecent(Int.MAX_VALUE).first().map { it.toBackup() },
            recentViews = db.recentViewDao().observeRecent(Int.MAX_VALUE).first().map { it.toBackup() },
        )
    }

    // ---- import ---------------------------------------------------------------------------------------------

    /** Reads (size-limited) and validates the file. Nothing is written yet. */
    suspend fun readFromUri(uri: Uri): ParsedBackup = withContext(io) {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readLimited(MAX_BACKUP_BYTES) }
                ?: throw BackupException("The selected file could not be opened.")
        } catch (e: IOException) {
            throw BackupException("Reading the file failed.", e)
        } catch (e: SecurityException) {
            throw BackupException("The app is not allowed to read the selected file.", e)
        } ?: throw BackupException("The file is larger than ${MAX_BACKUP_BYTES / (1024 * 1024)} MB and cannot be a backup.")
        ParsedBackup(BackupValidator.parse(bytes.toString(Charsets.UTF_8)))
    }

    /**
     * Writes [parsed] into the database in one transaction. A failure rolls everything back.
     * Bookmarks-only imports (requested, or because the file only contains bookmarks) always merge and only touch bookmarks.
     */
    suspend fun import(parsed: ParsedBackup, mode: ImportMode, bookmarksOnlyRequested: Boolean = false): ImportSummary = withContext(io) {
        // A bookmarks-only file can never replace everything else, whichever button was used to pick it.
        val bookmarksOnly = bookmarksOnlyRequested || parsed.isBookmarksOnly
        val incoming = if (bookmarksOnly) BackupData(bookmarks = parsed.backup.data.bookmarks) else parsed.backup.data
        val effectiveMode = if (bookmarksOnly) ImportMode.MERGE else mode
        val plan = try {
            db.withTransaction {
                val existing = if (effectiveMode == ImportMode.MERGE) collect(bookmarksOnly) else BackupData()
                if (effectiveMode == ImportMode.REPLACE) clearUserData()
                val snippets = BackupMerger.merge(existing, incoming).snippets
                db.snippetDao().insertAll(snippets.map { it.toEntity() })
                val resolved = resolveSnippetReferences(incoming, db.snippetDao().getAll())
                val plan = BackupMerger.merge(existing, resolved).copy(snippets = snippets)
                write(plan.copy(snippets = emptyList()))
                plan
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw BackupException("Importing failed. Your data was left unchanged.", e)
        }
        ImportSummary(effectiveMode, plan.counts(), bookmarksOnly)
    }

    private suspend fun clearUserData() {
        db.bookmarkDao().deleteAll()
        db.snippetDao().getAll().filter { it.builtInId == null }.forEach { db.snippetDao().delete(it.id) }
        db.learningDao().apply {
            clearProgress(); clearQuizResults(); clearFlashcardProgress(); clearStudyDays(); clearAchievements()
        }
        db.searchHistoryDao().clear()
        db.recentViewDao().clear()
    }

    private suspend fun write(plan: BackupData) {
        db.bookmarkDao().insertAll(plan.bookmarks.map { it.toEntity() })
        db.snippetDao().insertAll(plan.snippets.map { it.toEntity() })
        db.learningDao().apply {
            upsertAllProgress(plan.lessonProgress.map { it.toEntity() })
            insertQuizResults(plan.quizResults.map { it.toEntity() })
            upsertAllFlashcardProgress(plan.flashcardProgress.map { it.toEntity() })
            upsertAllStudyDays(plan.studyDays.map { it.toEntity() })
            insertAchievements(plan.achievements.map { it.toEntity() })
        }
        plan.searchHistory.forEach { db.searchHistoryDao().upsert(it.toEntity()) }
        plan.recentViews.forEach { db.recentViewDao().upsert(it.toEntity()) }
    }

    companion object {
        /** Default file name offered in the system file picker. */
        fun defaultFileName(bookmarksOnly: Boolean, date: LocalDate = LocalDate.now()): String =
            "smali-helper-plus-${if (bookmarksOnly) "bookmarks" else "backup"}-${date.format(DateTimeFormatter.BASIC_ISO_DATE)}.json"
    }
}

/** Reads at most [max] bytes; returns null when the stream is longer. */
internal fun InputStream.readLimited(max: Long): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0L
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        total += n
        if (total > max) return null
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}
