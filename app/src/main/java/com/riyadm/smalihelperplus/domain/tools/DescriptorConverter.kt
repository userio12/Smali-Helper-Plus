package com.riyadm.smalihelperplus.domain.tools

/** A parsed type: its descriptor, readable Java name and shape. */
data class TypeInfo(
    /** Smali / DEX descriptor, e.g. "[Ljava/lang/String;". */
    val descriptor: String,
    /** Readable Java name, e.g. "java.lang.String[]". */
    val java: String,
    /** Base type character: one of Z B S C I J F D V or 'L' for classes. */
    val base: Char,
    val arrayDimensions: Int,
) {
    /** Number of 32-bit register words: 2 for a plain long/double, 0 for void, otherwise 1. */
    val words: Int
        get() = when {
            arrayDimensions == 0 && (base == 'J' || base == 'D') -> 2
            arrayDimensions == 0 && base == 'V' -> 0
            else -> 1
        }
    val isWide: Boolean get() = words == 2
}

data class ParamInfo(val type: TypeInfo, val name: String?)

/** A method prototype: parameters plus return type. */
data class MethodProto(
    val params: List<ParamInfo>,
    val returnType: TypeInfo,
) {
    val descriptor: String get() = "(" + params.joinToString("") { it.type.descriptor } + ")" + returnType.descriptor
    val paramWords: Int get() = params.sumOf { it.type.words }
}

data class FieldRef(val owner: TypeInfo, val name: String, val type: TypeInfo) {
    val smali: String get() = "${owner.descriptor}->$name:${type.descriptor}"
    val java: String get() = "${type.java} ${owner.java}.$name"
}

data class MethodRef(val owner: TypeInfo, val name: String, val proto: MethodProto) {
    val smali: String get() = "${owner.descriptor}->$name${proto.descriptor}"
    val java: String
        get() = "${proto.returnType.java} ${owner.java}.$name(" + proto.params.joinToString(", ") { it.type.java } + ")"
}

/** Thrown internally by the parsers; converted to [DescriptorResult.Failure] at the public boundary. */
class DescriptorException(message: String, val position: Int) : IllegalArgumentException(message)

/** Outcome of a conversion on arbitrary user input. */
sealed interface DescriptorResult {
    data class Success(
        val kind: Kind,
        /** Labelled output lines (label, value), e.g. ("Descriptor", "[I"). */
        val lines: List<Pair<String, String>>,
        val notes: List<String>,
    ) : DescriptorResult

    /** [position] is a 0-based character index into the (untrimmed) input, or -1 when unknown. */
    data class Failure(val message: String, val position: Int) : DescriptorResult

    enum class Kind(val label: String) {
        JAVA_TYPE("Java type"),
        DESCRIPTOR("Type descriptor"),
        JAVA_METHOD("Java method signature"),
        PROTO("Method prototype"),
        METHOD_REF("Method reference"),
        FIELD_REF("Field reference"),
    }
}

/** Converts between Java type names / method signatures and Dalvik descriptors. Pure Kotlin. */
object DescriptorConverter {

    private val PRIMITIVES = mapOf(
        "boolean" to 'Z', "byte" to 'B', "short" to 'S', "char" to 'C', "int" to 'I',
        "long" to 'J', "float" to 'F', "double" to 'D', "void" to 'V',
    )
    private val PRIMITIVE_NAMES = PRIMITIVES.entries.associate { it.value to it.key }

    private val MODIFIERS = setOf(
        "public", "private", "protected", "static", "final", "abstract", "synchronized", "native", "strictfp",
        "default", "volatile", "transient", "synthetic", "bridge", "varargs", "constructor",
    )

    // ---------------------------------------------------------------- descriptors -> Java

    /** Parses one descriptor starting at [start]; returns the type and the index after it. */
    fun parseDescriptorAt(s: String, start: Int, allowVoid: Boolean = true, offset: Int = 0): Pair<TypeInfo, Int> {
        var i = start
        var dims = 0
        while (i < s.length && s[i] == '[') {
            dims++; i++
        }
        if (dims > 255) throw DescriptorException("Arrays can have at most 255 dimensions", offset + start)
        if (i >= s.length) {
            throw DescriptorException(if (dims > 0) "Array descriptor is missing its element type" else "Empty descriptor", offset + i)
        }
        val c = s[i]
        val info: TypeInfo
        when {
            c == 'L' -> {
                val semi = s.indexOf(';', i)
                if (semi == -1) throw DescriptorException("Class descriptor is missing the closing ';'", offset + s.length)
                val body = s.substring(i + 1, semi)
                validateClassBody(body, offset + i + 1)
                val java = binaryToJava(body)
                info = TypeInfo(s.substring(start, semi + 1), java + "[]".repeat(dims), 'L', dims)
                i = semi + 1
            }
            c in PRIMITIVE_NAMES -> {
                if (c == 'V' && dims > 0) throw DescriptorException("An array of void is not allowed", offset + i)
                if (c == 'V' && !allowVoid) throw DescriptorException("void is only allowed as a return type", offset + i)
                info = TypeInfo(s.substring(start, i + 1), PRIMITIVE_NAMES.getValue(c) + "[]".repeat(dims), c, dims)
                i++
            }
            else -> throw DescriptorException("Unexpected character '$c': expected a primitive (ZBSCIJFDV), 'L' or '['", offset + i)
        }
        return info to i
    }

    private fun validateClassBody(body: String, pos: Int) {
        if (body.isEmpty()) throw DescriptorException("Class name is empty", pos)
        var p = pos
        for (segment in body.split('/')) {
            if (segment.isEmpty()) throw DescriptorException("Empty package or class name segment (stray '/')", p)
            for ((k, ch) in segment.withIndex()) {
                if (ch == '.' || ch == ';' || ch == '[' || ch == '<' || ch == '>' || ch.isWhitespace()) {
                    throw DescriptorException("Character '$ch' is not allowed in a class name", p + k)
                }
            }
            p += segment.length + 1
        }
    }

    /** `com/a/Outer$Inner` -> `com.a.Outer.Inner`; anonymous / numeric `$1` parts keep their `$`. */
    fun binaryToJava(body: String): String {
        val dotted = body.replace('/', '.')
        val sb = StringBuilder()
        for ((i, ch) in dotted.withIndex()) {
            val prev = dotted.getOrNull(i - 1)
            val next = dotted.getOrNull(i + 1)
            if (ch == '$' && prev != null && prev != '.' && prev != '$' && next != null && !next.isDigit() && next != '.') sb.append('.') else sb.append(ch)
        }
        return sb.toString()
    }

    /** Parses a single, complete type descriptor. */
    fun parseTypeDescriptor(input: String, allowVoid: Boolean = true): TypeInfo {
        val s = input.trim()
        val lead = input.length - input.trimStart().length
        if (s.isEmpty()) throw DescriptorException("Enter a type descriptor", 0)
        val (info, end) = parseDescriptorAt(s, 0, allowVoid, lead)
        if (end != s.length) throw DescriptorException("Unexpected text after the descriptor", lead + end)
        return info
    }

    /** Parses a prototype like `(ILjava/lang/String;)V`. */
    fun parseProto(input: String, offset: Int = 0): MethodProto {
        val s = input
        if (!s.startsWith("(")) throw DescriptorException("A prototype must start with '('", offset)
        val params = ArrayList<ParamInfo>()
        var i = 1
        while (true) {
            if (i >= s.length) throw DescriptorException("Prototype is missing the closing ')'", offset + s.length)
            if (s[i] == ')') break
            val (t, next) = parseDescriptorAt(s, i, allowVoid = false, offset = offset)
            params += ParamInfo(t, null)
            i = next
        }
        i++ // ')'
        if (i >= s.length) throw DescriptorException("Prototype is missing its return type", offset + s.length)
        val (ret, end) = parseDescriptorAt(s, i, allowVoid = true, offset = offset)
        if (end != s.length) throw DescriptorException("Unexpected text after the return type", offset + end)
        if (params.sumOf { it.type.words } > 255) {
            throw DescriptorException("A method can take at most 255 parameter words (long/double count twice)", offset)
        }
        return MethodProto(params, ret)
    }

    /** Parses `Lcom/a/B;->name:I`. */
    fun parseFieldRef(input: String): FieldRef {
        val s = input.trim()
        val lead = input.length - input.trimStart().length
        val arrow = s.indexOf("->")
        if (arrow == -1) throw DescriptorException("A field reference needs '->' between class and field name", lead)
        val owner = parseOwner(s.substring(0, arrow), lead)
        val rest = s.substring(arrow + 2)
        val colon = rest.indexOf(':')
        if (colon == -1) throw DescriptorException("A field reference needs ':' between the field name and its type", lead + s.length)
        val name = rest.substring(0, colon)
        validateMemberName(name, lead + arrow + 2, allowInit = false)
        val typeStart = lead + arrow + 2 + colon + 1
        val (type, end) = parseDescriptorAt(rest, colon + 1, allowVoid = false, offset = lead + arrow + 2)
        if (end != rest.length) throw DescriptorException("Unexpected text after the field type", typeStart + (end - colon - 1))
        return FieldRef(owner, name, type)
    }

    /** Parses `Lcom/a/B;->m(ILjava/lang/String;)V`. */
    fun parseMethodRef(input: String): MethodRef {
        val s = input.trim()
        val lead = input.length - input.trimStart().length
        val arrow = s.indexOf("->")
        if (arrow == -1) throw DescriptorException("A method reference needs '->' between class and method name", lead)
        val owner = parseOwner(s.substring(0, arrow), lead)
        val rest = s.substring(arrow + 2)
        val paren = rest.indexOf('(')
        if (paren == -1) throw DescriptorException("A method reference needs '(' before the parameter types", lead + s.length)
        val name = rest.substring(0, paren)
        validateMemberName(name, lead + arrow + 2, allowInit = true)
        val proto = parseProto(rest.substring(paren), offset = lead + arrow + 2 + paren)
        return MethodRef(owner, name, proto)
    }

    private fun parseOwner(text: String, offset: Int): TypeInfo {
        if (text.isEmpty()) throw DescriptorException("Missing class descriptor before '->'", offset)
        val (t, end) = parseDescriptorAt(text, 0, allowVoid = false, offset = offset)
        if (end != text.length) throw DescriptorException("Unexpected text after the class descriptor", offset + end)
        if (t.base != 'L' && t.arrayDimensions == 0) throw DescriptorException("The owner must be a class or array descriptor", offset)
        return t
    }

    private fun validateMemberName(name: String, pos: Int, allowInit: Boolean) {
        if (name.isEmpty()) throw DescriptorException("Member name is empty", pos)
        if (name == "<init>" || name == "<clinit>") {
            if (!allowInit) throw DescriptorException("$name is a method name, not a field name", pos)
            return
        }
        for ((k, ch) in name.withIndex()) {
            if (ch.isWhitespace() || ch in ".;[/<>():") throw DescriptorException("Character '$ch' is not allowed in a member name", pos + k)
        }
    }

    // ---------------------------------------------------------------- Java -> descriptors

    /** Converts a Java type to a descriptor; generics are stripped (reported via [notes]). */
    fun javaTypeToDescriptor(input: String, notes: MutableList<String>? = null): TypeInfo {
        val lead = input.length - input.trimStart().length
        val stripped = stripGenerics(input, notes)
        var s = stripped.trim()
        if (s.isEmpty()) throw DescriptorException("Enter a Java type, e.g. int[] or java.lang.String", lead)
        var dims = 0
        if (s.endsWith("...")) {
            dims++
            s = s.removeSuffix("...").trimEnd()
            notes?.add("Varargs '...' is compiled as an extra array dimension")
        }
        while (true) {
            val t = s.trimEnd()
            if (t.endsWith("]")) {
                val open = t.dropLast(1).trimEnd()
                if (!open.endsWith("[")) throw DescriptorException("Array brackets must be written as []", lead + t.length - 1)
                dims++
                s = open.dropLast(1)
            } else {
                s = t
                break
            }
        }
        if (s.isEmpty()) throw DescriptorException("Missing element type before the array brackets", lead)
        if (dims > 255) throw DescriptorException("Arrays can have at most 255 dimensions", lead)
        val prim = PRIMITIVES[s]
        if (prim != null) {
            if (prim == 'V' && dims > 0) throw DescriptorException("An array of void is not allowed", lead)
            return TypeInfo("[".repeat(dims) + prim, s + "[]".repeat(dims), prim, dims)
        }
        val body = javaClassToBinary(s, lead, notes)
        return TypeInfo("[".repeat(dims) + "L$body;", binaryToJava(body) + "[]".repeat(dims), 'L', dims)
    }

    /** `java.util.Map.Entry` -> `java/util/Map$Entry` (nested classes inferred from capitalised segments). */
    private fun javaClassToBinary(name: String, pos: Int, notes: MutableList<String>?): String {
        var p = pos
        if (name.startsWith(".") || name.endsWith(".") || name.contains("..")) {
            throw DescriptorException("Class name has an empty segment (stray '.')", pos + name.indexOfFirst { it == '.' }.coerceAtLeast(0))
        }
        for ((k, ch) in name.withIndex()) {
            if (!(ch.isLetterOrDigit() || ch == '_' || ch == '$' || ch == '.' || ch == '/')) {
                throw DescriptorException("Character '$ch' is not allowed in a class name", p + k)
            }
        }
        if (name.first().isDigit()) throw DescriptorException("A class name cannot start with a digit", pos)
        if (name.contains('/')) {
            // Already looks like a binary name written with slashes.
            notes?.add("Slashes in the input were kept as package separators")
            return name
        }
        val segments = name.split('.')
        val firstUpper = segments.indexOfFirst { it.first().isUpperCase() }
        val sb = StringBuilder()
        for ((idx, seg) in segments.withIndex()) {
            when {
                idx == 0 -> sb.append(seg)
                firstUpper != -1 && idx > firstUpper -> sb.append('$').append(seg)
                else -> sb.append('/').append(seg)
            }
        }
        if (firstUpper != -1 && firstUpper < segments.lastIndex) {
            notes?.add(
                "Treated '${segments.drop(firstUpper + 1).joinToString(".")}' as nested inside '${segments[firstUpper]}' " +
                    "because the segments start with an uppercase letter; the binary name uses '$' for nesting",
            )
        }
        if (segments.size == 1 && !name.contains('$')) {
            notes?.add("No package given: \"$name\" is converted as a class in the default package")
        }
        return sb.toString()
    }

    /** Removes `<...>` generic arguments (nested), recording a note. */
    fun stripGenerics(text: String, notes: MutableList<String>? = null): String {
        if (!text.contains('<')) return text
        val sb = StringBuilder()
        var depth = 0
        for (ch in text) {
            when {
                ch == '<' -> depth++
                ch == '>' && depth > 0 -> depth--
                depth == 0 -> sb.append(ch)
            }
        }
        notes?.add("Generic type arguments are erased in descriptors and were removed")
        return sb.toString()
    }

    /**
     * Parses a Java method signature such as `public static int add(int a, long b)`.
     * The return type is required (use `void <init>(int)` for constructors).
     * Returns the proto plus the method name.
     */
    fun parseJavaMethod(input: String, notes: MutableList<String>? = null): Pair<String, MethodProto> {
        val lead = input.length - input.trimStart().length
        val open = input.indexOf('(')
        if (open == -1) throw DescriptorException("A method signature needs '(' before its parameters", input.length)
        val close = findMatchingClose(input, open)
        if (close == -1) throw DescriptorException("Missing closing ')'", input.length)
        val trailing = input.substring(close + 1).trim()
        if (trailing.isNotEmpty() && !trailing.startsWith("throws") && trailing != ";" && trailing != "{") {
            val afterClose = input.substring(close + 1)
            throw DescriptorException("Unexpected text after ')'", close + 1 + (afterClose.length - afterClose.trimStart().length))
        }
        val head = stripGenerics(input.substring(0, open).replace("<init>", "\u0001I").replace("<clinit>", "\u0001C"), notes)
            .replace("\u0001I", "<init>").replace("\u0001C", "<clinit>").trim()
        val tokens = head.split(Regex("\\s+")).filter { it.isNotEmpty() && it !in MODIFIERS }
        if (tokens.isEmpty()) throw DescriptorException("Missing return type and method name", lead)
        val name = tokens.last()
        if (name.contains('.') || name.contains('[')) throw DescriptorException("Invalid method name '$name'", lead + input.indexOf(name).coerceAtLeast(0))
        val returnTokens = tokens.dropLast(1)
        val returnType: TypeInfo = when {
            returnTokens.isEmpty() && (name == "<init>" || name == "<clinit>") -> {
                notes?.add("$name is a constructor/initializer, so the return type is void")
                javaTypeToDescriptor("void")
            }
            returnTokens.isEmpty() -> throw DescriptorException(
                "Missing return type before '$name' (write e.g. \"void $name(...)\")", lead + input.indexOf(name).coerceAtLeast(0),
            )
            else -> javaTypeToDescriptor(returnTokens.joinToString(" "), notes)
        }
        val paramText = input.substring(open + 1, close)
        val params = parseJavaParamList(paramText, open + 1, notes)
        val proto = MethodProto(params, returnType)
        if (proto.paramWords > 255) throw DescriptorException("A method can take at most 255 parameter words", open)
        return name to proto
    }

    private fun findMatchingClose(s: String, open: Int): Int {
        var depth = 0
        for (i in open until s.length) {
            if (s[i] == '(') depth++ else if (s[i] == ')') {
                depth--
                if (depth == 0) return i
            }
        }
        return -1
    }

    /** Parses `int a, long b` or `int, String[]` (names optional, generics stripped, `final` ignored). */
    fun parseJavaParamList(text: String, offset: Int = 0, notes: MutableList<String>? = null): List<ParamInfo> {
        if (text.isBlank()) return emptyList()
        val parts = splitTopLevelCommas(text)
        val out = ArrayList<ParamInfo>()
        var pos = offset
        for (raw in parts) {
            val partLead = raw.length - raw.trimStart().length
            val cleaned = stripGenerics(raw, notes).trim()
            if (cleaned.isEmpty()) throw DescriptorException("Empty parameter (stray ',')", pos + partLead)
            val tokens = cleaned.split(Regex("\\s+")).filter { it != "final" && !it.startsWith("@") }
            if (tokens.isEmpty()) throw DescriptorException("Empty parameter", pos + partLead)
            val typeText: String
            var name: String? = null
            // Re-attach brackets that were written with spaces: "int []" or "String ... args".
            val joined = tokens.joinToString(" ")
            val dots = joined.indexOf("...")
            if (dots != -1) {
                typeText = joined.substring(0, dots).trim() + "..."
                name = joined.substring(dots + 3).trim().ifEmpty { null }
            } else {
                val m = Regex("^(.*?[A-Za-z0-9_$>\\]])\\s+([A-Za-z_$][A-Za-z0-9_$]*)((?:\\s*\\[\\s*\\])*)$").find(joined)
                if (m != null) {
                    typeText = m.groupValues[1] + m.groupValues[3].replace(" ", "")
                    name = m.groupValues[2]
                } else {
                    typeText = joined
                }
            }
            val type = try {
                javaTypeToDescriptor(typeText, notes)
            } catch (e: DescriptorException) {
                throw DescriptorException(e.message ?: "Invalid type", pos + partLead + e.position.coerceAtLeast(0))
            }
            if (type.base == 'V' && type.arrayDimensions == 0) throw DescriptorException("A parameter cannot be void", pos + partLead)
            out += ParamInfo(type, name)
            pos += raw.length + 1
        }
        return out
    }

    private fun splitTopLevelCommas(text: String): List<String> {
        val out = ArrayList<String>()
        var depth = 0
        val cur = StringBuilder()
        for (ch in text) {
            when {
                ch == '<' -> { depth++; cur.append(ch) }
                ch == '>' -> { depth = (depth - 1).coerceAtLeast(0); cur.append(ch) }
                ch == ',' && depth == 0 -> { out += cur.toString(); cur.clear() }
                else -> cur.append(ch)
            }
        }
        out += cur.toString()
        return out
    }

    // ---------------------------------------------------------------- readable output

    /** `(IJ)I` -> `int method(int, long)`; names given by [name] / param names when known. */
    fun protoToJava(proto: MethodProto, name: String = "method"): String =
        proto.returnType.java + " " + name + "(" + proto.params.joinToString(", ") { p ->
            p.type.java + (p.name?.let { " $it" } ?: "")
        } + ")"

    // ---------------------------------------------------------------- auto-detecting entry point

    /**
     * Detects what [input] is and converts it in the natural direction:
     * field/method references and descriptors become readable Java, Java types/signatures become descriptors.
     */
    fun convert(input: String): DescriptorResult {
        val text = input.trim()
        if (text.isEmpty()) return DescriptorResult.Failure("Enter a type, signature, descriptor or member reference", -1)
        val lead = input.length - input.trimStart().length
        return try {
            when {
                text.contains("->") -> convertReference(text, lead)
                text.startsWith("(") && isLikelyProto(text) -> convertProto(text, lead)
                isLikelyDescriptor(text) -> convertDescriptor(text)
                text.contains('(') -> convertJavaMethod(input)
                else -> convertJavaType(input)
            }
        } catch (e: DescriptorException) {
            DescriptorResult.Failure(e.message ?: "Invalid input", e.position)
        }
    }

    private fun isLikelyDescriptor(s: String): Boolean {
        if (s.contains(' ') || s.contains(',')) return false
        if (s.startsWith("[") ) return true
        if (s.length == 1 && s[0] in PRIMITIVE_NAMES) return true
        if (s.startsWith("L") && s.endsWith(";") ) return true
        return false
    }

    private fun isLikelyProto(s: String): Boolean {
        if (!s.startsWith("(")) return false
        if (s.contains(',') || s.contains(' ')) return false
        val close = s.indexOf(')')
        if (close == -1) return false
        val inner = s.substring(1, close)
        // Java style "(int)" has lowercase words; descriptor style only has descriptor characters.
        return inner.isEmpty() || inner.first() in "[ZBSCIJFDL"
    }

    private fun convertDescriptor(text: String): DescriptorResult {
        val t = parseTypeDescriptor(text)
        val notes = mutableListOf<String>()
        if (t.base == 'L' && t.java.contains('.') && text.contains('$')) {
            notes += "'$' in a binary name usually separates an inner class from its outer class"
        }
        if (t.words == 2) notes += "A long or double takes two registers (a register pair)"
        if (t.base == 'V') notes += "void is only valid as a method return type"
        return DescriptorResult.Success(
            DescriptorResult.Kind.DESCRIPTOR,
            listOf("Java type" to t.java, "Descriptor" to t.descriptor, "Register words" to t.words.toString()),
            notes,
        )
    }

    private fun convertJavaType(input: String): DescriptorResult {
        val notes = mutableListOf<String>()
        val t = javaTypeToDescriptor(input, notes)
        if (t.words == 2) notes += "A long or double takes two registers (a register pair)"
        return DescriptorResult.Success(
            DescriptorResult.Kind.JAVA_TYPE,
            listOf("Descriptor" to t.descriptor, "Java type" to t.java, "Register words" to t.words.toString()),
            notes,
        )
    }

    private fun convertProto(text: String, lead: Int): DescriptorResult {
        val p = parseProto(text, lead)
        val notes = mutableListOf<String>()
        notes += "Parameter words: ${p.paramWords} (long and double take two each; an instance method also passes 'this')"
        return DescriptorResult.Success(
            DescriptorResult.Kind.PROTO,
            listOf("Java signature" to protoToJava(p), "Prototype" to p.descriptor, "Parameter words" to p.paramWords.toString()),
            notes,
        )
    }

    private fun convertJavaMethod(input: String): DescriptorResult {
        val notes = mutableListOf<String>()
        val (name, proto) = parseJavaMethod(input, notes)
        notes += "Parameter words: ${proto.paramWords} (an instance method also passes 'this')"
        return DescriptorResult.Success(
            DescriptorResult.Kind.JAVA_METHOD,
            listOf("Prototype" to proto.descriptor, "Method name" to name, "Smali signature" to "$name${proto.descriptor}"),
            notes.distinct(),
        )
    }

    private fun convertReference(text: String, lead: Int): DescriptorResult {
        val afterArrow = text.substringAfter("->")
        val isMethod = afterArrow.contains('(') && (!afterArrow.contains(':') || afterArrow.indexOf('(') < afterArrow.indexOf(':'))
        return if (isMethod) {
            val m = parseMethodRef(text)
            val notes = mutableListOf<String>()
            if (m.name == "<init>") notes += "<init> is a constructor"
            if (m.name == "<clinit>") notes += "<clinit> is the static initializer"
            notes += "Parameter words: ${m.proto.paramWords} (plus one for 'this' unless the call is static)"
            DescriptorResult.Success(
                DescriptorResult.Kind.METHOD_REF,
                listOf(
                    "Java" to m.java,
                    "Owner" to m.owner.java,
                    "Name" to m.name,
                    "Prototype" to m.proto.descriptor,
                    "Smali" to m.smali,
                ),
                notes,
            )
        } else {
            val f = parseFieldRef(text)
            DescriptorResult.Success(
                DescriptorResult.Kind.FIELD_REF,
                listOf("Java" to f.java, "Owner" to f.owner.java, "Name" to f.name, "Type" to f.type.java, "Smali" to f.smali),
                if (f.type.words == 2) listOf("A long/double field value takes two registers when loaded") else emptyList(),
            )
        }
    }

    // ---------------------------------------------------------------- combined parameter parsing (for the register tool)

    /**
     * Accepts any of: a prototype `(IJ)V`, a member reference `Lcom/a/B;->m(IJ)V`, a Java method signature
     * `int add(int a, long b)`, or a bare Java parameter list `int a, long b`.
     */
    fun parseAnySignature(input: String, notes: MutableList<String>? = null): MethodProto {
        val text = input.trim()
        val lead = input.length - input.trimStart().length
        if (text.isEmpty()) return MethodProto(emptyList(), javaTypeToDescriptor("void"))
        if (text.contains("->")) return parseMethodRef(text).proto
        if (text.startsWith("(") && isLikelyProto(text)) return parseProto(text, lead)
        if (text.startsWith("(") && text.endsWith(")")) {
            val params = parseJavaParamList(text.substring(1, text.length - 1), lead + 1, notes)
            return MethodProto(params, javaTypeToDescriptor("void"))
        }
        if (text.contains('(')) return parseJavaMethod(input, notes).second
        // A run of descriptors without parentheses, e.g. "IJLjava/lang/String;" (no commas or spaces).
        if (!text.contains(',') && !text.contains(' ') && !text.contains('.') && text.first() in "ZBSCIJFDL[") {
            val params = ArrayList<ParamInfo>()
            var i = 0
            var failed = false
            while (i < text.length) {
                try {
                    val (t, n) = parseDescriptorAt(text, i, allowVoid = false, offset = lead)
                    params += ParamInfo(t, null)
                    i = n
                } catch (e: DescriptorException) {
                    failed = true
                    break
                }
            }
            if (!failed) return MethodProto(params, javaTypeToDescriptor("void"))
        }
        return MethodProto(parseJavaParamList(input, 0, notes), javaTypeToDescriptor("void"))
    }
}
