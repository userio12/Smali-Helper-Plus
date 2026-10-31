package com.riyadm.smalihelperplus.core.smali.structure

import com.riyadm.smalihelperplus.core.smali.ast.MethodDefinition
import com.riyadm.smalihelperplus.core.smali.ast.ParamDirective
import com.riyadm.smalihelperplus.core.smali.ast.SmaliFile
import com.riyadm.smalihelperplus.core.smali.model.Descriptors

data class ParameterOutline(val descriptor: String, val javaType: String, val name: String?)

data class FieldOutline(
    val name: String,
    val descriptor: String,
    val javaType: String,
    val flags: List<String>,
    val initialValue: String?,
    val line: Int,
) {
    val javaDeclaration: String
        get() = buildString {
            flags.filter { it in JAVA_MODIFIERS }.forEach { append(it).append(' ') }
            append(javaType).append(' ').append(name)
            if (initialValue != null) append(" = ").append(initialValue)
        }
}

data class MethodOutline(
    val name: String,
    val flags: List<String>,
    val protoText: String,
    val parameters: List<ParameterOutline>,
    val returnDescriptor: String?,
    val javaReturnType: String?,
    val registers: Int?,
    val locals: Int?,
    val parameterWords: Int?,
    val instructionCount: Int,
    val startLine: Int,
    val endLine: Int?,
    /** Readable Java-style signature, e.g. `public static int add(int a, int b)`. */
    val javaSignature: String,
) {
    val smaliSignature: String get() = name + protoText
    val isConstructor: Boolean get() = name == "<init>"
    val isStaticInitializer: Boolean get() = name == "<clinit>"
    val isStatic: Boolean get() = "static" in flags
    val isAbstractOrNative: Boolean get() = "abstract" in flags || "native" in flags
}

data class ClassOutline(
    val descriptor: String?,
    val javaName: String?,
    val simpleName: String?,
    val flags: List<String>,
    val superClass: String?,
    val source: String?,
    val interfaces: List<String>,
    val fields: List<FieldOutline>,
    val methods: List<MethodOutline>,
    val annotationCount: Int,
) {
    val kind: String
        get() = when {
            "annotation" in flags -> "@interface"
            "interface" in flags -> "interface"
            "enum" in flags -> "enum"
            else -> "class"
        }

    val javaHeader: String
        get() = buildString {
            flags.filter { it in JAVA_MODIFIERS && it != "interface" && it != "enum" && it != "annotation" }
                .filterNot { kind != "class" && it == "abstract" }
                .forEach { append(it).append(' ') }
            append(kind).append(' ').append(simpleName ?: "?")
            superClass?.takeIf { it != "Ljava/lang/Object;" && kind == "class" }?.let { append(" extends ").append(Descriptors.toJavaName(it)) }
            if (interfaces.isNotEmpty()) {
                append(if (kind == "interface") " extends " else " implements ")
                append(interfaces.joinToString(", ") { Descriptors.toJavaName(it) })
            }
        }
}

private val JAVA_MODIFIERS = listOf(
    "public", "private", "protected", "static", "final", "abstract", "synchronized", "native", "transient", "volatile",
    "strictfp", "interface", "enum", "annotation",
)

/** Builds the class/method/field outline used by the Method explorer and the Playground outline sheet. */
object StructureAnalyzer {
    fun outline(file: SmaliFile): ClassOutline {
        val header = file.header
        val simple = header?.descriptor?.let { Descriptors.simpleName(it) }
        return ClassOutline(
            descriptor = header?.descriptor,
            javaName = header?.descriptor?.let { Descriptors.toJavaName(it) },
            simpleName = simple,
            flags = header?.flags.orEmpty(),
            superClass = file.superClass?.descriptor,
            source = file.source,
            interfaces = file.interfaces.map { it.descriptor },
            fields = file.fields.map { f ->
                FieldOutline(f.name, f.type, Descriptors.toJavaName(f.type, simple = true), f.flags, f.initialValue, f.line)
            },
            methods = file.methods.filter { !it.isImplicit }.map { methodOutline(it, simple) },
            annotationCount = file.annotations.size,
        )
    }

    fun methodOutline(m: MethodDefinition, classSimpleName: String?): MethodOutline {
        val names = HashMap<Int, String>()
        m.items.filterIsInstance<ParamDirective>().forEach { p ->
            val r = p.register
            if (r != null && r.isParameter && p.name != null) names[r.number] = p.name
        }
        var pIndex = if (m.isStatic) 0 else 1
        val params = m.proto?.parameters.orEmpty().map { type ->
            val outline = ParameterOutline(type, Descriptors.toJavaName(type, simple = true), names[pIndex])
            pIndex += Descriptors.wordSize(type)
            outline
        }
        val ret = m.proto?.returnType
        val sig = buildString {
            m.flags.filter { it in JAVA_MODIFIERS && it != "annotation" && it != "enum" && it != "interface" }.forEach { append(it).append(' ') }
            when (m.name) {
                "<clinit>" -> append("static {}")
                "<init>" -> append(classSimpleName ?: "<init>")
                else -> append(ret?.let { Descriptors.toJavaName(it, simple = true) } ?: "?").append(' ').append(m.name)
            }
            if (m.name != "<clinit>") {
                val p0 = if (m.isStatic) 0 else 1
                var p = p0
                append(params.joinToString(", ", "(", ")") { po ->
                    val name = po.name ?: "p$p"
                    p += Descriptors.wordSize(po.descriptor)
                    "${po.javaType} $name"
                })
            }
        }
        return MethodOutline(
            name = m.name, flags = m.flags, protoText = m.protoText, parameters = params, returnDescriptor = ret,
            javaReturnType = ret?.let { Descriptors.toJavaName(it, simple = true) }, registers = m.frameSize,
            locals = m.localCount, parameterWords = m.parameterWords, instructionCount = m.instructions.size,
            startLine = m.startLine, endLine = m.endLine, javaSignature = sig,
        )
    }

    /** Source text of a method (from `.method` to `.end method`, or to the end of the input when unterminated). */
    fun methodSource(text: String, method: MethodDefinition): String {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val from = (method.startLine - 1).coerceIn(0, lines.size)
        val to = (method.endLine ?: lines.size).coerceIn(from, lines.size)
        return lines.subList(from, to).joinToString("\n")
    }

    /**
     * A minimal, self-contained class around one method: the original `.class`, `.super` and `.source` lines plus the
     * method's source (used for "Open in Playground").
     */
    fun standaloneMethodSource(text: String, file: SmaliFile, method: MethodDefinition): String {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val header = listOfNotNull(file.header?.line, file.superClass?.line)
            .mapNotNull { lines.getOrNull(it - 1)?.trim() }
        val source = file.source?.let { ".source \"$it\"" }
        return buildString {
            header.forEach { append(it).append('\n') }
            if (source != null) append(source).append('\n')
            if (isNotEmpty()) append('\n')
            append(methodSource(text, method))
            append('\n')
        }
    }
}
