package com.riyadm.smalihelperplus.core.content

import androidx.room.withTransaction
import com.riyadm.smalihelperplus.core.database.AppDatabase
import com.riyadm.smalihelperplus.core.database.entity.ContentMetadataEntity
import com.riyadm.smalihelperplus.core.database.entity.FlashcardEntity
import com.riyadm.smalihelperplus.core.database.entity.LessonEntity
import com.riyadm.smalihelperplus.core.database.entity.OpcodeEntity
import com.riyadm.smalihelperplus.core.database.entity.SnippetEntity
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

object ContentPackage {
    const val OPCODES = "opcodes"
    const val LESSONS = "lessons"
    const val SNIPPETS = "snippets"
}

object ContentSource {
    const val BUNDLED = "bundled"
    const val REMOTE = "remote"
}

class ContentValidationException(val errors: List<String>) :
    IllegalArgumentException("Content validation failed: ${errors.take(5).joinToString("; ")}")

/**
 * Writes validated documentation content into Room. Every install runs inside a single transaction, so a failure
 * rolls back to the previous content automatically. User data tables (bookmarks, snippets created by the user,
 * learning progress, history) are never touched.
 */
@Singleton
class ContentInstaller @Inject constructor(
    private val db: AppDatabase,
    private val json: Json,
) {
    suspend fun installOpcodes(bundle: OpcodeBundleDto, source: String, description: String, nowMillis: Long, force: Boolean = false) {
        val result = ContentValidator.validateOpcodes(bundle)
        if (result is ContentValidator.Result.Invalid) throw ContentValidationException(result.errors)
        val entities = bundle.opcodes.map { it.toEntity(json) }
        db.withTransaction {
            if (!force && (db.contentMetadataDao().get(ContentPackage.OPCODES)?.contentVersion ?: 0) >= bundle.contentVersion) return@withTransaction
            db.opcodeDao().deleteAll()
            db.opcodeDao().insertAll(entities)
            db.contentMetadataDao().upsert(
                ContentMetadataEntity(
                    ContentPackage.OPCODES, bundle.contentVersion, bundle.schemaVersion, source, description, nowMillis,
                ),
            )
        }
    }

    suspend fun installLessons(bundle: LessonBundleDto, source: String, description: String, nowMillis: Long, force: Boolean = false) {
        val result = ContentValidator.validateLessons(bundle)
        if (result is ContentValidator.Result.Invalid) throw ContentValidationException(result.errors)
        val lessons = bundle.lessons.map {
            LessonEntity(it.id, it.level, it.order, it.title, it.summary, it.estimatedMinutes, json.encodeToString(it))
        }
        val cards = bundle.flashcards.map { FlashcardEntity(it.id, it.topic, it.front, it.back, it.code) }
        db.withTransaction {
            if (!force && (db.contentMetadataDao().get(ContentPackage.LESSONS)?.contentVersion ?: 0) >= bundle.contentVersion) return@withTransaction
            db.lessonDao().deleteAll()
            db.lessonDao().insertAll(lessons)
            db.lessonDao().deleteFlashcards()
            db.lessonDao().insertFlashcards(cards)
            db.contentMetadataDao().upsert(
                ContentMetadataEntity(
                    ContentPackage.LESSONS, bundle.contentVersion, bundle.schemaVersion, source, description, nowMillis,
                ),
            )
        }
    }

    /** Replaces only built-in snippets; user snippets (builtInId == null) are preserved. */
    suspend fun installSnippets(bundle: SnippetBundleDto, source: String, description: String, nowMillis: Long, force: Boolean = false) {
        val result = ContentValidator.validateSnippets(bundle)
        if (result is ContentValidator.Result.Invalid) throw ContentValidationException(result.errors)
        val entities = bundle.snippets.map {
            SnippetEntity(
                title = it.title, description = it.description, collection = it.collection, code = it.code,
                tags = it.tags, builtInId = it.id, createdAtMillis = nowMillis, updatedAtMillis = 0L,
            )
        }
        db.withTransaction {
            if (!force && (db.contentMetadataDao().get(ContentPackage.SNIPPETS)?.contentVersion ?: 0) >= bundle.contentVersion) return@withTransaction
            val dao = db.snippetDao()
            val existing = dao.getAll().filter { it.builtInId != null }
            val incomingIds = entities.mapTo(HashSet()) { it.builtInId }
            existing.filter { it.builtInId !in incomingIds }.forEach { dao.delete(it.id) }
            dao.insertAll(preserveBuiltInSnippetIds(existing, entities))
            db.contentMetadataDao().upsert(
                ContentMetadataEntity(
                    ContentPackage.SNIPPETS, bundle.contentVersion, bundle.schemaVersion, source, description, nowMillis,
                ),
            )
        }
    }
}

fun OpcodeDto.toEntity(json: Json) = OpcodeEntity(
    name = name,
    opcode = opcode,
    category = category,
    format = format,
    syntax = syntax,
    operandsJson = json.encodeToString(operands),
    summary = summary,
    description = description,
    technical = technical,
    example = example,
    exampleExplanation = exampleExplanation,
    javaEquivalent = javaEquivalent,
    kotlinEquivalent = kotlinEquivalent,
    registerUsage = registerUsage,
    minApi = minApi,
    dexVersion = dexVersion,
    compatibility = compatibility,
    commonMistakes = commonMistakes,
    related = related,
    referencesJson = json.encodeToString(references),
    tags = tags,
)

/** Match only built-in identities; a user row must never be overwritten by a content install. */
internal fun preserveBuiltInSnippetIds(existing: List<SnippetEntity>, incoming: List<SnippetEntity>): List<SnippetEntity> {
    val byId = existing.filter { it.builtInId != null }.associateBy { it.builtInId }
    return incoming.map { item ->
        val previous = item.builtInId?.let { byId[it] }
        if (previous == null) item.copy(id = 0) else item.copy(id = previous.id, createdAtMillis = previous.createdAtMillis)
    }
}
