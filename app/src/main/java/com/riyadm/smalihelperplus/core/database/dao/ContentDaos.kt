package com.riyadm.smalihelperplus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.riyadm.smalihelperplus.core.database.entity.ContentMetadataEntity
import com.riyadm.smalihelperplus.core.database.entity.FlashcardEntity
import com.riyadm.smalihelperplus.core.database.entity.LessonEntity
import com.riyadm.smalihelperplus.core.database.entity.OpcodeEntity
import kotlinx.coroutines.flow.Flow

data class CategoryCount(val category: String, val count: Int)

@Dao
interface OpcodeDao {
    @Query("SELECT * FROM opcodes ORDER BY opcode, name")
    fun observeAll(): Flow<List<OpcodeEntity>>

    @Query("SELECT * FROM opcodes ORDER BY opcode, name")
    suspend fun getAll(): List<OpcodeEntity>

    @Query("SELECT * FROM opcodes WHERE category = :category ORDER BY opcode, name")
    fun observeByCategory(category: String): Flow<List<OpcodeEntity>>

    @Query("SELECT * FROM opcodes WHERE name = :name LIMIT 1")
    fun observeByName(name: String): Flow<OpcodeEntity?>

    @Query("SELECT * FROM opcodes WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): OpcodeEntity?

    @Query("SELECT * FROM opcodes WHERE name IN (:names)")
    suspend fun getByNames(names: List<String>): List<OpcodeEntity>

    @Query("SELECT category, COUNT(*) AS count FROM opcodes GROUP BY category")
    fun observeCategoryCounts(): Flow<List<CategoryCount>>

    @Query("SELECT COUNT(*) FROM opcodes")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM opcodes")
    fun observeCount(): Flow<Int>

    /** Full-text search. [ftsQuery] must already be a sanitized FTS4 MATCH expression. */
    @Query(
        """
        SELECT opcodes.* FROM opcodes
        JOIN opcodes_fts ON opcodes.rowid = opcodes_fts.rowid
        WHERE opcodes_fts MATCH :ftsQuery
        """,
    )
    suspend fun searchFts(ftsQuery: String): List<OpcodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<OpcodeEntity>)

    @Query("DELETE FROM opcodes")
    suspend fun deleteAll()
}

@Dao
interface LessonDao {
    @Query("SELECT * FROM lessons ORDER BY sortOrder")
    fun observeAll(): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons ORDER BY sortOrder")
    suspend fun getAll(): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<LessonEntity?>

    @Query("SELECT * FROM lessons WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LessonEntity?

    @Query("SELECT COUNT(*) FROM lessons")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LessonEntity>)

    @Query("DELETE FROM lessons")
    suspend fun deleteAll()

    @Query("SELECT * FROM flashcards ORDER BY topic, id")
    fun observeFlashcards(): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards ORDER BY topic, id")
    suspend fun getFlashcards(): List<FlashcardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcards(items: List<FlashcardEntity>)

    @Query("DELETE FROM flashcards")
    suspend fun deleteFlashcards()
}

@Dao
interface ContentMetadataDao {
    @Query("SELECT * FROM content_metadata ORDER BY packageId")
    fun observeAll(): Flow<List<ContentMetadataEntity>>

    @Query("SELECT * FROM content_metadata")
    suspend fun getAll(): List<ContentMetadataEntity>

    @Query("SELECT * FROM content_metadata WHERE packageId = :packageId LIMIT 1")
    suspend fun get(packageId: String): ContentMetadataEntity?

    @Upsert
    suspend fun upsert(item: ContentMetadataEntity)
}
