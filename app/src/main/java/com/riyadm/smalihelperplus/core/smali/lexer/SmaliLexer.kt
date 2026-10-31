package com.riyadm.smalihelperplus.core.smali.lexer

/** Token types produced by [SmaliLexer]. */
enum class TokenType {
    /** `.class`, `.method`, `.end` ... (the leading dot is part of the text). */
    DIRECTIVE,

    /** Mnemonics, access flags, names, descriptors, member references and any other bare word. */
    WORD,

    /** `v0`, `p1` ... */
    REGISTER,

    /** `:cond_0` (the leading colon is part of the text). */
    LABEL,
    STRING,
    CHAR,
    NUMBER,
    COMMA,
    LBRACE,
    RBRACE,
    DOTDOT,
    EQUALS,
    COMMENT,

    /** Unterminated string or char literal. */
    ERROR,
}

/**
 * A token. [line] is 1-based; [column] and [endColumn] are 0-based offsets inside the line (end exclusive);
 * [offset] is the absolute offset of the first character in the whole text.
 */
data class Token(
    val type: TokenType,
    val text: String,
    val line: Int,
    val column: Int,
    val endColumn: Int,
    val offset: Int,
) {
    val endOffset: Int get() = offset + (endColumn - column)
}

/** All tokens of one source line. [text] is the raw line without the line terminator. */
data class LexedLine(val number: Int, val text: String, val startOffset: Int, val tokens: List<Token>) {
    val codeTokens: List<Token> get() = tokens.filter { it.type != TokenType.COMMENT }
    val comment: Token? get() = tokens.lastOrNull()?.takeIf { it.type == TokenType.COMMENT }
    val isBlank: Boolean get() = tokens.isEmpty()
    val indentation: Int get() = text.length - text.trimStart().length
}

/**
 * Line-oriented smali lexer. Smali is line based (string literals cannot span lines), so every line is tokenized
 * independently. The lexer never throws: malformed input yields [TokenType.ERROR] tokens.
 */
object SmaliLexer {
    private val REGISTER = Regex("[vp][0-9]+")
    private val INTEGER = Regex("-?(0[xX][0-9a-fA-F]+|[0-9]+)[lLsStT]?")
    private val FLOAT = Regex("-?(([0-9]+\\.[0-9]*|\\.[0-9]+|[0-9]+)([eE][+-]?[0-9]+)?[fFdD]?|(Infinity|NaN)[fFdD]?)")

    /** Splits text into lines, normalising `\r\n` and `\r` line terminators. */
    fun splitLines(text: String): List<String> = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')

    fun lex(text: String): List<LexedLine> {
        val lines = splitLines(text)
        val out = ArrayList<LexedLine>(lines.size)
        var offset = 0
        lines.forEachIndexed { index, line ->
            out += LexedLine(index + 1, line, offset, lexLine(line, index + 1, offset))
            offset += line.length
            if (text.getOrNull(offset) == '\r' && text.getOrNull(offset + 1) == '\n') offset += 2
            else if (offset < text.length) offset++
        }
        return out
    }

    /** Flattened token list of [text]. */
    fun tokenize(text: String): List<Token> = lex(text).flatMap { it.tokens }

    fun lexLine(line: String, lineNumber: Int, lineOffset: Int = 0): List<Token> {
        val tokens = ArrayList<Token>()
        var i = 0
        val n = line.length
        fun emit(type: TokenType, start: Int, end: Int) {
            tokens += Token(type, line.substring(start, end), lineNumber, start, end, lineOffset + start)
        }
        while (i < n) {
            val c = line[i]
            when {
                c == ' ' || c == '\t' || c == '\u000c' -> i++
                c == '#' -> { emit(TokenType.COMMENT, i, n); i = n }
                c == ',' -> { emit(TokenType.COMMA, i, i + 1); i++ }
                c == '{' -> { emit(TokenType.LBRACE, i, i + 1); i++ }
                c == '}' -> { emit(TokenType.RBRACE, i, i + 1); i++ }
                c == '=' -> { emit(TokenType.EQUALS, i, i + 1); i++ }
                c == '.' && i + 1 < n && line[i + 1] == '.' -> { emit(TokenType.DOTDOT, i, i + 2); i += 2 }
                c == '"' || c == '\'' -> {
                    var j = i + 1
                    var closed = false
                    while (j < n) {
                        val ch = line[j]
                        if (ch == '\\') { j += 2; continue }
                        if (ch == c) { closed = true; j++; break }
                        j++
                    }
                    if (j > n) j = n
                    val type = when {
                        !closed -> TokenType.ERROR
                        c == '"' -> TokenType.STRING
                        else -> TokenType.CHAR
                    }
                    emit(type, i, j)
                    i = j
                }
                else -> {
                    var j = i
                    while (j < n) {
                        val ch = line[j]
                        if (ch == ' ' || ch == '\t' || ch == ',' || ch == '{' || ch == '}' || ch == '"' ||
                            ch == '\'' || ch == '#' || ch == '=' || ch == '\u000c'
                        ) break
                        // `v0..v3` without spaces: stop before ".." once the word is a register or label.
                        if (ch == '.' && j + 1 < n && line[j + 1] == '.' && j > i) {
                            val soFar = line.substring(i, j)
                            if (REGISTER.matches(soFar) || soFar.startsWith(":")) break
                        }
                        j++
                    }
                    if (j == i) j = i + 1
                    val word = line.substring(i, j)
                    emit(classify(word, tokens.isEmpty()), i, j)
                    i = j
                }
            }
        }
        return tokens
    }

    private fun classify(word: String, first: Boolean): TokenType = when {
        word.startsWith(".") && word.length > 1 && word[1].isLetter() -> TokenType.DIRECTIVE
        word.startsWith(":") && word.length > 1 -> TokenType.LABEL
        REGISTER.matches(word) -> TokenType.REGISTER
        !first && (INTEGER.matches(word) || FLOAT.matches(word)) -> TokenType.NUMBER
        first && INTEGER.matches(word) -> TokenType.NUMBER
        else -> TokenType.WORD
    }

    fun isRegister(text: String): Boolean = REGISTER.matches(text)
    fun isIntegerLiteral(text: String): Boolean = INTEGER.matches(text)
    fun isFloatLiteral(text: String): Boolean = FLOAT.matches(text) && !INTEGER.matches(text)
}
