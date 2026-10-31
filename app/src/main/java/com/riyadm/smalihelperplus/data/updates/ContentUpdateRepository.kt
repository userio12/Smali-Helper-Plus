package com.riyadm.smalihelperplus.data.updates

import android.content.Context
import com.riyadm.smalihelperplus.BuildConfig
import com.riyadm.smalihelperplus.core.common.IoDispatcher
import com.riyadm.smalihelperplus.core.content.BundledContentSeeder
import com.riyadm.smalihelperplus.core.content.ContentInstaller
import com.riyadm.smalihelperplus.core.content.ContentSource
import com.riyadm.smalihelperplus.core.content.ContentValidationException
import com.riyadm.smalihelperplus.core.content.ContentValidator
import com.riyadm.smalihelperplus.core.content.LessonBundleDto
import com.riyadm.smalihelperplus.core.content.OpcodeBundleDto
import com.riyadm.smalihelperplus.core.content.SnippetBundleDto
import com.riyadm.smalihelperplus.core.database.dao.ContentMetadataDao
import com.riyadm.smalihelperplus.core.database.entity.ContentMetadataEntity
import com.riyadm.smalihelperplus.core.datastore.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Documentation updates.
 *
 * Rollback semantics: [applyUpdates] works in two phases. Phase 1 downloads and fully verifies (size, SHA-256, JSON
 * decoding, [ContentValidator]) every selected package without touching the database, so any network, integrity or
 * format problem aborts the whole operation with nothing installed. Phase 2 installs the verified packages one by one;
 * each install is a single Room transaction ([ContentInstaller]), so a failing package is rolled back completely and
 * packages installed before it stay valid (packages are independent). User tables are never touched.
 */
@Singleton
class ContentUpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    fetcher: OkHttpUpdateFetcher,
    private val installer: ContentInstaller,
    private val metadataDao: ContentMetadataDao,
    private val preferences: UserPreferencesRepository,
    private val checkStore: UpdateCheckStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val engine = UpdateEngine(fetcher, json)
    private val mutex = Mutex()

    /** Currently installed packages with version, source and update time. */
    val installedPackages: Flow<List<ContentMetadataEntity>> = metadataDao.observeAll()

    /** Updates found by the last successful check that are still newer than what is installed. */
    val pendingUpdates: Flow<List<AvailableUpdate>> =
        combine(checkStore.state, metadataDao.observeAll()) { stored, installed ->
            val versions = installed.associate { it.packageId to it.contentVersion }
            stored?.updates.orEmpty().filter { it.newVersion > (versions[it.packageId] ?: 0) }
        }

    suspend fun effectiveManifestUrl(): String {
        val override = preferences.preferences.first().updateManifestUrl.trim()
        return override.ifEmpty { BuildConfig.DEFAULT_CONTENT_MANIFEST_URL }
    }

    /** Fetches the manifest and compares it with the installed versions. Never installs anything. */
    suspend fun checkForUpdates(): CheckOutcome = mutex.withLock {
        withContext(io) {
            try {
                val installed = metadataDao.getAll().associate { it.packageId to it.contentVersion }
                val outcome = engine.check(effectiveManifestUrl(), installed, BuildConfig.VERSION_CODE)
                val now = System.currentTimeMillis()
                when (outcome) {
                    is CheckOutcome.Available -> checkStore.save(StoredCheck(now, outcome.publishedAt, outcome.updates))
                    is CheckOutcome.UpToDate -> checkStore.save(StoredCheck(now, outcome.publishedAt, emptyList()))
                    is CheckOutcome.AppUpdateRequired -> checkStore.clear()
                    is CheckOutcome.Failed -> Unit
                }
                if (outcome !is CheckOutcome.Failed) preferences.setLastUpdateCheck(now)
                outcome
            } catch (e: UpdateException) {
                CheckOutcome.Failed(e.error)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CheckOutcome.Failed(UpdateError.Unexpected(e.javaClass.simpleName))
            }
        }
    }

    /** Downloads, verifies and installs [selected]. See the class documentation for rollback semantics. */
    suspend fun applyUpdates(selected: List<AvailableUpdate>): ApplyOutcome = mutex.withLock {
        withContext(io) {
            if (selected.isEmpty()) return@withContext ApplyOutcome.Success(emptyList())
            // Phase 1: download + verify everything before touching the database.
            val verified = try {
                selected.map { engine.download(it) }
            } catch (e: UpdateException) {
                return@withContext ApplyOutcome.Failed(e.error)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withContext ApplyOutcome.Failed(UpdateError.Unexpected(e.javaClass.simpleName))
            }
            // Phase 2: atomic per-package installs.
            val results = mutableListOf<PackageInstallResult>()
            val now = System.currentTimeMillis()
            for (pkg in verified) {
                val error = installVerified(pkg, ContentSource.REMOTE, now)
                results += PackageInstallResult(pkg.packageId, pkg.version, error)
                if (error != null) break
            }
            refreshStoredCheck()
            val failed = results.firstOrNull { !it.success }
            when {
                failed == null -> ApplyOutcome.Success(results)
                results.any { it.success } -> ApplyOutcome.Partial(results)
                else -> ApplyOutcome.Failed(failed.error!!)
            }
        }
    }

    /**
     * Reinstalls the content bundled in the APK (the assets/content JSON files), even if the installed remote version is
     * higher. All bundled files are read and validated first; installs are atomic per package.
     */
    suspend fun restoreBundledContent(): ApplyOutcome = mutex.withLock {
        withContext(io) {
            val bundles = try {
                readBundled()
            } catch (e: UpdateException) {
                return@withContext ApplyOutcome.Failed(e.error)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withContext ApplyOutcome.Failed(UpdateError.BundledMissing)
            }
            val now = System.currentTimeMillis()
            val results = mutableListOf<PackageInstallResult>()
            for (pkg in bundles) {
                val error = installVerified(pkg, ContentSource.BUNDLED, now, force = true)
                results += PackageInstallResult(pkg.packageId, pkg.version, error)
                if (error != null) break
            }
            val failed = results.firstOrNull { !it.success }
            when {
                failed == null -> ApplyOutcome.Success(results)
                results.any { it.success } -> ApplyOutcome.Partial(results)
                else -> ApplyOutcome.Failed(failed.error!!)
            }
        }
    }

    private fun readBundled(): List<VerifiedPackage> {
        val description = "Bundled with the app"
        val packages = mutableListOf<VerifiedPackage>()
        packages += readAsset<OpcodeBundleDto>(BundledContentSeeder.OPCODES_ASSET, "opcodes")?.let {
            requireValid("opcodes", ContentValidator.validateOpcodes(it))
            VerifiedPackage.Opcodes(it, description)
        } ?: throw UpdateException(UpdateError.BundledMissing)
        packages += readAsset<LessonBundleDto>(BundledContentSeeder.LESSONS_ASSET, "lessons")?.let {
            requireValid("lessons", ContentValidator.validateLessons(it))
            VerifiedPackage.Lessons(it, description)
        } ?: throw UpdateException(UpdateError.BundledMissing)
        packages += readAsset<SnippetBundleDto>(BundledContentSeeder.SNIPPETS_ASSET, "snippets")?.let {
            requireValid("snippets", ContentValidator.validateSnippets(it))
            VerifiedPackage.Snippets(it, description)
        } ?: throw UpdateException(UpdateError.BundledMissing)
        return packages
    }

    private fun requireValid(id: String, result: ContentValidator.Result) {
        if (result is ContentValidator.Result.Invalid) {
            throw UpdateException(UpdateError.InvalidContent(id, result.errors.take(3).joinToString("; ")))
        }
    }

    private inline fun <reified T> readAsset(path: String, id: String): T? = try {
        context.assets.open(path).bufferedReader().use { json.decodeFromString<T>(it.readText()) }
    } catch (e: FileNotFoundException) {
        null
    } catch (e: SerializationException) {
        throw UpdateException(UpdateError.InvalidContent(id, "bundled file is not valid"), e)
    }

    /** Returns null on success. A failure leaves the previously installed content untouched (single transaction). */
    private suspend fun installVerified(pkg: VerifiedPackage, source: String, nowMillis: Long, force: Boolean = false): UpdateError? = try {
        when (pkg) {
            is VerifiedPackage.Opcodes -> installer.installOpcodes(pkg.bundle, source, pkg.description, nowMillis, force)
            is VerifiedPackage.Lessons -> installer.installLessons(pkg.bundle, source, pkg.description, nowMillis, force)
            is VerifiedPackage.Snippets -> installer.installSnippets(pkg.bundle, source, pkg.description, nowMillis, force)
        }
        null
    } catch (e: ContentValidationException) {
        UpdateError.InvalidContent(pkg.packageId, e.errors.take(3).joinToString("; "))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        UpdateError.InstallFailed(pkg.packageId)
    }

    private suspend fun refreshStoredCheck() {
        val stored = checkStore.state.value ?: return
        val versions = metadataDao.getAll().associate { it.packageId to it.contentVersion }
        val remaining = stored.updates.filter { it.newVersion > (versions[it.packageId] ?: 0) }
        checkStore.save(stored.copy(updates = remaining))
    }
}
