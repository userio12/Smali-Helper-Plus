package com.riyadm.smalihelperplus.data.updates

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.riyadm.smalihelperplus.core.datastore.UserPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Weekly background check. It only fetches the manifest and stores the result ([UpdateCheckStore]); the Updates screen
 * shows it. It never downloads or installs packages and posts no notification, so users stay in control.
 */
@HiltWorker
class ContentUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: ContentUpdateRepository,
    private val preferences: UserPreferencesRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!preferences.preferences.first().autoUpdateChecks) return Result.success()
        return when (val outcome = repository.checkForUpdates()) {
            is CheckOutcome.Failed -> when (outcome.error) {
                is UpdateError.Offline, is UpdateError.Timeout, is UpdateError.Http ->
                    if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
                else -> Result.success()
            }
            else -> Result.success()
        }
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
