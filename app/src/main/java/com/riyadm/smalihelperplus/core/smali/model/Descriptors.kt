package com.riyadm.smalihelperplus.core.smali.model

/** A parsed method prototype such as `(ILjava/lang/String;)V`. */
data class MethodProto(val parameters: List<String>, val returnType: String) {
    /** Number of 32-bit register words the parameters occupy (J and D take two). */
    val parameterWords: Int get() = parameters.sumOf { Descriptors.wordSize(it) }
    val descriptor: String get() = parameters.joinToString("", "(", ")") + returnType
}

/** A field reference `Lcls;->name:Type`. */
data class FieldReference(val owner: String, val name: String, val type: String) {
    override fun toString() = "$owner->$name:$type"
}

/** A method reference `Lcls;->name(params)Ret`. */
data class MethodReference(val owner: String, val name: String, val proto: MethodProto) {
    override fun toString() = "$owner->$name${proto.descriptor}"
}

/** Type descriptor helpers (validation and Java-readable rendering). */
object Descriptors {
    private const val PRIMITIVES = "ZBSCIJFD"

    fun isWide(type: String): Boolean = type == "J" || type == "D"
    fun wordSize(type: String): Int = if (isWide(type)) 2 else 1
    fun isReference(type: String): Boolean = type.startsWith("L") || type.startsWith("[")
    fun isPrimitive(type: String): Boolean = type.length == 1 && type[0] in PRIMITIVES

    /** True when [descriptor] is a valid field/value type (V only when [allowVoid]). */
    fun isValidType(descriptor: String, allowVoid: Boolean = false): Boolean {
        if (descriptor.isEmpty()) return false
        if (descriptor == "V") return allowVoid
        var i = 0
        while (i < descriptor.length && descriptor[i] == '[') i++
        if (i > 255) return false
        if (i == descriptor.length) return false
        val rest = descriptor.substring(i)
        return when {
            rest.length == 1 -> rest[0] in PRIMITIVES
            rest[0] == 'L' -> isValidClassDescriptor(rest)
            else -> false
        }
    }

    /** Validates `Lpkg/Name;`. */
    fun isValidClassDescriptor(descriptor: String): Boolean {
        if (descriptor.length < 3 || descriptor[0] != 'L' || descriptor.last() != ';') return false
        val body = descriptor.substring(1, descriptor.length - 1)
        if (body.isEmpty()) return false
        return body.split('/').all { segment -> segment.isNotEmpty() && segment.all { isSimpleNameChar(it) } }
    }

    /** Characters allowed in a dex "SimpleName" (simplified: letters, digits and `$-_`, plus non-ASCII). */
    fun isSimpleNameChar(c: Char): Boolean =
        c.isLetterOrDigit() || c == '$' || c == '-' || c == '_' || c.code >= 0xa0

    fun isValidMemberName(name: String): Boolean =
        name == "<init>" || name == "<clinit>" || (name.isNotEmpty() && name.all { isSimpleNameChar(it) })

    /** Splits the concatenated parameter descriptors of a prototype, or null when malformed. */
    fun splitTypes(params: String): List<String>? {
        val out = ArrayList<String>()
        var i = 0
        while (i < params.length) {
            val start = i
            while (i < params.length && params[i] == '[') i++
            if (i >= params.length) return null
            when (params[i]) {
                'L' -> {
                    val semi = params.indexOf(';', i)
                    if (semi == -1) return null
                    i = semi + 1
                }
                in PRIMITIVES -> i++
                else -> return null
            }
            out += params.substring(start, i)
        }
        return out
    }

    /** Parses `(params)Ret`, returning null when malformed. */
    fun parseProto(text: String): MethodProto? {
        if (!text.startsWith("(")) return null
        val close = text.indexOf(')')
        if (close == -1) return null
        val params = splitTypes(text.substring(1, close)) ?: return null
        val ret = text.substring(close + 1)
        if (!params.all { isValidType(it) } || !isValidType(ret, allowVoid = true)) return null
        return MethodProto(params, ret)
    }

    /** Parses `Lcls;->name(params)Ret`. */
    fun parseMethodReference(text: String): MethodReference? {
        val arrow = text.indexOf("->")
        if (arrow <= 0) return null
        val owner = text.substring(0, arrow)
        val rest = text.substring(arrow + 2)
        val paren = rest.indexOf('(')
        if (paren <= 0) return null
        val name = rest.substring(0, paren)
        if (!isValidType(owner)) return null
        if (!isValidMemberName(name)) return null
        val proto = parseProto(rest.substring(paren)) ?: return null
        return MethodReference(owner, name, proto)
    }

    /** Parses `Lcls;->name:Type`. */
    fun parseFieldReference(text: String): FieldReference? {
        val arrow = text.indexOf("->")
        if (arrow <= 0) return null
        val owner = text.substring(0, arrow)
        val rest = text.substring(arrow + 2)
        val colon = rest.indexOf(':')
        if (colon <= 0) return null
        val name = rest.substring(0, colon)
        val type = rest.substring(colon + 1)
        if (!isValidType(owner) || !isValidMemberName(name) || !isValidType(type)) return null
        return FieldReference(owner, name, type)
    }

    /** `Ljava/lang/String;` → `java.lang.String`, `[I` → `int[]`. */
    fun toJavaName(descriptor: String, simple: Boolean = false): String {
        var dims = 0
        while (dims < descriptor.length && descriptor[dims] == '[') dims++
        val base = descriptor.substring(dims)
        val name = when (base) {
            "V" -> "void"
            "Z" -> "boolean"
            "B" -> "byte"
            "S" -> "short"
            "C" -> "char"
            "I" -> "int"
            "J" -> "long"
            "F" -> "float"
            "D" -> "double"
            else -> if (base.startsWith("L") && base.endsWith(";")) {
                val fq = base.substring(1, base.length - 1).replace('/', '.')
                if (simple) fq.substringAfterLast('.') else fq
            } else {
                base
            }
        }
        return name + "[]".repeat(dims)
    }

    fun simpleName(descriptor: String): String = toJavaName(descriptor, simple = true)
}
