package com.riyadm.smalihelperplus.data.ai

/** Quick actions offered as chips above the input field. */
enum class AiQuickAction(val label: String, val instruction: String, val needsCode: Boolean) {
    EXPLAIN_CODE(
        "Explain code",
        "Explain what the following Smali code does, step by step. Mention which registers hold which values.",
        needsCode = true,
    ),
    EXPLAIN_ERROR(
        "Explain error",
        "Explain the following error or verifier/assembler message in the context of Smali/Dalvik, list the likely causes and how to check each one.",
        needsCode = false,
    ),
    SUGGEST_FIX(
        "Suggest fix",
        "Find problems in the following Smali code (wrong register counts, type mismatches, missing returns, bad descriptors, invalid branch targets) and suggest a corrected version.",
        needsCode = true,
    ),
    REGISTER_USAGE(
        "Describe register usage",
        "Describe how the registers are used in the following Smali method: which are locals, which are parameters (including p0/this), and how long each value lives.",
        needsCode = true,
    ),
    GENERATE_EXAMPLE(
        "Generate example",
        "Write a small, correct Smali example for the following request, followed by a short explanation. Keep register counts consistent with .registers or .locals.",
        needsCode = false,
    ),
    EXPLAIN_METHOD(
        "Explain method",
        "Explain the purpose of the following Smali method, its parameters and return value, and describe equivalent Java in plain words.",
        needsCode = true,
    ),
}

object AiPrompts {
    /** Smali-focused system prompt. Generic on purpose: nothing about the app or the user is included. */
    const val SYSTEM_PROMPT: String =
        "You are an assistant embedded in an educational Android app about Smali and Dalvik bytecode. " +
            "Help the user read, understand and write Smali for apps they own or are authorised to inspect. " +
            "Be accurate and concise. Use the Dalvik instruction set as documented by the Android Open Source Project; " +
            "do not invent opcodes, syntax or behaviour, and say so when you are unsure. " +
            "Always put Smali or other code in fenced code blocks (```smali). " +
            "Explain register usage (vN locals, pN parameters, p0 = this for instance methods) where relevant. " +
            "Describe Java equivalents only as illustrative pseudocode, not as exact decompilation. " +
            "Decline requests to help bypass licensing, steal credentials, or attack apps the user is not authorised to analyse."

    /** Builds the user message sent to the API from the optional action, the user's text and the attached code. */
    fun buildUserMessage(action: AiQuickAction?, text: String, code: String?): String = buildString {
        action?.let { append(it.instruction) }
        val trimmedText = text.trim()
        if (trimmedText.isNotEmpty()) {
            if (isNotEmpty()) append("\n\n")
            append(trimmedText)
        }
        val trimmedCode = code?.trim('\n', '\r')
        if (!trimmedCode.isNullOrBlank()) {
            if (isNotEmpty()) append("\n\n")
            append("```smali\n").append(trimmedCode).append("\n```")
        }
    }

    private val CODE_LINE = Regex(
        """^\s*(\.(class|super|method|end method|field|registers|locals|line|param|prologue|annotation|end annotation|implements|source|catch|catchall|packed-switch|sparse-switch|array-data)\b|(invoke-(virtual|direct|static|super|interface)|const(-string|-class|/\d+|/high16|-wide)?|move(-result|-object|-exception)?|return(-void|-object|-wide)?|iget|iput|sget|sput|new-instance|new-array|if-[a-z]+|goto|check-cast|instance-of|throw)\b)""",
        RegexOption.MULTILINE,
    )

    /** True if [text] contains a fenced code block or lines that look like Smali. Used to decide when consent is mandatory. */
    fun requiresCodeConsent(currentHasCode: Boolean, previousContent: List<String>): Boolean =
        currentHasCode || previousContent.any(::looksLikeCode)

    fun looksLikeCode(text: String): Boolean = text.contains("```") || CODE_LINE.containsMatchIn(text)
}

sealed interface AiSegment {
    data class Text(val text: String) : AiSegment
    data class Code(val code: String, val language: String) : AiSegment
}

/** Splits an assistant answer into prose and fenced code blocks (unclosed fences count as code until the end). */
object AiMessageParser {
    fun parse(markdown: String): List<AiSegment> {
        val segments = mutableListOf<AiSegment>()
        val text = StringBuilder()
        val code = StringBuilder()
        var inCode = false
        var language = ""

        fun flushText() {
            val t = text.toString().trim('\n')
            if (t.isNotBlank()) segments += AiSegment.Text(t)
            text.clear()
        }

        for (line in markdown.lines()) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```")) {
                if (inCode) {
                    segments += AiSegment.Code(code.toString().trimEnd('\n'), language)
                    code.clear()
                    inCode = false
                } else {
                    flushText()
                    language = trimmed.removePrefix("```").trim().lowercase()
                    inCode = true
                }
            } else if (inCode) {
                code.append(line).append('\n')
            } else {
                text.append(line).append('\n')
            }
        }
        if (inCode) {
            if (code.isNotBlank()) segments += AiSegment.Code(code.toString().trimEnd('\n'), language)
        } else {
            flushText()
        }
        return segments
    }
}
