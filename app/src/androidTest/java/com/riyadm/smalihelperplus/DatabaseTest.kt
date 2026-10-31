package com.riyadm.smalihelperplus

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.riyadm.smalihelperplus.core.content.ContentInstaller
import com.riyadm.smalihelperplus.core.content.ContentSource
import com.riyadm.smalihelperplus.core.content.ContentValidationException
import com.riyadm.smalihelperplus.core.content.OpcodeBundleDto
import com.riyadm.smalihelperplus.core.content.SnippetBundleDto
import com.riyadm.smalihelperplus.core.content.SnippetDto
import com.riyadm.smalihelperplus.core.database.AppDatabase
import com.riyadm.smalihelperplus.core.database.entity.BookmarkEntity
import com.riyadm.smalihelperplus.core.database.entity.BookmarkType
import com.riyadm.smalihelperplus.core.database.entity.SearchHistoryEntity
import com.riyadm.smalihelperplus.core.database.entity.SnippetEntity
import com.riyadm.smalihelperplus.domain.search.FtsQuery
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test

/** DAO and content-installer tests on an in-memory Room database (no Hilt, no UI). */
class DatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var installer: ContentInstaller
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        installer = ContentInstaller(db, json)
    }

    @After
    fun closeDatabase() = db.close()

    /** The opcode bundle shipped in the APK (the target app's assets, which also hold the content files). */
    private fun bundledOpcodes(): OpcodeBundleDto {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        return assets.open("content/opcodes.json").bufferedReader().use { json.decodeFromString<OpcodeBundleDto>(it.readText()) }
    }

    private fun runDb(block: suspend () -> Unit) = runBlocking { withTimeout(10_000) { block() } }

    private fun bookmark(name: String) =
        BookmarkEntity(type = BookmarkType.OPCODE, targetId = name, title = name, createdAtMillis = 1L)

    // ---- bookmarks -----------------------------------------------------------------------------------------------

    @Test
    fun bookmarks_insertAndObserve() = runDb {
        val dao = db.bookmarkDao()
        assertThat(dao.observeAll().first()).isEmpty()

        dao.insert(bookmark("const/4"))

        val all = dao.observeAll().first { it.isNotEmpty() }
        assertThat(all.map { it.targetId }).containsExactly("const/4")
        assertThat(dao.observeIsBookmarked(BookmarkType.OPCODE, "const/4").first()).isTrue()
        assertThat(dao.observeIsBookmarked(BookmarkType.OPCODE, "nop").first()).isFalse()
    }

    @Test
    fun bookmarks_duplicateInsertIsIgnored() = runDb {
        val dao = db.bookmarkDao()
        dao.insert(bookmark("const/4"))
        dao.insert(bookmark("const/4"))
        assertThat(dao.getAll()).hasSize(1)
    }

    @Test
    fun bookmarks_deleteRemovesOnlyThatBookmark() = runDb {
        val dao = db.bookmarkDao()
        dao.insert(bookmark("const/4"))
        dao.insert(bookmark("nop"))

        dao.delete(BookmarkType.OPCODE, "const/4")

        assertThat(dao.observeAll().first().map { it.targetId }).containsExactly("nop")
        assertThat(dao.observeIsBookmarked(BookmarkType.OPCODE, "const/4").first()).isFalse()
    }

    // ---- full-text search ----------------------------------------------------------------------------------------

    @Test
    fun fts_findsInstalledOpcodesByPrefix() = runDb {
        installer.installOpcodes(bundledOpcodes(), ContentSource.BUNDLED, "test", 1L)

        val hits = db.opcodeDao().searchFts(FtsQuery.sanitize("invoke-static")!!)

        assertThat(hits.map { it.name }).contains("invoke-static")
        assertThat(hits.map { it.name }).doesNotContain("const/4")
    }

    // ---- content installer ---------------------------------------------------------------------------------------

    @Test
    fun installOpcodes_replacesContentAndFtsIndex() = runDb {
        val original = bundledOpcodes()
        installer.installOpcodes(original, ContentSource.BUNDLED, "v1", 1L)
        val count = db.opcodeDao().count()
        assertThat(count).isEqualTo(original.opcodes.size)

        // v2: const/4 gets a new summary and another opcode is dropped (the bundle stays above the 200 minimum).
        val dropped = original.opcodes.last().name
        val updated = original.copy(
            contentVersion = original.contentVersion + 1,
            opcodes = original.opcodes.dropLast(1).map {
                if (it.name == "const/4") it.copy(summary = "Zorblax marker summary for the update test") else it
            },
        )
        installer.installOpcodes(updated, ContentSource.REMOTE, "v2", 2L)

        assertThat(db.opcodeDao().count()).isEqualTo(count - 1)
        assertThat(db.opcodeDao().getByName(dropped)).isNull()
        assertThat(db.opcodeDao().getByName("const/4")!!.summary).contains("Zorblax")
        // The FTS index follows the new content.
        assertThat(db.opcodeDao().searchFts("zorblax*").map { it.name }).containsExactly("const/4")
        val metadata = db.contentMetadataDao().get("opcodes")!!
        assertThat(metadata.contentVersion).isEqualTo(updated.contentVersion)
        assertThat(metadata.source).isEqualTo(ContentSource.REMOTE)
    }

    @Test
    fun installOpcodes_keepsUserTablesIntact() = runDb {
        installer.installOpcodes(bundledOpcodes(), ContentSource.BUNDLED, "v1", 1L)
        db.bookmarkDao().insert(bookmark("const/4"))
        db.searchHistoryDao().upsert(SearchHistoryEntity(query = "invoke", lastUsedMillis = 5L))
        val userSnippetId = db.snippetDao().insert(
            SnippetEntity(title = "Mine", code = ".method public static m()V\n.end method", createdAtMillis = 1L, updatedAtMillis = 1L),
        )

        val original = bundledOpcodes()
        installer.installOpcodes(original.copy(contentVersion = original.contentVersion + 1), ContentSource.REMOTE, "v2", 2L)

        assertThat(db.bookmarkDao().getAll().map { it.targetId }).containsExactly("const/4")
        assertThat(db.searchHistoryDao().observeRecent(10).first().map { it.query }).containsExactly("invoke")
        assertThat(db.snippetDao().getById(userSnippetId)?.title).isEqualTo("Mine")
    }

    @Test
    fun installOpcodes_invalidBundleLeavesInstalledContentUntouched() = runDb {
        val original = bundledOpcodes()
        installer.installOpcodes(original, ContentSource.BUNDLED, "v1", 1L)
        val before = db.opcodeDao().getAll()

        // A truncated bundle fails validation, so nothing may be deleted or written.
        val truncated = original.copy(contentVersion = original.contentVersion + 1, opcodes = original.opcodes.take(10))
        val failure = runCatching { installer.installOpcodes(truncated, ContentSource.REMOTE, "bad", 2L) }.exceptionOrNull()
        assertThat(failure).isInstanceOf(ContentValidationException::class.java)

        assertThat(db.opcodeDao().getAll()).isEqualTo(before)
        assertThat(db.contentMetadataDao().get("opcodes")!!.contentVersion).isEqualTo(original.contentVersion)
    }

    @Test
    fun installSnippets_replacesBuiltInsButKeepsUserSnippets() = runDb {
        fun bundle(version: Int, vararg ids: String) = SnippetBundleDto(
            schemaVersion = 1, contentVersion = version, generatedAt = "2026-01-01",
            snippets = ids.map { SnippetDto(id = it, title = "Built-in $it", description = "", collection = "Classes", code = ".class public L$it;") },
        )
        installer.installSnippets(bundle(1, "a", "b"), ContentSource.BUNDLED, "v1", 1L)
        db.snippetDao().insert(SnippetEntity(title = "Mine", code = ".class public LMine;", createdAtMillis = 1L, updatedAtMillis = 1L))

        installer.installSnippets(bundle(2, "b", "c"), ContentSource.REMOTE, "v2", 2L)

        val titles = db.snippetDao().getAll().map { it.title }
        assertThat(titles).containsExactly("Mine", "Built-in b", "Built-in c")
    }
}
