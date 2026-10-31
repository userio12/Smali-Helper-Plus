package com.riyadm.smalihelperplus.data.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AiPromptsTest {
    @Test fun buildsMessageFromActionTextAndCode() {
        val msg = AiPrompts.buildUserMessage(AiQuickAction.EXPLAIN_CODE, " why? ", "\n.method foo()V\n.end method\n")
        assertThat(msg).startsWith(AiQuickAction.EXPLAIN_CODE.instruction)
        assertThat(msg).contains("why?")
        assertThat(msg).endsWith("```smali\n.method foo()V\n.end method\n```")
    }

    @Test fun plainQuestionHasNoFence() {
        assertThat(AiPrompts.buildUserMessage(null, "What is v0?", null)).isEqualTo("What is v0?")
    }

    @Test fun detectsCodeLikeText() {
        assertThat(AiPrompts.looksLikeCode("see ```x```")).isTrue()
        assertThat(AiPrompts.looksLikeCode("    invoke-virtual {v0}, Ljava/lang/Object;->toString()Ljava/lang/String;")).isTrue()
        assertThat(AiPrompts.looksLikeCode("first line\n.method public foo()V")).isTrue()
        assertThat(AiPrompts.looksLikeCode("const/4 v0, 0x1")).isTrue()
        assertThat(AiPrompts.looksLikeCode("What does the move-result opcode do?")).isFalse()
        assertThat(AiPrompts.looksLikeCode("How do I return a value?")).isFalse()
    }

    @Test fun systemPromptHasNoSecretsOrUserData() {
        assertThat(AiPrompts.SYSTEM_PROMPT).contains("Smali")
        assertThat(AiPrompts.SYSTEM_PROMPT.lowercase()).doesNotContain("api key")
    }
}

class AiMessageParserTest {
    @Test fun splitsProseAndCode() {
        val segments = AiMessageParser.parse("Intro line\n\n```smali\nconst/4 v0, 0x1\nreturn v0\n```\nAfter text")
        assertThat(segments).containsExactly(
            AiSegment.Text("Intro line"),
            AiSegment.Code("const/4 v0, 0x1\nreturn v0", "smali"),
            AiSegment.Text("After text"),
        ).inOrder()
    }

    @Test fun unclosedFenceIsTreatedAsCode() {
        val segments = AiMessageParser.parse("Here:\n```java\nint a = 1;")
        assertThat(segments).containsExactly(AiSegment.Text("Here:"), AiSegment.Code("int a = 1;", "java")).inOrder()
    }

    @Test fun plainTextOnly() {
        assertThat(AiMessageParser.parse("just words")).containsExactly(AiSegment.Text("just words"))
        assertThat(AiMessageParser.parse("")).isEmpty()
    }

    @Test fun fenceWithoutLanguage() {
        val segments = AiMessageParser.parse("```\nx\n```")
        assertThat(segments).containsExactly(AiSegment.Code("x", ""))
    }
}

class AiHistoryTest {
    private fun u(s: String) = AiMessage(ChatRole.USER, s)
    private fun a(s: String) = AiMessage(ChatRole.ASSISTANT, s)

    @Test fun keepsOnlyNewestMessagesAndStartsWithUser() {
        val history = (1..20).flatMap { listOf(u("q$it"), a("a$it")) }
        val trimmed = AiAssistantRepository.trimHistory(history)
        assertThat(trimmed.size).isAtMost(AiAssistantRepository.MAX_HISTORY_MESSAGES)
        assertThat(trimmed.first().role).isEqualTo(ChatRole.USER)
        assertThat(trimmed.last()).isEqualTo(a("a20"))
    }

    @Test fun alwaysKeepsLatestMessageEvenIfHuge() {
        val huge = "x".repeat(AiAssistantRepository.MAX_HISTORY_CHARS + 10)
        val trimmed = AiAssistantRepository.trimHistory(listOf(u("old"), a("old answer"), u(huge)))
        assertThat(trimmed).containsExactly(u(huge))
    }
}

class AesGcmBoxTest {
    private val key = AesGcmBox.randomSoftwareKey()
    private val box = AesGcmBox { key }

    @Test fun roundTrips() {
        val sealed = box.encrypt("sk-test-key".toByteArray())
        assertThat(String(sealed.ciphertext)).doesNotContain("sk-test")
        assertThat(sealed.iv).hasLength(12)
        assertThat(String(box.decrypt(sealed))).isEqualTo("sk-test-key")
    }

    @Test fun tamperedCiphertextFails() {
        val sealed = box.encrypt("secret".toByteArray())
        sealed.ciphertext[0] = (sealed.ciphertext[0].toInt() xor 1).toByte()
        val result = runCatching { box.decrypt(sealed) }
        assertThat(result.isFailure).isTrue()
    }

    @Test fun wrongKeyFails() {
        val sealed = box.encrypt("secret".toByteArray())
        val other = AesGcmBox { AesGcmBox.randomSoftwareKey() }
        assertThat(runCatching { other.decrypt(sealed) }.isFailure).isTrue()
    }
}
