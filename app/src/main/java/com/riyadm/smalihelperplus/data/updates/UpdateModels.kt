package com.riyadm.smalihelperplus.data.updates

import kotlinx.serialization.Serializable

/** Version of the manifest format understood by this app. */
const val MANIFEST_SCHEMA_VERSION = 1

/** Hard limits for downloads. Content packages are plain JSON documentation; nothing larger is ever accepted. */
const val MAX_MANIFEST_BYTES = 256L * 1024
const val MAX_PACKAGE_BYTES = 5L * 1024 * 1024

/**
 * Remote update manifest (see content/README.md). Package files are exactly the bundle DTOs of
 * `core/content/ContentModels.kt`.
 */
@Serializable
data class UpdateManifestDto(
    val schemaVersion: Int,
    val publishedAt: String = "",
    /** Lowest app versionCode able to read these packages. */
    val minAppVersionCode: Int = 1,
    val packages: List<UpdatePackageDto> = emptyList(),
)

@Serializable
data class UpdatePackageDto(
    /** "opcodes" | "lessons" | "snippets" */
    val id: String,
    val contentVersion: Int,
    val url: String,
    /** Lower-case hex SHA-256 of the exact bytes served at [url]. */
    val sha256: String,
    val sizeBytes: Long,
    val description: String = "",
)

/** A package that is newer than the installed one and passed manifest validation. */
@Serializable
data class AvailableUpdate(
    val packageId: String,
    /** Null when the package is not installed at all. */
    val installedVersion: Int?,
    val newVersion: Int,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val description: String,
)

/** Result of a manifest check. A failed request is never reported as "up to date" or "update available". */
sealed interface CheckOutcome {
    data class Available(val updates: List<AvailableUpdate>, val publishedAt: String) : CheckOutcome
    data class UpToDate(val publishedAt: String) : CheckOutcome

    /** The manifest requires a newer version of the app than the one installed. */
    data class AppUpdateRequired(val requiredVersionCode: Int) : CheckOutcome
    data class Failed(val error: UpdateError) : CheckOutcome
}

/** Per-package outcome of [ContentUpdateRepository.applyUpdates]. */
data class PackageInstallResult(val packageId: String, val newVersion: Int, val error: UpdateError?) {
    val success: Boolean get() = error == null
}

sealed interface ApplyOutcome {
    /** Every selected package was installed. */
    data class Success(val installed: List<PackageInstallResult>) : ApplyOutcome

    /**
     * Some packages were installed before a later one failed. Packages are independent and each install is atomic,
     * so already-installed packages stay valid.
     */
    data class Partial(val results: List<PackageInstallResult>) : ApplyOutcome

    /** Nothing was installed and nothing changed. */
    data class Failed(val error: UpdateError) : ApplyOutcome
}

/** User-presentable failure reasons. [message] is safe to show; it never contains response bodies or secrets. */
sealed class UpdateError(val message: String) {
    data object Offline : UpdateError("No connection. Check your network and try again. Installed content keeps working.")
    data object Timeout : UpdateError("The server took too long to respond.")
    data class Http(val code: Int) : UpdateError(
        if (code == 404) {
            "No update information was found at the update source (HTTP 404). Installed content keeps working."
        } else {
            "The server answered with HTTP $code. Installed content keeps working."
        },
    )
    data class InsecureUrl(val reason: String) : UpdateError("Only HTTPS addresses are allowed: $reason")
    data object TooLarge : UpdateError("The download is larger than the allowed size and was rejected.")
    data class InvalidManifest(val reason: String) : UpdateError("The update manifest is invalid: $reason")
    data class UnsupportedSchema(val found: Int) :
        UpdateError("The update uses format version $found, which this app version does not understand.")
    data class HashMismatch(val packageId: String) :
        UpdateError("Integrity check failed for \"$packageId\" (checksum does not match). Nothing was installed.")
    data class SizeMismatch(val packageId: String) :
        UpdateError("Size of \"$packageId\" does not match the manifest. Nothing was installed.")
    data class InvalidContent(val packageId: String, val reason: String) :
        UpdateError("The \"$packageId\" package was rejected: $reason")
    data class InstallFailed(val packageId: String) :
        UpdateError("Installing \"$packageId\" failed. The previous content was kept.")
    data object BundledMissing : UpdateError("The bundled content could not be read from the app.")
    data class Unexpected(val kind: String) : UpdateError("Unexpected problem ($kind).")
}

/** Thrown inside the update pipeline; mapped to [CheckOutcome.Failed] / [ApplyOutcome] at the repository boundary. */
class UpdateException(val error: UpdateError, cause: Throwable? = null) : Exception(error.message, cause)
