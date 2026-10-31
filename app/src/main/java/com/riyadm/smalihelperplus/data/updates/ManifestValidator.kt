package com.riyadm.smalihelperplus.data.updates

import com.riyadm.smalihelperplus.core.content.ContentPackage
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Pure validation of manifests and URLs (JVM-testable, no Android dependencies). */
object ManifestValidator {

    private val SHA256_REGEX = Regex("^[0-9a-f]{64}$")
    val KNOWN_PACKAGES = setOf(ContentPackage.OPCODES, ContentPackage.LESSONS, ContentPackage.SNIPPETS)

    /** Parses [raw] as an absolute HTTPS URL without credentials. Throws [UpdateException] otherwise. */
    fun requireHttpsUrl(raw: String, requireHttps: Boolean = true): HttpUrl {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) throw UpdateException(UpdateError.InsecureUrl("the address is empty"))
        val url = trimmed.toHttpUrlOrNull()
            ?: throw UpdateException(UpdateError.InsecureUrl("not a valid web address"))
        if (requireHttps && !url.isHttps) throw UpdateException(UpdateError.InsecureUrl("the address must start with https://"))
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            throw UpdateException(UpdateError.InsecureUrl("addresses with embedded credentials are not allowed"))
        }
        return url
    }

    /** Returns null when [raw] is acceptable as a user-entered manifest URL, otherwise a short explanation. */
    fun manifestUrlProblem(raw: String): String? = try {
        requireHttpsUrl(raw)
        null
    } catch (e: UpdateException) {
        (e.error as? UpdateError.InsecureUrl)?.reason ?: "invalid address"
    }

    /**
     * Validates structure and values of a decoded manifest. Throws [UpdateException] with a specific error.
     * [requireHttps] exists so that unit tests can talk to a plain-HTTP MockWebServer; production code never disables it.
     */
    fun validate(manifest: UpdateManifestDto, requireHttps: Boolean = true) {
        if (manifest.schemaVersion != MANIFEST_SCHEMA_VERSION) {
            throw UpdateException(UpdateError.UnsupportedSchema(manifest.schemaVersion))
        }
        if (manifest.minAppVersionCode < 1) invalid("minAppVersionCode must be >= 1")
        if (manifest.packages.size > KNOWN_PACKAGES.size) invalid("too many packages")
        val seen = HashSet<String>()
        manifest.packages.forEach { pkg ->
            if (pkg.id !in KNOWN_PACKAGES) invalid("unknown package id \"${pkg.id.take(40)}\"")
            if (!seen.add(pkg.id)) invalid("duplicate package \"${pkg.id}\"")
            if (pkg.contentVersion < 1) invalid("${pkg.id}: contentVersion must be >= 1")
            if (!SHA256_REGEX.matches(pkg.sha256)) invalid("${pkg.id}: sha256 must be 64 lower-case hex characters")
            if (pkg.sizeBytes !in 1..MAX_PACKAGE_BYTES) {
                if (pkg.sizeBytes > MAX_PACKAGE_BYTES) throw UpdateException(UpdateError.TooLarge)
                invalid("${pkg.id}: sizeBytes must be positive")
            }
            requireHttpsUrl(pkg.url, requireHttps)
        }
    }

    private fun invalid(reason: String): Nothing = throw UpdateException(UpdateError.InvalidManifest(reason))
}
