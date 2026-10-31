package com.riyadm.smalihelperplus

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.riyadm.smalihelperplus.core.datastore.EditorTheme
import com.riyadm.smalihelperplus.core.datastore.ThemeMode
import com.riyadm.smalihelperplus.core.datastore.UserPreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppStateEntryPoint {
    fun preferences(): UserPreferencesRepository
}

/**
 * Puts the process-wide state that survives between tests (DataStore preferences and the SharedPreferences files)
 * back to a fresh install, and initialises WorkManager for [HiltTestApplication] (the real application is not used, so
 * nothing else would). Must run after the Hilt rule and before the activity is launched.
 *
 * The Room database does not need resetting here: [TestAppModule] provides a new in-memory database per test.
 */
class AppStateRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())

            val prefs = EntryPointAccessors.fromApplication(context, AppStateEntryPoint::class.java).preferences()
            runBlocking {
                prefs.setThemeMode(ThemeMode.SYSTEM)
                prefs.setDynamicColor(true)
                prefs.setEditorFontSize(14f)
                prefs.setEditorTheme(EditorTheme.MATCH_APP)
                prefs.setEditorShowLineNumbers(true)
                prefs.setEditorWordWrap(false)
                prefs.setAutoUpdateChecks(false)
                prefs.setUpdateManifestUrl("")
                prefs.setLastUpdateCheck(0L)
                prefs.setDailyGoal(3)
                prefs.setAiEnabled(false)
                prefs.setAiEndpoint("")
                prefs.setAiModel("")
                prefs.setAiAlwaysAsk(true)
                prefs.setLastPlaygroundCode("")
            }
            // Same file names as UpdateCheckStore / KeystoreApiKeyStore.
            listOf("update_check_state", "secure_ai_prefs").forEach {
                context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
            }
            base.evaluate()
        }
    }
}
