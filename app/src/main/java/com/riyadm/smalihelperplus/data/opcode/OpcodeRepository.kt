package com.riyadm.smalihelperplus.data.opcode

import com.riyadm.smalihelperplus.core.common.ApplicationScope
import com.riyadm.smalihelperplus.core.common.DefaultDispatcher
import com.riyadm.smalihelperplus.core.database.dao.OpcodeDao
import com.riyadm.smalihelperplus.domain.model.Opcode
import com.riyadm.smalihelperplus.domain.model.OpcodeCategory
import com.riyadm.smalihelperplus.domain.search.FeaturedOpcode
import com.riyadm.smalihelperplus.domain.search.FtsQuery
import com.riyadm.smalihelperplus.domain.search.OpcodeSearchEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class CategoryInfo(val category: OpcodeCategory, val count: Int)

/**
 * Read access to the opcode documentation. The whole table (a few hundred rows) is decoded once into an in-memory list
 * and kept current by observing Room, so categories, lists, detail pages, the featured opcode and the search index are
 * all derived from one source without further queries.
 */
@Singleton
class OpcodeRepository @Inject constructor(
    private val dao: OpcodeDao,
    json: Json,
    @ApplicationScope scope: CoroutineScope,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {
    /** All opcodes ordered by opcode value; null until the first load completes. */
    val allOrNull: StateFlow<List<Opcode>?> = dao.observeAll()
        .map { rows -> rows.map { it.toDomain(json) } }
        .flowOn(defaultDispatcher)
        .stateIn(scope, SharingStarted.Lazily, null)

    /** Emits the (empty-list-safe) opcode list as soon as it is loaded. */
    val all: Flow<List<Opcode>> = allOrNull.filterNotNull()

    /** The search index; rebuilt whenever the opcode table changes. */
    val searchEngine: StateFlow<OpcodeSearchEngine?> = all
        .map { OpcodeSearchEngine(it) }
        .flowOn(defaultDispatcher)
        .stateIn(scope, SharingStarted.Lazily, null)

    /** Categories (in [OpcodeCategory] order) that contain at least one opcode, with their counts. */
    fun observeCategories(): Flow<List<CategoryInfo>> = all.map { list ->
        val counts = list.groupingBy { it.category }.eachCount()
        OpcodeCategory.entries.mapNotNull { c -> counts[c.id]?.let { CategoryInfo(c, it) } }
    }.distinctUntilChanged()

    fun observeTotalCount(): Flow<Int> = all.map { it.size }.distinctUntilChanged()

    /** Opcodes of [category] (an [OpcodeCategory] id) or all opcodes when null, ordered by opcode value. */
    fun observeOpcodes(category: String?): Flow<List<Opcode>> =
        if (category == null) all else all.map { list -> list.filter { it.category == category } }

    /** The opcode called [name] (exact match first, then case-insensitive), or null if there is none. */
    fun observeOpcode(name: String): Flow<Opcode?> = all.map { list -> findByName(list, name) }.distinctUntilChanged()

    /** Related opcodes of [opcode] that exist in the table, in the authored order. */
    fun observeRelated(opcode: Opcode): Flow<List<Opcode>> = all.map { list ->
        val byName = list.associateBy { it.name }
        opcode.related.mapNotNull { byName[it] }
    }.distinctUntilChanged()

    /** Opcode of the day for [epochDay] (deterministic, see [FeaturedOpcode]). */
    fun observeFeatured(epochDay: Long): Flow<Opcode?> =
        all.map { FeaturedOpcode.pick(it, epochDay) }.distinctUntilChanged()

    /**
     * Names matched by the Room FTS4 index for [query] (long-text signal for ranking). Never throws: a malformed
     * expression or a missing table simply yields no extra signal.
     */
    suspend fun ftsMatchedNames(query: String): Set<String> {
        val expression = FtsQuery.sanitize(query) ?: return emptySet()
        return try {
            dao.searchFts(expression).mapTo(HashSet()) { it.name }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            emptySet()
        }
    }

    companion object {
        fun findByName(list: List<Opcode>, name: String): Opcode? =
            list.firstOrNull { it.name == name } ?: list.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
