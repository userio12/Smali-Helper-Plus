package com.riyadm.smalihelperplus.data.updates

import com.google.common.truth.Truth.assertThat
import com.riyadm.smalihelperplus.core.content.CONTENT_SCHEMA_VERSION
import com.riyadm.smalihelperplus.core.content.SnippetBundleDto
import com.riyadm.smalihelperplus.core.content.SnippetDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class UpdateEngineTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    private val client = OkHttpClient()
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After fun tearDown() {
        runCatching { server.shutdown() }
    }

    /** Engine that may talk to the plain-HTTP MockWebServer (test-only relaxation). */
    private fun lenientEngine() = UpdateEngine(OkHttpUpdateFetcher(client, allowCleartext = true), json, requireHttps = false)

    private fun strictEngine() = UpdateEngine(OkHttpUpdateFetcher(client), json)

    private fun snippetBundle(version: Int, schema: Int = CONTENT_SCHEMA_VERSION) = SnippetBundleDto(
        schemaVersion = schema,
        contentVersion = version,
        generatedAt = "2026-01-01",
        snippets = listOf(SnippetDto("s1", "Title", "Desc", "Methods", ".method public static foo()V\n    return-void\n.end method")),
    )

    private fun manifestFor(bytes: ByteArray, version: Int, sha: String = UpdateEngine.sha256Hex(bytes), size: Long = bytes.size.toLong(), url: String = server.url("/snippets.json").toString()) =
        UpdateManifestDto(
            schemaVersion = 1,
            publishedAt = "2026-02-01",
            minAppVersionCode = 1,
            packages = listOf(UpdatePackageDto("snippets", version, url, sha, size, "Added snippets")),
        )

    private fun serve(path: String, body: String) {
        val previous = server.dispatcher
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse =
                if (request.path == path) MockResponse().setBody(body) else previous.dispatch(request)
        }
    }

    private fun serveAll(vararg routes: Pair<String, String>) {
        val map = routes.toMap()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse =
                map[request.path]?.let { MockResponse().setBody(it) } ?: MockResponse().setResponseCode(404)
        }
    }

    private suspend fun expectError(block: suspend () -> Unit): UpdateError {
        val result = runCatching { block() }
        val e = result.exceptionOrNull()
        assertThat(e).isInstanceOf(UpdateException::class.java)
        return (e as UpdateException).error
    }

    @Test fun validUpdateIsDetectedDownloadedAndVerified() = runTest {
        val bytes = json.encodeToString(snippetBundle(2)).toByteArray()
        serveAll(
            "/manifest.json" to json.encodeToString(manifestFor(bytes, 2)),
            "/snippets.json" to bytes.toString(Charsets.UTF_8),
        )
        val engine = lenientEngine()
        val outcome = engine.check(server.url("/manifest.json").toString(), mapOf("snippets" to 1), appVersionCode = 1)
        assertThat(outcome).isInstanceOf(CheckOutcome.Available::class.java)
        val update = (outcome as CheckOutcome.Available).updates.single()
        assertThat(update.packageId).isEqualTo("snippets")
        assertThat(update.installedVersion).isEqualTo(1)
        assertThat(update.newVersion).isEqualTo(2)
        assertThat(update.description).isEqualTo("Added snippets")

        val pkg = engine.download(update)
        assertThat(pkg).isInstanceOf(VerifiedPackage.Snippets::class.java)
        assertThat(pkg.version).isEqualTo(2)
    }

    @Test fun shaMismatchIsRejected() = runTest {
        val bytes = json.encodeToString(snippetBundle(2)).toByteArray()
        val wrongSha = "0".repeat(64)
        serveAll("/snippets.json" to bytes.toString(Charsets.UTF_8))
        val manifest = manifestFor(bytes, 2, sha = wrongSha)
        val update = (UpdatePlanner.plan(manifest, mapOf("snippets" to 1), 1) as CheckOutcome.Available).updates.single()
        val error = expectError { lenientEngine().download(update) }
        assertThat(error).isEqualTo(UpdateError.HashMismatch("snippets"))
    }

    @Test fun declaredSizeAboveLimitIsRejectedInManifest() = runTest {
        val bytes = json.encodeToString(snippetBundle(2)).toByteArray()
        val manifest = manifestFor(bytes, 2, size = MAX_PACKAGE_BYTES + 1)
        serveAll("/manifest.json" to json.encodeToString(manifest))
        val error = expectError { lenientEngine().fetchManifest(server.url("/manifest.json").toString()) }
        assertThat(error).isEqualTo(UpdateError.TooLarge)
    }

    @Test fun bodyLargerThanDeclaredIsAbortedWhileStreaming() = runTest {
        val bytes = json.encodeToString(snippetBundle(2)).toByteArray()
        // Server serves much more than the manifest promised.
        val padded = bytes.toString(Charsets.UTF_8) + " ".repeat(10_000)
        serveAll("/snippets.json" to padded)
        val update = (UpdatePlanner.plan(manifestFor(bytes, 2), mapOf("snippets" to 1), 1) as CheckOutcome.Available).updates.single()
        val error = expectError { lenientEngine().download(update) }
        assertThat(error).isEqualTo(UpdateError.TooLarge)
    }

    @Test fun smallerBodyThanDeclaredIsSizeMismatch() = runTest {
        val bytes = json.encodeToString(snippetBundle(2)).toByteArray()
        serveAll("/snippets.json" to bytes.toString(Charsets.UTF_8))
        val update = (UpdatePlanner.plan(manifestFor(bytes, 2, size = bytes.size + 50L), mapOf("snippets" to 1), 1) as CheckOutcome.Available).updates.single()
        val error = expectError { lenientEngine().download(update) }
        assertThat(error).isEqualTo(UpdateError.SizeMismatch("snippets"))
    }

    @Test fun nonHttpsManifestUrlIsRejectedBeforeAnyRequest() = runTest {
        val error = expectError { strictEngine().fetchManifest(server.url("/manifest.json").toString()) }
        assertThat(error).isInstanceOf(UpdateError.InsecureUrl::class.java)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test fun nonHttpsPackageUrlInManifestIsRejected() {
        val bytes = "{}".toByteArray()
        val manifest = manifestFor(bytes, 2, url = "http://example.com/snippets.json")
        val e = runCatching { ManifestValidator.validate(manifest) }.exceptionOrNull() as UpdateException
        assertThat(e.error).isInstanceOf(UpdateError.InsecureUrl::class.java)
    }

    @Test fun nonHttpsPackageUrlIsRejectedByStrictDownload() = runTest {
        val update = AvailableUpdate("snippets", 1, 2, server.url("/snippets.json").toString(), "0".repeat(64), 10, "")
        val error = expectError { strictEngine().download(update) }
        assertThat(error).isInstanceOf(UpdateError.InsecureUrl::class.java)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test fun malformedManifestJsonIsRejected() = runTest {
        serveAll("/manifest.json" to "{ this is not json")
        val error = expectError { lenientEngine().fetchManifest(server.url("/manifest.json").toString()) }
        assertThat(error).isInstanceOf(UpdateError.InvalidManifest::class.java)
    }

    @Test fun malformedPackageJsonIsRejectedEvenWithMatchingChecksum() = runTest {
        val garbage = "{\"schemaVersion\": 1, \"snippets\": [ oops"
        val bytes = garbage.toByteArray()
        serveAll("/snippets.json" to garbage)
        val update = (UpdatePlanner.plan(manifestFor(bytes, 2), mapOf("snippets" to 1), 1) as CheckOutcome.Available).updates.single()
        val error = expectError { lenientEngine().download(update) }
        assertThat(error).isInstanceOf(UpdateError.InvalidContent::class.java)
    }

    @Test fun manifestSchemaVersionMismatchIsRejected() = runTest {
        val bytes = json.encodeToString(snippetBundle(2)).toByteArray()
        val manifest = manifestFor(bytes, 2).copy(schemaVersion = 2)
        serveAll("/manifest.json" to json.encodeToString(manifest))
        val error = expectError { lenientEngine().fetchManifest(server.url("/manifest.json").toString()) }
        assertThat(error).isEqualTo(UpdateError.UnsupportedSchema(2))
    }

    @Test fun packageSchemaVersionMismatchIsRejectedByContentValidator() = runTest {
        val bytes = json.encodeToString(snippetBundle(2, schema = 9)).toByteArray()
        serveAll("/snippets.json" to bytes.toString(Charsets.UTF_8))
        val update = (UpdatePlanner.plan(manifestFor(bytes, 2), mapOf("snippets" to 1), 1) as CheckOutcome.Available).updates.single()
        val error = expectError { lenientEngine().download(update) }
        assertThat(error).isInstanceOf(UpdateError.InvalidContent::class.java)
        assertThat(error.message).contains("schemaVersion")
    }

    @Test fun packageVersionMustMatchManifest() = runTest {
        val bytes = json.encodeToString(snippetBundle(3)).toByteArray()
        serveAll("/snippets.json" to bytes.toString(Charsets.UTF_8))
        val update = (UpdatePlanner.plan(manifestFor(bytes, 2), mapOf("snippets" to 1), 1) as CheckOutcome.Available).updates.single()
        val error = expectError { lenientEngine().download(update) }
        assertThat(error).isInstanceOf(UpdateError.InvalidContent::class.java)
    }

    @Test fun httpErrorIsReportedNotTreatedAsUpToDate() = runTest {
        serveAll() // everything is 404
        val error = expectError { lenientEngine().fetchManifest(server.url("/manifest.json").toString()) }
        assertThat(error).isEqualTo(UpdateError.Http(404))
    }

    @Test fun unreachableServerIsOffline() = runTest {
        val url = server.url("/manifest.json").toString()
        server.shutdown()
        val error = expectError { lenientEngine().fetchManifest(url) }
        assertThat(error).isEqualTo(UpdateError.Offline)
    }

    @Test fun oversizedManifestBodyIsRejected() = runTest {
        serveAll("/manifest.json" to " ".repeat((MAX_MANIFEST_BYTES + 10).toInt()))
        val error = expectError { lenientEngine().fetchManifest(server.url("/manifest.json").toString()) }
        assertThat(error).isEqualTo(UpdateError.TooLarge)
    }
}

class UpdatePlannerTest {
    private fun pkg(id: String, version: Int) = UpdatePackageDto(id, version, "https://example.com/$id.json", "a".repeat(64), 100, "d-$id")
    private fun manifest(vararg p: UpdatePackageDto, minApp: Int = 1) = UpdateManifestDto(1, "2026-01-01", minApp, p.toList())

    @Test fun olderAndEqualVersionsAreIgnored() {
        val outcome = UpdatePlanner.plan(
            manifest(pkg("opcodes", 1), pkg("lessons", 2), pkg("snippets", 4)),
            installed = mapOf("opcodes" to 1, "lessons" to 3, "snippets" to 4),
            appVersionCode = 1,
        )
        assertThat(outcome).isEqualTo(CheckOutcome.UpToDate("2026-01-01"))
    }

    @Test fun onlyStrictlyNewerPackagesAreOffered() {
        val outcome = UpdatePlanner.plan(
            manifest(pkg("opcodes", 2), pkg("lessons", 1), pkg("snippets", 5)),
            installed = mapOf("opcodes" to 1, "lessons" to 1, "snippets" to 4),
            appVersionCode = 1,
        ) as CheckOutcome.Available
        assertThat(outcome.updates.map { it.packageId }).containsExactly("opcodes", "snippets").inOrder()
        assertThat(outcome.updates.first().installedVersion).isEqualTo(1)
    }

    @Test fun missingInstalledPackageCountsAsVersionZero() {
        val outcome = UpdatePlanner.plan(manifest(pkg("lessons", 1)), emptyMap(), 1) as CheckOutcome.Available
        assertThat(outcome.updates.single().installedVersion).isNull()
    }

    @Test fun manifestNeedingNewerAppIsReported() {
        val outcome = UpdatePlanner.plan(manifest(pkg("lessons", 9), minApp = 7), emptyMap(), appVersionCode = 3)
        assertThat(outcome).isEqualTo(CheckOutcome.AppUpdateRequired(7))
    }
}

class ManifestValidatorTest {
    private fun pkg(id: String = "opcodes", version: Int = 1, sha: String = "a".repeat(64), size: Long = 100, url: String = "https://example.com/x.json") =
        UpdatePackageDto(id, version, url, sha, size, "")

    private fun validate(vararg p: UpdatePackageDto): UpdateError? =
        (runCatching { ManifestValidator.validate(UpdateManifestDto(1, "", 1, p.toList())) }.exceptionOrNull() as? UpdateException)?.error

    @Test fun acceptsValidManifest() {
        assertThat(validate(pkg(), pkg("lessons"), pkg("snippets"))).isNull()
    }

    @Test fun rejectsUnknownDuplicateAndBadFields() {
        assertThat(validate(pkg("kernel"))).isInstanceOf(UpdateError.InvalidManifest::class.java)
        assertThat(validate(pkg(), pkg())).isInstanceOf(UpdateError.InvalidManifest::class.java)
        assertThat(validate(pkg(sha = "XYZ"))).isInstanceOf(UpdateError.InvalidManifest::class.java)
        assertThat(validate(pkg(sha = "A".repeat(64)))).isInstanceOf(UpdateError.InvalidManifest::class.java)
        assertThat(validate(pkg(size = 0))).isInstanceOf(UpdateError.InvalidManifest::class.java)
        assertThat(validate(pkg(version = 0))).isInstanceOf(UpdateError.InvalidManifest::class.java)
    }

    @Test fun rejectsInsecureAndCredentialUrls() {
        assertThat(validate(pkg(url = "http://example.com/x.json"))).isInstanceOf(UpdateError.InsecureUrl::class.java)
        assertThat(validate(pkg(url = "ftp://example.com/x.json"))).isInstanceOf(UpdateError.InsecureUrl::class.java)
        assertThat(validate(pkg(url = "https://user:pw@example.com/x.json"))).isInstanceOf(UpdateError.InsecureUrl::class.java)
        assertThat(validate(pkg(url = "not a url"))).isInstanceOf(UpdateError.InsecureUrl::class.java)
    }

    @Test fun manifestUrlProblemMessages() {
        assertThat(ManifestValidator.manifestUrlProblem("https://example.com/manifest.json")).isNull()
        assertThat(ManifestValidator.manifestUrlProblem("http://example.com/manifest.json")).isNotNull()
        assertThat(ManifestValidator.manifestUrlProblem("example.com")).isNotNull()
    }
}
