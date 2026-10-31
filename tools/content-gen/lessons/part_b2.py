"""Beginner track, part 2 (orders 6-10)."""
from common import card, code, exercise, java, lesson, question, text, tip, warning

L = "beginner"

LESSONS = [
    # ------------------------------------------------------------------------------------------------ 6. Methods
    lesson(
        id="methods",
        level=L,
        order=6,
        title="Methods",
        summary="""
            Every piece of executable Smali lives inside a .method block. Learn how a method header is built from
            access flags, a name and a prototype, how .registers and .locals size the frame, and what makes
            constructors, static initialisers, abstract and native methods special.
        """,
        minutes=10,
        sections=[
            text("""
                A method starts with a .method line and ends with .end method. The header line has three parts:
                - access flags such as public, private, static or final,
                - the method name,
                - the prototype: parameter descriptors in parentheses followed by the return descriptor.

                So .method public static max(II)I declares a public static method named max that takes two ints
                and returns an int. Inside the block come an optional register directive (.registers or .locals),
                optional metadata such as .param and .annotation, and then the instructions.
            """, title="Anatomy of a method"),
            code("""
                .class public abstract Lcom/acme/demo/Shape;
                .super Ljava/lang/Object;

                .field private final name:Ljava/lang/String;

                .method public constructor <init>(Ljava/lang/String;)V
                    .registers 2
                    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                    iput-object p1, p0, Lcom/acme/demo/Shape;->name:Ljava/lang/String;
                    return-void
                .end method

                .method public final getName()Ljava/lang/String;
                    .locals 1
                    iget-object v0, p0, Lcom/acme/demo/Shape;->name:Ljava/lang/String;
                    return-object v0
                .end method

                .method public abstract area()D
                .end method

                .method public static native nativeVersion()I
                .end method
            """, title="Four kinds of method in one class"),
            java("""
                package com.acme.demo;

                public abstract class Shape {
                    private final String name;

                    public Shape(String name) {
                        this.name = name;
                    }

                    public final String getName() {
                        return name;
                    }

                    public abstract double area();

                    public static native int nativeVersion();
                }
            """, title="Equivalent Java"),
            text("""
                .registers N gives the total number of registers in the frame, parameters included. .locals N
                gives only the registers you use for local values; the assembler adds the parameter registers on
                top. In the example, getName() uses .locals 1, which is the same as .registers 2: one local (v0)
                plus one parameter register for this (p0, which is v1).

                Both forms produce identical bytecode. baksmali prints .locals by default because it stays correct
                when you add or remove parameters, while .registers matches the registers_size field stored in
                the DEX file.
            """, title=".registers versus .locals"),
            text("""
                Constructors are ordinary methods with the reserved name <init>, a void return type and the
                constructor flag. The first thing a constructor normally does is call a superclass (or another
                own) <init> with invoke-direct on p0. The static initialiser is named <clinit>, is static, takes
                no parameters and runs once when the class is initialised; it is where static fields that need
                code get their values.

                Methods marked abstract or native have no body at all: no register directive and no instructions,
                just the header followed by .end method. Their code is supplied by a subclass or by a native
                library loaded at runtime.

                Some flags only show up in compiled code. A Java synchronized method is marked
                declared-synchronized, and the compiler inserts explicit monitor-enter and monitor-exit
                instructions to do the locking. Compilers may also add bridge, synthetic and varargs flags.
            """, title="Constructors, initialisers and bodiless methods"),
            warning("""
                Execution must never run past the last instruction. Every path through a method has to end in a
                return-*, a throw or a goto back into the code, otherwise the verifier rejects the class. The
                return instruction must also match the prototype: return-void for V, return-wide for J and D,
                return-object for references and return for the other primitives.
            """, title="Do not fall off the end"),
            tip("""
                A constructor that returns without calling a superclass or this-class <init> on p0 leaves the
                object uninitialised, and the verifier refuses it. When you add a constructor by hand, copy the
                invoke-direct {p0}, ...-><init> line first.
            """),
        ],
        exercises=[
            exercise(
                "methods-ex1",
                """
                    Add a static initialiser to Lcom/acme/demo/Shape; that stores the string "Shape" in the static
                    field TAG of type String.
                """,
                starter="""
                    .method static constructor <clinit>()V
                        .registers 1
                        # load the string and store it in Lcom/acme/demo/Shape;->TAG:Ljava/lang/String;
                    .end method
                """,
                solution="""
                    .method static constructor <clinit>()V
                        .registers 1
                        const-string v0, "Shape"
                        sput-object v0, Lcom/acme/demo/Shape;->TAG:Ljava/lang/String;
                        return-void
                    .end method
                """,
                hint="Static fields are written with sput-object; the method must still end with return-void.",
            ),
            exercise(
                "methods-ex2",
                """
                    Write a public static method square(I)I that returns its argument multiplied by itself. Use
                    .locals rather than .registers.
                """,
                solution="""
                    .method public static square(I)I
                        .locals 1
                        mul-int v0, p0, p0
                        return v0
                    .end method
                """,
                hint="A static method has no this, so its single int parameter is p0.",
            ),
            exercise(
                "methods-ex3",
                """
                    An instance method is declared as .method public update(IJ)V with .registers 6. Rewrite the
                    register directive using .locals.
                """,
                solution="""
                    Use .locals 2. The parameters need 4 registers: one for this, one for the int and two for
                    the long. 6 total registers minus 4 parameter registers leaves 2 locals.
                """,
                hint="Count this as one register and every long or double as two.",
            ),
        ],
        quiz=[
            question(
                "methods-q1",
                "What does this method header declare?",
                [
                    "A static method taking a String and an int, returning boolean",
                    "An instance method taking a String and an int, returning boolean",
                    "A static method taking a boolean, returning a String and an int",
                    "A static method taking an int, returning a String",
                ],
                0,
                """
                    The flags say public static, the parentheses list the parameters (Ljava/lang/String; and I),
                    and the descriptor after them is the return type, Z for boolean.
                """,
                code="""
                    .method public static check(Ljava/lang/String;I)Z
                        .locals 1
                        const/4 v0, 0x1
                        return v0
                    .end method
                """,
            ),
            question(
                "methods-q2",
                "An instance method has one long parameter and uses .locals 3. What is its .registers value?",
                ["4", "5", "6", "3"],
                2,
                "Parameters need 3 registers (1 for this, 2 for the long). 3 locals + 3 parameter registers = 6.",
            ),
            question(
                "methods-q3",
                "What is the name of a class's static initialiser in Smali?",
                ["<init>", "<clinit>", "static", "<static>"],
                1,
                "<clinit> is the class initialiser. <init> is the name of every instance constructor.",
            ),
            question(
                "methods-q4",
                "Which methods are written without .registers/.locals and without instructions?",
                [
                    "abstract and native methods",
                    "static and final methods",
                    "constructors and static initialisers",
                    "private and synthetic methods",
                ],
                0,
                """
                    abstract methods are implemented by subclasses and native methods by a native library, so
                    their .method block contains no code.
                """,
            ),
            question(
                "methods-q5",
                "A method declared to return J ends with return v0. What is wrong?",
                [
                    "Nothing, return works for every type",
                    "It must use return-wide because long is a 64-bit value",
                    "It must use return-object because long is boxed",
                    "It must use return-void",
                ],
                1,
                """
                    long (J) and double (D) are wide values held in a register pair and must be returned with
                    return-wide. The verifier rejects a mismatched return instruction.
                """,
            ),
        ],
        related=["invoke-direct", "return-void", "return", "return-wide", "return-object", "iput-object", "sput-object"],
    ),
    # ---------------------------------------------------------------------------------------------- 7. Registers
    lesson(
        id="registers",
        level=L,
        order=7,
        title="Registers",
        summary="""
            Dalvik is a register machine: every method works on a fixed array of 32-bit registers instead of an
            operand stack. Learn the v and p naming schemes, where parameters and this live, how long and double
            values use register pairs, and why some instructions can only reach v0 to v15.
        """,
        minutes=12,
        sections=[
            text("""
                Each method invocation gets a frame of N registers, where N is the registers_size from the method's
                code. Registers are numbered v0 to vN-1 and each holds 32 bits: an int, a float, a reference, or
                half of a 64-bit value. Registers are untyped; the instruction that reads a register decides how
                its bits are interpreted, and the verifier checks that this is consistent.

                The incoming arguments are copied into the last registers of the frame. Smali gives those
                registers a second name: p0, p1 and so on. If a method has N registers and its parameters need P
                of them, then pX is simply another name for v(N - P + X).
            """, title="A frame of registers"),
            code("""
                .method public sum(IJ)J
                    .registers 6
                    # v0-v1: local pair       p0 = v2: this
                    # p1 = v3: int a          p2-p3 = v4-v5: long b
                    int-to-long v0, p1
                    add-long/2addr v0, p2
                    return-wide v0
                .end method
            """, title="Mapping p registers to v registers"),
            java("""
                public long sum(int a, long b) {
                    return a + b;
                }
            """, title="Equivalent Java"),
            text("""
                For an instance method, p0 always holds this, and the declared parameters start at p1. Static
                methods have no this, so their first parameter is p0.

                long (J) and double (D) values are wide: they occupy two consecutive registers, and you always name
                the lower one. In the example the long b lives in p2 and p3, and the result of int-to-long fills v0
                and v1. Never use the upper half of a pair on its own, and remember that writing to either half
                destroys the wide value.

                To size a frame, count the parameter registers (1 for this, 2 for every J or D, 1 for everything
                else) and add the locals you need. The sum method needs 4 parameter registers and 2 locals, hence
                .registers 6, or equivalently .locals 2.
            """, title="this, parameters and wide pairs"),
            text("""
                Register numbers are encoded inside the instruction, and many formats only have room for 4 bits:
                - 4-bit fields (v0 to v15): formats 11n, 12x, 22c, 22s, 22t and each argument of 35c. That covers
                  const/4, move, the /2addr arithmetic, iget/iput, instance-of, if-eq and the non-range invokes.
                - 8-bit fields (v0 to v255): formats such as 11x, 21c, 21s, 22b and 23x, for example
                  const-string, sget, add-int and move-result.
                - 16-bit fields (v0 to v65535): move/16, the source of move/from16, and the first register of
                  /range invokes (format 3rc).

                Because parameters sit at the top of the frame, a method with many locals can push p0 above v15.
                The value then has to be copied to a low register (for example with move-object/from16) before a
                4-bit instruction can use it.
            """, title="How far an instruction can reach"),
            code("""
                .method public static negate(I)I
                    .locals 20
                    # p0 is v20 here, beyond the 4-bit operands of neg-int (format 12x)
                    move/from16 v0, p0
                    neg-int v0, v0
                    return v0
                .end method
            """, title="Copying a high register down"),
            warning("""
                Increasing .locals in a hand-edited method shifts every p register upwards. Code such as
                iget v0, p0, ... that assembled before can suddenly fail with a register-out-of-range error.
                Either keep .locals small or move the parameter into a low register first.
            """, title="Editing .locals moves the parameters"),
        ],
        exercises=[
            exercise(
                "registers-ex1",
                """
                    A method is declared .method public static compute(JDI)V with .locals 3. Which v register does
                    each p register correspond to?
                """,
                solution="""
                    The parameters need 5 registers (2 for J, 2 for D, 1 for I), so the frame has 8 registers.
                    p0/p1 = v3/v4 hold the long, p2/p3 = v5/v6 hold the double and p4 = v7 holds the int.
                """,
                hint="Static methods have no this. Parameters fill the last registers of the frame in order.",
            ),
            exercise(
                "registers-ex2",
                """
                    Write an instance method twice(I)I that returns its argument multiplied by 2, using a single
                    local register.
                """,
                solution="""
                    .method public twice(I)I
                        .locals 1
                        add-int v0, p1, p1
                        return v0
                    .end method
                """,
                hint="In an instance method p0 is this, so the int argument is p1.",
            ),
            exercise(
                "registers-ex3",
                """
                    This getter fails to assemble with a register-out-of-range error. Explain why and fix it
                    without reducing .locals.
                """,
                starter="""
                    .method public getCount()I
                        .locals 16
                        iget v0, p0, Lcom/acme/demo/Counter;->count:I
                        return v0
                    .end method
                """,
                solution="""
                    .method public getCount()I
                        .locals 16
                        move-object/from16 v1, p0
                        iget v0, v1, Lcom/acme/demo/Counter;->count:I
                        return v0
                    .end method
                """,
                hint="With 16 locals, p0 is v16. iget (format 22c) only has 4-bit register fields.",
                check_starter=False,
            ),
        ],
        quiz=[
            question(
                "registers-q1",
                "An instance method (Ljava/lang/String;I)V declares .registers 5. Which register is p0?",
                ["v0", "v1", "v2", "v4"],
                2,
                "Parameters need 3 registers (this, String, int), so they occupy v2 to v4 and p0 is v2.",
            ),
            question(
                "registers-q2",
                "In an instance method, what does p0 contain?",
                ["The first declared parameter", "this", "The return value", "Always zero"],
                1,
                "Instance methods receive the object they were called on as an implicit first parameter in p0.",
            ),
            question(
                "registers-q3",
                "How is a long parameter stored?",
                [
                    "In one 64-bit register",
                    "In two consecutive registers, named by the lower one",
                    "On a separate operand stack",
                    "As a boxed java.lang.Long reference",
                ],
                1,
                "Registers are 32 bits wide, so J and D values take a pair such as p1/p2, referred to as p1.",
            ),
            question(
                "registers-q4",
                "Why does this method copy p0 into v0 before negating it?",
                [
                    "neg-int cannot read parameter registers",
                    "p0 is v20, but neg-int (format 12x) only encodes registers v0 to v15",
                    "move/from16 converts the int to a long",
                    "Parameters are read-only and must be copied before use",
                ],
                1,
                """
                    With .locals 20 and one parameter, p0 maps to v20. Format 12x stores each register in 4 bits,
                    so neg-int v0, p0 would not assemble; move/from16 can read a 16-bit source register.
                """,
                code="""
                    .method public static negate(I)I
                        .locals 20
                        move/from16 v0, p0
                        neg-int v0, v0
                        return v0
                    .end method
                """,
            ),
            question(
                "registers-q5",
                "A static method (JI)V uses .locals 2. What is its total register count?",
                ["3", "4", "5", "6"],
                2,
                "The long needs 2 registers and the int 1, so 3 parameter registers + 2 locals = 5.",
            ),
        ],
        related=["move", "move/from16", "move/16", "move-object/from16", "move-wide", "int-to-long", "add-long/2addr", "neg-int"],
    ),
    # --------------------------------------------------------------------------------------------- 8. Data types
    lesson(
        id="data-types",
        level=L,
        order=8,
        title="Data Types & Descriptors",
        summary="""
            Smali writes every type as a compact descriptor: single letters for primitives, L...; for classes and
            [ for arrays. Learn to read and write descriptors fluently and map them to Java and Kotlin types.
        """,
        minutes=9,
        sections=[
            text("""
                Primitive types use one letter each:
                - V void (return types only)
                - Z boolean
                - B byte
                - S short
                - C char
                - I int
                - J long
                - F float
                - D double

                The two letters people mix up most are Z (boolean, because B is already byte) and J (long, because
                L is used for classes).
            """, title="Primitive descriptors"),
            text("""
                A class or interface is written as L, the fully qualified name with slashes instead of dots, and a
                closing semicolon: java.lang.String becomes Ljava/lang/String;. Nested classes use the $ separator
                that the compiler puts in the binary name, so java.util.Map.Entry becomes Ljava/util/Map$Entry;.

                An array prefixes its element type with one [ per dimension: int[] is [I, String[] is
                [Ljava/lang/String; and int[][] is [[I. The DEX format allows at most 255 dimensions.
            """, title="Classes and arrays"),
            code("""
                .class public Lcom/acme/demo/Types;
                .super Ljava/lang/Object;

                .field public flag:Z
                .field public small:B
                .field public letter:C
                .field public count:I
                .field public total:J
                .field public ratio:D
                .field public label:Ljava/lang/String;
                .field public grid:[[I
                .field public names:[Ljava/lang/String;
                .field public entry:Ljava/util/Map$Entry;
            """, title="Descriptors in field declarations"),
            java("""
                package com.acme.demo;

                import java.util.Map;

                public class Types {
                    public boolean flag;
                    public byte small;
                    public char letter;
                    public int count;
                    public long total;
                    public double ratio;
                    public String label;
                    public int[][] grid;
                    public String[] names;
                    public Map.Entry entry;
                }
            """, title="Equivalent Java"),
            text("""
                Kotlin compiles to the same descriptors. Non-null Int, Long, Boolean and friends become primitives
                (I, J, Z), while nullable Int? needs an object and becomes Ljava/lang/Integer;. IntArray is [I but
                Array<Int> is [Ljava/lang/Integer;. Any maps to Ljava/lang/Object;, a Unit return type becomes V,
                and String is Ljava/lang/String;.

                Generic type arguments are erased: List<String> and List<Int> are both Ljava/util/List; in a
                descriptor. The original generic signature is kept separately in a Signature annotation.
            """, title="Mapping Kotlin types"),
            tip("""
                Inside a register, boolean, byte, short and char are all plain 32-bit int values (a boolean is 0
                or 1). The descriptor only matters where the type is declared: in field and method references and
                in typed instructions such as iget-boolean or aget-char.
            """),
            warning("""
                Descriptors use slashes, but Class.getName() returns dots ("java.lang.String"), and for arrays a
                mixed form ("[Ljava.lang.String;"). When you build class names for reflection, do not paste a
                descriptor in unchanged.
            """, title="Descriptors are not Java class names"),
        ],
        exercises=[
            exercise(
                "data-types-ex1",
                """
                    Write the descriptors for: char[][], java.lang.Thread.State, Map<String, List<int[]>> and
                    Kotlin's LongArray.
                """,
                solution="""
                    char[][] is [[C. java.lang.Thread.State is a nested class, so it is Ljava/lang/Thread$State;.
                    Generics are erased, so Map<String, List<int[]>> is just Ljava/util/Map;. LongArray is [J.
                """,
                hint="Count the brackets, use $ for nested classes and drop everything between < and >.",
            ),
            exercise(
                "data-types-ex2",
                """
                    Declare three fields: a public static final long TIMEOUT, a private byte array buffer, and a
                    protected field retries that holds a Kotlin Int?.
                """,
                solution="""
                    .field public static final TIMEOUT:J

                    .field private buffer:[B

                    .field protected retries:Ljava/lang/Integer;
                """,
                hint="A nullable Int cannot be a primitive, so it is boxed.",
            ),
        ],
        quiz=[
            question(
                "data-types-q1",
                "What is the descriptor for boolean?",
                ["B", "Z", "b", "Ljava/lang/Boolean;"],
                1,
                "boolean is Z. B is byte, and Ljava/lang/Boolean; is the boxed wrapper class.",
            ),
            question(
                "data-types-q2",
                "Which Java type does this field have?",
                ["String", "String[]", "String[][]", "List<String>"],
                2,
                "Each [ adds one array dimension, so two brackets around Ljava/lang/String; make String[][].",
                code=".field private table:[[Ljava/lang/String;",
            ),
            question(
                "data-types-q3",
                "What does the descriptor J stand for?",
                ["long", "java.lang.Object", "a JNI handle", "short"],
                0,
                "J is long; the letter L was already taken by class descriptors.",
            ),
            question(
                "data-types-q4",
                "A Kotlin property is declared as var retries: Int?. Which descriptor does its field use?",
                ["I", "Ljava/lang/Integer;", "Lkotlin/Int;", "[I"],
                1,
                "A nullable Int must be able to hold null, so Kotlin boxes it as java.lang.Integer.",
            ),
            question(
                "data-types-q5",
                "How is the nested class com.app.Outer.Inner written as a descriptor?",
                ["Lcom/app/Outer/Inner;", "Lcom/app/Outer$Inner;", "Lcom.app.Outer.Inner;", "Lcom/app/Inner;"],
                1,
                "Nested classes use their binary name, which joins outer and inner names with $.",
            ),
        ],
        related=["iget-boolean", "iget-wide", "iget-object", "aget-char", "new-array", "check-cast"],
    ),
    # ----------------------------------------------------------------------------------------------- 9. Constants
    lesson(
        id="constants",
        level=L,
        order=9,
        title="Constants",
        summary="""
            Dalvik has a family of const instructions that differ only in how many bits of literal they can
            encode. Learn the exact range of each, how to pick the smallest one, how floats and doubles are
            loaded as raw bit patterns, and how strings and classes are loaded.
        """,
        minutes=11,
        sections=[
            text("""
                For 32-bit values (int, float, boolean, char, references):
                - const/4 vA, #+B: 4-bit signed literal, -8 to 7, and vA must be v0 to v15. One code unit.
                - const/16 vAA, #+BBBB: 16-bit signed literal, -32768 to 32767. Two code units.
                - const/high16 vAA, #+BBBB0000: sets the top 16 bits and clears the bottom 16. Two code units.
                - const vAA, #+BBBBBBBB: any 32-bit value. Three code units.

                For 64-bit values (long, double), written to a register pair:
                - const-wide/16 vAA, #+BBBB: 16-bit signed, sign-extended to 64 bits.
                - const-wide/32 vAA, #+BBBBBBBB: 32-bit signed, sign-extended.
                - const-wide/high16 vAA, #+BBBB000000000000: sets only the top 16 bits.
                - const-wide vAA, #+BBBBBBBBBBBBBBBB: any 64-bit value, five code units.

                Compilers always choose the shortest form that can represent the value, and so should you.
            """, title="The numeric const family"),
            code("""
                const/4 v0, 0x7
                const/4 v1, -0x8
                const/16 v2, 0x3e8
                const/high16 v3, 0x10000
                const v4, 0x186a0
                const-wide/16 v5, 0x64
                const-wide/32 v7, 0x186a0
                const-wide v9, 0x123456789abcL
            """, title="Picking the smallest encoding"),
            text("""
                There are no float-specific const instructions. A float is loaded as its 32-bit IEEE 754 bit
                pattern and a double as its 64-bit pattern. Many common values have all-zero low bits, which is
                exactly what the high16 variants are for: 1.0f is 0x3f800000 and 1.0 (double) is
                0x3ff0000000000000, so both fit in a high16 instruction. A value like 3.14f (0x4048f5c3) needs the
                full const.

                baksmali prints the decoded value as a comment, which makes these easy to read. The number zero
                works for every type: const/4 v0, 0x0 is the int 0, the float 0.0f, false, and the null reference.
            """, title="Floats, doubles and null"),
            code("""
                .method public static defaults()V
                    .locals 6
                    const/high16 v0, 0x3f800000    # 1.0f
                    const v1, 0x4048f5c3           # 3.14f
                    const-wide/high16 v2, 0x3ff0000000000000L    # 1.0
                    const/4 v4, 0x0                # null, false or 0
                    const/4 v5, 0x1                # true
                    return-void
                .end method
            """, title="Bit patterns for floating-point values"),
            text("""
                const-string vAA, string@BBBB loads a reference to a string from the DEX string table. The index is
                16 bits, so a DEX file with more than 65,536 strings needs const-string/jumbo (a 32-bit index) for
                the later ones. In Smali you write the literal itself; the assembler picks the index.

                const-class vAA, type@BBBB loads the java.lang.Class object for a type, which is what Foo.class
                compiles to.
            """, title="Strings and classes"),
            code("""
                const-string v0, "smali"
                const-string/jumbo v1, "used when the string index is above 0xFFFF"
                const-class v2, Ljava/lang/String;
                const-class v3, [I
            """, title="Loading references"),
            tip("""
                Literals are signed. -1 fits in const/4 (const/4 v0, -0x1), but 0xFFFF does not fit in const/16
                because it means 65535, not -1. Use const v0, 0xffff for that value.
            """),
        ],
        exercises=[
            exercise(
                "constants-ex1",
                """
                    Choose the smallest instruction for each value: 7, -9, 1000, 65536 (0x10000), 100000 and the
                    long 5.
                """,
                solution="""
                    7 uses const/4. -9 is outside -8..7, so const/16. 1000 uses const/16. 65536 is 0x00010000,
                    whose low 16 bits are zero, so const/high16 v0, 0x10000 works. 100000 (0x186a0) needs const.
                    The long 5 uses const-wide/16.
                """,
                hint="Check the signed ranges first, then see whether the low 16 bits are all zero.",
            ),
            exercise(
                "constants-ex2",
                "Write a static method two()F that returns 2.0f using a single const instruction.",
                solution="""
                    .method public static two()F
                        .locals 1
                        const/high16 v0, 0x40000000    # 2.0f
                        return v0
                    .end method
                """,
                hint="2.0f is 0x40000000 in IEEE 754, and floats are returned with the plain return instruction.",
            ),
            exercise(
                "constants-ex3",
                """
                    Load the string "Hello" into v0 and the Class object for java.lang.Integer into v1.
                """,
                solution="""
                    const-string v0, "Hello"
                    const-class v1, Ljava/lang/Integer;
                """,
                hint="const-class takes a type descriptor, not a dotted class name.",
            ),
        ],
        quiz=[
            question(
                "constants-q1",
                "Which literal range does const/4 support?",
                ["0 to 15", "-8 to 7", "-16 to 15", "-128 to 127"],
                1,
                "const/4 (format 11n) stores a signed 4-bit literal, giving -8 to 7.",
            ),
            question(
                "constants-q2",
                "Which instruction is needed to load 0x7fffffff into v0?",
                ["const/16", "const/high16", "const", "const-wide/32"],
                2,
                """
                    The value does not fit in 16 signed bits and its low half is not zero, so only the full 32-bit
                    const can encode it. const-wide/32 would write a register pair.
                """,
            ),
            question(
                "constants-q3",
                "What float value does v0 hold after this instruction?",
                ["1.0f", "0.5f", "16256.0f", "It is an int, not a float"],
                0,
                """
                    0x3f800000 is the IEEE 754 single-precision pattern for 1.0. The register just holds bits; it
                    becomes a float when a float instruction or a F-typed return uses it.
                """,
                code="const/high16 v0, 0x3f800000",
            ),
            question(
                "constants-q4",
                "When does a compiler use const-string/jumbo instead of const-string?",
                [
                    "For strings longer than 255 characters",
                    "When the string's index in the DEX string table is above 65535",
                    "For strings containing non-ASCII characters",
                    "For strings used in more than one class",
                ],
                1,
                "const-string has a 16-bit string index; /jumbo has 32 bits for very large DEX files.",
            ),
            question(
                "constants-q5",
                "How do you put null into a register?",
                ["const-string v0, \"null\"", "const/4 v0, 0x0", "const-class v0, Ljava/lang/Void;", "return-void"],
                1,
                "null is the zero reference, so the smallest const with literal 0 loads it.",
            ),
        ],
        related=[
            "const/4", "const/16", "const", "const/high16", "const-wide/16", "const-wide/32", "const-wide",
            "const-wide/high16", "const-string", "const-string/jumbo", "const-class",
        ],
    ),
    # --------------------------------------------------------------------------------------- 10. Method signatures
    lesson(
        id="method-signatures",
        level=L,
        order=10,
        title="Method Signatures",
        summary="""
            Every call in Smali names its target with a full method reference. Learn the reference syntax, how to
            read long parameter lists, how overloading, varargs and generics show up, and what the shorty
            descriptor in the DEX file is.
        """,
        minutes=10,
        sections=[
            text("""
                A method reference has four parts, written without spaces:
                - the class that declares or inherits the method, as a descriptor: Ljava/lang/String;
                - the arrow ->
                - the method name: indexOf
                - the prototype: parameter descriptors in parentheses, then the return descriptor: (Ljava/lang/String;I)I

                Put together: Ljava/lang/String;->indexOf(Ljava/lang/String;I)I. Field references look similar
                but use a colon and a single type: Lcom/acme/demo/User;->name:Ljava/lang/String;.
            """, title="Reading a reference"),
            text("""
                Long prototypes are just descriptors written back to back. Read them left to right: a primitive is
                one letter, a class runs up to the next semicolon, and [ attaches to whatever follows it.

                (ILjava/lang/String;[JLjava/util/Map;Z)Landroid/os/Bundle; therefore reads as int, String, long[],
                Map and boolean, returning a Bundle.
            """, title="Splitting a long parameter list"),
            code("""
                .method public static describe(I)Ljava/lang/String;
                    .locals 4
                    const/4 v0, 0x1
                    new-array v0, v0, [Ljava/lang/Object;
                    invoke-static {p0}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
                    move-result-object v1
                    const/4 v2, 0x0
                    aput-object v1, v0, v2
                    const-string v1, "%d items"
                    invoke-static {v1, v0}, Ljava/lang/String;->format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;
                    move-result-object v0
                    return-object v0
                .end method
            """, title="Varargs are plain arrays"),
            java("""
                public static String describe(int count) {
                    return String.format("%d items", count);
                }
            """, title="Equivalent Java"),
            text("""
                The one-line Java call hides two conversions that Smali makes visible. The int is boxed with
                Integer.valueOf because the varargs parameter is Object..., and the arguments are packed into a new
                Object[] because a varargs parameter is an ordinary array in the descriptor. The varargs access
                flag on the declaration is only a hint for compilers.

                Overloads are distinct references: append(I) and append(Ljava/lang/String;) are different
                methods. The return type is part of the reference too, so the bytecode can contain two methods
                that differ only in return type. Java source cannot, but javac creates exactly that when it adds
                a synthetic bridge method for a covariant override.
            """, title="Overloads, varargs and bridges"),
            code("""
                .method public setNames(Ljava/util/List;)V
                    .locals 0
                    .annotation system Ldalvik/annotation/Signature;
                        value = {
                            "(",
                            "Ljava/util/List<",
                            "Ljava/lang/String;",
                            ">;)V"
                        }
                    .end annotation
                    return-void
                .end method
            """, title="Generics survive only in a Signature annotation"),
            tip("""
                Each prototype in the DEX file also stores a shorty descriptor: the return type followed by the
                parameters, with every class or array type collapsed to L. (ILjava/lang/String;[J)Z has the
                shorty ZILL. You will meet shorties in DEX dumps and in native (JNI) tooling.
            """),
            warning("""
                The assembler does not check that a referenced method exists. A typo in a name or a wrong return
                type assembles fine and only fails when the call is resolved at runtime, typically with
                NoSuchMethodError.
            """, title="Signatures are matched exactly"),
        ],
        exercises=[
            exercise(
                "method-signatures-ex1",
                """
                    Write the method reference for SharedPreferences.getBoolean(String key, boolean defValue),
                    declared in the interface android.content.SharedPreferences.
                """,
                solution="""
                    Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                """,
                hint="Interfaces use the same reference syntax as classes. boolean is Z.",
            ),
            exercise(
                "method-signatures-ex2",
                """
                    Two longs are stored in the register pairs v0/v1 and v2/v3. Call Math.max(long, long) and put
                    the result back in v0/v1.
                """,
                solution="""
                    invoke-static {v0, v1, v2, v3}, Ljava/lang/Math;->max(JJ)J
                    move-result-wide v0
                """,
                hint="Non-range invokes list both registers of each wide argument, and a J result needs move-result-wide.",
            ),
            exercise(
                "method-signatures-ex3",
                """
                    Translate this prototype into Java parameter and return types:
                    (Landroid/content/Context;[[ILjava/lang/CharSequence;J)[Ljava/lang/String;
                """,
                solution="""
                    Parameters: Context, int[][], CharSequence and long. Return type: String[].
                """,
                hint="Split at each semicolon and remember that [[ belongs to the I after it.",
            ),
        ],
        quiz=[
            question(
                "method-signatures-q1",
                "What does this reference describe?",
                [
                    "String.indexOf(String, int) returning int",
                    "String.indexOf(int) returning String",
                    "A field named indexOf of type int",
                    "String.indexOf(String) returning int[]",
                ],
                0,
                "The parentheses hold Ljava/lang/String; and I, and the final I is the int return type.",
                code="Ljava/lang/String;->indexOf(Ljava/lang/String;I)I",
            ),
            question(
                "method-signatures-q2",
                "How does a Java varargs parameter String... args appear in a prototype?",
                ["Ljava/lang/String;...", "[Ljava/lang/String;", "Ljava/lang/String;*", "Ljava/util/List;"],
                1,
                "Varargs compile to an ordinary array parameter; only the varargs access flag marks the method.",
            ),
            question(
                "method-signatures-q3",
                "What does List<String> become in a method descriptor?",
                ["Ljava/util/List<Ljava/lang/String;>;", "Ljava/util/List;", "[Ljava/lang/String;", "Ljava/lang/Object;"],
                1,
                """
                    Type arguments are erased from descriptors. The generic form is only kept in the
                    dalvik.annotation.Signature annotation.
                """,
            ),
            question(
                "method-signatures-q4",
                "What is the shorty descriptor of the prototype (J[ILjava/lang/Object;)V?",
                ["VJLL", "JILV", "VJ[IL", "V3"],
                0,
                "The shorty starts with the return type (V), then lists parameters with every reference or array type as L.",
            ),
            question(
                "method-signatures-q5",
                "A hand-written invoke uses the wrong return type in its method reference. What happens?",
                [
                    "smali refuses to assemble the file",
                    "The return value is converted automatically",
                    "It assembles, but the call fails at runtime with NoSuchMethodError",
                    "The method is called and the result is discarded",
                ],
                2,
                """
                    The assembler only checks syntax. Method resolution happens at runtime and matches the name and
                    the full prototype, including the return type.
                """,
            ),
        ],
        related=["invoke-static", "invoke-virtual", "invoke-interface", "move-result-wide", "new-array", "aput-object"],
    ),
]

FLASHCARDS = [
    card("fc-b2-01", "Registers", "Where do a method's parameters live in its register frame?",
         "In the last registers of the frame. With N registers and P parameter registers, pX is v(N - P + X)."),
    card("fc-b2-02", "Registers", "What does p0 hold in an instance method?",
         "this, the object the method was called on. Declared parameters start at p1."),
    card("fc-b2-03", "Directives", "What is the difference between .registers and .locals?",
         ".registers N is the total register count including parameters. .locals N counts only non-parameter "
         "registers; the assembler adds the parameter registers."),
    card("fc-b2-04", "Registers", "How are long and double values stored in registers?",
         "In a pair of consecutive registers, always referred to by the lower number (e.g. v0 means v0 and v1).",
         code="""
            const-wide/16 v0, 0x64
            return-wide v0
         """),
    card("fc-b2-05", "Descriptors", "Which descriptor letters stand for boolean and long?",
         "Z is boolean and J is long. B (byte) and L (class prefix) were already taken."),
    card("fc-b2-06", "Descriptors", "What Java type is [[I?", "int[][], a two-dimensional int array."),
    card("fc-b2-07", "Descriptors", "How is the nested class java.util.Map.Entry written as a descriptor?",
         "Ljava/util/Map$Entry; using the binary name with $ between outer and inner class."),
    card("fc-b2-08", "Opcodes", "What literal range does const/4 accept?",
         "A signed 4-bit value from -8 to 7, and the destination must be v0 to v15."),
    card("fc-b2-09", "Opcodes", "What does const/high16 do?",
         "Loads a 16-bit literal into the top half of a 32-bit register and clears the bottom half. Common for "
         "float constants such as 1.0f.",
         code="const/high16 v0, 0x3f800000    # 1.0f"),
    card("fc-b2-10", "Opcodes", "When is const-string/jumbo used?",
         "When the string's index in the DEX string table does not fit in 16 bits (above 65535)."),
    card("fc-b2-11", "Formats", "Which registers can format 12x instructions such as move or add-int/2addr address?",
         "Only v0 to v15, because both register fields are 4 bits wide."),
    card("fc-b2-12", "Descriptors", "What is a shorty descriptor?",
         "The compact form of a prototype stored in proto_ids: return type first, then parameters, with every "
         "reference or array type written as L. (ILjava/lang/String;[J)Z becomes ZILL."),
    card("fc-b2-13", "Directives", "What are a constructor and a static initialiser called in Smali?",
         "Instance constructors are <init> and the static initialiser is <clinit>; both carry the constructor flag.",
         code="""
            .method public constructor <init>()V
                .registers 1
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method
         """),
    card("fc-b2-14", "Directives", "Which methods have no register directive and no instructions?",
         "abstract and native methods: their .method line is followed directly by .end method."),
]
