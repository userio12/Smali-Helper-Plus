package com.riyadm.smalihelperplus.data.snippets

import com.google.common.truth.Truth.assertThat
import com.riyadm.smalihelperplus.core.content.ContentValidator
import com.riyadm.smalihelperplus.core.content.SnippetBundleDto
import com.riyadm.smalihelperplus.domain.tools.SmaliSnippetChecker
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File

class BundledSnippetsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun snippetsFile(): File {
        val candidates = listOf(
            "src/main/assets/content/snippets.json",
            "app/src/main/assets/content/snippets.json",
            "../app/src/main/assets/content/snippets.json",
        )
        return candidates.map { File(it) }.firstOrNull { it.isFile }
            ?: error("snippets.json not found from ${File(".").absoluteFile}")
    }

    private val bundle: SnippetBundleDto by lazy { json.decodeFromString(snippetsFile().readText()) }

    @Test fun headerMatchesTheContractVersions() {
        assertThat(bundle.schemaVersion).isEqualTo(1)
        assertThat(bundle.contentVersion).isEqualTo(1)
        assertThat(bundle.generatedAt).isEqualTo("2026-10-30")
    }

    @Test fun passesContentValidator() {
        val result = ContentValidator.validateSnippets(bundle)
        assertThat(result).isEqualTo(ContentValidator.Result.Valid)
    }

    @Test fun hasAtLeastFortySnippets() {
        assertThat(bundle.snippets.size).isAtLeast(40)
    }

    @Test fun coversTheRequiredCollections() {
        val collections = bundle.snippets.map { it.collection }.toSet()
        assertThat(collections).containsAtLeast(
            "Classes", "Methods", "Strings", "Arrays", "Conditions", "Loops", "Method calls", "Exceptions",
        )
        for (c in collections) assertThat(bundle.snippets.count { it.collection == c }).isAtLeast(4)
    }

    @Test fun everySnippetHasMetadata() {
        for (s in bundle.snippets) {
            assertThat(s.id).matches("[a-z0-9]+(-[a-z0-9]+)*")
            assertThat(s.title.trim()).isNotEmpty()
            assertThat(s.description.trim().length).isAtLeast(20)
            assertThat(s.tags).isNotEmpty()
            assertThat(s.code.trim()).isNotEmpty()
        }
        assertThat(bundle.snippets.map { it.title }.toSet()).hasSize(bundle.snippets.size)
    }

    @Test fun everySnippetIsStructurallyValidSmali() {
        val problems = bundle.snippets.flatMap { s -> SmaliSnippetChecker.check(s.code).map { "${s.id}: $it" } }
        assertThat(problems).isEmpty()
    }

    @Test fun checkerCatchesBrokenSnippets() {
        val undefinedLabel = ".method public static a()V\n    .locals 0\n    goto :nowhere\n.end method"
        assertThat(SmaliSnippetChecker.check(undefinedLabel).joinToString()).contains("undefined label")
        val badRegister = ".method public static a()V\n    .locals 1\n    const/4 v3, 0x1\n    return-void\n.end method"
        assertThat(SmaliSnippetChecker.check(badRegister).joinToString()).contains("outside frame")
        val unknown = ".method public static a()V\n    .locals 0\n    frobnicate v0\n    return-void\n.end method"
        assertThat(SmaliSnippetChecker.check(unknown).joinToString()).contains("unknown mnemonic")
        val missingEnd = ".method public static a()V\n    .locals 0\n    return-void"
        assertThat(SmaliSnippetChecker.check(missingEnd).joinToString()).contains(".end method")
        val highRegister = ".method public static a(IIII)V\n    .locals 14\n    move v0, p3\n    return-void\n.end method"
        assertThat(SmaliSnippetChecker.check(highRegister).joinToString()).contains("cannot address")
        val widePair = ".method public static a()J\n    .locals 1\n    const-wide/16 v0, 0x1\n    return-wide v0\n.end method"
        assertThat(SmaliSnippetChecker.check(widePair).joinToString()).contains("wide register pair")
        val good = ".method public static a(J)J\n    .locals 0\n    return-wide p0\n.end method"
        assertThat(SmaliSnippetChecker.check(good)).isEmpty()
    }

    @Test fun mentionsEveryInvokeKindAndKeyConstructs() {
        val all = bundle.snippets.joinToString("\n") { it.code }
        for (needle in listOf(
            "invoke-virtual ", "invoke-static ", "invoke-direct ", "invoke-super ", "invoke-interface ", "invoke-static/range",
            "monitor-enter", "monitor-exit", ".catch ", ".catchall", "sparse-switch", "packed-switch", "fill-array-data",
            "Landroid/util/Log;->d", "new-array", "filled-new-array", "cmp-long", "move-exception", "<clinit>",
        )) {
            assertThat(all).contains(needle)
        }
    }
}
