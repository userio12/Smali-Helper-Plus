package com.riyadm.smalihelperplus.core.content

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContentValidationRegressionTest {
    @Test fun emptySnippetPackageCannotEraseBuiltIns() {
        assertThat(ContentValidator.validateSnippets(SnippetBundleDto(1, 2, "", emptyList())))
            .isInstanceOf(ContentValidator.Result.Invalid::class.java)
    }
    @Test fun blankSnippetIdentityIsRejected() {
        val bundle = SnippetBundleDto(1, 2, "", listOf(SnippetDto("", "Title", "", "Methods", "return-void")))
        assertThat(ContentValidator.validateSnippets(bundle)).isInstanceOf(ContentValidator.Result.Invalid::class.java)
    }
    @Test fun duplicateQuizIdsAreRejected() {
        val question = QuizQuestionDto("q", "Question", options = listOf("a", "b"), correctIndex = 0, explanation = "Reason")
        val lesson = LessonDto("l", "beginner", 0, "Title", "Summary", 5,
            listOf(LessonSectionDto("text", body = "Body")), quiz = listOf(question, question))
        assertThat(ContentValidator.validateLessons(LessonBundleDto(1, 2, "", listOf(lesson))))
            .isInstanceOf(ContentValidator.Result.Invalid::class.java)
    }
}
