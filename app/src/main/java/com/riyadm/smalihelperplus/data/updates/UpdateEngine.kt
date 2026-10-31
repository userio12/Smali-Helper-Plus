package com.riyadm.smalihelperplus.data.updates

import com.riyadm.smalihelperplus.core.content.ContentPackage
import com.riyadm.smalihelperplus.core.content.ContentValidator
import com.riyadm.smalihelperplus.core.content.LessonBundleDto
import com.riyadm.smalihelperplus.core.content.OpcodeBundleDto
import com.riyadm.smalihelperplus.core.content.SnippetBundleDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** A downloaded package that passed size, checksum, decoding and content validation. Ready to be written to Room. */
sealed interface VerifiedPackage {
    val packageId: String
    val version: Int
    val description: String

    data class Opcodes(val bundle: OpcodeBundleDto, override val description: String) : VerifiedPackage {
        override val packageId get() = ContentPackage.OPCODES
        override val version get() = bundle.contentVersion
    }

    data class Lessons(val bundle: LessonBundleDto, override val description: String) : VerifiedPackage {
        override val packageId get() = ContentPackage.LESSONS
        override val version get() = bundle.contentVersion
    }

    data class Snippets(val bundle: SnippetBundleDto, override val description: String) : VerifiedPackage {
        override val packageId get() = ContentPackage.SNIPPETS
        override val version get() = bundle.contentVersion
    }
}

/**
 * Network + verification half of the update pipeline. Has no Android or Room dependency, so it is fully
 * unit-testable. Downloaded bytes are only ever decoded as the typed JSON DTOs; nothing is executed.
 */
class UpdateEngine(
    private val fetcher: UpdateFetcher,
    private val json: Json,
    private val requireHttps: Boolean = true,
) {
    /** Downloads, decodes and validates the manifest at [manifestUrl]. */
    suspend fun fetchManifest(manifestUrl: String): UpdateManifestDto {
        ManifestValidator.requireHttpsUrl(manifestUrl, requireHttps)
        val bytes = fetcher.fetch(manifestUrl, MAX_MANIFEST_BYTES)
        val manifest = try {
            json.decodeFromString<UpdateManifestDto>(bytes.toString(Charsets.UTF_8))
        } catch (e: SerializationException) {
            throw UpdateException(UpdateError.InvalidManifest("it is not valid JSON in the expected format"), e)
        } catch (e: IllegalArgumentException) {
            throw UpdateException(UpdateError.InvalidManifest("it is not valid JSON in the expected format"), e)
        }
        ManifestValidator.validate(manifest, requireHttps)
        return manifest
    }

    suspend fun check(manifestUrl: String, installed: Map<String, Int>, appVersionCode: Int): CheckOutcome =
        UpdatePlanner.plan(fetchManifest(manifestUrl), installed, appVersionCode)

    /** Downloads one package and runs every check. Throws [UpdateException] on any problem. */
    suspend fun download(update: AvailableUpdate): VerifiedPackage {
        // Re-validate here: the update may come from persisted state rather than a fresh manifest.
        ManifestValidator.requireHttpsUrl(update.url, requireHttps)
        if (update.packageId !in ManifestValidator.KNOWN_PACKAGES) {
            throw UpdateException(UpdateError.InvalidManifest("unknown package id"))
        }
        if (update.sizeBytes !in 1..MAX_PACKAGE_BYTES) throw UpdateException(UpdateError.TooLarge)
        val bytes = fetcher.fetch(update.url, update.sizeBytes)
        if (bytes.size.toLong() != update.sizeBytes) throw UpdateException(UpdateError.SizeMismatch(update.packageId))
        if (!sha256Hex(bytes).equals(update.sha256, ignoreCase = true)) {
            throw UpdateException(UpdateError.HashMismatch(update.packageId))
        }
        return decodeAndValidate(update, bytes.toString(Charsets.UTF_8))
    }

    private fun decodeAndValidate(update: AvailableUpdate, text: String): VerifiedPackage {
        fun <T> decode(block: () -> T): T = try {
            block()
        } catch (e: SerializationException) {
            throw UpdateException(UpdateError.InvalidContent(update.packageId, "the file is not valid content JSON"), e)
        } catch (e: IllegalArgumentException) {
            throw UpdateException(UpdateError.InvalidContent(update.packageId, "the file is not valid content JSON"), e)
        }

        fun ensureValid(result: ContentValidator.Result) {
            if (result is ContentValidator.Result.Invalid) {
                throw UpdateException(UpdateError.InvalidContent(update.packageId, result.errors.take(3).joinToString("; ")))
            }
        }

        fun checkVersion(contentVersion: Int) {
            if (contentVersion != update.newVersion) {
                throw UpdateException(
                    UpdateError.InvalidContent(update.packageId, "version $contentVersion does not match the manifest (${update.newVersion})"),
                )
            }
        }

        return when (update.packageId) {
            ContentPackage.OPCODES -> {
                val bundle = decode { json.decodeFromString<OpcodeBundleDto>(text) }
                checkVersion(bundle.contentVersion)
                ensureValid(ContentValidator.validateOpcodes(bundle))
                VerifiedPackage.Opcodes(bundle, update.description)
            }
            ContentPackage.LESSONS -> {
                val bundle = decode { json.decodeFromString<LessonBundleDto>(text) }
                checkVersion(bundle.contentVersion)
                ensureValid(ContentValidator.validateLessons(bundle))
                VerifiedPackage.Lessons(bundle, update.description)
            }
            else -> {
                val bundle = decode { json.decodeFromString<SnippetBundleDto>(text) }
                checkVersion(bundle.contentVersion)
                ensureValid(ContentValidator.validateSnippets(bundle))
                VerifiedPackage.Snippets(bundle, update.description)
            }
        }
    }

    companion object {
        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
