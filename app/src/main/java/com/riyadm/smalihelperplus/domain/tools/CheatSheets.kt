package com.riyadm.smalihelperplus.domain.tools

data class CheatTable(val headers: List<String>, val rows: List<List<String>>) {
    /** Plain text with tab separated columns, for the copy button. */
    fun toPlainText(): String = (listOf(headers) + rows).joinToString("\n") { it.joinToString("\t") }
}

/** One card of a cheat sheet: optional text, table and/or code. */
data class CheatBlock(
    val title: String,
    val text: String? = null,
    val table: CheatTable? = null,
    val code: String? = null,
) {
    val copyText: String
        get() = buildString {
            append(title)
            text?.let { append("\n").append(it) }
            table?.let { append("\n").append(it.toPlainText()) }
            code?.let { append("\n").append(it) }
        }
}

data class CheatSheet(val id: String, val title: String, val summary: String, val blocks: List<CheatBlock>) {
    val copyText: String get() = title + "\n\n" + blocks.joinToString("\n\n") { it.copyText }
}

/** Original quick-reference sheets. Facts follow the official Dalvik bytecode / DEX format documents. */
object CheatSheets {

    private fun table(vararg headers: String, rows: List<List<String>>) = CheatTable(headers.toList(), rows)

    private val typeDescriptors = CheatSheet(
        "descriptors", "Type descriptors",
        "How types are spelled inside smali and DEX files.",
        listOf(
            CheatBlock(
                "Primitive types",
                table = table(
                    "Code", "Java type", "Registers", "Notes",
                    rows = listOf(
                        listOf("Z", "boolean", "1", "0 or 1"),
                        listOf("B", "byte", "1", "signed 8 bit"),
                        listOf("S", "short", "1", "signed 16 bit"),
                        listOf("C", "char", "1", "unsigned 16 bit"),
                        listOf("I", "int", "1", "32 bit"),
                        listOf("J", "long", "2", "64 bit, register pair"),
                        listOf("F", "float", "1", "32 bit IEEE 754"),
                        listOf("D", "double", "2", "64 bit IEEE 754, register pair"),
                        listOf("V", "void", "0", "return type only"),
                    ),
                ),
            ),
            CheatBlock(
                "Classes and arrays",
                table = table(
                    "Descriptor", "Java type",
                    rows = listOf(
                        listOf("Ljava/lang/String;", "java.lang.String"),
                        listOf("Lcom/acme/demo/Outer\$Inner;", "com.acme.demo.Outer.Inner (binary name uses \$)"),
                        listOf("[I", "int[]"),
                        listOf("[[Ljava/lang/String;", "String[][]"),
                        listOf("[J", "long[]  (one register, it is a reference)"),
                    ),
                ),
                text = "A class descriptor is L, the package path with slashes, the class name and a closing semicolon. " +
                    "Each leading [ adds one array dimension (up to 255).",
            ),
            CheatBlock(
                "Prototypes and references",
                table = table(
                    "Form", "Example",
                    rows = listOf(
                        listOf("Prototype (params)return", "(ILjava/lang/String;J)V"),
                        listOf("Field reference", "Lcom/a/B;->count:I"),
                        listOf("Method reference", "Lcom/a/B;->add(II)I"),
                        listOf("Constructor", "Lcom/a/B;-><init>(I)V"),
                        listOf("Static initializer", "Lcom/a/B;-><clinit>()V"),
                    ),
                ),
                text = "Parameter descriptors are written back to back without separators. A long or double parameter " +
                    "counts as two parameter words.",
            ),
        ),
    )

    private val accessFlags = CheatSheet(
        "access", "Access flags",
        "Modifier keywords and their DEX values.",
        listOf(
            CheatBlock(
                "Flag values",
                table = table(
                    "Smali keyword", "Value", "Applies to",
                    rows = listOf(
                        listOf("public", "0x1", "class, field, method"),
                        listOf("private", "0x2", "field, method (inner classes via annotation)"),
                        listOf("protected", "0x4", "field, method (inner classes via annotation)"),
                        listOf("static", "0x8", "field, method (inner classes via annotation)"),
                        listOf("final", "0x10", "class, field, method"),
                        listOf("synchronized", "0x20", "method; the VM only honours it together with native"),
                        listOf("volatile", "0x40", "field"),
                        listOf("bridge", "0x40", "method"),
                        listOf("transient", "0x80", "field"),
                        listOf("varargs", "0x80", "method"),
                        listOf("native", "0x100", "method"),
                        listOf("interface", "0x200", "class"),
                        listOf("abstract", "0x400", "class, method"),
                        listOf("strictfp", "0x800", "method"),
                        listOf("synthetic", "0x1000", "class, field, method"),
                        listOf("annotation", "0x2000", "class"),
                        listOf("enum", "0x4000", "class, field"),
                        listOf("constructor", "0x10000", "method"),
                        listOf("declared-synchronized", "0x20000", "method (informational)"),
                    ),
                ),
            ),
            CheatBlock(
                "Combining flags",
                text = "The numeric value is the sum (bitwise OR) of the keywords. Example: public static final = 0x1 + 0x8 + 0x10 = 0x19. " +
                    "A constructor is written \"constructor\" in smali; interface classes are also marked abstract.",
                code = ".field public static final MAX:I = 0x64\n.method public static final constructor <clinit>()V",
            ),
        ),
    )

    private val directives = CheatSheet(
        "directives", "Directives",
        "Lines starting with a dot describe structure instead of executing code.",
        listOf(
            CheatBlock(
                "Class level",
                table = table(
                    "Directive", "Meaning",
                    rows = listOf(
                        listOf(".class <flags> L...;", "Declares the class being defined"),
                        listOf(".super L...;", "Superclass (exactly one)"),
                        listOf(".implements L...;", "One line per implemented interface"),
                        listOf(".source \"File.java\"", "Original source file name (debug info)"),
                        listOf(".field <flags> name:Type [= value]", "Field with optional constant initial value"),
                        listOf(".annotation <visibility> L...;", "Starts an annotation; ends with .end annotation"),
                    ),
                ),
            ),
            CheatBlock(
                "Inside a method",
                table = table(
                    "Directive", "Meaning",
                    rows = listOf(
                        listOf(".method <flags> name(params)ret", "Starts a method; ends with .end method"),
                        listOf(".locals N", "Number of non-parameter registers"),
                        listOf(".registers N", "Total registers (locals + parameter words)"),
                        listOf(".param pN, \"name\"", "Names a parameter (debug info), may hold annotations"),
                        listOf(".prologue", "Marks the end of the method prologue (debug info)"),
                        listOf(".line N", "Maps following code to source line N (debug info)"),
                        listOf(".local vN, \"name\":Type", "Starts a named local variable (debug info)"),
                        listOf(".end local vN", "Ends the variable's scope"),
                        listOf(".restart local vN", "Re-opens a variable's scope"),
                        listOf(":label", "Branch / exception range target"),
                        listOf(".catch Type {:start .. :end} :handler", "Exceptions of Type inside the range jump to handler"),
                        listOf(".catchall {:start .. :end} :handler", "Any exception (used for finally and synchronized)"),
                    ),
                ),
            ),
            CheatBlock(
                "Data tables",
                table = table(
                    "Directive", "Used by",
                    rows = listOf(
                        listOf(".packed-switch FIRST ... .end packed-switch", "packed-switch (consecutive keys)"),
                        listOf(".sparse-switch ... .end sparse-switch", "sparse-switch (key -> :label, ascending)"),
                        listOf(".array-data WIDTH ... .end array-data", "fill-array-data (element width in bytes)"),
                    ),
                ),
                text = "Tables sit after the method's code; the switch or fill instruction refers to them by label.",
            ),
        ),
    )

    private val registerRules = CheatSheet(
        "registers", "Register rules",
        "How a method's register frame is laid out and what limits apply.",
        listOf(
            CheatBlock(
                "The frame",
                text = "total registers = .locals + parameter words. Parameters occupy the LAST registers of the frame, so " +
                    "with .locals 3 and parameters (I J) in a static method: p0 = v3, p1/p2 = v4/v5. In an instance method p0 is this. " +
                    "pN and vN are two names for the same registers; smali accepts both.",
                code = ".method public foo(IJ)V\n    .locals 3        # v0..v2 free\n    # p0 = this = v3, p1 = v4, p2/p3 = v5/v6\n    return-void\n.end method",
            ),
            CheatBlock(
                "Wide values",
                text = "long and double use two consecutive registers. Name the lower one (v4 means v4+v5). Never touch half of a pair; " +
                    "the verifier rejects reading a pair that was partly overwritten.",
            ),
            CheatBlock(
                "How far each operand reaches",
                table = table(
                    "Operand width", "Registers", "Typical formats",
                    rows = listOf(
                        listOf("4 bit", "v0 .. v15", "12x, 22c, 22t, 22s, 35c"),
                        listOf("8 bit", "v0 .. v255", "11x, 21c, 21s, 21t, 22b, 23x"),
                        listOf("16 bit", "v0 .. v65535", "22x (source), 32x, 3rc range start"),
                    ),
                ),
                text = "A method can have at most 65535 registers. Use move/from16 or move/16 to bring a high register into a low one.",
            ),
            CheatBlock(
                "Rules the verifier enforces",
                text = "move-result* must directly follow the invoke or filled-new-array that produced the value. " +
                    "move-exception must be the first instruction of an exception handler. " +
                    "A new-instance result is unusable until its <init> constructor was invoked. " +
                    "Use the move / move-wide / move-object variant that matches the value kind (non-object, pair, reference). " +
                    "Registers must hold a value on every path before they are read.",
            ),
        ),
    )

    private val invokeKinds = CheatSheet(
        "invokes", "Invoke kinds",
        "Which invoke instruction to use for which call.",
        listOf(
            CheatBlock(
                "Choosing an invoke",
                table = table(
                    "Instruction", "Calls", "Receiver",
                    rows = listOf(
                        listOf("invoke-virtual", "Normal instance method on a class", "yes (first register)"),
                        listOf("invoke-super", "Parent class's version of an overridden method", "yes (this)"),
                        listOf("invoke-direct", "Constructors, private methods", "yes"),
                        listOf("invoke-static", "Static methods", "no"),
                        listOf("invoke-interface", "Method through an interface type", "yes"),
                        listOf("invoke-polymorphic", "MethodHandle.invoke / invokeExact style calls (DEX 038)", "yes"),
                        listOf("invoke-custom", "Call site resolved by a bootstrap method (DEX 038)", "depends on the call site"),
                    ),
                ),
            ),
            CheatBlock(
                "Argument lists",
                text = "Plain invoke-* takes up to 5 registers, each v0..v15: {v0, v1, v2}. A long or double uses two of those slots. " +
                    "Use the /range form for more or for high registers: {v0 .. v5} lists consecutive registers (up to 255 words).",
                code = "invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z\nmove-result v2\n\ninvoke-static/range {v0 .. v5}, Lcom/a/B;->sum(IIIIII)I\nmove-result v0",
            ),
            CheatBlock(
                "Reading the result",
                text = "Use move-result, move-result-wide or move-result-object right after the call. Skip it when the result is not needed.",
            ),
        ),
    )

    private val patterns = CheatSheet(
        "patterns", "Common patterns",
        "Small recipes you will see in compiled code.",
        listOf(
            CheatBlock(
                "String concatenation (StringBuilder)",
                text = "String s = \"a\" + name;",
                code = "new-instance v0, Ljava/lang/StringBuilder;\ninvoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V\nconst-string v1, \"a\"\ninvoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;\ninvoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;\ninvoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;\nmove-result-object v0",
            ),
            CheatBlock(
                "Null check",
                text = "if (obj == null) return;",
                code = "if-nez p1, :cond_0\nreturn-void\n:cond_0\n# continue with p1",
            ),
            CheatBlock(
                "Counting loop",
                text = "for (int i = 0; i < n; i++) { ... }",
                code = "const/4 v0, 0x0\n:goto_0\nif-ge v0, p1, :cond_0\n# loop body\nadd-int/lit8 v0, v0, 0x1\ngoto :goto_0\n:cond_0",
            ),
            CheatBlock(
                "try / catch",
                text = "try { risky(); } catch (IOException e) { ... }",
                code = ":try_start_0\ninvoke-static {}, Lcom/a/B;->risky()V\n:try_end_0\n.catch Ljava/io/IOException; {:try_start_0 .. :try_end_0} :catch_0\nreturn-void\n:catch_0\nmove-exception v0\n# handle v0\nreturn-void",
            ),
            CheatBlock(
                "Type test and cast",
                text = "if (o instanceof String) { String s = (String) o; }",
                code = "instance-of v0, p1, Ljava/lang/String;\nif-eqz v0, :cond_0\ncheck-cast p1, Ljava/lang/String;\n:cond_0",
            ),
            CheatBlock(
                "Getting and setting fields",
                text = "this.count = this.count + 1;",
                code = "iget v0, p0, Lcom/a/B;->count:I\nadd-int/lit8 v0, v0, 0x1\niput v0, p0, Lcom/a/B;->count:I",
            ),
        ),
    )

    private val ifTests = CheatSheet(
        "iftests", "If tests",
        "Conditional branches and what they compare.",
        listOf(
            CheatBlock(
                "Two registers (format 22t)",
                table = table(
                    "Instruction", "Branches when", "Java test",
                    rows = listOf(
                        listOf("if-eq vA, vB, :l", "vA == vB", "a == b"),
                        listOf("if-ne vA, vB, :l", "vA != vB", "a != b"),
                        listOf("if-lt vA, vB, :l", "vA < vB", "a < b"),
                        listOf("if-ge vA, vB, :l", "vA >= vB", "a >= b"),
                        listOf("if-gt vA, vB, :l", "vA > vB", "a > b"),
                        listOf("if-le vA, vB, :l", "vA <= vB", "a <= b"),
                    ),
                ),
            ),
            CheatBlock(
                "Against zero or null (format 21t)",
                table = table(
                    "Instruction", "Branches when", "Java test",
                    rows = listOf(
                        listOf("if-eqz vAA, :l", "vAA == 0 / null / false", "x == 0, x == null, !flag"),
                        listOf("if-nez vAA, :l", "vAA != 0 / non-null / true", "x != 0, x != null, flag"),
                        listOf("if-ltz vAA, :l", "vAA < 0", "x < 0"),
                        listOf("if-gez vAA, :l", "vAA >= 0", "x >= 0"),
                        listOf("if-gtz vAA, :l", "vAA > 0", "x > 0"),
                        listOf("if-lez vAA, :l", "vAA <= 0", "x <= 0"),
                    ),
                ),
            ),
            CheatBlock(
                "Reading compiled branches",
                text = "Compilers usually branch around the body, so the test is the opposite of the source condition: " +
                    "`if (a < b) { body }` appears as if-ge a, b, :skip, then the body, then :skip. " +
                    "Longs, floats and doubles are first reduced with cmp-long, cmpl-float / cmpg-float, cmpl-double / cmpg-double " +
                    "(result -1, 0 or 1), then tested against zero. The l / g variants differ only in how NaN compares.",
            ),
        ),
    )

    private val literalRanges = CheatSheet(
        "literals", "Literal ranges",
        "Which const instruction can hold which value.",
        listOf(
            CheatBlock(
                "Constants into registers",
                table = table(
                    "Instruction", "Format", "Values", "Units",
                    rows = listOf(
                        listOf("const/4", "11n", "-8 .. 7", "1"),
                        listOf("const/16", "21s", "-32768 .. 32767", "2"),
                        listOf("const/high16", "21h", "low 16 bits zero, e.g. 0x3f800000", "2"),
                        listOf("const", "31i", "any 32 bit value", "3"),
                        listOf("const-wide/16", "21s", "-32768 .. 32767 (sign extended)", "2"),
                        listOf("const-wide/32", "31i", "-2^31 .. 2^31-1 (sign extended)", "3"),
                        listOf("const-wide/high16", "21h", "low 48 bits zero, e.g. 0x4000000000000000L", "2"),
                        listOf("const-wide", "51l", "any 64 bit value", "5"),
                    ),
                ),
                text = "One code unit is 16 bits. Smaller encodings save space, so the assembler/compiler picks the smallest that fits.",
            ),
            CheatBlock(
                "Literal arithmetic",
                table = table(
                    "Instruction family", "Literal range",
                    rows = listOf(
                        listOf("add-int/lit8, rsub-int/lit8, mul-int/lit8 ...", "-128 .. 127"),
                        listOf("add-int/lit16, rsub-int/lit16, mul-int/lit16 ...", "-32768 .. 32767"),
                    ),
                ),
            ),
            CheatBlock(
                "Writing literals in smali",
                table = table(
                    "Written", "Meaning",
                    rows = listOf(
                        listOf("0x1f, -0x1", "int in hexadecimal"),
                        listOf("0x7fffffffffffffffL", "long (suffix L)"),
                        listOf("0x7ft", "byte (suffix t)"),
                        listOf("0x7fffs", "short (suffix s)"),
                        listOf("1.5f", "float"),
                        listOf("1.5", "double"),
                        listOf("'a'", "char"),
                        listOf("true / false", "booleans are ints: const/4 v0, 0x1 or 0x0"),
                    ),
                ),
                text = "Floats and doubles are stored by their IEEE 754 bit patterns: 1.0f is 0x3f800000, which const/high16 can load.",
            ),
        ),
    )

    val all: List<CheatSheet> = listOf(
        typeDescriptors, accessFlags, directives, registerRules, invokeKinds, patterns, ifTests, literalRanges,
    )

    fun byId(id: String): CheatSheet? = all.firstOrNull { it.id == id }
}
