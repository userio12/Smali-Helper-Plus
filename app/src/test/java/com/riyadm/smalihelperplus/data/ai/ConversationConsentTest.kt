package com.riyadm.smalihelperplus.data.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConversationConsentTest {
    @Test fun plainFollowupStillNeedsConsentForEarlierCode() {
        assertThat(AiPrompts.requiresCodeConsent(false, listOf("```smali\nreturn-void\n```", "Why?"))).isTrue()
    }
    @Test fun assistantCodeAlsoNeedsConsentWhenResent() {
        assertThat(AiPrompts.requiresCodeConsent(false, listOf(".method public f()V"))).isTrue()
    }
    @Test fun attachmentNeedsConsentEvenWithoutCodeDetection() {
        assertThat(AiPrompts.requiresCodeConsent(true, emptyList())).isTrue()
    }
    @Test fun ordinaryConversationCanUsePlainTextPreference() {
        assertThat(AiPrompts.requiresCodeConsent(false, listOf("Hello", "Explain registers"))).isFalse()
    }
}
