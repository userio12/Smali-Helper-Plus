package com.riyadm.smalihelperplus.core.content

import android.content.Context
import android.util.Log
import com.riyadm.smalihelperplus.core.common.IoDispatcher
import com.riyadm.smalihelperplus.core.database.dao.ContentMetadataDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs the documentation bundled in assets/content on first launch, and again whenever the APK ships a newer
 * content version than the one installed. A newer remotely-updated version is never downgraded.
 */
@Singleton
class BundledContentSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val installer: ContentInstaller,
    private val metadataDao: ContentMetadataDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    suspend fun seedIfNeeded() = withContext(io) {
        try {
            val now = System.currentTimeMillis()
            readAsset<OpcodeBundleDto>(OPCODES_ASSET)?.let { bundle ->
                if (needsInstall(ContentPackage.OPCODES, bundle.contentVersion)) {
                    installer.installOpcodes(bundle, ContentSource.BUNDLED, "Bundled with the app", now)
                }
            }
            readAsset<LessonBundleDto>(LESSONS_ASSET)?.let { bundle ->
                if (needsInstall(ContentPackage.LESSONS, bundle.contentVersion)) {
                    installer.installLessons(bundle, ContentSource.BUNDLED, "Bundled with the app", now)
                }
            }
            readAsset<SnippetBundleDto>(SNIPPETS_ASSET)?.let { bundle ->
                if (needsInstall(ContentPackage.SNIPPETS, bundle.contentVersion)) {
                    installer.installSnippets(bundle, ContentSource.BUNDLED, "Bundled with the app", now)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Content stays at the previous (transactional) state; screens show their empty/error states.
            Log.e(TAG, "Bundled content seeding failed: ${e.javaClass.simpleName}")
        } finally {
            _ready.value = true
        }
    }

    private suspend fun needsInstall(packageId: String, bundledVersion: Int): Boolean {
        val installed = metadataDao.get(packageId) ?: return true
        return bundledVersion > installed.contentVersion
    }

    private inline fun <reified T> readAsset(path: String): T? = try {
        context.assets.open(path).bufferedReader().use { json.decodeFromString<T>(it.readText()) }
    } catch (e: java.io.FileNotFoundException) {
        null
    }

    companion object {
        private const val TAG = "ContentSeeder"
        const val OPCODES_ASSET = "content/opcodes.json"
        const val LESSONS_ASSET = "content/lessons.json"
        const val SNIPPETS_ASSET = "content/snippets.json"
    }
}
