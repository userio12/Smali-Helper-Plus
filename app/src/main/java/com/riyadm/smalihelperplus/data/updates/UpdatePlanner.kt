package com.riyadm.smalihelperplus.data.updates

/** Pure comparison of a validated manifest against the installed content versions. */
object UpdatePlanner {

    /**
     * @param installed packageId -> installed contentVersion
     * @return updates with a strictly higher contentVersion than installed (older or equal versions are ignored),
     * or [CheckOutcome.AppUpdateRequired] when the manifest needs a newer app.
     */
    fun plan(manifest: UpdateManifestDto, installed: Map<String, Int>, appVersionCode: Int): CheckOutcome {
        if (manifest.minAppVersionCode > appVersionCode) {
            return CheckOutcome.AppUpdateRequired(manifest.minAppVersionCode)
        }
        val updates = manifest.packages
            .filter { pkg -> pkg.contentVersion > (installed[pkg.id] ?: 0) }
            .map { pkg ->
                AvailableUpdate(
                    packageId = pkg.id,
                    installedVersion = installed[pkg.id],
                    newVersion = pkg.contentVersion,
                    url = pkg.url,
                    sha256 = pkg.sha256,
                    sizeBytes = pkg.sizeBytes,
                    description = pkg.description,
                )
            }
            .sortedBy { it.packageId }
        return if (updates.isEmpty()) CheckOutcome.UpToDate(manifest.publishedAt) else CheckOutcome.Available(updates, manifest.publishedAt)
    }
}
