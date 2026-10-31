package com.riyadm.smalihelperplus.data.updates

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class StoredCheck(
    val checkedAtMillis: Long,
    val publishedAt: String,
    val updates: List<AvailableUpdate>,
)

/**
 * Remembers the result of the last successful manifest check (also from the background worker) so the Updates screen
 * can show a badge without hitting the network again. Contains only public manifest data.
 */
@Singleton
class UpdateCheckStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<StoredCheck?> = _state.asStateFlow()

    fun save(check: StoredCheck) {
        prefs.edit { putString(KEY, json.encodeToString(StoredCheck.serializer(), check)) }
        _state.value = check
    }

    fun clear() {
        prefs.edit { remove(KEY) }
        _state.value = null
    }

    private fun read(): StoredCheck? = prefs.getString(KEY, null)?.let {
        runCatching { json.decodeFromString(StoredCheck.serializer(), it) }.getOrNull()
    }

    private companion object {
        const val FILE = "update_check_state"
        const val KEY = "last_check"
    }
}
