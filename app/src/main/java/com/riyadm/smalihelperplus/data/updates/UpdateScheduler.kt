package com.riyadm.smalihelperplus.data.updates

import android.content.Context
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.riyadm.smalihelperplus.core.common.ApplicationScope
import com.riyadm.smalihelperplus.core.datastore.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the weekly background check in sync with the "Automatically check for documentation updates" preference.
 * [start] is called once from `SmaliHelperApp.onCreate()`. The worker only *checks*; it never installs content.
 */
@Singleton
class UpdateScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: UserPreferencesRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            preferences.preferences.map { it.autoUpdateChecks }.distinctUntilChanged().collect { enabled ->
                // HiltTestApplication has no Configuration.Provider and the default initializer is removed.
                // Tests that exercise workers explicitly initialize WorkManager with WorkManagerTestInitHelper.
                if (context.applicationContext is Configuration.Provider) {
                    if (enabled) schedule() else cancel()
                }
            }
        }
    }

    private fun schedule() {
        val request = PeriodicWorkRequestBuilder<ContentUpdateWorker>(7, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "content_update_check"
    }
}
