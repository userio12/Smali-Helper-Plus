package com.riyadm.smalihelperplus.core.content

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File

/** Validates the bundled lessons asset exactly as the app would install it. */
class BundledLessonsTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    private val bundle: LessonBundleDto by lazy {
        json.decodeFromString<LessonBundleDto>(locateAsset().readText())
    }

    /** Gradle runs unit tests from the module directory, IDEs sometimes from the project root. */
    private fun locateAsset(): File {
        val relative = "src/main/assets/content/lessons.json"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            listOf(File(dir, relative), File(dir, "app/$relative")).firstOrNull { it.isFile }?.let { return it }
            dir = dir.parentFile
        }
        error("lessons.json not found from ${System.getProperty("user.dir")}")
    }

    @Test
    fun passesContentValidator() {
        val result = ContentValidator.validateLessons(bundle)
        assertThat(result).isEqualTo(ContentValidator.Result.Valid)
    }

    @Test
    fun versionsAndMetadata() {
        assertThat(bundle.schemaVersion).isEqualTo(CONTENT_SCHEMA_VERSION)
        assertThat(bundle.contentVersion).isAtLeast(1)
        assertThat(bundle.generatedAt).isEqualTo("2026-10-30")
    }

    @Test
    fun has28LessonsSplitTenNineNine() {
        assertThat(bundle.lessons).hasSize(28)
        val byLevel = bundle.lessons.groupingBy { it.level }.eachCount()
        assertThat(byLevel).containsExactly("beginner", 10, "intermediate", 9, "advanced", 9)
        assertThat(bundle.lessons.map { it.order }.sorted()).isEqualTo((1..28).toList())
        bundle.lessons.forEach { l ->
            val expected = when (l.order) {
                in 1..10 -> "beginner"
                in 11..19 -> "intermediate"
                else -> "advanced"
            }
            assertThat(l.level).isEqualTo(expected)
        }
    }

    @Test
    fun idsAreUnique() {
        assertThat(bundle.lessons.map { it.id }.toSet()).hasSize(bundle.lessons.size)
        val questionIds = bundle.lessons.flatMap { l -> l.quiz.map { it.id } }
        assertThat(questionIds.toSet()).hasSize(questionIds.size)
        val exerciseIds = bundle.lessons.flatMap { l -> l.exercises.map { it.id } }
        assertThat(exerciseIds.toSet()).hasSize(exerciseIds.size)
        assertThat(bundle.flashcards.map { it.id }.toSet()).hasSize(bundle.flashcards.size)
    }

    @Test
    fun everyLessonIsComplete() {
        bundle.lessons.forEach { l ->
            assertThat(l.summary).isNotEmpty()
            assertThat(l.estimatedMinutes).isGreaterThan(0)
            assertThat(l.sections.size).isIn(4..8)
            assertThat(l.sections.map { it.type }).contains("code")
            assertThat(l.exercises.size).isAtLeast(2)
            l.exercises.forEach { assertThat(it.solution).isNotEmpty() }
            assertThat(l.quiz.size).isIn(4..6)
            l.quiz.forEach { q ->
                assertThat(q.correctIndex).isIn(q.options.indices)
                assertThat(q.explanation).isNotEmpty()
                assertThat(q.options.toSet()).hasSize(q.options.size)
            }
            assertThat(l.relatedOpcodes).isNotEmpty()
        }
    }

    @Test
    fun relatedOpcodesAreSmaliMnemonics() {
        val mnemonic = Regex("^[a-z]+(-[a-z0-9]+)*(/[a-z0-9]+)?$")
        bundle.lessons.flatMap { it.relatedOpcodes }.forEach { assertThat(it).matches(mnemonic.pattern) }
    }

    @Test
    fun atLeastSixtyFlashcardsAcrossTopics() {
        assertThat(bundle.flashcards.size).isAtLeast(60)
        assertThat(bundle.flashcards.map { it.topic }.toSet().size).isAtLeast(5)
        bundle.flashcards.forEach {
            assertThat(it.front).isNotEmpty()
            assertThat(it.back).isNotEmpty()
        }
    }
}
