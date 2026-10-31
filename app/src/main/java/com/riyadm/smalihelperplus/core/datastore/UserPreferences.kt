package com.riyadm.smalihelperplus.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class EditorTheme(val title: String) { MATCH_APP("Match app"), MIDNIGHT("Midnight"), PAPER("Paper"), SOLAR("Solarized") }

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val editorFontSizeSp: Float = 14f,
    val editorTheme: EditorTheme = EditorTheme.MATCH_APP,
    val editorShowLineNumbers: Boolean = true,
    val editorWordWrap: Boolean = false,
    val autoUpdateChecks: Boolean = false,
    val updateManifestUrl: String = "",
    val lastUpdateCheckMillis: Long = 0L,
    val dailyGoal: Int = 3,
    val aiEnabled: Boolean = false,
    /** OpenAI-compatible chat-completions base URL configured by the user (HTTPS only). */
    val aiEndpoint: String = "",
    val aiModel: String = "",
    /** When true the user is asked every time before code is sent to the AI service. */
    val aiAlwaysAskBeforeSending: Boolean = true,
    val lastPlaygroundCode: String = "",
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val editorFontSize = floatPreferencesKey("editor_font_size")
        val editorTheme = stringPreferencesKey("editor_theme")
        val editorLineNumbers = booleanPreferencesKey("editor_line_numbers")
        val editorWordWrap = booleanPreferencesKey("editor_word_wrap")
        val autoUpdateChecks = booleanPreferencesKey("auto_update_checks")
        val updateManifestUrl = stringPreferencesKey("update_manifest_url")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val dailyGoal = intPreferencesKey("daily_goal")
        val aiEnabled = booleanPreferencesKey("ai_enabled")
        val aiEndpoint = stringPreferencesKey("ai_endpoint")
        val aiModel = stringPreferencesKey("ai_model")
        val aiAlwaysAsk = booleanPreferencesKey("ai_always_ask")
        val lastPlaygroundCode = stringPreferencesKey("last_playground_code")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }.map { p ->
        UserPreferences(
            themeMode = p[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.dynamicColor] ?: true,
            editorFontSizeSp = p[Keys.editorFontSize] ?: 14f,
            editorTheme = p[Keys.editorTheme]?.let { runCatching { EditorTheme.valueOf(it) }.getOrNull() }
                ?: EditorTheme.MATCH_APP,
            editorShowLineNumbers = p[Keys.editorLineNumbers] ?: true,
            editorWordWrap = p[Keys.editorWordWrap] ?: false,
            autoUpdateChecks = p[Keys.autoUpdateChecks] ?: false,
            updateManifestUrl = p[Keys.updateManifestUrl] ?: "",
            lastUpdateCheckMillis = p[Keys.lastUpdateCheck] ?: 0L,
            dailyGoal = p[Keys.dailyGoal] ?: 3,
            aiEnabled = p[Keys.aiEnabled] ?: false,
            aiEndpoint = p[Keys.aiEndpoint] ?: "",
            aiModel = p[Keys.aiModel] ?: "",
            aiAlwaysAskBeforeSending = p[Keys.aiAlwaysAsk] ?: true,
            lastPlaygroundCode = p[Keys.lastPlaygroundCode] ?: "",
        )
    }

    suspend fun setThemeMode(value: ThemeMode) = context.dataStore.edit { it[Keys.themeMode] = value.name }
    suspend fun setDynamicColor(value: Boolean) = context.dataStore.edit { it[Keys.dynamicColor] = value }
    suspend fun setEditorFontSize(value: Float) =
        context.dataStore.edit { it[Keys.editorFontSize] = value.coerceIn(10f, 24f) }
    suspend fun setEditorTheme(value: EditorTheme) = context.dataStore.edit { it[Keys.editorTheme] = value.name }
    suspend fun setEditorShowLineNumbers(value: Boolean) = context.dataStore.edit { it[Keys.editorLineNumbers] = value }
    suspend fun setEditorWordWrap(value: Boolean) = context.dataStore.edit { it[Keys.editorWordWrap] = value }
    suspend fun setAutoUpdateChecks(value: Boolean) = context.dataStore.edit { it[Keys.autoUpdateChecks] = value }
    suspend fun setUpdateManifestUrl(value: String) = context.dataStore.edit { it[Keys.updateManifestUrl] = value.trim() }
    suspend fun setLastUpdateCheck(value: Long) = context.dataStore.edit { it[Keys.lastUpdateCheck] = value }
    suspend fun setDailyGoal(value: Int) = context.dataStore.edit { it[Keys.dailyGoal] = value.coerceIn(1, 20) }
    suspend fun setAiEnabled(value: Boolean) = context.dataStore.edit { it[Keys.aiEnabled] = value }
    suspend fun setAiEndpoint(value: String) = context.dataStore.edit { it[Keys.aiEndpoint] = value.trim() }
    suspend fun setAiModel(value: String) = context.dataStore.edit { it[Keys.aiModel] = value.trim() }
    suspend fun setAiAlwaysAsk(value: Boolean) = context.dataStore.edit { it[Keys.aiAlwaysAsk] = value }
    suspend fun setLastPlaygroundCode(value: String) = context.dataStore.edit { it[Keys.lastPlaygroundCode] = value }
}
